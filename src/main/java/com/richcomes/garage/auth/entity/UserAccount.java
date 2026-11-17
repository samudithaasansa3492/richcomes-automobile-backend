package com.richcomes.garage.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.richcomes.garage.employee.entity.Employee;
import com.richcomes.garage.customer.entity.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "user_accounts", schema = "garage")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", unique = true, updatable = false)
    private Employee employee;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", unique = true, updatable = false)
    private Customer customer;

    @Column(nullable = false, unique = true, length = 80, updatable = false)
    private String username;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_account_roles", schema = "garage",
            joinColumns = @JoinColumn(name = "user_account_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "role_id", nullable = false),
            uniqueConstraints = @UniqueConstraint(columnNames = {"user_account_id", "role_id"}))
    private Set<Role> roles = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "account_permissions", schema = "garage",
            joinColumns = @JoinColumn(name = "account_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "permission_id", nullable = false),
            uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "permission_id"}))
    private Set<Permission> directPermissions = new LinkedHashSet<>();

    @org.hibernate.annotations.ColumnDefault("'BUSINESS'")
    @Column(name = "identity_kind", nullable = false, updatable = false, length = 16)
    private String identityKind = "BUSINESS";

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    @JsonIgnore
    @Column(name = "recovery_email", length = 254)
    private String recoveryEmail;

    @JsonIgnore
    @Column(name = "recovery_last_sent_at")
    private Instant recoveryLastSentAt;

    // CUSTOMER-only proof metadata; never store scanned IDs, document numbers
    // or a credential in this row. The reference points to a controlled
    // garage verification case, not the customer's raw identity document.
    @Column(name = "customer_verified_at")
    private Instant customerVerifiedAt;
    @Column(name = "customer_verified_by")
    private Long customerVerifiedBy;
    @Column(name = "customer_verification_ref", length = 64)
    private String customerVerificationRef;
    @Column(name = "customer_verified_email", length = 254)
    private String customerVerifiedEmail;
    @Column(name = "customer_password_set_at")
    private Instant customerPasswordSetAt;

    public boolean isTechnical() { return "TECHNICAL".equals(identityKind); }
    public boolean isCustomer() { return "CUSTOMER".equals(identityKind); }
    public String getIdentityKind() { return identityKind; }
    public long getCredentialVersion() { return credentialVersion; }
    @JsonIgnore public String getRecoveryEmail() { return recoveryEmail; }
    @JsonIgnore public Instant getRecoveryLastSentAt() { return recoveryLastSentAt; }
    @JsonIgnore public Instant getCustomerVerifiedAt() { return customerVerifiedAt; }
    @JsonIgnore public Instant getCustomerPasswordSetAt() { return customerPasswordSetAt; }
    @JsonIgnore public boolean hasCustomerVerifiedIdentity() {
        return isCustomer() && customer != null && customerVerifiedAt != null
                && customerVerifiedBy != null && customerVerificationRef != null
                && customerVerifiedEmail != null && customer.getEmail() != null
                && customerVerifiedEmail.equalsIgnoreCase(customer.getEmail().strip());
    }
    @JsonIgnore public boolean hasCustomerChosenPassword() {
        return isCustomer() && customerPasswordSetAt != null;
    }
    public void recordCustomerIdentityProof(Long verifier, String reference, String email, Instant at) {
        if (!isCustomer() || customer == null || verifier == null || at == null
                || reference == null || !reference.matches("[A-Z0-9][A-Z0-9_-]{7,63}")
                || email == null || customer.getEmail() == null
                || !email.equalsIgnoreCase(customer.getEmail().strip())
                || customerPasswordSetAt != null) {
            throw new IllegalArgumentException("Invalid customer identity attestation");
        }
        customerVerifiedAt = at;
        customerVerifiedBy = verifier;
        customerVerificationRef = reference;
        customerVerifiedEmail = email.strip().toLowerCase(Locale.ROOT);
        // Any older invitation and JWT become invalid when proof is reissued.
        revokeTokens();
    }
    public void setCustomerInitialPassword(String encodedHash, Instant at) {
        if (!hasCustomerVerifiedIdentity() || customerPasswordSetAt != null || isEnabled() || at == null) {
            throw new IllegalStateException("Customer must be verified and disabled");
        }
        resetPasswordHash(encodedHash);
        customerPasswordSetAt = at;
    }
    public void recordRecoveryDelivery(Instant time) { recoveryLastSentAt = time; }
    public void verifyRecoveryEmail(String email) { recoveryEmail = Objects.requireNonNull(email); }
    public void revokeTokens() { credentialVersion = Math.incrementExact(credentialVersion); }
    public void resetPasswordHash(String hash) { changePasswordHash(hash); revokeTokens(); }

    /** Operator-only creation; business HTTP APIs never call this factory. */
    public static UserAccount technical(String username, String hash, Role admin) {
        if (!"ADMIN".equals(admin.getCode())) throw new IllegalArgumentException("ADMIN role required");
        var account = new UserAccount();
        account.identityKind = "TECHNICAL";
        account.username = normalizeUsername(username);
        account.changePasswordHash(hash);
        account.assignRole(admin);
        return account;
    }

    /** Staff-verified enrollment only; never expose this factory on a public endpoint. */
    public static UserAccount customer(Customer customer, String username, String hash, Role role) {
        Objects.requireNonNull(customer, "customer");
        if (!"CUSTOMER".equals(Objects.requireNonNull(role, "role").getCode())) {
            throw new IllegalArgumentException("CUSTOMER role required");
        }
        var account = new UserAccount();
        account.identityKind = "CUSTOMER";
        account.customer = customer;
        account.username = normalizeUsername(username);
        account.changePasswordHash(hash);
        account.assignRole(role);
        return account;
    }

    @jakarta.persistence.Version
    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected UserAccount() {
        // Required by JPA.
    }

    /** The caller must encode the password before constructing an account. */
    public UserAccount(Employee employee, String username, String passwordHash) {
        this.employee = Objects.requireNonNull(employee, "employee");
        this.username = normalizeUsername(username);
        changePasswordHash(passwordHash);
    }

    public static String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Username is required");
        }
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,79}")) {
            throw new IllegalArgumentException("Username must contain 3–80 letters, digits, dots, underscores or hyphens");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public Customer getCustomer() { return customer; }
    public String getUsername() { return username; }
    @JsonIgnore
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<Role> getRoles() { return Collections.unmodifiableSet(roles); }
    public Set<Permission> getDirectPermissions() { return Collections.unmodifiableSet(directPermissions); }

    /** Explicit per-CASHIER delegation; never change role-wide permissions. */
    public void grantDirectPermission(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        directPermissions.removeIf(existing -> existing.getCode().equals(permission.getCode()));
        directPermissions.add(permission);
    }

    public void revokeDirectPermission(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        directPermissions.removeIf(existing -> existing.getCode().equals(permission.getCode()));
    }

    public void enable() { enabled = true; }
    public void disable() { enabled = false; }

    /** Accepts an encoded password, never a raw password. Encoding belongs in the service layer. */
    public void changePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank() || passwordHash.length() > 255) {
            throw new IllegalArgumentException("A password hash of at most 255 characters is required");
        }
        this.passwordHash = passwordHash;
    }

    public void assignRole(Role role) {
        Objects.requireNonNull(role, "role");
        if (roles.stream().noneMatch(existing -> existing.getCode().equals(role.getCode()))) {
            roles.add(role);
        }
    }

    public void removeRole(Role role) {
        Objects.requireNonNull(role, "role");
        roles.removeIf(existing -> existing.getCode().equals(role.getCode()));
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
