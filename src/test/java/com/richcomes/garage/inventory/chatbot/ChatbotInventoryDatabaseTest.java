
package com.richcomes.garage.inventory.chatbot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Disabled("Manual Supabase integration test")
class ChatbotInventoryDatabaseTest {

    @Autowired
    private ChatbotInventoryTool inventoryTool;

    @Test
    void shouldReadLowStockItemsFromSupabase() {

        List<ChatbotInventoryTool.LowStockItem> items =
                inventoryTool.getLowStockItems();

        assertNotNull(items);
        assertTrue(items.size() <= 20);

        System.out.println("SUPABASE LOW STOCK ITEMS:");

        for (var item : items) {
            System.out.println(
                    item.itemCode() + " | " +
                            item.itemName() + " | " +
                            item.currentQuantity() + " | " +
                            item.reorderLevel()
            );
        }
    }
}
