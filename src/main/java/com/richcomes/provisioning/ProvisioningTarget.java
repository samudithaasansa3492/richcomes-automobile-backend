package com.richcomes.provisioning;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/** Explicit non-secret configuration; never loads application.properties or environment defaults. */
public record ProvisioningTarget(String jdbcUrl, String databaseUser, String displayName) {
    public static ProvisioningTarget load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (!properties.stringPropertyNames().equals(Set.of("jdbc.url", "jdbc.username"))) {
            throw new IllegalArgumentException("Only jdbc.url and jdbc.username are allowed; passwords are prompted");
        }
        String url = properties.getProperty("jdbc.url").strip();
        String user = properties.getProperty("jdbc.username").strip();
        if (!url.startsWith("jdbc:postgresql://") || user.isBlank() || user.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid PostgreSQL target configuration");
        }
        URI uri;
        try { uri = URI.create(url.substring(5)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid PostgreSQL URL"); }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getPath() == null || !uri.getPath().matches("/[A-Za-z0-9_-]+")
                || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535
                || (uri.getRawQuery() != null && !Set.of("sslmode=disable", "sslmode=require",
                        "sslmode=verify-ca", "sslmode=verify-full").contains(uri.getRawQuery()))) {
            throw new IllegalArgumentException("Use a single database URL with no embedded credentials; only sslmode is allowed");
        }
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        return new ProvisioningTarget(url, user, uri.getHost() + ":" + port + uri.getPath());
    }
}
