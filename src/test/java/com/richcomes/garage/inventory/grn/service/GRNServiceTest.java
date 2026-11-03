
package com.richcomes.garage.inventory.grn.service;

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
import com.richcomes.garage.inventory.grn.dto.request.GRNRequest;
import com.richcomes.garage.inventory.grn.dto.request.GRNItemRequest;
import java.util.List;
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
class GRNServiceTest {

    @Mock
    private GRNRepository grnRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockTransactionRepository transactionRepository;

    @InjectMocks
    private GRNService grnService;

    private GRN grn;
    private StockItem stockItem;

    @BeforeEach
    void setUp() {

        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSupplierName("ABC Auto Parts");

        stockItem = new StockItem();
        stockItem.setId(1L);
        stockItem.setItemCode("OIL-001");
        stockItem.setItemName("Engine Oil 1L");
        stockItem.setCurrentQuantity(0);
        stockItem.setActive(true);

        GRNItem grnItem = new GRNItem();
        grnItem.setStockItem(stockItem);
        grnItem.setReceivedQuantity(20);
        grnItem.setUnitCost(new BigDecimal("1500.00"));

        grn = new GRN();
        grn.setGrnNumber("GRN-TEST-001");
        grn.setSupplier(supplier);
        grn.setStatus(GRNStatus.DRAFT);
        grn.addItem(grnItem);
    }

    @Test
    void confirmDraftGRN_ShouldIncreaseStockAndSaveTransaction() {

        when(grnRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(grn));

        when(stockItemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(stockItem));

        when(grnRepository.saveAndFlush(any(GRN.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        grnService.confirmGRN(1L);

        assertEquals(GRNStatus.CONFIRMED, grn.getStatus());
        assertEquals(20, stockItem.getCurrentQuantity());
        assertNotNull(grn.getReceivedAt());

        ArgumentCaptor<StockTransaction> captor =
                ArgumentCaptor.forClass(StockTransaction.class);

        verify(transactionRepository).save(captor.capture());

        StockTransaction transaction = captor.getValue();

        assertEquals(
                StockTransactionType.STOCK_IN,
                transaction.getTransactionType()
        );

        assertEquals(20, transaction.getQuantity());
        assertEquals(0, transaction.getQuantityBefore());
        assertEquals(20, transaction.getQuantityAfter());
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
        assertEquals(0, stockItem.getCurrentQuantity());

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

        verify(grnRepository, never()).saveAndFlush(any(GRN.class));
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

        // An empty GRN with DRAFT status
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

        assertEquals(0, stockItem.getCurrentQuantity());

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
        firstItem.setReceivedQuantity(10);
        firstItem.setUnitCost(new BigDecimal("1500.00"));

        GRNItemRequest secondItem = new GRNItemRequest();
        secondItem.setStockItemId(1L);
        secondItem.setReceivedQuantity(5);
        secondItem.setUnitCost(new BigDecimal("1500.00"));

        request.setItems(List.of(firstItem, secondItem));

        when(supplierRepository.findById(1L))
                .thenReturn(Optional.of(grn.getSupplier()));

        when(stockItemRepository.findById(1L))
                .thenReturn(Optional.of(stockItem));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> grnService.createDraft(request)
        );

        assertEquals(400, exception.getStatusCode().value());

        verify(grnRepository, never()).saveAndFlush(any(GRN.class));
    }

    @Test
    void createDraftWithInvalidQuantity_ShouldReject() {

        GRNRequest request = new GRNRequest();
        request.setSupplierId(1L);

        GRNItemRequest itemRequest = new GRNItemRequest();
        itemRequest.setStockItemId(1L);
        itemRequest.setReceivedQuantity(0);
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
        verify(grnRepository, never()).saveAndFlush(any(GRN.class));
    }

    @Test
    void createDraftWithInactiveSupplier_ShouldReject() {

        GRNRequest request = new GRNRequest();
        request.setSupplierId(1L);

        GRNItemRequest itemRequest = new GRNItemRequest();
        itemRequest.setStockItemId(1L);
        itemRequest.setReceivedQuantity(5);
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
        verify(grnRepository, never()).saveAndFlush(any(GRN.class));
    }



}
