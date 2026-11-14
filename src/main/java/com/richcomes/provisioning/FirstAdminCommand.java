package com.richcomes.provisioning;

import com.richcomes.garage.auth.config.PasswordConfig;
import java.io.Console;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

/** Run explicitly with a terminal. No Spring context, web endpoint, migration or automatic execution. */
public final class FirstAdminCommand {
    private FirstAdminCommand() {}

    interface Terminal {
        String readLine(String prompt);
        char[] readPassword(String prompt);
        void print(String message);
    }

    @FunctionalInterface
    interface ProvisioningAction {
        long run(ProvisioningTarget target, char[] databasePassword, UUID employeeId,
                 String username, char[] accountPassword) throws Exception;
    }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("A real terminal is required for hidden password input. No database connection attempted.");
            System.exit(2);
            return;
        }
        Terminal terminal = new Terminal() {
            public String readLine(String prompt) { return console.readLine("%s", prompt); }
            public char[] readPassword(String prompt) { return console.readPassword("%s", prompt); }
            public void print(String message) { console.printf("%s%n", message); }
        };
        System.exit(execute(args, terminal, FirstAdminCommand::provision));
    }

    static int execute(String[] args, Terminal terminal, ProvisioningAction action) {
        char[] databasePassword = null;
        char[] password = null;
        char[] confirmation = null;
        try {
            if (args.length != 2 || !"--config".equals(args[0])) {
                terminal.print("Usage: FirstAdminCommand --config /absolute/path/to/target.properties");
                return 2;
            }
            var path = Path.of(args[1]);
            if (!path.isAbsolute()) {
                terminal.print("Configuration path must be absolute.");
                return 2;
            }
            var target = ProvisioningTarget.load(path);
            terminal.print("Database target: " + target.displayName());
            terminal.print("This creates an enabled OWNER account. Migrations must already be applied.");
            if (!"CREATE FIRST OWNER".equals(terminal.readLine("Type CREATE FIRST OWNER to continue: "))) {
                terminal.print("Cancelled. No database connection attempted.");
                return 2;
            }
            UUID employeeId = UUID.fromString(requiredLine(terminal, "Existing active Employee UUID: "));
            String username = requiredLine(terminal, "New account username: ");
            databasePassword = terminal.readPassword("Database password: ");
            password = terminal.readPassword("New Owner password (15–72 characters, at most 72 UTF-8 bytes): ");
            confirmation = terminal.readPassword("Confirm Owner password: ");
            if (databasePassword == null || databasePassword.length == 0 || password == null
                    || confirmation == null || !Arrays.equals(password, confirmation)) {
                terminal.print("Password input missing or confirmation does not match. No database connection attempted.");
                return 2;
            }
            long id = action.run(target, databasePassword, employeeId, username, password);
            terminal.print("Created enabled owner account ID " + id + ". No credentials are displayed.");
            return 0;
        } catch (ProvisioningException exception) {
            terminal.print(exception.getCode() + ": " + exception.getMessage());
            return exception.getCode() == ProvisioningException.Code.DATABASE_FAILURE ? 4 : 3;
        } catch (Exception exception) {
            // Do not print arbitrary parser, driver or encoder messages, stack traces or input values.
            terminal.print("Provisioning failed. Check configuration and inputs; no automatic retry was performed.");
            return 4;
        } finally {
            clear(databasePassword);
            clear(password);
            clear(confirmation);
        }
    }

    private static long provision(ProvisioningTarget target, char[] databasePassword, UUID employeeId,
                                  String username, char[] password) {
        var properties = new Properties();
        properties.setProperty("user", target.databaseUser());
        properties.setProperty("password", new String(databasePassword));
        properties.setProperty("sslmode", "require");
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "30");
        properties.setProperty("ApplicationName", "richcomes-first-admin");
        try {
            var provisioner = new FirstAdminProvisioner(
                    () -> DriverManager.getConnection(target.jdbcUrl(), properties),
                    new PasswordConfig().passwordEncoder());
            return provisioner.provision(employeeId, username, password);
        } finally {
            properties.clear();
        }
    }

    private static String requiredLine(Terminal terminal, String prompt) {
        String input = terminal.readLine(prompt);
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Missing input");
        return input.strip();
    }

    private static void clear(char[] secret) {
        if (secret != null) Arrays.fill(secret, '\0');
    }
}
