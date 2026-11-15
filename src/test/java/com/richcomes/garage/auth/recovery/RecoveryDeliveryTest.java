package com.richcomes.garage.auth.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryDeliveryTest {
    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        var provider = (ObjectProvider<JavaMailSender>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender); return provider;
    }
    @Test void disabledDeliveryDoesNotNeedSmtpAndNeverSends() {
        var delivery = new SmtpRecoveryDelivery(provider(null), false, "", "");
        assertFalse(delivery.available());
        assertThrows(RecoveryFailure.class, () -> delivery.send("owner@example.invalid", "RESET_PASSWORD", "hidden"));
    }
    @Test void enabledDeliveryRequiresSmtpAndFixedHttpsPage() {
        assertThrows(IllegalStateException.class, () -> new SmtpRecoveryDelivery(provider(null), true,
                "support@example.invalid", "https://client.example.invalid/recover"));
        for (String url : new String[]{"http://client.example.invalid", "https://client.example.invalid/?token=x",
                "https://client.example.invalid/#fragment", "https://user@client.example.invalid"}) {
            assertThrows(IllegalStateException.class, () -> new SmtpRecoveryDelivery(provider(mock(JavaMailSender.class)),
                    true, "support@example.invalid", url));
        }
    }
    @Test void tokenAppearsOnlyInRecipientMessageAndLinkFragment() {
        var sender = mock(JavaMailSender.class);
        var delivery = new SmtpRecoveryDelivery(provider(sender), true, "support@example.invalid",
                "https://client.example.invalid/recover");
        delivery.send("owner@example.invalid", "RESET_PASSWORD", "synthetic-token");
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertArrayEquals(new String[]{"owner@example.invalid"}, captor.getValue().getTo());
        assertTrue(captor.getValue().getText().contains("#purpose=RESET_PASSWORD&token=synthetic-token"));
    }
    @Test void deliveryFailureDoesNotExposeProviderMessage() {
        var sender = mock(JavaMailSender.class);
        doThrow(new IllegalStateException("private provider content")).when(sender).send(any(SimpleMailMessage.class));
        var delivery = new SmtpRecoveryDelivery(provider(sender), true, "support@example.invalid",
                "https://client.example.invalid/recover");
        var error = assertThrows(RecoveryFailure.class, () -> delivery.send("owner@example.invalid", "RESET_PASSWORD", "hidden"));
        assertFalse(error.toString().contains("private provider")); assertNull(error.getCause());
    }
}
