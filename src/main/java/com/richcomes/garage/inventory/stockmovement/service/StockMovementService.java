
package com.richcomes.garage.inventory.stockmovement.service;

import com.richcomes.garage.inventory.stockitem.entity.StockItem;
import com.richcomes.garage.inventory.stockitem.repository.StockItemRepository;

import com.richcomes.garage.inventory.stockmovement.dto.request.StockMovementRequest;
import com.richcomes.garage.inventory.stockmovement.dto.response.StockMovementResponse;
import com.richcomes.garage.inventory.stockmovement.entity.StockTransaction;
import com.richcomes.garage.inventory.stockmovement.enums.StockTransactionType;
import com.richcomes.garage.inventory.stockmovement.repository.StockTransactionRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class StockMovementService {

    private static final int CHATBOT_RESULT_LIMIT = 20;

    private final StockItemRepository itemRepository;
    private final StockTransactionRepository transactionRepository;

    public StockMovementService(
            StockItemRepository itemRepository,
            StockTransactionRepository transactionRepository) {

        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
    }

    // RECORD STOCK MOVEMENT
    @Transactional
    public StockMovementResponse recordMovement(
            StockMovementRequest request) {

        if (request == null
                || request.getStockItemId() == null
                || request.getTransactionType() == null
                || request.getQuantity() == null
                || request.getQuantity() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid stock item, transaction type and positive quantity are required"
            );
        }

        if (request.getReason() != null
                && request.getReason().length() > 500) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reason cannot exceed 500 characters"
            );
        }

        StockTransactionType type = request.getTransactionType();

        int quantity = request.getQuantity();

        if ((type == StockTransactionType.DAMAGE
                || type == StockTransactionType.WASTAGE
                || type == StockTransactionType.ADJUSTMENT_IN
                || type == StockTransactionType.ADJUSTMENT_OUT)
                && (request.getReason() == null
                || request.getReason().isBlank())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reason is required for this transaction"
            );
        }

        // Lock stock item before changing quantity
        StockItem item = itemRepository
                .findByIdForUpdate(request.getStockItemId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Stock item not found"
                ));

        if (!item.isActive()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot move stock for inactive item"
            );
        }

        Integer currentQuantity = item.getCurrentQuantity();

        if (currentQuantity == null || currentQuantity < 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid current stock quantity"
            );
        }

        int before = currentQuantity;
        int after;

        try {

            after = switch (type) {

                case STOCK_IN, ADJUSTMENT_IN ->
                        Math.addExact(before, quantity);

                case STOCK_OUT, DAMAGE, WASTAGE, ADJUSTMENT_OUT ->
                        Math.subtractExact(before, quantity);
            };

        } catch (ArithmeticException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Stock quantity exceeds supported range"
            );
        }

        if (after < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient stock. Available: " + before
            );
        }

        // Update item quantity
        item.setCurrentQuantity(after);

        itemRepository.saveAndFlush(item);

        // Save transaction history
        StockTransaction transaction = new StockTransaction();

        transaction.setStockItem(item);
        transaction.setTransactionType(type);
        transaction.setQuantity(quantity);
        transaction.setQuantityBefore(before);
        transaction.setQuantityAfter(after);
        transaction.setReason(request.getReason());

        // Audit user and external references should be set
        // by trusted backend integration after authentication.

        StockTransaction saved =
                transactionRepository.saveAndFlush(transaction);

        return mapToResponse(saved);
    }

    // EXISTING: GET ALL TRANSACTIONS
    @Transactional(readOnly = true)
    public List<StockMovementResponse> getAllTransactions() {

        return transactionRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // EXISTING: GET ONE ITEM'S TRANSACTION HISTORY
    @Transactional(readOnly = true)
    public List<StockMovementResponse> getItemTransactions(
            Long itemId) {

        if (itemId == null || itemId <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid stock item ID is required"
            );
        }

        if (!itemRepository.existsById(itemId)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Stock item not found"
            );
        }

        return transactionRepository
                .findByStockItemIdOrderByCreatedAtDesc(itemId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // NEW: GET LATEST 20 TRANSACTIONS
    // DATABASE-LEVEL LIMIT FOR CHATBOT
    @Transactional(readOnly = true)
    public List<StockMovementResponse> getRecentTransactions() {

        return transactionRepository
                .findAllByOrderByCreatedAtDesc(
                        PageRequest.of(0, CHATBOT_RESULT_LIMIT)
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // UPDATED: FILTER TRANSACTIONS BY TYPE
    // DATABASE-LEVEL LIMIT FOR CHATBOT
    @Transactional(readOnly = true)
    public List<StockMovementResponse> getTransactionsByType(
            StockTransactionType transactionType) {

        if (transactionType == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid transaction type is required"
            );
        }

        return transactionRepository
                .findByTransactionTypeOrderByCreatedAtDesc(
                        transactionType,
                        PageRequest.of(0, CHATBOT_RESULT_LIMIT)
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // CONVERT ENTITY TO RESPONSE DTO
    private StockMovementResponse mapToResponse(
            StockTransaction transaction) {

        StockMovementResponse response =
                new StockMovementResponse();

        StockItem item = transaction.getStockItem();

        response.setId(transaction.getId());
        response.setStockItemId(item.getId());
        response.setItemCode(item.getItemCode());
        response.setItemName(item.getItemName());

        response.setTransactionType(
                transaction.getTransactionType()
        );

        response.setQuantity(
                transaction.getQuantity()
        );

        response.setQuantityBefore(
                transaction.getQuantityBefore()
        );

        response.setQuantityAfter(
                transaction.getQuantityAfter()
        );

        response.setReferenceType(
                transaction.getReferenceType()
        );

        response.setReferenceId(
                transaction.getReferenceId()
        );

        response.setReason(
                transaction.getReason()
        );

        response.setPerformedBy(
                transaction.getPerformedBy()
        );

        response.setCreatedAt(
                transaction.getCreatedAt()
        );

        return response;
    }
}
