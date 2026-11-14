package com.richcomes.provisioning;

import com.richcomes.garage.auth.entity.UserAccount;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.richcomes.provisioning.ProvisioningException.Code.*;

/** Explicit operator tool, outside GarageApplication's component scan. No startup hook. */
public final class FirstAdminProvisioner {
    private static final Set<String> REQUIRED_PERMISSIONS = Set.of(
            "employee:read", "employee:create", "employee:update", "employee:deactivate",
            "account:read", "account:create", "account:manage");

    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private final ConnectionFactory connections;
    private final PasswordEncoder encoder;

    public FirstAdminProvisioner(ConnectionFactory connections, PasswordEncoder encoder) {
        this.connections = connections;
        this.encoder = encoder;
    }

    /** Consumes and clears password even when validation or the transaction fails. */
    public long provision(UUID employeeId, String username, char[] password) {
        try {
            if (employeeId == null) throw failure(INVALID_INPUT, "Employee ID is required");
            String normalized;
            try {
                normalized = UserAccount.normalizeUsername(username);
            } catch (IllegalArgumentException exception) {
                throw failure(INVALID_INPUT, "Invalid username format");
            }
            validatePassword(password);
            try (Connection connection = connections.open()) {
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(false);
                try {
                    long roleId = lockOwnerRole(connection);
                    rejectExistingAdmin(connection);
                    verifyPermissions(connection, roleId);
                    verifyEmployee(connection, employeeId);
                    rejectDuplicateAccount(connection, employeeId, normalized);
                    String hash = encoder.encode(CharBuffer.wrap(password));
                    long id = insertAccount(connection, employeeId, normalized, hash);
                    try (var statement = prepare(connection,
                            "INSERT INTO garage.user_account_roles (user_account_id, role_id) VALUES (?, ?)")) {
                        statement.setLong(1, id);
                        statement.setLong(2, roleId);
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return id;
                } catch (SQLException | RuntimeException exception) {
                    try { connection.rollback(); }
                    catch (SQLException ignored) { /* Never replace the original failure or print SQL. */ }
                    throw exception;
                }
            } catch (SQLException exception) {
                // Driver messages can contain SQL parameter values. Never propagate them to the console.
                throw failure(DATABASE_FAILURE, "Database operation failed; verify the target, schema and access");
            }
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    private void validatePassword(char[] password) {
        if (password == null || password.length < 15 || password.length > 72
                || CharBuffer.wrap(password).chars().allMatch(Character::isWhitespace)) {
            throw failure(INVALID_INPUT, "Password must contain 15–72 characters and at most 72 UTF-8 bytes");
        }
        var bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        try {
            if (bytes.remaining() > 72) {
                throw failure(INVALID_INPUT, "Password must not exceed 72 UTF-8 bytes");
            }
        } finally {
            if (bytes.hasArray()) Arrays.fill(bytes.array(), (byte) 0);
        }
    }

    private long lockOwnerRole(Connection connection) throws SQLException {
        // Same stable row and lock order as AdminAccessGuard. Held until commit/rollback.
        try (var statement = prepare(connection,
                "SELECT id FROM garage.roles WHERE code = 'OWNER' FOR UPDATE");
             var rows = statement.executeQuery()) {
            if (!rows.next()) throw failure(SEED_REQUIRED, "Apply the approved role/permission seed first");
            return rows.getLong(1);
        }
    }

    private void rejectExistingAdmin(Connection connection) throws SQLException {
        try (var statement = prepare(connection, """
                SELECT COUNT(*) FROM garage.user_accounts a
                JOIN garage.employees e ON e.id = a.employee_id
                JOIN garage.user_account_roles ar ON ar.user_account_id = a.id
                JOIN garage.roles r ON r.id = ar.role_id
                WHERE a.enabled = TRUE AND e.active = TRUE AND r.code = 'OWNER'
                  AND e.position <> 'TECHNICIAN'
                  AND NOT EXISTS (SELECT 1 FROM garage.user_account_roles other
                      JOIN garage.roles other_role ON other_role.id = other.role_id
                      WHERE other.user_account_id = a.id AND other_role.code NOT IN ('OWNER', 'CASHIER'))
                """); var rows = statement.executeQuery()) {
            rows.next();
            if (rows.getLong(1) != 0) {
                throw failure(OWNER_ALREADY_EXISTS, "An active owner already exists; use the account API");
            }
        }
    }

    private void verifyPermissions(Connection connection, long roleId) throws SQLException {
        Set<String> actual = new HashSet<>();
        try (var statement = prepare(connection, """
                SELECT p.code FROM garage.permissions p
                JOIN garage.role_permissions rp ON rp.permission_id = p.id WHERE rp.role_id = ?
                """)) {
            statement.setLong(1, roleId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) actual.add(rows.getString(1));
            }
        }
        if (!actual.containsAll(REQUIRED_PERMISSIONS)) {
            throw failure(SEED_REQUIRED, "Owner role is missing required permissions");
        }
    }

    private void verifyEmployee(Connection connection, UUID employeeId) throws SQLException {
        try (var statement = prepare(connection,
                "SELECT active, position FROM garage.employees WHERE id = ? FOR UPDATE")) {
            statement.setObject(1, employeeId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw failure(EMPLOYEE_NOT_FOUND, "Employee not found");
                if (!rows.getBoolean(1)) throw failure(EMPLOYEE_INACTIVE, "Employee must be active");
                if ("TECHNICIAN".equals(rows.getString(2))) {
                    throw failure(INVALID_INPUT, "Technicians cannot have system accounts");
                }
            }
        }
    }

    private void rejectDuplicateAccount(Connection connection, UUID employeeId, String username) throws SQLException {
        try (var statement = prepare(connection,
                "SELECT employee_id, username FROM garage.user_accounts WHERE employee_id = ? OR username = ?")) {
            statement.setObject(1, employeeId);
            statement.setString(2, username);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    if (employeeId.equals(rows.getObject(1, UUID.class))) {
                        throw failure(EMPLOYEE_HAS_ACCOUNT, "Employee already has an account; no changes made");
                    }
                    throw failure(USERNAME_EXISTS, "Username already exists; no changes made");
                }
            }
        }
    }

    private long insertAccount(Connection connection, UUID employeeId, String username, String hash) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO garage.user_accounts
                    (employee_id, username, password_hash, enabled, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setQueryTimeout(15);
            statement.setObject(1, employeeId);
            statement.setString(2, username);
            statement.setString(3, hash);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Account identifier was not returned");
                return keys.getLong(1);
            }
        }
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        var statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(15);
        return statement;
    }

    private ProvisioningException failure(ProvisioningException.Code code, String message) {
        return new ProvisioningException(code, message);
    }
}
