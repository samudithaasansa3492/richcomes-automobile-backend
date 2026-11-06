
package com.richcomes.garage.inventory.chatbot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceIntegrationTest {

    // Run only when GROQ_API_KEY exists and is not blank.
    @Test
    @EnabledIfEnvironmentVariable(
            named = "GROQ_API_KEY",
            matches = ".*\\S.*"
    )
    void shouldGetResponseFromGroqAI() {

        ChatService chatService = new ChatService();

        String response = chatService.askAI(
                "What is a garage management system? Reply briefly."
        );

        assertNotNull(response);
        assertFalse(response.isBlank());

        System.out.println("AI RESPONSE: " + response);
    }
}
