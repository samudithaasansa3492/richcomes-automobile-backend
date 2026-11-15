package com.richcomes.garage.auth.recovery;

import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Explicitly enabled only after SMTP and the client-owned HTTPS recovery page are configured. */
@Component
public class SmtpRecoveryDelivery implements RecoveryDelivery {
    private final JavaMailSender sender;
    private final String from;
    private final String page;
    private final boolean enabled;
    public SmtpRecoveryDelivery(ObjectProvider<JavaMailSender> provider,
            @Value("${app.recovery.enabled:false}") boolean enabled,
            @Value("${app.recovery.from:}") String from,
            @Value("${app.recovery.page:}") String page) {
        this.sender = provider.getIfAvailable(); this.enabled = enabled; this.from = from; this.page = page;
        if (enabled) {
            var uri = URI.create(page);
            if (sender instanceof org.springframework.mail.javamail.JavaMailSenderImpl smtp) {
                var properties = smtp.getSession().getProperties();
                if (smtp.getProtocol() == null) smtp.setProtocol("smtp");
                properties.setProperty("mail.debug", "false");
                if (!java.util.Set.of("smtp", "smtps").contains(smtp.getProtocol())) {
                    throw new IllegalStateException("Recovery delivery requires SMTP over TLS");
                }
                for (String protocol : new String[]{"smtp", "smtps"}) {
                    String prefix = "mail." + protocol + ".";
                    properties.setProperty(prefix + "connectiontimeout", "5000");
                    properties.setProperty(prefix + "timeout", "5000");
                    properties.setProperty(prefix + "writetimeout", "5000");
                    properties.setProperty(prefix + "ssl.checkserveridentity", "true");
                    properties.remove(prefix + "ssl.trust");
                }
                if (!"true".equalsIgnoreCase(properties.getProperty("mail.smtp.ssl.enable"))) {
                    properties.setProperty("mail.smtp.starttls.enable", "true");
                    properties.setProperty("mail.smtp.starttls.required", "true");
                }
                smtp.getSession().setDebug(false);
            }
            if (sender == null || from.isBlank() || from.contains("\r") || from.contains("\n")
                    || !"https".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
                throw new IllegalStateException("Recovery delivery requires SMTP, sender and a fixed HTTPS page");
            }
        }
    }
    public boolean available() { return enabled; }
    public void send(String email, String purpose, String token) {
        if (!enabled) throw new RecoveryFailure();
        try {
            var message = new SimpleMailMessage();
            message.setFrom(from); message.setTo(email);
            message.setSubject("Richcomes account verification");
            // Fragment keeps the secret out of ordinary HTTP request/access-log URLs.
            message.setText("Use this single-use link within 15 minutes: " + page
                    + "#purpose=" + purpose + "&token=" + token
                    + "\nIf you did not request this, ignore this message. Never share this link with support.");
            sender.send(message);
        } catch (RuntimeException exception) {
            // SMTP exceptions can contain addresses or message content. Do not propagate them.
            throw new RecoveryFailure();
        }
    }
}
