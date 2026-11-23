package com.richcomes.garage.service.service.impl;

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
import com.richcomes.garage.service.service.ServiceJobPartService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceJobPartServiceImpl
        implements ServiceJobPartService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ServiceJobPartRepository repository;
    private final ServiceJobRepository jobRepository;
    private final StockItemRepository stockItemRepository;
    private final StockTransactionRepository transactionRepository;
    private final StoreLocationRepository storeLocationRepository;
    private final StoreStockBalanceRepository storeStockBalanceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public ServiceJobPartServiceImpl(
            ServiceJobPartRepository repository,
            ServiceJobRepository jobRepository,
            StockItemRepository stockItemRepository,
            StockTransactionRepository transactionRepository,
            StoreLocationRepository storeLocationRepository,
            StoreStockBalanceRepository storeStockBalanceRepository) {

        this.repository = repository;
        this.jobRepository = jobRepository;
        this.stockItemRepository = stockItemRepository;
        this.transactionRepository = transactionRepository;
        this.storeLocationRepository = storeLocationRepository;
        this.storeStockBalanceRepository = storeStockBalanceRepository;
    }

    // ENTITY TO DTO
    private ServiceJobPartDTO convertToDTO(
            ServiceJobPart entity) {

        ServiceJobPartDTO dto = new ServiceJobPartDTO();

        dto.setId(entity.getId());

        if (entity.getServiceJob() != null) {
            dto.setServiceJobId(
                    entity.getServiceJob().getId()
            );
        }

        dto.setPartId(entity.getPartId());
        if (entity.getStoreLocation() != null) {
            dto.setStoreLocationId(entity.getStoreLocation().getId());
        }
        dto.setQuantity(entity.getQuantity());
        dto.setUnitPrice(entity.getUnitPrice());
        dto.setTotalAmount(entity.getTotalAmount());

        return dto;
    }

    // DTO TO ENTITY
    private ServiceJobPart convertToEntity(
            ServiceJobPartDTO dto) {

        ServiceJobPart entity = new ServiceJobPart();

        if (dto.getServiceJobId() != null) {

            ServiceJob job = jobRepository
                    .findById(dto.getServiceJobId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Job not found with id: "
                                            + dto.getServiceJobId()
                            )
                    );

            entity.setServiceJob(job);
        }

        entity.setPartId(dto.getPartId());
        // Store is assigned by addJobPart after validating the selected store.
        entity.setQuantity(dto.getQuantity());
        entity.setUnitPrice(dto.getUnitPrice());
        entity.setTotalAmount(dto.getTotalAmount());

        return entity;
    }

    // VALIDATE POSITIVE DECIMAL QUANTITY
    private BigDecimal validateQuantity(Double quantity) {

        if (quantity == null
                || !Double.isFinite(quantity)
                || quantity <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Part quantity must be greater than zero"
            );
        }

        BigDecimal value = BigDecimal.valueOf(quantity);

        if (!fitsQuantityColumn(value)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Part quantity supports up to 3 decimal places"
            );
        }

        return value;
    }

    // NEW: VALIDATE QUANTITY AGAINST ITEM UNIT
    private void validateQuantityForUnit(
            StockItem stockItem,
            BigDecimal quantity) {

        String unit = stockItem.getUnit();

        if ("PCS".equals(unit)) {

            // 2 and 2.000 are valid whole numbers.
            // 1.5 is invalid for PCS.
            if (quantity.stripTrailingZeros().scale() > 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "PCS items require whole-number quantities"
                );
            }

        } else if (!"L".equals(unit)
                && !"ML".equals(unit)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stock item has an invalid measurement unit"
            );
        }
    }

    // VALIDATE NUMERIC(14,3)
    private boolean fitsQuantityColumn(
            BigDecimal quantity) {

        if (quantity == null) {
            return false;
        }

        BigDecimal normalized =
                quantity.stripTrailingZeros();

        int fractionDigits =
                Math.max(0, normalized.scale());

        int integerDigits =
                Math.max(
                        0,
                        normalized.precision()
                                - normalized.scale()
                );

        return fractionDigits <= 3
                && integerDigits <= 11;
    }

    // ADD JOB PART AND DEDUCT STOCK
    @Override
    @Transactional
    public ServiceJobPartDTO addJobPart(
            ServiceJobPartDTO jobPartDTO) {

        if (jobPartDTO == null
                || jobPartDTO.getServiceJobId() == null
                || jobPartDTO.getPartId() == null
                || jobPartDTO.getStoreLocationId() == null
                || jobPartDTO.getServiceJobId() <= 0
                || jobPartDTO.getPartId() <= 0
                || jobPartDTO.getStoreLocationId() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid job ID, part ID and store location ID are required"
            );
        }

        if (jobPartDTO.getId() != null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Part ID must be empty when adding a new job part"
            );
        }

        BigDecimal quantity =
                validateQuantity(jobPartDTO.getQuantity());

        // ServiceJobPart.partId == StockItem.id
        StockItem stockItem = stockItemRepository
                .findByIdForUpdate(jobPartDTO.getPartId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Inventory stock item not found"
                        )
                );

        if (!stockItem.isActive()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot use an inactive stock item"
            );
        }

        // NEW: Enforce PCS / L / ML quantity rules.
        validateQuantityForUnit(stockItem, quantity);

        BigDecimal before =
                stockItem.getCurrentQuantity();

        if (before == null
                || before.compareTo(ZERO) < 0
                || !fitsQuantityColumn(before)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid current stock quantity"
            );
        }

        // Prevent invalid fractional balances for PCS.
        if ("PCS".equals(stockItem.getUnit())
                && before.stripTrailingZeros().scale() > 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "PCS item has an invalid fractional stock balance"
            );
        }

        if (before.compareTo(quantity) < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient stock. Available: "
                            + before.toPlainString()
            );
        }

        // Lock global stock item first, then store balance (same ordering as GRN).
        Long storeId = jobPartDTO.getStoreLocationId();
        StoreLocation store = storeLocationRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Store location not found"));
        if (!store.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot issue parts from an inactive store");
        }

        // Never create a missing store balance here: a store without stock cannot issue it.
        StoreStockBalance balance = storeStockBalanceRepository
                .findForUpdate(storeId, stockItem.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Insufficient stock in selected store"));
        BigDecimal storeBefore = balance.getQuantity();
        if (storeBefore == null || storeBefore.compareTo(ZERO) < 0
                || !fitsQuantityColumn(storeBefore)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Invalid selected store stock balance");
        }
        if ("PCS".equals(stockItem.getUnit())
                && storeBefore.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "PCS item has fractional store stock");
        }
        if (storeBefore.compareTo(quantity) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Insufficient stock in selected store. Available: "
                    + storeBefore.toPlainString());
        }
        BigDecimal storeAfter = storeBefore.subtract(quantity);
        BigDecimal after = before.subtract(quantity);

        ServiceJobPart entity =
                convertToEntity(jobPartDTO);

        entity.setStoreLocation(store);
        ServiceJobPart saved =
                repository.saveAndFlush(entity);

        balance.setQuantity(storeAfter);
        storeStockBalanceRepository.saveAndFlush(balance);

        stockItem.setCurrentQuantity(after);
        stockItemRepository.saveAndFlush(stockItem);

        // Record STOCK_OUT history.
        StockTransaction transaction =
                new StockTransaction();

        transaction.setStockItem(stockItem);
        transaction.setStoreLocation(store);
        transaction.setTransactionType(
                StockTransactionType.STOCK_OUT
        );

        transaction.setQuantity(quantity);
        transaction.setQuantityBefore(before);
        transaction.setQuantityAfter(after);

        transaction.setReferenceType("JOB_PART");
        transaction.setReferenceId(saved.getId());

        transaction.setReason(
                "Part issued to job #"
                        + jobPartDTO.getServiceJobId()
        );

        transactionRepository.save(transaction);

        return convertToDTO(saved);
    }

    // GET JOB PARTS
    @Override
    @Transactional(readOnly = true)
    public List<ServiceJobPartDTO> getPartsByJobId(
            Long jobId) {

        return repository.findByServiceJobId(jobId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // REMOVE JOB PART AND RESTORE STOCK
    @Override
    @Transactional
    public void removeJobPart(Long id) {

        if (id == null || id <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid job part ID is required"
            );
        }

        // Lock job part to prevent duplicate restoration.
        ServiceJobPart jobPart = entityManager.find(
                ServiceJobPart.class,
                id,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (jobPart == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Job part not found"
            );
        }

        if (jobPart.getPartId() == null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job part has no linked stock item"
            );
        }

        BigDecimal quantity =
                validateQuantity(jobPart.getQuantity());

        if (jobPart.getStoreLocation() == null
                || jobPart.getStoreLocation().getId() == null) {
            // Legacy job parts have no known source store. Never guess where to restore.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This job part has no recorded source store; manual reconciliation required");
        }

        StockItem stockItem = stockItemRepository
                .findByIdForUpdate(jobPart.getPartId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Inventory stock item not found"
                        )
                );

        // NEW: Validate unit during stock restoration.
        validateQuantityForUnit(stockItem, quantity);

        BigDecimal before =
                stockItem.getCurrentQuantity();

        if (before == null
                || before.compareTo(ZERO) < 0
                || !fitsQuantityColumn(before)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid current stock quantity"
            );
        }

        if ("PCS".equals(stockItem.getUnit())
                && before.stripTrailingZeros().scale() > 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "PCS item has an invalid fractional stock balance"
            );
        }

        BigDecimal after = before.add(quantity);

        if (!fitsQuantityColumn(after)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Restored stock exceeds supported range"
            );
        }

        Long storeId = jobPart.getStoreLocation().getId();
        StoreStockBalance balance = storeStockBalanceRepository
                .findForUpdate(storeId, stockItem.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Original store balance is missing; manual reconciliation required"));
        BigDecimal storeBefore = balance.getQuantity();
        if (storeBefore == null || storeBefore.compareTo(ZERO) < 0
                || !fitsQuantityColumn(storeBefore)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Invalid original store stock balance");
        }
        if ("PCS".equals(stockItem.getUnit())
                && storeBefore.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "PCS item has fractional store stock");
        }
        BigDecimal storeAfter = storeBefore.add(quantity);
        if (!fitsQuantityColumn(storeAfter)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Restored store stock exceeds supported range");
        }

        balance.setQuantity(storeAfter);
        storeStockBalanceRepository.saveAndFlush(balance);
        stockItem.setCurrentQuantity(after);
        stockItemRepository.saveAndFlush(stockItem);

        // Keep history after removing the job part.
        StockTransaction transaction =
                new StockTransaction();

        transaction.setStockItem(stockItem);
        transaction.setStoreLocation(jobPart.getStoreLocation());
        transaction.setTransactionType(
                StockTransactionType.STOCK_IN
        );

        transaction.setQuantity(quantity);
        transaction.setQuantityBefore(before);
        transaction.setQuantityAfter(after);

        transaction.setReferenceType("JOB_PART");
        transaction.setReferenceId(jobPart.getId());

        transaction.setReason(
                "Stock restored after removing job part #"
                        + jobPart.getId()
        );

        transactionRepository.save(transaction);

        repository.delete(jobPart);
    }
}
