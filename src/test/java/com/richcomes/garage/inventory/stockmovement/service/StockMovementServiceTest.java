
package com.richcomes.garage.inventory.stockmovement.service;

import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;
import com.richcomes.garage.inventory.stockmovement.dto.request.StockMovementRequest;
import com.richcomes.garage.inventory.stockmovement.dto.response.StockMovementResponse;
import com.richcomes.garage.inventory.stockmovement.entity.StockTransaction;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.repository.StockTransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    private StockItemRepository itemRepository;

    @Mock
    private StockTransactionRepository transactionRepository;

    @InjectMocks
    private StockMovementService service;

    private StockItem item;

    @BeforeEach
    void setUp() {

        item = new StockItem();
        item.setId(1L);
        item.setItemCode("OIL-001");
        item.setItemName("Engine Oil 1L");
        item.setCurrentQuantity(new BigDecimal("20.000"));
        item.setActive(true);
        item.setUnit("L");
    }

    // Helper supporting whole-number and decimal quantities.
    private StockMovementRequest request(
            StockTransactionType type,
            BigDecimal quantity,
            String reason) {

        StockMovementRequest request =
                new StockMovementRequest();

        request.setStockItemId(1L);
        request.setTransactionType(type);
        request.setQuantity(quantity);
        request.setReason(reason);

        return request;
    }

    // Helper for existing integer-based test inputs.
    private StockMovementRequest request(
            StockTransactionType type,
            int quantity,
            String reason) {

        return request(
                type,
                BigDecimal.valueOf(quantity),
                reason
        );
    }

    private void mockSuccessfulMovement() {

        when(itemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(item));

        when(transactionRepository.saveAndFlush(
                any(StockTransaction.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));
    }

    // Compare BigDecimal numerically, ignoring scale.
    private void assertQuantity(
            String expected,
            BigDecimal actual) {

        assertNotNull(actual);

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }

    @Test
    void stockIn_ShouldIncreaseQuantity() {

        mockSuccessfulMovement();

        StockMovementResponse response =
                service.recordMovement(
                        request(
                                StockTransactionType.STOCK_IN,
                                10,
                                "New stock"
                        )
                );

        assertQuantity("30", item.getCurrentQuantity());
        assertQuantity("20", response.getQuantityBefore());
        assertQuantity("30", response.getQuantityAfter());

        ArgumentCaptor<StockTransaction> captor =
                ArgumentCaptor.forClass(StockTransaction.class);

        verify(transactionRepository)
                .saveAndFlush(captor.capture());

        assertEquals(
                StockTransactionType.STOCK_IN,
                captor.getValue().getTransactionType()
        );

        assertQuantity(
                "10",
                captor.getValue().getQuantity()
        );
    }

    @Test
    void stockOut_ShouldDecreaseQuantity() {

        mockSuccessfulMovement();

        StockMovementResponse response =
                service.recordMovement(
                        request(
                                StockTransactionType.STOCK_OUT,
                                5,
                                "Issued stock"
                        )
                );

        assertQuantity("15", item.getCurrentQuantity());
        assertQuantity("15", response.getQuantityAfter());
    }

    @Test
    void insufficientStock_ShouldReject() {

        when(itemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(item));

        assertThrows(
                ResponseStatusException.class,
                () -> service.recordMovement(
                        request(
                                StockTransactionType.STOCK_OUT,
                                25,
                                "Issue"
                        )
                )
        );

        assertQuantity("20", item.getCurrentQuantity());

        verify(itemRepository, never())
                .saveAndFlush(any(StockItem.class));

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void damage_ShouldReduceStock() {

        mockSuccessfulMovement();

        StockMovementResponse response =
                service.recordMovement(
                        request(
                                StockTransactionType.DAMAGE,
                                3,
                                "Damaged cans"
                        )
                );

        assertQuantity("17", item.getCurrentQuantity());
        assertQuantity("17", response.getQuantityAfter());
    }

    @Test
    void wastage_ShouldReduceStock() {

        mockSuccessfulMovement();

        StockMovementResponse response =
                service.recordMovement(
                        request(
                                StockTransactionType.WASTAGE,
                                2,
                                "Expired stock"
                        )
                );

        assertQuantity("18", item.getCurrentQuantity());
        assertQuantity("18", response.getQuantityAfter());
    }

    @Test
    void adjustmentIn_ShouldIncreaseStock() {

        mockSuccessfulMovement();

        StockMovementResponse response =
                service.recordMovement(
                        request(
                                StockTransactionType.ADJUSTMENT_IN,
                                4,
                                "Stock count"
                        )
                );

        assertQuantity("24", item.getCurrentQuantity());
        assertQuantity("24", response.getQuantityAfter());
    }

    @Test
    void adjustmentOut_ShouldDecreaseStock() {

        mockSuccessfulMovement();

        StockMovementResponse response =
                service.recordMovement(
                        request(
                                StockTransactionType.ADJUSTMENT_OUT,
                                4,
                                "Stock count"
                        )
                );

        assertQuantity("16", item.getCurrentQuantity());
        assertQuantity("16", response.getQuantityAfter());
    }

    @Test
    void damageWithoutReason_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.recordMovement(
                        request(
                                StockTransactionType.DAMAGE,
                                2,
                                null
                        )
                )
        );

        assertQuantity("20", item.getCurrentQuantity());

        // Invalid request rejected before accessing database.
        verifyNoInteractions(
                itemRepository,
                transactionRepository
        );
    }

    @Test
    void zeroQuantity_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.recordMovement(
                        request(
                                StockTransactionType.STOCK_IN,
                                0,
                                "Test"
                        )
                )
        );

        assertQuantity("20", item.getCurrentQuantity());

        verifyNoInteractions(
                itemRepository,
                transactionRepository
        );
    }

    @Test
    void negativeQuantity_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.recordMovement(
                        request(
                                StockTransactionType.STOCK_OUT,
                                -5,
                                "Test"
                        )
                )
        );

        assertQuantity("20", item.getCurrentQuantity());

        verifyNoInteractions(
                itemRepository,
                transactionRepository
        );
    }

    @Test
    void nullTransactionType_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.recordMovement(
                        request(
                                null,
                                5,
                                "Test"
                        )
                )
        );

        assertQuantity("20", item.getCurrentQuantity());

        verifyNoInteractions(
                itemRepository,
                transactionRepository
        );
    }

    // NEW: Decimal stock receipt.
    @Test
    void decimalStockIn_ShouldIncreaseQuantityPrecisely() {

        item.setCurrentQuantity(
                new BigDecimal("10.500")
        );

        mockSuccessfulMovement();

        StockMovementResponse response =
                service.recordMovement(
                        request(
                                StockTransactionType.STOCK_IN,
                                new BigDecimal("1.250"),
                                "Engine oil received"
                        )
                );

        assertQuantity("11.750", item.getCurrentQuantity());
        assertQuantity("10.500", response.getQuantityBefore());
        assertQuantity("1.250", response.getQuantity());
        assertQuantity("11.750", response.getQuantityAfter());

        ArgumentCaptor<StockTransaction> captor =
                ArgumentCaptor.forClass(StockTransaction.class);

        verify(transactionRepository)
                .saveAndFlush(captor.capture());

        assertQuantity(
                "1.250",
                captor.getValue().getQuantity()
        );

        assertQuantity(
                "11.750",
                captor.getValue().getQuantityAfter()
        );
    }

    // NEW: Decimal stock issue.
    @Test
    void decimalStockOut_ShouldDecreaseQuantityPrecisely() {

        item.setCurrentQuantity(
                new BigDecimal("10.500")
        );

        mockSuccessfulMovement();

        StockMovementResponse response =
                service.recordMovement(
                        request(
                                StockTransactionType.STOCK_OUT,
                                new BigDecimal("1.250"),
                                "Engine oil issued"
                        )
                );

        assertQuantity("9.250", item.getCurrentQuantity());
        assertQuantity("10.500", response.getQuantityBefore());
        assertQuantity("1.250", response.getQuantity());
        assertQuantity("9.250", response.getQuantityAfter());
    }
}
