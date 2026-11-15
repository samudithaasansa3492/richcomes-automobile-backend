
package com.richcomes.garage.service.service;

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

    @Mock
    private ServiceJobPartRepository repository;

    @Mock
    private ServiceJobRepository jobRepository;

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockTransactionRepository transactionRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ServiceJobPartServiceImpl service;

    private StockItem stockItem;
    private ServiceJob job;

    @BeforeEach
    void setUp() {

        // FIX: Inject the mock EntityManager into
        // the @PersistenceContext field manually.
        ReflectionTestUtils.setField(
                service,
                "entityManager",
                entityManager
        );

        stockItem = new StockItem();
        stockItem.setId(1L);
        stockItem.setItemCode("OIL-001");
        stockItem.setItemName("Engine Oil");
        stockItem.setCurrentQuantity(
                new BigDecimal("10.000")
        );
        stockItem.setActive(true);
        stockItem.setUnit("L");

        job = new ServiceJob();
        job.setId(100L);
    }

    private ServiceJobPartDTO createRequest(
            double quantity) {

        ServiceJobPartDTO dto = new ServiceJobPartDTO();

        dto.setServiceJobId(100L);
        dto.setPartId(1L);
        dto.setQuantity(quantity);

        return dto;
    }

    private void mockAddPart() {

        when(stockItemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(stockItem));

        when(jobRepository.findById(100L))
                .thenReturn(Optional.of(job));

        when(repository.saveAndFlush(
                any(ServiceJobPart.class)
        )).thenAnswer(invocation -> {

            ServiceJobPart part =
                    invocation.getArgument(0);

            part.setId(50L);
            return part;
        });
    }

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
    void addPart_ShouldDeductStock() {

        mockAddPart();

        ServiceJobPartDTO result =
                service.addJobPart(
                        createRequest(2.0)
                );

        assertNotNull(result);
        assertEquals(50L, result.getId());

        assertQuantity(
                "8.000",
                stockItem.getCurrentQuantity()
        );

        verify(stockItemRepository)
                .saveAndFlush(stockItem);

        ArgumentCaptor<StockTransaction> captor =
                ArgumentCaptor.forClass(
                        StockTransaction.class
                );

        verify(transactionRepository)
                .save(captor.capture());

        StockTransaction transaction =
                captor.getValue();

        assertEquals(
                StockTransactionType.STOCK_OUT,
                transaction.getTransactionType()
        );

        assertQuantity(
                "2.000",
                transaction.getQuantity()
        );

        assertQuantity(
                "10.000",
                transaction.getQuantityBefore()
        );

        assertQuantity(
                "8.000",
                transaction.getQuantityAfter()
        );

        assertEquals(
                "JOB_PART",
                transaction.getReferenceType()
        );

        assertEquals(
                50L,
                transaction.getReferenceId()
        );
    }

    @Test
    void addDecimalPart_ShouldDeductPrecisely() {

        mockAddPart();

        service.addJobPart(
                createRequest(1.5)
        );

        assertQuantity(
                "8.500",
                stockItem.getCurrentQuantity()
        );

        ArgumentCaptor<StockTransaction> captor =
                ArgumentCaptor.forClass(
                        StockTransaction.class
                );

        verify(transactionRepository)
                .save(captor.capture());

        assertQuantity(
                "1.500",
                captor.getValue().getQuantity()
        );

        assertQuantity(
                "8.500",
                captor.getValue().getQuantityAfter()
        );
    }

    @Test
    void insufficientStock_ShouldReject() {

        when(stockItemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(stockItem));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.addJobPart(
                                createRequest(15.0)
                        )
                );

        assertEquals(
                400,
                exception.getStatusCode().value()
        );

        assertQuantity(
                "10.000",
                stockItem.getCurrentQuantity()
        );

        verify(repository, never())
                .saveAndFlush(any(ServiceJobPart.class));

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void inactiveStockItem_ShouldReject() {

        stockItem.setActive(false);

        when(stockItemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(stockItem));

        assertThrows(
                ResponseStatusException.class,
                () -> service.addJobPart(
                        createRequest(1.0)
                )
        );

        verifyNoInteractions(
                repository,
                transactionRepository
        );
    }

    @Test
    void zeroQuantity_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.addJobPart(
                        createRequest(0.0)
                )
        );

        verifyNoInteractions(
                stockItemRepository,
                transactionRepository,
                repository
        );
    }

    @Test
    void negativeQuantity_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.addJobPart(
                        createRequest(-1.0)
                )
        );

        verifyNoInteractions(
                stockItemRepository,
                transactionRepository,
                repository
        );
    }

    @Test
    void invalidDecimalPrecision_ShouldReject() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.addJobPart(
                        createRequest(1.2345)
                )
        );

        verifyNoInteractions(
                stockItemRepository,
                transactionRepository,
                repository
        );
    }

    @Test
    void removePart_ShouldRestoreStock() {

        stockItem.setCurrentQuantity(
                new BigDecimal("8.500")
        );

        ServiceJobPart part =
                new ServiceJobPart();

        part.setId(50L);
        part.setServiceJob(job);
        part.setPartId(1L);
        part.setQuantity(1.5);

        when(entityManager.find(
                ServiceJobPart.class,
                50L,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(part);

        when(stockItemRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(stockItem));

        service.removeJobPart(50L);

        assertQuantity(
                "10.000",
                stockItem.getCurrentQuantity()
        );

        verify(repository).delete(part);

        ArgumentCaptor<StockTransaction> captor =
                ArgumentCaptor.forClass(
                        StockTransaction.class
                );

        verify(transactionRepository)
                .save(captor.capture());

        StockTransaction transaction =
                captor.getValue();

        assertEquals(
                StockTransactionType.STOCK_IN,
                transaction.getTransactionType()
        );

        assertQuantity(
                "1.500",
                transaction.getQuantity()
        );

        assertQuantity(
                "8.500",
                transaction.getQuantityBefore()
        );

        assertQuantity(
                "10.000",
                transaction.getQuantityAfter()
        );

        assertEquals(
                "JOB_PART",
                transaction.getReferenceType()
        );

        assertEquals(
                50L,
                transaction.getReferenceId()
        );
    }

    @Test
    void removeNonExistingPart_ShouldReject() {

        when(entityManager.find(
                ServiceJobPart.class,
                99L,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(null);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.removeJobPart(99L)
                );

        assertEquals(
                404,
                exception.getStatusCode().value()
        );

        verifyNoInteractions(
                stockItemRepository,
                transactionRepository
        );
    }

    @Test
    void getPartsByJobId_ShouldReturnParts() {

        ServiceJobPart part =
                new ServiceJobPart();

        part.setId(50L);
        part.setServiceJob(job);
        part.setPartId(1L);
        part.setQuantity(2.0);

        when(repository.findByServiceJobId(100L))
                .thenReturn(List.of(part));

        List<ServiceJobPartDTO> result =
                service.getPartsByJobId(100L);

        assertEquals(1, result.size());
        assertEquals(50L, result.get(0).getId());
        assertEquals(1L, result.get(0).getPartId());
    }
}
