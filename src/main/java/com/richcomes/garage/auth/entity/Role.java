package com.richcomes.garage.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "roles", schema = "garage")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permissions", schema = "garage",
            joinColumns = @JoinColumn(name = "role_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "permission_id", nullable = false),
            uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_id"}))
    private Set<Permission> permissions = new LinkedHashSet<>();

    protected Role() {
        // Required by JPA.
    }

    public Role(String code, String description) {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,49}")) {
            throw new IllegalArgumentException("Role code must use uppercase letters, digits and underscores");
        }
        this.code = code;
        this.description = Objects.requireNonNull(description, "description");
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public Set<Permission> getPermissions() { return Collections.unmodifiableSet(permissions); }

    public void grantPermission(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        if (permissions.stream().noneMatch(existing -> existing.getCode().equals(permission.getCode()))) {
            permissions.add(permission);
        }
    }

    public void revokePermission(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        permissions.removeIf(existing -> existing.getCode().equals(permission.getCode()));
    }
}
