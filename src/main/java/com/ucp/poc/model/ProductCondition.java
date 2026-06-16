package com.ucp.poc.model;

/**
 * Product Condition Enum
 *
 * Represents the condition of a product following Schema.org itemCondition property.
 *
 * @see <a href="https://schema.org/OfferItemCondition">Schema.org OfferItemCondition</a>
 */
public enum ProductCondition {
    /**
     * Brand new, unused product in original packaging
     */
    NEW("New"),

    /**
     * Previously owned, used product
     */
    USED("Used"),

    /**
     * Professionally restored to working condition
     */
    REFURBISHED("Refurbished"),

    /**
     * Product with damage but still functional
     */
    DAMAGED("Damaged");

    private final String displayName;

    ProductCondition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns Schema.org compliant value
     * @return Schema.org itemCondition value
     */
    public String getSchemaOrgValue() {
        return switch (this) {
            case NEW -> "NewCondition";
            case USED -> "UsedCondition";
            case REFURBISHED -> "RefurbishedCondition";
            case DAMAGED -> "DamagedCondition";
        };
    }
}
