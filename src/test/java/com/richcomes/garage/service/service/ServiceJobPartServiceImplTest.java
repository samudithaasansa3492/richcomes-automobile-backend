package com.richcomes.garage.service.service;

import com.richcomes.garage.inventory.store.entity.StoreLocation;
import com.richcomes.garage.inventory.store.entity.StoreStockBalance;
import com.richcomes.garage.inventory.store.repository.StoreLocationRepository;
import com.richcomes.garage.inventory.store.repository.StoreStockBalanceRepository;
import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;
import com.richcomes.garage.inventory.stockmovement.entity.StockTransaction;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.repository.StockTransactionRepository;
import com.richcomes.garage.service.dto.ServiceJobPartDTO;
import com.richcomes.garage.service.entity.ServiceJob;
import com.richcomes.garage.service.entity.ServiceJobPart;
import com.richcomes.garage.service.repository.ServiceJobPartRepository;
import com.richcomes.garage.service.repository.ServiceJobRepository;
import com.richcomes.garage.service.service.impl.ServiceJobPartServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
class ServiceJobPartServiceImplTest {

    @Mock private ServiceJobPartRepository repository;
    @Mock private ServiceJobRepository jobRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockTransactionRepository transactionRepository;
    @Mock private StoreLocationRepository storeLocationRepository;
    @Mock private StoreStockBalanceRepository storeStockBalanceRepository;
    @Mock private EntityManager entityManager;
    @InjectMocks private ServiceJobPartServiceImpl service;

    private StockItem stockItem;
    private ServiceJob job;
    private StoreLocation store;
    private StoreStockBalance balance;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        stockItem = new StockItem();
        stockItem.setId(1L);
        stockItem.setItemCode("OIL-001");
        stockItem.setItemName("Engine Oil");
        stockItem.setCurrentQuantity(new BigDecimal("10.000"));
        stockItem.setActive(true);
        stockItem.setUnit("L");

        job = new ServiceJob();
        job.setId(100L);

        store = new StoreLocation();
        ReflectionTestUtils.setField(store, "id", 2L);
        store.setActive(true);

        balance = new StoreStockBalance();
        balance.setStoreLocation(store);
        balance.setStockItem(stockItem);
        balance.setQuantity(new BigDecimal("6.000"));
    }

    private ServiceJobPartDTO createRequest(double quantity) {
        ServiceJobPartDTO dto = new ServiceJobPartDTO();
        dto.setServiceJobId(100L);
        dto.setPartId(1L);
        dto.setStoreLocationId(2L);
        dto.setQuantity(quantity);
        return dto;
    }

    private void mockStockLookup() {
        when(stockItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(stockItem));
    }

    private void mockStoreLookup() {
        when(storeLocationRepository.findById(2L)).thenReturn(Optional.of(store));
    }

    private void mockAddPart() {
        mockStockLookup();
        mockStoreLookup();
        when(storeStockBalanceRepository.findForUpdate(2L, 1L)).thenReturn(Optional.of(balance));
        when(jobRepository.findById(100L)).thenReturn(Optional.of(job));
        when(repository.saveAndFlush(any(ServiceJobPart.class))).thenAnswer(invocation -> {
            ServiceJobPart part = invocation.getArgument(0);
            part.setId(50L);
            return part;
        });
    }

    private ServiceJobPart jobPart(double quantity) {
        ServiceJobPart part = new ServiceJobPart();
        part.setId(50L);
        part.setServiceJob(job);
        part.setPartId(1L);
        part.setStoreLocation(store);
        part.setQuantity(quantity);
        return part;
    }

    private void mockRemoval(ServiceJobPart part) {
        when(entityManager.find(ServiceJobPart.class, 50L, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(part);
        mockStockLookup();
        when(storeStockBalanceRepository.findForUpdate(2L, 1L)).thenReturn(Optional.of(balance));
    }

    private void assertQuantity(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void assertStatus(int expected, ExecutableAction action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, exception.getStatusCode().value());
    }

    @FunctionalInterface
    private interface ExecutableAction { void run(); }

    @Test
    void addPart_ShouldDeductStock() {
        mockAddPart();
        ServiceJobPartDTO result = service.addJobPart(createRequest(2.0));
        assertEquals(50L, result.getId());
        assertEquals(2L, result.getStoreLocationId());
        assertQuantity("8.000", stockItem.getCurrentQuantity());
        assertQuantity("4.000", balance.getQuantity());
        verify(stockItemRepository).saveAndFlush(stockItem);
        verify(storeStockBalanceRepository).saveAndFlush(balance);
        ArgumentCaptor<StockTransaction> captor = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactionRepository).save(captor.capture());
        StockTransaction transaction = captor.getValue();
        assertEquals(StockTransactionType.STOCK_OUT, transaction.getTransactionType());
        assertQuantity("2.000", transaction.getQuantity());
        assertQuantity("10.000", transaction.getQuantityBefore());
        assertQuantity("8.000", transaction.getQuantityAfter());
        assertEquals("JOB_PART", transaction.getReferenceType());
        assertEquals(50L, transaction.getReferenceId());
    }

    @Test
    void addDecimalPart_ShouldDeductPrecisely() {
        mockAddPart();
        service.addJobPart(createRequest(1.5));
        assertQuantity("8.500", stockItem.getCurrentQuantity());
        assertQuantity("4.500", balance.getQuantity());
        ArgumentCaptor<StockTransaction> captor = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertQuantity("1.500", captor.getValue().getQuantity());
        assertQuantity("8.500", captor.getValue().getQuantityAfter());
    }

    @Test
    void insufficientStock_ShouldReject() {
        mockStockLookup();
        assertStatus(400, () -> service.addJobPart(createRequest(15.0)));
        assertQuantity("10.000", stockItem.getCurrentQuantity());
        verify(repository, never()).saveAndFlush(any(ServiceJobPart.class));
        verifyNoInteractions(transactionRepository, storeStockBalanceRepository);
    }

    @Test
    void inactiveStockItem_ShouldReject() {
        stockItem.setActive(false);
        mockStockLookup();
        assertStatus(400, () -> service.addJobPart(createRequest(1.0)));
        verifyNoInteractions(repository, transactionRepository, storeLocationRepository);
    }

    @Test
    void zeroQuantity_ShouldReject() {
        assertStatus(400, () -> service.addJobPart(createRequest(0.0)));
        verifyNoInteractions(stockItemRepository, transactionRepository, repository);
    }

    @Test
    void negativeQuantity_ShouldReject() {
        assertStatus(400, () -> service.addJobPart(createRequest(-1.0)));
        verifyNoInteractions(stockItemRepository, transactionRepository, repository);
    }

    @Test
    void invalidDecimalPrecision_ShouldReject() {
        assertStatus(400, () -> service.addJobPart(createRequest(1.2345)));
        verifyNoInteractions(stockItemRepository, transactionRepository, repository);
    }

    @Test
    void removePart_ShouldRestoreStock() {
        stockItem.setCurrentQuantity(new BigDecimal("8.500"));
        balance.setQuantity(new BigDecimal("4.500"));
        ServiceJobPart part = jobPart(1.5);
        mockRemoval(part);
        service.removeJobPart(50L);
        assertQuantity("10.000", stockItem.getCurrentQuantity());
        assertQuantity("6.000", balance.getQuantity());
        verify(repository).delete(part);
        verify(storeStockBalanceRepository).saveAndFlush(balance);
        ArgumentCaptor<StockTransaction> captor = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactionRepository).save(captor.capture());
        StockTransaction transaction = captor.getValue();
        assertEquals(StockTransactionType.STOCK_IN, transaction.getTransactionType());
        assertQuantity("1.500", transaction.getQuantity());
        assertQuantity("8.500", transaction.getQuantityBefore());
        assertQuantity("10.000", transaction.getQuantityAfter());
        assertEquals("JOB_PART", transaction.getReferenceType());
        assertEquals(50L, transaction.getReferenceId());
    }

    @Test
    void removeNonExistingPart_ShouldReject() {
        when(entityManager.find(ServiceJobPart.class, 99L, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(null);
        assertStatus(404, () -> service.removeJobPart(99L));
        verifyNoInteractions(stockItemRepository, transactionRepository);
    }

    @Test
    void getPartsByJobId_ShouldReturnParts() {
        ServiceJobPart part = jobPart(2.0);
        when(repository.findByServiceJobId(100L)).thenReturn(List.of(part));
        List<ServiceJobPartDTO> result = service.getPartsByJobId(100L);
        assertEquals(1, result.size());
        assertEquals(50L, result.get(0).getId());
        assertEquals(1L, result.get(0).getPartId());
        assertEquals(2L, result.get(0).getStoreLocationId());
    }

    @Test
    void missingStoreId_ShouldRejectBeforeStockLookup() {
        ServiceJobPartDTO dto = createRequest(1.0);
        dto.setStoreLocationId(null);
        assertStatus(400, () -> service.addJobPart(dto));
        verifyNoInteractions(stockItemRepository, storeLocationRepository, transactionRepository);
    }

    @Test
    void unknownStore_ShouldReject() {
        mockStockLookup();
        when(storeLocationRepository.findById(2L)).thenReturn(Optional.empty());
        assertStatus(404, () -> service.addJobPart(createRequest(1.0)));
        verifyNoInteractions(storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void inactiveStore_ShouldReject() {
        store.setActive(false);
        mockStockLookup();
        mockStoreLookup();
        assertStatus(400, () -> service.addJobPart(createRequest(1.0)));
        verifyNoInteractions(storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void missingStoreBalance_ShouldReject() {
        mockStockLookup();
        mockStoreLookup();
        when(storeStockBalanceRepository.findForUpdate(2L, 1L)).thenReturn(Optional.empty());
        assertStatus(400, () -> service.addJobPart(createRequest(1.0)));
        verifyNoInteractions(transactionRepository);
        verify(repository, never()).saveAndFlush(any(ServiceJobPart.class));
    }

    @Test
    void insufficientSelectedStoreStock_ShouldRejectWithoutChangingGlobal() {
        balance.setQuantity(new BigDecimal("0.500"));
        mockStockLookup();
        mockStoreLookup();
        when(storeStockBalanceRepository.findForUpdate(2L, 1L)).thenReturn(Optional.of(balance));
        assertStatus(400, () -> service.addJobPart(createRequest(1.0)));
        assertQuantity("10.000", stockItem.getCurrentQuantity());
        assertQuantity("0.500", balance.getQuantity());
        verifyNoInteractions(transactionRepository);
        verify(repository, never()).saveAndFlush(any(ServiceJobPart.class));
    }

    @Test
    void pcsFractionalIssue_ShouldReject() {
        stockItem.setUnit("PCS");
        mockStockLookup();
        assertStatus(400, () -> service.addJobPart(createRequest(1.5)));
        verifyNoInteractions(storeLocationRepository, storeStockBalanceRepository, transactionRepository);
    }

    @Test
    void removeLegacyPartWithoutStore_ShouldRejectWithoutRestoring() {
        ServiceJobPart part = jobPart(1.0);
        part.setStoreLocation(null);
        when(entityManager.find(ServiceJobPart.class, 50L, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(part);
        assertStatus(409, () -> service.removeJobPart(50L));
        verifyNoInteractions(stockItemRepository, storeStockBalanceRepository, transactionRepository);
        verify(repository, never()).delete(any(ServiceJobPart.class));
    }

    @Test
    void removePartWhenOriginalBalanceMissing_ShouldReject() {
        ServiceJobPart part = jobPart(1.0);
        when(entityManager.find(ServiceJobPart.class, 50L, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(part);
        mockStockLookup();
        when(storeStockBalanceRepository.findForUpdate(2L, 1L)).thenReturn(Optional.empty());
        assertStatus(409, () -> service.removeJobPart(50L));
        assertQuantity("10.000", stockItem.getCurrentQuantity());
        verify(repository, never()).delete(any(ServiceJobPart.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void restoreShouldReturnToOriginalStoreEvenWhenInactive() {
        store.setActive(false);
        stockItem.setCurrentQuantity(new BigDecimal("8.000"));
        balance.setQuantity(new BigDecimal("4.000"));
        ServiceJobPart part = jobPart(2.0);
        mockRemoval(part);
        service.removeJobPart(50L);
        assertQuantity("10.000", stockItem.getCurrentQuantity());
        assertQuantity("6.000", balance.getQuantity());
        verify(repository).delete(part);
    }
}
