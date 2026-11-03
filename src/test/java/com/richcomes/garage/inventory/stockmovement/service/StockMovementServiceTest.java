
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
        item.setCurrentQuantity(20);
        item.setActive(true);
    }

    private StockMovementRequest request(
            StockTransactionType type,
            int quantity,
            String reason) {

        StockMovementRequest request = new StockMovementRequest();
        request.setStockItemId(1L);
        request.setTransactionType(type);
        request.setQuantity(quantity);
        request.setReason(reason);
        return request;
    }

    private void mockSuccessfulMovement() {

        when(itemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(item));

        when(transactionRepository.saveAndFlush(
                any(StockTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void stockIn_ShouldIncreaseQuantity() {

        mockSuccessfulMovement();

        StockMovementResponse response = service.recordMovement(
                request(StockTransactionType.STOCK_IN, 10, "New stock")
        );

        assertEquals(30, item.getCurrentQuantity());
        assertEquals(20, response.getQuantityBefore());
        assertEquals(30, response.getQuantityAfter());

        ArgumentCaptor<StockTransaction> captor =
                ArgumentCaptor.forClass(StockTransaction.class);

        verify(transactionRepository).saveAndFlush(captor.capture());

        assertEquals(
                StockTransactionType.STOCK_IN,
                captor.getValue().getTransactionType()
        );
    }

    @Test
    void stockOut_ShouldDecreaseQuantity() {

        mockSuccessfulMovement();

        StockMovementResponse response = service.recordMovement(
                request(StockTransactionType.STOCK_OUT, 5, "Issued stock")
        );

        assertEquals(15, item.getCurrentQuantity());
        assertEquals(15, response.getQuantityAfter());
    }

    @Test
    void insufficientStock_ShouldReject() {

        when(itemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(item));

        assertThrows(
                ResponseStatusException.class,
                () -> service.recordMovement(
                        request(StockTransactionType.STOCK_OUT, 25, "Issue")
                )
        );

        assertEquals(20, item.getCurrentQuantity());

        verify(itemRepository, never()).saveAndFlush(any(StockItem.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void damage_ShouldReduceStock() {

        mockSuccessfulMovement();

        StockMovementResponse response = service.recordMovement(
                request(StockTransactionType.DAMAGE, 3, "Damaged cans")
        );

        assertEquals(17, item.getCurrentQuantity());
        assertEquals(17, response.getQuantityAfter());
    }

    @Test
    void wastage_ShouldReduceStock() {

        mockSuccessfulMovement();

        StockMovementResponse response = service.recordMovement(
                request(StockTransactionType.WASTAGE, 2, "Expired stock")
        );

        assertEquals(18, item.getCurrentQuantity());
        assertEquals(18, response.getQuantityAfter());
    }

    @Test
    void adjustmentIn_ShouldIncreaseStock() {

        mockSuccessfulMovement();

        StockMovementResponse response = service.recordMovement(
                request(StockTransactionType.ADJUSTMENT_IN, 4, "Stock count")
        );

        assertEquals(24, item.getCurrentQuantity());
        assertEquals(24, response.getQuantityAfter());
    }

    @Test
    void adjustmentOut_ShouldDecreaseStock() {

        mockSuccessfulMovement();

        StockMovementResponse response = service.recordMovement(
                request(StockTransactionType.ADJUSTMENT_OUT, 4, "Stock count")
        );

        assertEquals(16, item.getCurrentQuantity());
        assertEquals(16, response.getQuantityAfter());
    }


    @Test
    void damageWithoutReason_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.recordMovement(
                        request(StockTransactionType.DAMAGE, 2, null)
                )
        );

        assertEquals(20, item.getCurrentQuantity());

        // Invalid request must be rejected before accessing database.
        verifyNoInteractions(itemRepository, transactionRepository);
    }

    @Test
    void zeroQuantity_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.recordMovement(
                        request(StockTransactionType.STOCK_IN, 0, "Test")
                )
        );

        assertEquals(20, item.getCurrentQuantity());
        verifyNoInteractions(itemRepository, transactionRepository);
    }

    @Test
    void negativeQuantity_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.recordMovement(
                        request(StockTransactionType.STOCK_OUT, -5, "Test")
                )
        );

        assertEquals(20, item.getCurrentQuantity());
        verifyNoInteractions(itemRepository, transactionRepository);
    }

    @Test
    void nullTransactionType_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.recordMovement(
                        request(null, 5, "Test")
                )
        );

        assertEquals(20, item.getCurrentQuantity());
        verifyNoInteractions(itemRepository, transactionRepository);
    }


}
