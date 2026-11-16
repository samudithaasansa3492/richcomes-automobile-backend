package com.richcomes.provisioning;

import com.richcomes.garage.auth.entity.UserAccount;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import static com.richcomes.provisioning.ProvisioningException.Code.*;

/** Explicit operator-only provisioning. No Employee, business grants, or recovery address is created. */
public final class DeveloperAdminProvisioner {
    private final FirstAdminProvisioner.ConnectionFactory connections;
    private final PasswordEncoder encoder;
    public DeveloperAdminProvisioner(FirstAdminProvisioner.ConnectionFactory connections, PasswordEncoder encoder) {
        this.connections = connections; this.encoder = encoder;
    }
    public long provision(String username, char[] password) {
        try {
            String normalized;
            try { normalized = UserAccount.normalizeUsername(username); }
            catch (IllegalArgumentException e) { throw new ProvisioningException(INVALID_INPUT, "Invalid username"); }
            if (password == null || password.length < 15 || password.length > 72
                    || CharBuffer.wrap(password).chars().allMatch(Character::isWhitespace)) {
                throw new ProvisioningException(INVALID_INPUT, "Invalid password length");
            }
            var bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
            try {
                if (bytes.remaining() > 72) throw new ProvisioningException(INVALID_INPUT, "Password exceeds UTF-8 byte limit");
            } finally { if (bytes.hasArray()) Arrays.fill(bytes.array(), (byte) 0); }
            try (Connection connection = connections.open()) {
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(false);
                try {
                    long role;
                    try (var query = prepare(connection, "SELECT id FROM garage.roles WHERE code = 'ADMIN' FOR UPDATE");
                         var rows = query.executeQuery()) {
                        if (!rows.next()) throw new ProvisioningException(SEED_REQUIRED, "Apply approved technical identity migrations first");
                        role = rows.getLong(1);
                    }
                    try (var query = prepare(connection, "SELECT id FROM garage.user_accounts WHERE username = ?")) {
                        query.setString(1, normalized);
                        try (var rows = query.executeQuery()) {
                            if (rows.next()) throw new ProvisioningException(USERNAME_EXISTS, "Username already exists");
                        }
                    }
                    long id;
                    try (var insert = connection.prepareStatement("""
                            INSERT INTO garage.user_accounts
                                (identity_kind, employee_id, username, password_hash, enabled, created_at, updated_at)
                            VALUES ('TECHNICAL', NULL, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """, Statement.RETURN_GENERATED_KEYS)) {
                        insert.setQueryTimeout(15);
                        insert.setString(1, normalized); insert.setString(2, encoder.encode(CharBuffer.wrap(password)));
                        insert.executeUpdate();
                        try (var rows = insert.getGeneratedKeys()) { if (!rows.next()) throw new SQLException(); id = rows.getLong(1); }
                    }
                    try (var insert = prepare(connection, "INSERT INTO garage.user_account_roles (user_account_id, role_id) VALUES (?, ?)")) {
                        insert.setLong(1, id); insert.setLong(2, role); insert.executeUpdate();
                    }
                    try (var insert = prepare(connection, """
                            INSERT INTO garage.security_audit_events (id, actor_id, target_id, event_code, occurred_at)
                            VALUES (?, NULL, ?, 'TECHNICAL_ACCOUNT_PROVISIONED', CURRENT_TIMESTAMP)
                            """)) {
                        insert.setObject(1, UUID.randomUUID()); insert.setLong(2, id); insert.executeUpdate();
                    }
                    connection.commit(); return id;
                } catch (SQLException | RuntimeException e) {
                    try { connection.rollback(); } catch (SQLException ignored) { }
                    throw e;
                }
            } catch (SQLException e) {
                throw new ProvisioningException(DATABASE_FAILURE, "Database operation failed; no credentials are displayed");
            }
        } finally { if (password != null) Arrays.fill(password, '\0'); }
    }
    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        var query = connection.prepareStatement(sql); query.setQueryTimeout(15); return query;
    }
}
