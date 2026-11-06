
package com.richcomes.garage.inventory.chatbot;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryChatServiceManualTest {

    @Test
    @Disabled("Enable manually for a real Groq API test")
    void shouldAnswerUsingInventoryTool() {

        // Mock tool: no real database connection
        ChatbotInventoryTool inventoryTool =
                mock(ChatbotInventoryTool.class);

        when(inventoryTool.getLowStockItems())
                .thenReturn(List.of(
                        new ChatbotInventoryTool.LowStockItem(
                                "OIL-001",
                                "Engine Oil",
                                "3",
                                "10"
                        )
                ));

        InventoryChatService chatService =
                new InventoryChatService(inventoryTool, new ChatService());

        String answer = chatService.askInventoryQuestion(
                "Check the garage database using the available " +
                        "tool and tell me which items have low stock."
        );

        System.out.println("AI ANSWER: " + answer);

        assertNotNull(answer);
        assertFalse(answer.isBlank());

        // AI must actually request the inventory tool
        verify(inventoryTool, times(1)).getLowStockItems();
    }
}
