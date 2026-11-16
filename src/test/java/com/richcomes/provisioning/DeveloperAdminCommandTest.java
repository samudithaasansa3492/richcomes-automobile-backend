package com.richcomes.provisioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DeveloperAdminCommandTest {
    @TempDir Path temporary;

    @Test void successfulCommandUsesHiddenInputsAndClearsAllPasswordArrays() throws Exception {
        var terminal = inputs("CREATE DEVELOPER ADMIN", "same synthetic password", "same synthetic password");
        var calls = new AtomicInteger();
        int result = DeveloperAdminCommand.execute(args(), terminal, (target, dbPassword, username, password) -> {
            calls.incrementAndGet();
            assertEquals("localhost:5432/validation", target.displayName());
            assertEquals("first.admin", username);
            assertEquals("disposable-db-password", new String(dbPassword));
            assertEquals("same synthetic password", new String(password));
            return 19L;
        });
        assertEquals(0, result);
        assertEquals(1, calls.get());
        assertTrue(terminal.output.toString().contains("account ID 19"));
        assertNoSecrets(terminal);
    }

    @Test void confirmationMismatchDoesNotCallProvisioner() throws Exception {
        var terminal = inputs("CREATE DEVELOPER ADMIN", "same synthetic password", "different password");
        assertEquals(2, DeveloperAdminCommand.execute(args(), terminal, (a, b, c, d) -> {
            fail("No database connection allowed"); return 0;
        }));
        assertNoSecrets(terminal);
    }

    @Test void cancelledTargetConfirmationDoesNotReadPasswordsOrConnect() throws Exception {
        var terminal = new FakeTerminal(List.of("no"), List.of());
        assertEquals(2, DeveloperAdminCommand.execute(args(), terminal, (a, b, c, d) -> {
            fail("No database connection allowed"); return 0;
        }));
    }

    @Test void rejectsPasswordArguments() {
        var terminal = new FakeTerminal(List.of(), List.of());
        assertEquals(2, DeveloperAdminCommand.execute(new String[]{"--password", "secret", "--config", "/tmp/example"},
                terminal, (a, b, c, d) -> { fail("No connection allowed"); return 0; }));
        assertFalse(terminal.output.toString().contains("secret"));
    }

    @Test void unexpectedExceptionsNeverPrintSecretsOrStackTrace() throws Exception {
        var terminal = inputs("CREATE DEVELOPER ADMIN", "same synthetic password", "same synthetic password");
        assertEquals(4, DeveloperAdminCommand.execute(args(), terminal, (a, b, c, d) -> {
            throw new IllegalStateException("secret-value same synthetic password");
        }));
        assertFalse(terminal.output.toString().contains("secret-value"));
        assertFalse(terminal.output.toString().contains("IllegalStateException"));
        assertNoSecrets(terminal);
    }

    @Test void domainRefusalReturnsNonzeroAndClearsSecrets() throws Exception {
        var terminal = inputs("CREATE DEVELOPER ADMIN", "same synthetic password", "same synthetic password");
        assertEquals(3, DeveloperAdminCommand.execute(args(), terminal, (a, b, c, d) -> {
            throw new ProvisioningException(ProvisioningException.Code.USERNAME_EXISTS, "Administrator exists");
        }));
        assertTrue(terminal.output.toString().contains("USERNAME_EXISTS"));
        assertNoSecrets(terminal);
    }

    private String[] args() throws Exception {
        Path config = temporary.resolve("target.properties");
        Files.writeString(config, "jdbc.url=jdbc:postgresql://localhost:5432/validation?sslmode=disable\njdbc.username=disposable\n");
        return new String[]{"--config", config.toString()};
    }

    private FakeTerminal inputs(String confirm, String password, String repeat) {
        return new FakeTerminal(List.of(confirm, "first.admin"),
                List.of("disposable-db-password".toCharArray(), password.toCharArray(), repeat.toCharArray()));
    }

    private void assertNoSecrets(FakeTerminal terminal) {
        assertFalse(terminal.output.toString().contains("same synthetic password"));
        assertFalse(terminal.output.toString().contains("disposable-db-password"));
        for (char[] secret : terminal.secrets) assertArrayEquals(new char[secret.length], secret);
    }

    private static class FakeTerminal implements DeveloperAdminCommand.Terminal {
        final ArrayDeque<String> lines;
        final ArrayDeque<char[]> passwords;
        final List<char[]> secrets;
        final List<String> output = new ArrayList<>();
        FakeTerminal(List<String> lines, List<char[]> passwords) {
            this.lines = new ArrayDeque<>(lines);
            this.passwords = new ArrayDeque<>(passwords);
            this.secrets = passwords;
        }
        public String readLine(String prompt) { return lines.remove(); }
        public char[] readPassword(String prompt) { return passwords.remove(); }
        public void print(String message) { output.add(message); }
    }
}
