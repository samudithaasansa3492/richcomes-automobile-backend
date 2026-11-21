package com.richcomes.garage.inventory.stockmovement.service;

import com.richcomes.garage.inventory.store.entity.StoreLocation;
import com.richcomes.garage.inventory.store.entity.StoreStockBalance;
import com.richcomes.garage.inventory.store.repository.StoreLocationRepository;
import com.richcomes.garage.inventory.store.repository.StoreStockBalanceRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock private StockItemRepository itemRepository;
    @Mock private StockTransactionRepository transactionRepository;
    @Mock private StoreLocationRepository storeLocationRepository;
    @Mock private StoreStockBalanceRepository storeStockBalanceRepository;

    @InjectMocks private StockMovementService service;

    private StockItem item;
    private StoreLocation store;
    private StoreStockBalance balance;

    @BeforeEach
    void setUp() {
        item = new StockItem();
        item.setId(1L);
        item.setItemCode("OIL-001");
        item.setItemName("Engine Oil 1L");
        item.setCurrentQuantity(new BigDecimal("20.000"));
        item.setActive(true);
        item.setUnit("L");

        store = new StoreLocation();
        // The JPA-generated StoreLocation ID has no public setter.
        ReflectionTestUtils.setField(store, "id", 2L);
        store.setActive(true);

        balance = new StoreStockBalance();
        balance.setStoreLocation(store);
        balance.setStockItem(item);
        balance.setQuantity(new BigDecimal("8.000"));
    }

    private StockMovementRequest request(StockTransactionType type, BigDecimal qty, String reason) {
        StockMovementRequest request = new StockMovementRequest();
        request.setStockItemId(1L);
        request.setStoreLocationId(2L);
        request.setTransactionType(type);
        request.setQuantity(qty);
        request.setReason(reason);
        return request;
    }

    private StockMovementRequest request(StockTransactionType type, int qty, String reason) {
        return request(type, BigDecimal.valueOf(qty), reason);
    }

    private void mockValidStock() {
        when(itemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
    }

    private void mockValidStore() {
        when(storeLocationRepository.findById(2L)).thenReturn(Optional.of(store));
        when(storeStockBalanceRepository.findForUpdate(2L, 1L)).thenReturn(Optional.of(balance));
    }

    private void mockSuccessfulMovement() {
        mockValidStock();
        mockValidStore();
        when(transactionRepository.saveAndFlush(any(StockTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertQuantity(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void assertStatus(int status, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, error.getStatusCode().value());
    }

    @Test
    void stockIn_ShouldRejectDirectReceipt() {
        assertStatus(400, () -> service.recordMovement(request(StockTransactionType.STOCK_IN, 10, "New stock")));
        verifyNoInteractions(itemRepository, storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void stockOut_ShouldRejectDirectIssue() {
        assertStatus(400, () -> service.recordMovement(request(StockTransactionType.STOCK_OUT, 5, "Issued stock")));
        verifyNoInteractions(itemRepository, storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void insufficientGlobalStock_ShouldReject() {
        mockValidStock();
        mockValidStore();
        assertStatus(400, () -> service.recordMovement(request(StockTransactionType.ADJUSTMENT_OUT, 25, "Count correction")));
        assertQuantity("20", item.getCurrentQuantity());
        assertQuantity("8", balance.getQuantity());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void damage_ShouldReduceSelectedStoreAndGlobalStock() {
        mockSuccessfulMovement();
        StockMovementResponse result = service.recordMovement(request(StockTransactionType.DAMAGE, 3, "Damaged cans"));
        assertQuantity("17", item.getCurrentQuantity());
        assertQuantity("5", balance.getQuantity());
        assertQuantity("20", result.getQuantityBefore());
        assertQuantity("17", result.getQuantityAfter());
        ArgumentCaptor<StockTransaction> captor = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        assertEquals(StockTransactionType.DAMAGE, captor.getValue().getTransactionType());
        assertEquals("STORE_LOCATION", captor.getValue().getReferenceType());
        assertEquals(2L, captor.getValue().getReferenceId());
        verify(storeStockBalanceRepository).saveAndFlush(balance);
        verify(itemRepository).saveAndFlush(item);
    }

    @Test
    void wastage_ShouldReduceSelectedStoreAndGlobalStock() {
        mockSuccessfulMovement();
        StockMovementResponse result = service.recordMovement(request(StockTransactionType.WASTAGE, 2, "Expired stock"));
        assertQuantity("18", item.getCurrentQuantity());
        assertQuantity("6", balance.getQuantity());
        assertQuantity("18", result.getQuantityAfter());
    }

    @Test
    void adjustmentIn_ShouldIncreaseSelectedStoreAndGlobalStock() {
        mockSuccessfulMovement();
        StockMovementResponse result = service.recordMovement(request(StockTransactionType.ADJUSTMENT_IN, 4, "Stock count"));
        assertQuantity("24", item.getCurrentQuantity());
        assertQuantity("12", balance.getQuantity());
        assertQuantity("24", result.getQuantityAfter());
        assertEquals("STORE_LOCATION", result.getReferenceType());
        assertEquals(2L, result.getReferenceId());
    }

    @Test
    void adjustmentOut_ShouldDecreaseSelectedStoreAndGlobalStock() {
        mockSuccessfulMovement();
        StockMovementResponse result = service.recordMovement(request(StockTransactionType.ADJUSTMENT_OUT, 4, "Stock count"));
        assertQuantity("16", item.getCurrentQuantity());
        assertQuantity("4", balance.getQuantity());
        assertQuantity("16", result.getQuantityAfter());
    }

    @Test
    void damageWithoutReason_ShouldRejectBeforeDatabaseAccess() {
        assertStatus(400, () -> service.recordMovement(request(StockTransactionType.DAMAGE, 2, null)));
        verifyNoInteractions(itemRepository, storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void zeroQuantity_ShouldReject() {
        assertStatus(400, () -> service.recordMovement(request(StockTransactionType.ADJUSTMENT_IN, 0, "Test")));
        verifyNoInteractions(itemRepository, storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void negativeQuantity_ShouldReject() {
        assertStatus(400, () -> service.recordMovement(request(StockTransactionType.ADJUSTMENT_OUT, -5, "Test")));
        verifyNoInteractions(itemRepository, storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void nullTransactionType_ShouldReject() {
        assertStatus(400, () -> service.recordMovement(request(null, 5, "Test")));
        verifyNoInteractions(itemRepository, storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void decimalStockIn_ShouldRejectDirectReceipt() {
        assertStatus(400, () -> service.recordMovement(request(
                StockTransactionType.STOCK_IN, new BigDecimal("1.250"), "Engine oil received")));
        verifyNoInteractions(itemRepository, storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void decimalStockOut_ShouldRejectDirectIssue() {
        assertStatus(400, () -> service.recordMovement(request(
                StockTransactionType.STOCK_OUT, new BigDecimal("1.250"), "Engine oil issued")));
        verifyNoInteractions(itemRepository, storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void decimalAdjustment_ShouldCalculateExactly() {
        item.setCurrentQuantity(new BigDecimal("10.500"));
        balance.setQuantity(new BigDecimal("3.500"));
        mockSuccessfulMovement();
        StockMovementResponse response = service.recordMovement(request(
                StockTransactionType.ADJUSTMENT_OUT, new BigDecimal("1.250"), "Count correction"));
        assertQuantity("9.250", item.getCurrentQuantity());
        assertQuantity("2.250", balance.getQuantity());
        assertQuantity("10.500", response.getQuantityBefore());
        assertQuantity("1.250", response.getQuantity());
    }

    @Test
    void insufficientSelectedStoreStock_ShouldRejectEvenWhenGlobalIsEnough() {
        mockValidStock();
        mockValidStore();
        assertStatus(400, () -> service.recordMovement(request(
                StockTransactionType.ADJUSTMENT_OUT, 9, "Count correction")));
        assertQuantity("20", item.getCurrentQuantity());
        assertQuantity("8", balance.getQuantity());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void missingStoreId_ShouldRejectBeforeDatabaseAccess() {
        StockMovementRequest req = request(StockTransactionType.DAMAGE, 1, "Broken");
        req.setStoreLocationId(null);
        assertStatus(400, () -> service.recordMovement(req));
        verifyNoInteractions(itemRepository, storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void inactiveStore_ShouldReject() {
        store.setActive(false);
        mockValidStock();
        when(storeLocationRepository.findById(2L)).thenReturn(Optional.of(store));
        assertStatus(400, () -> service.recordMovement(request(StockTransactionType.DAMAGE, 1, "Broken")));
        verifyNoInteractions(storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void missingStoreBalance_ShouldRejectWithoutCreatingOne() {
        mockValidStock();
        when(storeLocationRepository.findById(2L)).thenReturn(Optional.of(store));
        when(storeStockBalanceRepository.findForUpdate(2L, 1L)).thenReturn(Optional.empty());
        assertStatus(409, () -> service.recordMovement(request(StockTransactionType.DAMAGE, 1, "Broken")));
        verify(storeStockBalanceRepository, never()).saveAndFlush(any(StoreStockBalance.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void fractionalPcsMovement_ShouldReject() {
        item.setUnit("PCS");
        mockValidStock();
        assertStatus(400, () -> service.recordMovement(request(
                StockTransactionType.DAMAGE, new BigDecimal("1.500"), "Broken")));
        verifyNoInteractions(storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void fractionalExistingPcsStoreBalance_ShouldReject() {
        item.setUnit("PCS");
        balance.setQuantity(new BigDecimal("8.500"));
        mockValidStock();
        mockValidStore();
        assertStatus(409, () -> service.recordMovement(request(StockTransactionType.DAMAGE, 1, "Broken")));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void overflowOnSelectedStore_ShouldRejectWithoutSaving() {
        item.setCurrentQuantity(new BigDecimal("99999999999.000"));
        balance.setQuantity(new BigDecimal("99999999999.000"));
        mockValidStock();
        mockValidStore();
        assertStatus(400, () -> service.recordMovement(request(
                StockTransactionType.ADJUSTMENT_IN, 1, "Count correction")));
        verify(storeStockBalanceRepository, never()).saveAndFlush(any(StoreStockBalance.class));
        verify(itemRepository, never()).saveAndFlush(any(StockItem.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getItemTransactions_ShouldReturnHistory() {
        when(itemRepository.existsById(1L)).thenReturn(true);
        StockTransaction transaction = new StockTransaction();
        transaction.setStockItem(item);
        transaction.setTransactionType(StockTransactionType.DAMAGE);
        transaction.setQuantity(BigDecimal.ONE);
        transaction.setReferenceType("STORE_LOCATION");
        transaction.setReferenceId(2L);
        when(transactionRepository.findByStockItemIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(transaction));
        List<StockMovementResponse> responses = service.getItemTransactions(1L);
        assertEquals(1, responses.size());
        assertEquals(2L, responses.get(0).getReferenceId());
    }

    @Test
    void inactiveStockItem_ShouldRejectBeforeStoreLookup() {
        item.setActive(false);
        mockValidStock();
        assertStatus(400, () -> service.recordMovement(request(StockTransactionType.DAMAGE, 1, "Broken")));
        verifyNoInteractions(storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }
}
