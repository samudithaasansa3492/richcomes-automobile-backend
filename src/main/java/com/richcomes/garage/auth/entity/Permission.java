package com.richcomes.garage.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "permissions", schema = "garage")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100, updatable = false)
    private String code;

    @Column(nullable = false, length = 255)
    private String description;

    protected Permission() {
        // Required by JPA.
    }

    public Permission(String code, String description) {
        if (code == null || code.length() > 100
                || !code.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Permission code must use resource:action format");
        }
        this.code = code;
        this.description = Objects.requireNonNull(description, "description");
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
}
