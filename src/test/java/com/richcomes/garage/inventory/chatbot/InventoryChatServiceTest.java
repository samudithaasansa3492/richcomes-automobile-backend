
package com.richcomes.garage.inventory.chatbot;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class InventoryChatServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // PREPARE MOCK GROQ RESPONSES
    private TestContext prepareTest(
            String toolName,
            Map<String, Object> arguments,
            String finalAnswer) throws Exception {

        ChatService groqClient = mock(ChatService.class);

        ChatbotInventoryTool inventoryTool =
                mock(ChatbotInventoryTool.class);

        String argumentsJson =
                mapper.writeValueAsString(arguments);

        String toolCallJson = mapper.writeValueAsString(
                Map.of(
                        "id", "call_test_001",
                        "type", "function",
                        "function", Map.of(
                                "name", toolName,
                                "arguments", argumentsJson
                        )
                )
        );

        String firstResponse =
                "{\"choices\":[{\"message\":{" +
                        "\"content\":null," +
                        "\"tool_calls\":[" + toolCallJson + "]" +
                        "}}]}";

        String finalResponse = mapper.writeValueAsString(
                Map.of(
                        "choices", List.of(
                                Map.of(
                                        "message", Map.of(
                                                "content", finalAnswer
                                        )
                                )
                        )
                )
        );

        when(groqClient.sendToGroq(anyMap()))
                .thenReturn(
                        mapper.readTree(firstResponse),
                        mapper.readTree(finalResponse)
                );

        InventoryChatService service =
                new InventoryChatService(
                        inventoryTool,
                        groqClient
                );

        return new TestContext(
                groqClient,
                inventoryTool,
                service
        );
    }

    private record TestContext(
            ChatService groqClient,
            ChatbotInventoryTool inventoryTool,
            InventoryChatService service
    ) {
    }

    // TEST 1: LOW-STOCK ITEMS
    @Test
    void shouldUseInventoryToolAndReturnAIAnswer()
            throws Exception {

        TestContext test = prepareTest(
                "get_low_stock_items",
                Map.of(),
                "Engine Oil stock eka adui."
        );

        when(test.inventoryTool().getLowStockItems())
                .thenReturn(List.of(
                        new ChatbotInventoryTool.LowStockItem(
                                "OIL-001",
                                "Engine Oil",
                                "3",
                                "10"
                        )
                ));

        String answer = test.service().askInventoryQuestion(
                "Low stock items monawada?"
        );

        assertEquals("Engine Oil stock eka adui.", answer);

        verify(test.inventoryTool()).getLowStockItems();

        verify(test.groqClient(), times(2))
                .sendToGroq(anyMap());
    }

    // TEST 2: FIND STOCK ITEM BY CODE
    @Test
    void shouldFindStockItemByCode() throws Exception {

        TestContext test = prepareTest(
                "get_stock_item_by_code",
                Map.of("itemCode", "OIL-001"),
                "Premium Engine Oil 1L stock eka 0. Item eka inactive."
        );

        when(test.inventoryTool().getStockItemByCode("OIL-001"))
                .thenReturn(Optional.of(
                        new ChatbotInventoryTool.StockItemDetail(
                                "OIL-001",
                                "Premium Engine Oil 1L",
                                "Engine Oil",
                                "0",
                                "5",
                                false
                        )
                ));

        String answer = test.service().askInventoryQuestion(
                "OIL-001 item eke stock kiyada?"
        );

        assertTrue(answer.contains("0"));
        assertTrue(answer.contains("inactive"));

        verify(test.inventoryTool())
                .getStockItemByCode("OIL-001");

        verify(test.groqClient(), times(2))
                .sendToGroq(anyMap());
    }

    // TEST 3: SEARCH ITEMS BY NAME
    @Test
    void shouldSearchStockItemsByName() throws Exception {

        TestContext test = prepareTest(
                "search_stock_items_by_name",
                Map.of("itemName", "Engine Oil"),
                "Engine Oil items 2k thiyenawa."
        );

        when(test.inventoryTool()
                .searchStockItemsByName("Engine Oil"))
                .thenReturn(List.of(
                        new ChatbotInventoryTool.StockItemDetail(
                                "OIL-001",
                                "Premium Engine Oil 1L",
                                "Engine Oil",
                                "0",
                                "5",
                                false
                        ),
                        new ChatbotInventoryTool.StockItemDetail(
                                "OIL-002",
                                "Synthetic Engine Oil",
                                "Engine Oil",
                                "12",
                                "5",
                                true
                        )
                ));

        String answer = test.service().askInventoryQuestion(
                "Engine Oil items monawada?"
        );

        assertEquals(
                "Engine Oil items 2k thiyenawa.",
                answer
        );

        verify(test.inventoryTool())
                .searchStockItemsByName("Engine Oil");

        verify(test.groqClient(), times(2))
                .sendToGroq(anyMap());
    }

    // TEST 4: GET SUPPLIERS
    @Test
    void shouldGetSuppliersUsingAITool() throws Exception {

        TestContext test = prepareTest(
                "get_suppliers",
                Map.of(),
                "ABC Auto Parts supplier active wela thiyenawa."
        );

        when(test.inventoryTool().getSuppliers())
                .thenReturn(List.of(
                        new ChatbotInventoryTool.SupplierInfo(
                                1L,
                                "ABC Auto Parts",
                                "Kamal",
                                "0771234567",
                                "abc@example.com",
                                true
                        )
                ));

        String answer = test.service().askInventoryQuestion(
                "Supplier list eka pennanna"
        );

        assertEquals(
                "ABC Auto Parts supplier active wela thiyenawa.",
                answer
        );

        verify(test.inventoryTool()).getSuppliers();

        verify(test.groqClient(), times(2))
                .sendToGroq(anyMap());
    }

    // TEST 5: GET STOCK CATEGORIES
    @Test
    void shouldGetStockCategoriesUsingAITool()
            throws Exception {

        TestContext test = prepareTest(
                "get_stock_categories",
                Map.of(),
                "Engine Oil saha Brake Parts categories thiyenawa."
        );

        when(test.inventoryTool().getCategories())
                .thenReturn(List.of(
                        new ChatbotInventoryTool.CategoryInfo(
                                1L,
                                "Engine Oil",
                                "Engine oils and lubricants",
                                true
                        ),
                        new ChatbotInventoryTool.CategoryInfo(
                                2L,
                                "Brake Parts",
                                "Brake system components",
                                false
                        )
                ));

        String answer = test.service().askInventoryQuestion(
                "Stock categories monawada?"
        );

        assertEquals(
                "Engine Oil saha Brake Parts categories thiyenawa.",
                answer
        );

        verify(test.inventoryTool()).getCategories();

        verify(test.groqClient(), times(2))
                .sendToGroq(anyMap());

        verifyNoMoreInteractions(test.inventoryTool());
    }

    // TEST 6: GET RECENT GRNS
    @Test
    void shouldGetRecentGRNsUsingAITool()
            throws Exception {

        TestContext test = prepareTest(
                "get_recent_grns",
                Map.of(),
                "GRN-TEST-001 eka CONFIRMED wela thiyenawa."
        );

        when(test.inventoryTool().getRecentGRNs())
                .thenReturn(List.of(
                        new ChatbotInventoryTool.GRNSummary(
                                1L,
                                "GRN-TEST-001",
                                "ABC Auto Parts",
                                "CONFIRMED",
                                "2026-11-01T10:30",
                                "2026-11-02T09:00"
                        )
                ));

        String answer = test.service().askInventoryQuestion(
                "Latest GRNs monawada?"
        );

        assertEquals(
                "GRN-TEST-001 eka CONFIRMED wela thiyenawa.",
                answer
        );

        verify(test.inventoryTool()).getRecentGRNs();

        verify(test.groqClient(), times(2))
                .sendToGroq(anyMap());

        verifyNoMoreInteractions(test.inventoryTool());
    }

    // TEST 7: RECENT STOCK MOVEMENTS
    @Test
    void shouldGetRecentStockMovementsUsingAITool()
            throws Exception {

        TestContext test = prepareTest(
                "get_recent_stock_movements",
                Map.of(),
                "OIL-001 item eken units 2k DAMAGE wela thiyenawa."
        );

        when(test.inventoryTool().getRecentStockMovements())
                .thenReturn(List.of(
                        new ChatbotInventoryTool.StockMovementSummary(
                                1L,
                                "OIL-001",
                                "Engine Oil",
                                "DAMAGE",
                                2,
                                15,
                                13,
                                "2026-11-05T14:30"
                        )
                ));

        String answer = test.service().askInventoryQuestion(
                "Recent stock movements monawada?"
        );

        assertEquals(
                "OIL-001 item eken units 2k DAMAGE wela thiyenawa.",
                answer
        );

        verify(test.inventoryTool())
                .getRecentStockMovements();

        verify(test.groqClient(), times(2))
                .sendToGroq(anyMap());

        verifyNoMoreInteractions(test.inventoryTool());
    }

    // TEST 8: FILTER DAMAGE MOVEMENTS
    @Test
    void shouldGetDamageMovementsUsingFilteredAITool()
            throws Exception {

        TestContext test = prepareTest(
                "get_stock_movements_by_type",
                Map.of("transactionType", "DAMAGE"),
                "OIL-001 item eken units 3k DAMAGE wela thiyenawa."
        );

        when(test.inventoryTool()
                .getStockMovementsByType("DAMAGE"))
                .thenReturn(List.of(
                        new ChatbotInventoryTool.StockMovementSummary(
                                2L,
                                "OIL-001",
                                "Engine Oil",
                                "DAMAGE",
                                3,
                                20,
                                17,
                                "2026-11-06T09:15"
                        )
                ));

        String answer = test.service().askInventoryQuestion(
                "Damage stock records pennanna"
        );

        assertEquals(
                "OIL-001 item eken units 3k DAMAGE wela thiyenawa.",
                answer
        );

        verify(test.inventoryTool())
                .getStockMovementsByType("DAMAGE");

        verify(test.inventoryTool(), never())
                .getRecentStockMovements();

        verify(test.groqClient(), times(2))
                .sendToGroq(anyMap());

        verifyNoMoreInteractions(test.inventoryTool());
    }

    // TEST 9: FILTER WASTAGE MOVEMENTS
    @Test
    void shouldGetWastageMovementsUsingFilteredAITool()
            throws Exception {

        TestContext test = prepareTest(
                "get_stock_movements_by_type",
                Map.of("transactionType", "WASTAGE"),
                "FILTER-001 item eken unit 1k WASTAGE wela thiyenawa."
        );

        when(test.inventoryTool()
                .getStockMovementsByType("WASTAGE"))
                .thenReturn(List.of(
                        new ChatbotInventoryTool.StockMovementSummary(
                                3L,
                                "FILTER-001",
                                "Oil Filter",
                                "WASTAGE",
                                1,
                                8,
                                7,
                                "2026-11-06T10:00"
                        )
                ));

        String answer = test.service().askInventoryQuestion(
                "Wastage records monawada?"
        );

        assertEquals(
                "FILTER-001 item eken unit 1k WASTAGE wela thiyenawa.",
                answer
        );

        verify(test.inventoryTool())
                .getStockMovementsByType("WASTAGE");

        verify(test.inventoryTool(), never())
                .getRecentStockMovements();

        verify(test.groqClient(), times(2))
                .sendToGroq(anyMap());

        verifyNoMoreInteractions(test.inventoryTool());
    }

    // TEST 10: REJECT MISSING TRANSACTION TYPE
    @Test
    void shouldRejectMissingTransactionType()
            throws Exception {

        TestContext test = prepareTest(
                "get_stock_movements_by_type",
                Map.of(),
                "This answer should never be used."
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> test.service().askInventoryQuestion(
                        "Show damage records"
                )
        );

        assertTrue(
                error.getMessage().contains(
                        "Inventory AI request failed"
                )
        );

        verifyNoInteractions(test.inventoryTool());

        // Only the initial mocked Groq request is made.
        verify(test.groqClient(), times(1))
                .sendToGroq(anyMap());
    }

    // TEST 11: REJECT INVALID TRANSACTION TYPE
    @Test
    void shouldRejectInvalidTransactionType()
            throws Exception {

        TestContext test = prepareTest(
                "get_stock_movements_by_type",
                Map.of("transactionType", "INVALID_TYPE"),
                "This answer should never be used."
        );

        when(test.inventoryTool()
                .getStockMovementsByType("INVALID_TYPE"))
                .thenThrow(
                        new IllegalArgumentException(
                                "Unsupported transaction type"
                        )
                );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> test.service().askInventoryQuestion(
                        "Show invalid stock movements"
                )
        );

        assertTrue(
                error.getMessage().contains(
                        "Inventory AI request failed"
                )
        );

        verify(test.inventoryTool())
                .getStockMovementsByType("INVALID_TYPE");

        verify(test.groqClient(), times(1))
                .sendToGroq(anyMap());
    }
}
