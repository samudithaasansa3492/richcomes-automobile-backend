package com.richcomes.garage.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.richcomes.garage.employee.entity.Employee;
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
@Table(name = "user_accounts", schema = "garage")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true, updatable = false)
    private Employee employee;

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
    public String getUsername() { return username; }
    @JsonIgnore
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<Role> getRoles() { return Collections.unmodifiableSet(roles); }

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
