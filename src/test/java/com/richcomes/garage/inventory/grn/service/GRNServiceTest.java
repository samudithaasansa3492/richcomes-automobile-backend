package com.richcomes.garage.inventory.grn.service;

import com.richcomes.garage.inventory.grn.dto.request.GRNItemRequest;
import com.richcomes.garage.inventory.grn.dto.request.GRNRequest;
import com.richcomes.garage.inventory.grn.dto.request.GRNStoreAllocationRequest;
import com.richcomes.garage.inventory.grn.entity.GRNItemAllocation;
import com.richcomes.garage.inventory.grn.entity.GRN;
import com.richcomes.garage.inventory.grn.entity.GRNItem;
import com.richcomes.garage.inventory.grn.enums.GRNStatus;
import com.richcomes.garage.inventory.grn.repository.GRNRepository;
import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;
import com.richcomes.garage.inventory.stockmovement.entity.StockTransaction;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.repository.StockTransactionRepository;
import com.richcomes.garage.inventory.supplier.entity.Supplier;
import com.richcomes.garage.inventory.supplier.repository.SupplierRepository;
import com.richcomes.garage.inventory.store.entity.StoreLocation;
import com.richcomes.garage.inventory.store.entity.StoreStockBalance;
import com.richcomes.garage.inventory.store.repository.StoreLocationRepository;
import com.richcomes.garage.inventory.store.repository.StoreStockBalanceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GRNServiceTest {

    @Mock
    private GRNRepository grnRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockTransactionRepository transactionRepository;

    @Mock
    private StoreLocationRepository storeLocationRepository;

    @Mock
    private StoreStockBalanceRepository storeStockBalanceRepository;

    @InjectMocks
    private GRNService grnService;

    private GRN grn;
    private StockItem stockItem;
    private StoreLocation store1;
    private StoreLocation store2;
    private StoreLocation store3;

    @BeforeEach
    void setUp() {

        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSupplierName("ABC Auto Parts");

        stockItem = new StockItem();
        stockItem.setId(1L);
        stockItem.setItemCode("OIL-001");
        stockItem.setItemName("Engine Oil 1L");
        stockItem.setCurrentQuantity(BigDecimal.ZERO);
        stockItem.setActive(true);
        stockItem.setUnit("PCS");

        store1 = store(1L, "STORE_01");
        store2 = store(2L, "STORE_02");
        store3 = store(3L, "STORE_03");

        GRNItem grnItem = new GRNItem();
        grnItem.setStockItem(stockItem);
        grnItem.setReceivedQuantity(new BigDecimal("20.000"));
        grnItem.setUnitCost(new BigDecimal("1500.00"));

        grn = new GRN();
        grn.setGrnNumber("GRN-TEST-001");
        grn.setSupplier(supplier);
        grn.setStatus(GRNStatus.DRAFT);
        grn.addItem(grnItem);
        grnItem.addAllocation(allocation(store1, "10.000"));
        grnItem.addAllocation(allocation(store2, "6.000"));
        grnItem.addAllocation(allocation(store3, "4.000"));
    }

    @Test
    void confirmDraftGRN_ShouldIncreaseStockAndSaveTransaction() {

        when(grnRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(grn));

        when(stockItemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(stockItem));

        stubThreeStoreBalances();

        when(grnRepository.saveAndFlush(any(GRN.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        grnService.confirmGRN(1L);

        assertEquals(GRNStatus.CONFIRMED, grn.getStatus());

        assertEquals(
                0,
                new BigDecimal("20.000")
                        .compareTo(stockItem.getCurrentQuantity())
        );

        assertNotNull(grn.getReceivedAt());

        ArgumentCaptor<StockTransaction> captor =
                ArgumentCaptor.forClass(StockTransaction.class);

        verify(transactionRepository).save(captor.capture());

        StockTransaction transaction = captor.getValue();

        assertEquals(
                StockTransactionType.STOCK_IN,
                transaction.getTransactionType()
        );

        assertEquals(
                0,
                new BigDecimal("20.000")
                        .compareTo(transaction.getQuantity())
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        transaction.getQuantityBefore()
                )
        );

        assertEquals(
                0,
                new BigDecimal("20.000")
                        .compareTo(transaction.getQuantityAfter())
        );

        assertEquals("GRN", transaction.getReferenceType());

        verify(grnRepository).saveAndFlush(grn);
    }

    @Test
    void confirmAlreadyConfirmedGRN_ShouldReject() {

        grn.setStatus(GRNStatus.CONFIRMED);

        when(grnRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(grn));

        assertThrows(
                ResponseStatusException.class,
                () -> grnService.confirmGRN(1L)
        );

        verifyNoInteractions(transactionRepository);

        verify(stockItemRepository, never()).save(any());
    }

    @Test
    void cancelDraftGRN_ShouldChangeStatusWithoutChangingStock() {

        when(grnRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(grn));

        when(grnRepository.saveAndFlush(any(GRN.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        grnService.cancelGRN(1L);

        assertEquals(GRNStatus.CANCELLED, grn.getStatus());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        stockItem.getCurrentQuantity()
                )
        );

        verifyNoInteractions(transactionRepository);

        verify(stockItemRepository, never()).save(any());
    }

    @Test
    void cancelConfirmedGRN_ShouldReject() {

        grn.setStatus(GRNStatus.CONFIRMED);

        when(grnRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(grn));

        assertThrows(
                ResponseStatusException.class,
                () -> grnService.cancelGRN(1L)
        );

        verify(grnRepository, never())
                .saveAndFlush(any(GRN.class));

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void confirmNonExistingGRN_ShouldReturnNotFound() {

        when(grnRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> grnService.confirmGRN(99L)
        );

        assertEquals(404, exception.getStatusCode().value());

        verifyNoInteractions(
                stockItemRepository,
                transactionRepository
        );
    }

    @Test
    void confirmEmptyGRN_ShouldReject() {

        GRN emptyGRN = new GRN();
        emptyGRN.setStatus(GRNStatus.DRAFT);

        when(grnRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(emptyGRN));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> grnService.confirmGRN(1L)
        );

        assertEquals(400, exception.getStatusCode().value());

        verifyNoInteractions(
                stockItemRepository,
                transactionRepository
        );
    }

    @Test
    void confirmCancelledGRN_ShouldReject() {

        grn.setStatus(GRNStatus.CANCELLED);

        when(grnRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(grn));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> grnService.confirmGRN(1L)
        );

        assertEquals(409, exception.getStatusCode().value());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        stockItem.getCurrentQuantity()
                )
        );

        verifyNoInteractions(
                stockItemRepository,
                transactionRepository
        );
    }

    @Test
    void createDraftWithoutSupplierId_ShouldReject() {

        GRNRequest request = new GRNRequest();

        request.setSupplierId(null);

        assertThrows(
                ResponseStatusException.class,
                () -> grnService.createDraft(request)
        );

        verifyNoInteractions(
                supplierRepository,
                stockItemRepository,
                grnRepository
        );
    }

    @Test
    void createDraftWithEmptyItems_ShouldReject() {

        GRNRequest request = new GRNRequest();

        request.setSupplierId(1L);
        request.setItems(List.of());

        assertThrows(
                ResponseStatusException.class,
                () -> grnService.createDraft(request)
        );

        verifyNoInteractions(
                supplierRepository,
                stockItemRepository,
                grnRepository
        );
    }

    @Test
    void createDraftWithDuplicateItems_ShouldReject() {

        GRNRequest request = new GRNRequest();
        request.setSupplierId(1L);

        GRNItemRequest firstItem = new GRNItemRequest();
        firstItem.setStockItemId(1L);
        firstItem.setReceivedQuantity(new BigDecimal("10.000"));
        firstItem.setUnitCost(new BigDecimal("1500.00"));
        firstItem.setAllocations(List.of(allocationRequest(1L, "10.000")));

        GRNItemRequest secondItem = new GRNItemRequest();
        secondItem.setStockItemId(1L);
        secondItem.setReceivedQuantity(new BigDecimal("5.000"));
        secondItem.setUnitCost(new BigDecimal("1500.00"));

        request.setItems(List.of(firstItem, secondItem));

        when(supplierRepository.findById(1L))
                .thenReturn(Optional.of(grn.getSupplier()));

        when(stockItemRepository.findById(1L))
                .thenReturn(Optional.of(stockItem));
        when(storeLocationRepository.findById(1L))
                .thenReturn(Optional.of(store1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> grnService.createDraft(request)
        );

        assertEquals(400, exception.getStatusCode().value());

        verify(grnRepository, never())
                .saveAndFlush(any(GRN.class));
    }

    @Test
    void createDraftWithInvalidQuantity_ShouldReject() {

        GRNRequest request = new GRNRequest();
        request.setSupplierId(1L);

        GRNItemRequest itemRequest = new GRNItemRequest();
        itemRequest.setStockItemId(1L);
        itemRequest.setReceivedQuantity(BigDecimal.ZERO);
        itemRequest.setUnitCost(new BigDecimal("1500.00"));

        request.setItems(List.of(itemRequest));

        when(supplierRepository.findById(1L))
                .thenReturn(Optional.of(grn.getSupplier()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> grnService.createDraft(request)
        );

        assertEquals(400, exception.getStatusCode().value());

        verify(stockItemRepository, never()).findById(any());

        verify(grnRepository, never())
                .saveAndFlush(any(GRN.class));
    }

    @Test
    void createDraftWithInactiveSupplier_ShouldReject() {

        GRNRequest request = new GRNRequest();
        request.setSupplierId(1L);

        GRNItemRequest itemRequest = new GRNItemRequest();
        itemRequest.setStockItemId(1L);
        itemRequest.setReceivedQuantity(new BigDecimal("5.000"));
        itemRequest.setUnitCost(new BigDecimal("1500.00"));

        request.setItems(List.of(itemRequest));

        Supplier inactiveSupplier = new Supplier();
        inactiveSupplier.setActive(false);

        when(supplierRepository.findById(1L))
                .thenReturn(Optional.of(inactiveSupplier));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> grnService.createDraft(request)
        );

        assertEquals(400, exception.getStatusCode().value());

        verifyNoInteractions(stockItemRepository);

        verify(grnRepository, never())
                .saveAndFlush(any(GRN.class));
    }

    @Test
    void confirmDecimalGRN_ShouldIncreaseStockPrecisely() {

        stockItem.setUnit("L");

        stockItem.setCurrentQuantity(new BigDecimal("10.500"));

        GRNItem grnItem = grn.getItems().get(0);
        grnItem.setReceivedQuantity(new BigDecimal("1.250"));
        grnItem.getAllocations().clear();
        grnItem.addAllocation(allocation(store1, "0.500"));
        grnItem.addAllocation(allocation(store2, "0.500"));
        grnItem.addAllocation(allocation(store3, "0.250"));

        when(grnRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(grn));

        when(stockItemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(stockItem));

        stubThreeStoreBalances();

        when(grnRepository.saveAndFlush(any(GRN.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        grnService.confirmGRN(1L);

        assertEquals(
                0,
                new BigDecimal("11.750")
                        .compareTo(stockItem.getCurrentQuantity())
        );

        ArgumentCaptor<StockTransaction> captor =
                ArgumentCaptor.forClass(StockTransaction.class);

        verify(transactionRepository).save(captor.capture());

        StockTransaction transaction = captor.getValue();

        assertEquals(
                0,
                new BigDecimal("1.250")
                        .compareTo(transaction.getQuantity())
        );

        assertEquals(
                0,
                new BigDecimal("10.500")
                        .compareTo(transaction.getQuantityBefore())
        );

        assertEquals(
                0,
                new BigDecimal("11.750")
                        .compareTo(transaction.getQuantityAfter())
        );

        assertEquals(GRNStatus.CONFIRMED, grn.getStatus());
    }

    // Test helpers: mock entities do not touch a database.
    private StoreLocation store(Long id, String code) {
        StoreLocation location = new StoreLocation();
        location.setStoreCode(code);
        location.setStoreName(code);
        location.setActive(true);
        // JPA ID has no setter; use Spring's test-only reflection helper.
        org.springframework.test.util.ReflectionTestUtils.setField(location, "id", id);
        return location;
    }

    private GRNItemAllocation allocation(StoreLocation location, String quantity) {
        GRNItemAllocation result = new GRNItemAllocation();
        result.setStoreLocation(location);
        result.setAllocatedQuantity(new BigDecimal(quantity));
        return result;
    }

    private GRNStoreAllocationRequest allocationRequest(Long storeId, String quantity) {
        GRNStoreAllocationRequest result = new GRNStoreAllocationRequest();
        result.setStoreLocationId(storeId);
        result.setAllocatedQuantity(new BigDecimal(quantity));
        return result;
    }

    private GRNRequest draftRequest(BigDecimal received, GRNStoreAllocationRequest... allocations) {
        GRNItemRequest item = new GRNItemRequest();
        item.setStockItemId(1L);
        item.setReceivedQuantity(received);
        item.setUnitCost(new BigDecimal("1500.00"));
        item.setAllocations(List.of(allocations));
        GRNRequest request = new GRNRequest();
        request.setSupplierId(1L);
        request.setItems(List.of(item));
        return request;
    }

    private void stubDraftDependencies() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(grn.getSupplier()));
        when(stockItemRepository.findById(1L)).thenReturn(Optional.of(stockItem));
    }

    private void stubThreeStoreBalances() {
        for (StoreLocation location : List.of(store1, store2, store3)) {
            Long id = location.getId();
            when(storeLocationRepository.findById(id)).thenReturn(Optional.of(location));
            // No existing store balance: service must create one.
            when(storeStockBalanceRepository.findForUpdate(id, 1L))
                    .thenReturn(Optional.empty());
        }
        when(storeStockBalanceRepository.saveAndFlush(any(StoreStockBalance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createDraftWithThreeStoreAllocations_ShouldPersistSplitWithoutStockChange() {
        GRNRequest request = draftRequest(new BigDecimal("20.000"),
                allocationRequest(1L, "10.000"),
                allocationRequest(2L, "6.000"),
                allocationRequest(3L, "4.000"));
        stubDraftDependencies();
        when(storeLocationRepository.findById(1L)).thenReturn(Optional.of(store1));
        when(storeLocationRepository.findById(2L)).thenReturn(Optional.of(store2));
        when(storeLocationRepository.findById(3L)).thenReturn(Optional.of(store3));
        when(grnRepository.saveAndFlush(any(GRN.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = grnService.createDraft(request);

        assertEquals(3, response.getItems().get(0).getAllocations().size());
        assertEquals(0, new BigDecimal("20.000").compareTo(
                response.getItems().get(0).getAllocations().stream()
                        .map(a -> a.allocatedQuantity())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(0, BigDecimal.ZERO.compareTo(stockItem.getCurrentQuantity()));
        verifyNoInteractions(transactionRepository, storeStockBalanceRepository);
    }

    @Test
    void createDraftWithAllocationMismatch_ShouldReject() {
        GRNRequest request = draftRequest(new BigDecimal("20.000"),
                allocationRequest(1L, "10.000"));
        stubDraftDependencies();
        when(storeLocationRepository.findById(1L)).thenReturn(Optional.of(store1));

        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> grnService.createDraft(request)).getStatusCode().value());
        verify(grnRepository, never()).saveAndFlush(any(GRN.class));
    }

    @Test
    void createDraftWithDuplicateStore_ShouldReject() {
        GRNRequest request = draftRequest(new BigDecimal("20.000"),
                allocationRequest(1L, "10.000"), allocationRequest(1L, "10.000"));
        stubDraftDependencies();
        when(storeLocationRepository.findById(1L)).thenReturn(Optional.of(store1));

        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> grnService.createDraft(request)).getStatusCode().value());
        verify(grnRepository, never()).saveAndFlush(any(GRN.class));
    }

    @Test
    void createDraftWithoutAllocations_ShouldReject() {
        GRNRequest request = draftRequest(new BigDecimal("20.000"));
        stubDraftDependencies();
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> grnService.createDraft(request)).getStatusCode().value());
    }

    @Test
    void createDraftForInactiveStore_ShouldReject() {
        GRNRequest request = draftRequest(new BigDecimal("20.000"),
                allocationRequest(1L, "20.000"));
        store1.setActive(false);
        stubDraftDependencies();
        when(storeLocationRepository.findById(1L)).thenReturn(Optional.of(store1));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> grnService.createDraft(request)).getStatusCode().value());
    }

    @Test
    void createDraftWithFractionalPcsAllocation_ShouldReject() {
        GRNRequest request = draftRequest(new BigDecimal("2.000"),
                allocationRequest(1L, "1.500"), allocationRequest(2L, "0.500"));
        stubDraftDependencies();
        when(storeLocationRepository.findById(1L)).thenReturn(Optional.of(store1));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> grnService.createDraft(request)).getStatusCode().value());
    }

    @Test
    void confirmGRN_ShouldIncreaseAllThreeStoreBalances() {
        when(grnRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(grn));
        when(stockItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(stockItem));
        stubThreeStoreBalances();
        when(grnRepository.saveAndFlush(any(GRN.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        grnService.confirmGRN(1L);

        ArgumentCaptor<StoreStockBalance> captor = ArgumentCaptor.forClass(StoreStockBalance.class);
        verify(storeStockBalanceRepository, times(3)).saveAndFlush(captor.capture());
        var balances = captor.getAllValues();
        assertEquals(3, balances.size());
        assertEquals(List.of("10.000", "6.000", "4.000"), balances.stream()
                .map(balance -> balance.getQuantity().toPlainString()).toList());
        assertEquals(GRNStatus.CONFIRMED, grn.getStatus());
        assertEquals(0, new BigDecimal("20.000").compareTo(stockItem.getCurrentQuantity()));
    }

    @Test
    void confirmWithMissingAllocations_ShouldNotChangeAnyStock() {
        grn.getItems().get(0).getAllocations().clear();
        when(grnRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(grn));
        when(stockItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(stockItem));

        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> grnService.confirmGRN(1L)).getStatusCode().value());
        verifyNoInteractions(storeStockBalanceRepository, transactionRepository);
        assertEquals(0, BigDecimal.ZERO.compareTo(stockItem.getCurrentQuantity()));
    }

    @Test
    void confirmWithMismatchedAllocations_ShouldReject() {
        grn.getItems().get(0).getAllocations().get(0)
                .setAllocatedQuantity(new BigDecimal("9.000"));
        when(grnRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(grn));
        when(stockItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(stockItem));

        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> grnService.confirmGRN(1L)).getStatusCode().value());
        verifyNoInteractions(storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void confirmForInactiveStore_ShouldRejectBeforeStockUpdate() {
        store1.setActive(false);
        when(grnRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(grn));
        when(stockItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(stockItem));
        when(storeLocationRepository.findById(1L)).thenReturn(Optional.of(store1));

        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> grnService.confirmGRN(1L)).getStatusCode().value());
        verifyNoInteractions(storeStockBalanceRepository, transactionRepository);
    }


    // Supplier purchase history tests (D07).
    @Test
    void supplierHistory_WithInvalidIds_ShouldRejectBeforeDatabaseAccess() {
        for (Long invalidId : new Long[]{null, 0L, -2L}) {
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> grnService.getSupplierPurchaseHistory(invalidId));
            assertEquals(400, exception.getStatusCode().value());
        }
        verifyNoInteractions(supplierRepository, grnRepository);
    }

    @Test
    void supplierHistory_ForUnknownSupplier_ShouldReturnNotFound() {
        when(supplierRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> grnService.getSupplierPurchaseHistory(99L));

        assertEquals(404, exception.getStatusCode().value());
        verify(grnRepository, never()).findBySupplierIdOrderByCreatedAtDesc(any());
    }

    @Test
    void supplierHistory_WithNoGrns_ShouldReturnEmptyList() {
        when(supplierRepository.existsById(1L)).thenReturn(true);
        when(grnRepository.findBySupplierIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        var history = grnService.getSupplierPurchaseHistory(1L);

        assertTrue(history.isEmpty());
        verify(grnRepository).findBySupplierIdOrderByCreatedAtDesc(1L);
        verifyNoInteractions(stockItemRepository, transactionRepository);
    }

    @Test
    void supplierHistory_ShouldReturnGrnsInRepositoryOrderWithStatusesAndAmounts() {
        GRN confirmed = grn;
        confirmed.setStatus(GRNStatus.CONFIRMED);

        GRN cancelled = new GRN();
        cancelled.setSupplier(grn.getSupplier());
        cancelled.setGrnNumber("GRN-TEST-002");
        cancelled.setStatus(GRNStatus.CANCELLED);
        GRNItem cancelledItem = new GRNItem();
        cancelledItem.setStockItem(stockItem);
        cancelledItem.setReceivedQuantity(new BigDecimal("3.000"));
        cancelledItem.setUnitCost(new BigDecimal("100.00"));
        cancelled.addItem(cancelledItem);
        cancelledItem.addAllocation(allocation(store1, "3.000"));

        when(supplierRepository.existsById(1L)).thenReturn(true);
        when(grnRepository.findBySupplierIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(confirmed, cancelled));

        var history = grnService.getSupplierPurchaseHistory(1L);

        assertEquals(2, history.size());
        assertEquals("GRN-TEST-001", history.get(0).getGrnNumber());
        assertEquals(GRNStatus.CONFIRMED, history.get(0).getStatus());
        assertEquals(0, new BigDecimal("30000.00")
                .compareTo(history.get(0).getTotalAmount()));
        assertEquals(3, history.get(0).getItems().get(0).getAllocations().size());
        assertEquals("GRN-TEST-002", history.get(1).getGrnNumber());
        assertEquals(GRNStatus.CANCELLED, history.get(1).getStatus());
        assertEquals(0, new BigDecimal("300.00")
                .compareTo(history.get(1).getTotalAmount()));
        // History includes cancelled GRNs; callers must not count them as purchases.
        verifyNoInteractions(transactionRepository, storeStockBalanceRepository);
    }

    @Test
    void supplierHistory_ForInactiveExistingSupplier_ShouldStillReturnHistory() {
        // History is allowed for inactive suppliers as long as the supplier exists.
        grn.getSupplier().setActive(false);
        when(supplierRepository.existsById(1L)).thenReturn(true);
        when(grnRepository.findBySupplierIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(grn));

        var history = grnService.getSupplierPurchaseHistory(1L);

        assertEquals(1, history.size());
        assertEquals(1L, history.get(0).getSupplierId());
        assertEquals(GRNStatus.DRAFT, history.get(0).getStatus());
    }

}
