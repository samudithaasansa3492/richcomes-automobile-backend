
package com.richcomes.garage.inventory.chatbot;

import com.richcomes.garage.inventory.stockitem.dto.responce.StockItemResponse;
import com.richcomes.garage.inventory.stockitem.service.StockItemService;

import com.richcomes.garage.inventory.supplier.dto.responce.SupplierResponse;
import com.richcomes.garage.inventory.supplier.service.SupplierService;

import com.richcomes.garage.inventory.category.dto.response.StockCategoryResponse;
import com.richcomes.garage.inventory.category.service.StockCategoryService;

import com.richcomes.garage.inventory.grn.dto.response.GRNResponse;
import com.richcomes.garage.inventory.grn.enums.GRNStatus;
import com.richcomes.garage.inventory.grn.service.GRNService;

import com.richcomes.garage.inventory.stockmovement.dto.response.StockMovementResponse;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.service.StockMovementService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatbotInventoryToolTest {

    private StockItemService stockItemService;
    private SupplierService supplierService;
    private StockCategoryService categoryService;
    private GRNService grnService;
    private StockMovementService stockMovementService;

    private ChatbotInventoryTool chatbotInventoryTool;

    @BeforeEach
    void setUp() {

        stockItemService = mock(StockItemService.class);
        supplierService = mock(SupplierService.class);
        categoryService = mock(StockCategoryService.class);
        grnService = mock(GRNService.class);
        stockMovementService = mock(StockMovementService.class);

        chatbotInventoryTool = new ChatbotInventoryTool(
                stockItemService,
                supplierService,
                categoryService,
                grnService,
                stockMovementService
        );
    }

    // TEST 1: RETURN LOW-STOCK ITEMS
    @Test
    void shouldReturnLowStockItems() {

        StockItemResponse item = new StockItemResponse();

        item.setItemCode("OIL-001");
        item.setItemName("Engine Oil");
        item.setCurrentQuantity(3);
        item.setReorderLevel(10);

        when(stockItemService.getLowStockItems())
                .thenReturn(List.of(item));

        var result = chatbotInventoryTool.getLowStockItems();

        assertEquals(1, result.size());
        assertEquals("OIL-001", result.get(0).itemCode());
        assertEquals("Engine Oil", result.get(0).itemName());
        assertEquals("3", result.get(0).currentQuantity());
        assertEquals("10", result.get(0).reorderLevel());

        verify(stockItemService).getLowStockItems();
    }

    // TEST 2: EMPTY LOW-STOCK LIST
    @Test
    void shouldReturnEmptyListWhenNoLowStockItems() {

        when(stockItemService.getLowStockItems())
                .thenReturn(List.of());

        var result = chatbotInventoryTool.getLowStockItems();

        assertTrue(result.isEmpty());

        verify(stockItemService).getLowStockItems();
    }

    // TEST 3: LIMIT LOW-STOCK RESULTS TO 20
    @Test
    void shouldLimitResultsToTwentyItems() {

        List<StockItemResponse> items = new ArrayList<>();

        for (int i = 1; i <= 25; i++) {

            StockItemResponse item = new StockItemResponse();

            item.setItemCode("ITEM-" + i);
            item.setItemName("Stock Item " + i);
            item.setCurrentQuantity(2);
            item.setReorderLevel(10);

            items.add(item);
        }

        when(stockItemService.getLowStockItems())
                .thenReturn(items);

        var result = chatbotInventoryTool.getLowStockItems();

        assertEquals(20, result.size());

        verify(stockItemService).getLowStockItems();
    }

    // TEST 4: FIND STOCK ITEM BY CODE
    @Test
    void shouldFindStockItemByCodeUsingService() {

        StockItemResponse item = new StockItemResponse();

        item.setItemCode("OIL-001");
        item.setItemName("Premium Engine Oil 1L");
        item.setCategoryName("Engine Oil");
        item.setCurrentQuantity(0);
        item.setReorderLevel(5);
        item.setActive(false);

        when(stockItemService.getItemByCode("OIL-001"))
                .thenReturn(item);

        var result =
                chatbotInventoryTool.getStockItemByCode("OIL-001");

        assertTrue(result.isPresent());
        assertEquals("OIL-001", result.get().itemCode());
        assertEquals("0", result.get().currentQuantity());
        assertFalse(result.get().active());

        verify(stockItemService).getItemByCode("OIL-001");
        verify(stockItemService, never()).getAllItems();
    }

    // TEST 5: GET SUPPLIERS
    @Test
    void shouldReturnSupplierInformation() {

        SupplierResponse supplier = new SupplierResponse(
                1L,
                "ABC Auto Parts",
                "123456789",
                "Kamal",
                "0771234567",
                "abc@example.com",
                "Colombo",
                true
        );

        when(supplierService.getAllSuppliers())
                .thenReturn(List.of(supplier));

        var result = chatbotInventoryTool.getSuppliers();

        assertEquals(1, result.size());
        assertEquals(
                "ABC Auto Parts",
                result.get(0).supplierName()
        );
        assertEquals(
                "Kamal",
                result.get(0).contactPerson()
        );
        assertEquals(
                "0771234567",
                result.get(0).phone()
        );
        assertEquals(
                "abc@example.com",
                result.get(0).email()
        );
        assertTrue(result.get(0).active());

        verify(supplierService).getAllSuppliers();
    }

    // TEST 6: GET STOCK CATEGORIES
    @Test
    void shouldReturnStockCategories() {

        StockCategoryResponse category =
                new StockCategoryResponse(
                        1L,
                        "Engine Oil",
                        "Engine oils and lubricants",
                        true
                );

        when(categoryService.getAllCategories())
                .thenReturn(List.of(category));

        var result = chatbotInventoryTool.getCategories();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("Engine Oil", result.get(0).name());
        assertEquals(
                "Engine oils and lubricants",
                result.get(0).description()
        );
        assertTrue(result.get(0).active());

        verify(categoryService).getAllCategories();
    }

    // TEST 7: GET RECENT GRNS
    @Test
    void shouldReturnRecentGRNSummaries() {

        GRNResponse grn = new GRNResponse();

        grn.setId(1L);
        grn.setGrnNumber("GRN-TEST-001");
        grn.setSupplierName("ABC Auto Parts");
        grn.setStatus(GRNStatus.CONFIRMED);
        grn.setCreatedAt(
                LocalDateTime.of(2026, 11, 1, 10, 30)
        );
        grn.setReceivedAt(
                LocalDateTime.of(2026, 11, 2, 9, 0)
        );

        when(grnService.getAllGRNs())
                .thenReturn(List.of(grn));

        var result = chatbotInventoryTool.getRecentGRNs();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals(
                "GRN-TEST-001",
                result.get(0).grnNumber()
        );
        assertEquals(
                "ABC Auto Parts",
                result.get(0).supplierName()
        );
        assertEquals(
                "CONFIRMED",
                result.get(0).status()
        );
        assertEquals(
                "2026-11-01T10:30",
                result.get(0).createdAt()
        );
        assertEquals(
                "2026-11-02T09:00",
                result.get(0).receivedAt()
        );

        verify(grnService).getAllGRNs();

        verify(grnService, never()).confirmGRN(anyLong());
        verify(grnService, never()).cancelGRN(anyLong());
    }

    // TEST 8: RECENT STOCK MOVEMENTS WITH PAGINATION
    @Test
    void shouldReturnRecentStockMovements() {

        StockMovementResponse movement =
                new StockMovementResponse();

        movement.setId(1L);
        movement.setStockItemId(10L);
        movement.setItemCode("OIL-001");
        movement.setItemName("Engine Oil");
        movement.setTransactionType(
                StockTransactionType.DAMAGE
        );
        movement.setQuantity(2);
        movement.setQuantityBefore(15);
        movement.setQuantityAfter(13);
        movement.setCreatedAt(
                LocalDateTime.of(2026, 11, 5, 14, 30)
        );

        // UPDATED: Mock the paginated service method
        when(stockMovementService.getRecentTransactions())
                .thenReturn(List.of(movement));

        var result =
                chatbotInventoryTool.getRecentStockMovements();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("OIL-001", result.get(0).itemCode());
        assertEquals("Engine Oil", result.get(0).itemName());
        assertEquals("DAMAGE", result.get(0).transactionType());
        assertEquals(2, result.get(0).quantity());
        assertEquals(15, result.get(0).quantityBefore());
        assertEquals(13, result.get(0).quantityAfter());
        assertEquals(
                "2026-11-05T14:30",
                result.get(0).createdAt()
        );

        // UPDATED: Verify paginated method is used
        verify(stockMovementService).getRecentTransactions();

        // Ensure old full-history query is NOT used
        verify(stockMovementService, never())
                .getAllTransactions();

        // Chatbot must never modify stock
        verify(stockMovementService, never())
                .recordMovement(any());
    }

    // TEST 9: FILTER DAMAGE AND WASTAGE
    @Test
    void shouldFilterStockMovementsByType() {

        StockMovementResponse damage =
                new StockMovementResponse();

        damage.setId(2L);
        damage.setItemCode("OIL-001");
        damage.setItemName("Engine Oil");
        damage.setTransactionType(
                StockTransactionType.DAMAGE
        );
        damage.setQuantity(3);
        damage.setQuantityBefore(20);
        damage.setQuantityAfter(17);

        StockMovementResponse wastage =
                new StockMovementResponse();

        wastage.setId(3L);
        wastage.setItemCode("FILTER-001");
        wastage.setItemName("Oil Filter");
        wastage.setTransactionType(
                StockTransactionType.WASTAGE
        );
        wastage.setQuantity(1);
        wastage.setQuantityBefore(8);
        wastage.setQuantityAfter(7);

        when(stockMovementService.getTransactionsByType(
                StockTransactionType.DAMAGE
        )).thenReturn(List.of(damage));

        when(stockMovementService.getTransactionsByType(
                StockTransactionType.WASTAGE
        )).thenReturn(List.of(wastage));

        var damageResults =
                chatbotInventoryTool.getStockMovementsByType(
                        "damage"
                );

        var wastageResults =
                chatbotInventoryTool.getStockMovementsByType(
                        "WASTAGE"
                );

        assertEquals(1, damageResults.size());
        assertEquals(
                "DAMAGE",
                damageResults.get(0).transactionType()
        );
        assertEquals(3, damageResults.get(0).quantity());

        assertEquals(1, wastageResults.size());
        assertEquals(
                "WASTAGE",
                wastageResults.get(0).transactionType()
        );
        assertEquals(
                "FILTER-001",
                wastageResults.get(0).itemCode()
        );

        verify(stockMovementService)
                .getTransactionsByType(StockTransactionType.DAMAGE);

        verify(stockMovementService)
                .getTransactionsByType(StockTransactionType.WASTAGE);

        verify(stockMovementService, never())
                .recordMovement(any());
    }
}
