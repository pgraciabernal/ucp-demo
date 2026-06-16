package com.ucp.poc.util;

import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ISO 4217 Currency Code Validator
 *
 * Validates currency codes against ISO 4217 standard (3-letter codes: USD, EUR, GBP).
 * Used across all UCP models to ensure currency consistency and compliance.
 *
 * @see <a href="https://www.iso.org/iso-4217-currency-codes.html">ISO 4217 Standard</a>
 */
public class ISOCurrencyValidator {

    /**
     * Set of all valid ISO 4217 currency codes
     * Loaded from Java's Currency class which implements ISO 4217
     */
    private static final Set<String> VALID_CURRENCIES = Currency.getAvailableCurrencies()
            .stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toSet());

    /**
     * Common currencies for quick reference
     */
    private static final Set<String> COMMON_CURRENCIES = Set.of(
            "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "MXN"
    );

    /**
     * Validates if a currency code is valid according to ISO 4217
     *
     * @param currencyCode The 3-letter currency code (e.g., "USD", "EUR")
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            return false;
        }

        // Currency codes must be uppercase 3-letter codes
        String normalized = currencyCode.trim().toUpperCase();

        if (!normalized.matches("^[A-Z]{3}$")) {
            return false;
        }

        return VALID_CURRENCIES.contains(normalized);
    }

    /**
     * Validates currency code and throws exception if invalid
     *
     * @param currencyCode The 3-letter currency code to validate
     * @throws IllegalArgumentException if currency code is invalid
     */
    public static void validate(String currencyCode) {
        if (!isValid(currencyCode)) {
            throw new IllegalArgumentException(
                    String.format("Invalid ISO 4217 currency code: '%s'. Must be a 3-letter code like USD, EUR, GBP.",
                            currencyCode)
            );
        }
    }

    /**
     * Normalizes currency code to uppercase ISO 4217 format
     *
     * @param currencyCode The currency code to normalize
     * @return Normalized uppercase currency code
     * @throws IllegalArgumentException if currency code is invalid
     */
    public static String normalize(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be null or empty");
        }

        String normalized = currencyCode.trim().toUpperCase();
        validate(normalized);
        return normalized;
    }

    /**
     * Checks if a currency is commonly used
     *
     * @param currencyCode The 3-letter currency code
     * @return true if currency is in the common currencies list
     */
    public static boolean isCommon(String currencyCode) {
        return currencyCode != null && COMMON_CURRENCIES.contains(currencyCode.toUpperCase());
    }

    /**
     * Gets the currency symbol for a given currency code
     *
     * @param currencyCode ISO 4217 currency code
     * @return Currency symbol (e.g., "$" for USD, "€" for EUR)
     * @throws IllegalArgumentException if currency code is invalid
     */
    public static String getSymbol(String currencyCode) {
        validate(currencyCode);
        return Currency.getInstance(currencyCode).getSymbol();
    }

    /**
     * Gets the display name for a currency code
     *
     * @param currencyCode ISO 4217 currency code
     * @return Currency display name (e.g., "US Dollar", "Euro")
     * @throws IllegalArgumentException if currency code is invalid
     */
    public static String getDisplayName(String currencyCode) {
        validate(currencyCode);
        return Currency.getInstance(currencyCode).getDisplayName();
    }
}
