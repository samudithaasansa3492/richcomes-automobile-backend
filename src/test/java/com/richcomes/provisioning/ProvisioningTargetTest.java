package com.richcomes.provisioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProvisioningTargetTest {
    @TempDir Path temporary;

    @Test void loadsOnlyExplicitNonSecretTarget() throws Exception {
        var target = ProvisioningTarget.load(file("jdbc.url=jdbc:postgresql://localhost/validation?sslmode=disable\njdbc.username=disposable\n"));
        assertEquals("localhost:5432/validation", target.displayName());
        assertEquals("disposable", target.databaseUser());
    }

    @Test void rejectsPasswordPropertiesAndUnknownSettings() throws Exception {
        for (String extra : new String[]{"jdbc.password=secret", "spring.config.import=.env"}) {
            var path = file("jdbc.url=jdbc:postgresql://localhost/validation\njdbc.username=disposable\n" + extra);
            assertThrows(IllegalArgumentException.class, () -> ProvisioningTarget.load(path));
        }
    }

    @Test void rejectsCredentialUrlsOtherDatabasesAndDriverOptions() throws Exception {
        for (String url : new String[]{"jdbc:h2:mem:unsafe", "jdbc:postgresql://user:secret@localhost/validation",
                "jdbc:postgresql://localhost/validation?password=secret",
                "jdbc:postgresql://localhost/validation?options=unsafe", "jdbc:postgresql://localhost/validation#secret"}) {
            var path = file("jdbc.url=" + url + "\njdbc.username=disposable\n");
            assertThrows(IllegalArgumentException.class, () -> ProvisioningTarget.load(path));
        }
    }

    private Path file(String contents) throws Exception {
        Path path = temporary.resolve("target.properties");
        Files.writeString(path, contents);
        return path;
    }
}
