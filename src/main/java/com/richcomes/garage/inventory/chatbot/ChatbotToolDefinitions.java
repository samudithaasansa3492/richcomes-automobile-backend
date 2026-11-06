
package com.richcomes.garage.inventory.chatbot;

import java.util.List;
import java.util.Map;

public final class ChatbotToolDefinitions {

    private ChatbotToolDefinitions() {
    }

    public static List<Map<String, Object>> getTools() {

        // TOOL 1: LOW-STOCK ITEMS
        Map<String, Object> lowStockFunction = Map.of(
                "name", "get_low_stock_items",
                "description",
                "Get current active low-stock inventory items. " +
                        "Use when the user asks about low stock or " +
                        "items needing reordering. Results are limited.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of(),
                        "additionalProperties", false
                )
        );

        // TOOL 2: FIND STOCK ITEM BY CODE
        Map<String, Object> itemByCodeFunction = Map.of(
                "name", "get_stock_item_by_code",
                "description",
                "Look up a stock item using its exact item code. " +
                        "Return item name, category, current quantity, " +
                        "reorder level and active status.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "itemCode", Map.of(
                                        "type", "string",
                                        "description",
                                        "Exact stock item code, for example OIL-001"
                                )
                        ),
                        "required", List.of("itemCode"),
                        "additionalProperties", false
                )
        );

        // TOOL 3: SEARCH STOCK ITEMS BY NAME
        Map<String, Object> searchByNameFunction = Map.of(
                "name", "search_stock_items_by_name",
                "description",
                "Search stock items using a full or partial " +
                        "item name, such as Engine Oil or Brake Pads.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "itemName", Map.of(
                                        "type", "string",
                                        "description",
                                        "Full or partial stock item name"
                                )
                        ),
                        "required", List.of("itemName"),
                        "additionalProperties", false
                )
        );

        // TOOL 4: SUPPLIERS
        Map<String, Object> supplierFunction = Map.of(
                "name", "get_suppliers",
                "description",
                "Get up to 20 garage inventory suppliers. " +
                        "Includes basic supplier information. " +
                        "Use only when supplier information is requested.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of(),
                        "additionalProperties", false
                )
        );

        // TOOL 5: STOCK CATEGORIES
        Map<String, Object> categoryFunction = Map.of(
                "name", "get_stock_categories",
                "description",
                "Get up to 20 stock categories, including " +
                        "their names, descriptions and active status.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of(),
                        "additionalProperties", false
                )
        );

        // TOOL 6: RECENT GRNS
        Map<String, Object> grnFunction = Map.of(
                "name", "get_recent_grns",
                "description",
                "Get up to 20 recent Goods Received Notes. " +
                        "Includes GRN number, supplier and status. " +
                        "DRAFT does not mean stock was received. " +
                        "Only CONFIRMED means receipt was confirmed. " +
                        "Do not treat this limited list as all GRNs.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of(),
                        "additionalProperties", false
                )
        );

        // TOOL 7: RECENT STOCK MOVEMENTS
        Map<String, Object> movementFunction = Map.of(
                "name", "get_recent_stock_movements",
                "description",
                "Get up to 20 recent stock movements across " +
                        "all transaction types. Includes item code, " +
                        "item name, transaction type, quantity, " +
                        "before and after quantity, and date. " +
                        "Use for general recent stock movement questions. " +
                        "Do not use for transaction-type-specific searches.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of(),
                        "additionalProperties", false
                )
        );

        // TOOL 8: FILTER STOCK MOVEMENTS BY TYPE
        Map<String, Object> movementByTypeFunction = Map.of(
                "name", "get_stock_movements_by_type",
                "description",
                "Get up to 20 recent stock movements filtered " +
                        "by one transaction type. Use DAMAGE for damaged " +
                        "stock, WASTAGE for wasted stock, STOCK_IN for " +
                        "stock received, STOCK_OUT for stock issued, " +
                        "ADJUSTMENT_IN for positive adjustments and " +
                        "ADJUSTMENT_OUT for negative adjustments. " +
                        "Use this instead of get_recent_stock_movements " +
                        "when the user specifies a transaction type. " +
                        "Results are limited and cannot establish " +
                        "a complete history or exact total count.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "transactionType", Map.of(
                                        "type", "string",
                                        "description",
                                        "Required stock transaction type",
                                        "enum", List.of(
                                                "STOCK_IN",
                                                "STOCK_OUT",
                                                "DAMAGE",
                                                "WASTAGE",
                                                "ADJUSTMENT_IN",
                                                "ADJUSTMENT_OUT"
                                        )
                                )
                        ),
                        "required", List.of("transactionType"),
                        "additionalProperties", false
                )
        );

        // REGISTER ALL EIGHT GROQ TOOLS
        return List.of(
                Map.of(
                        "type", "function",
                        "function", lowStockFunction
                ),
                Map.of(
                        "type", "function",
                        "function", itemByCodeFunction
                ),
                Map.of(
                        "type", "function",
                        "function", searchByNameFunction
                ),
                Map.of(
                        "type", "function",
                        "function", supplierFunction
                ),
                Map.of(
                        "type", "function",
                        "function", categoryFunction
                ),
                Map.of(
                        "type", "function",
                        "function", grnFunction
                ),
                Map.of(
                        "type", "function",
                        "function", movementFunction
                ),
                Map.of(
                        "type", "function",
                        "function", movementByTypeFunction
                )
        );
    }
}
