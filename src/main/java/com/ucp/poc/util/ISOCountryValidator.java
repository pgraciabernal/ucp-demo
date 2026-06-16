package com.ucp.poc.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ISO 3166-1 Country Code Validator
 *
 * Validates country codes against ISO 3166-1 alpha-2 standard (2-letter codes: US, GB, FR).
 * Used in PostalAddress and other models to ensure country code consistency.
 *
 * @see <a href="https://www.iso.org/iso-3166-country-codes.html">ISO 3166-1 Standard</a>
 */
public class ISOCountryValidator {

    /**
     * Set of all valid ISO 3166-1 alpha-2 country codes
     * Loaded from Java's Locale class
     */
    private static final Set<String> VALID_COUNTRY_CODES = Arrays.stream(Locale.getISOCountries())
            .collect(Collectors.toSet());

    /**
     * Validates if a country code is valid according to ISO 3166-1 alpha-2
     *
     * @param countryCode The 2-letter country code (e.g., "US", "GB", "FR")
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            return false;
        }

        // Country codes must be uppercase 2-letter codes
        String normalized = countryCode.trim().toUpperCase();

        if (!normalized.matches("^[A-Z]{2}$")) {
            return false;
        }

        return VALID_COUNTRY_CODES.contains(normalized);
    }

    /**
     * Validates country code and throws exception if invalid
     * Also accepts common country names and converts them
     *
     * @param countryCode The 2-letter country code or country name to validate
     * @throws IllegalArgumentException if country code is invalid
     */
    public static void validate(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Country code cannot be null or empty");
        }

        String normalized = countryCode.trim().toUpperCase();

        // Check if it's already valid
        if (isValid(normalized)) {
            return;
        }

        // Try to convert from common name
        String isoCode = fromCommonName(normalized);
        if (isoCode != null) {
            return; // Valid - can be converted
        }

        // Invalid
        throw new IllegalArgumentException(
                String.format("Invalid ISO 3166-1 alpha-2 country code: '%s'. Must be a 2-letter code like US, GB, FR, or a recognized country name like 'Spain', 'Germany', etc.",
                        countryCode)
        );
    }

    /**
     * Normalizes country code to uppercase ISO 3166-1 alpha-2 format
     * Automatically converts common country names to ISO codes
     *
     * @param countryCode The country code or name to normalize
     * @return Normalized uppercase country code
     * @throws IllegalArgumentException if country code is invalid
     */
    public static String normalize(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Country code cannot be null or empty");
        }

        String normalized = countryCode.trim().toUpperCase();

        // Try to convert common names to ISO codes
        String isoCode = fromCommonName(normalized);
        if (isoCode != null) {
            return isoCode;
        }

        // If already valid, return it
        if (isValid(normalized)) {
            return normalized;
        }

        // Invalid - throw exception
        throw new IllegalArgumentException(
                String.format("Invalid ISO 3166-1 alpha-2 country code: '%s'. Must be a 2-letter code like US, GB, FR, or a recognized country name like 'Spain', 'Germany', etc.",
                        countryCode)
        );
    }

    /**
     * Gets the display name for a country code
     *
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return Country display name (e.g., "United States", "United Kingdom")
     * @throws IllegalArgumentException if country code is invalid
     */
    public static String getDisplayName(String countryCode) {
        validate(countryCode);
        Locale locale = new Locale("", countryCode);
        return locale.getDisplayCountry();
    }

    /**
     * Converts common country name variations to ISO code
     * Useful for user input normalization
     *
     * @param countryName Common country name or code
     * @return ISO 3166-1 alpha-2 code, or null if not recognized
     */
    public static String fromCommonName(String countryName) {
        if (countryName == null || countryName.trim().isEmpty()) {
            return null;
        }

        String normalized = countryName.trim().toUpperCase();

        // Already a valid ISO code
        if (isValid(normalized)) {
            return normalized;
        }

        // Common variations
        return switch (normalized) {
            case "USA", "UNITED STATES", "UNITED STATES OF AMERICA" -> "US";
            case "UK", "BRITAIN", "GREAT BRITAIN", "UNITED KINGDOM" -> "GB";
            case "ENGLAND", "SCOTLAND", "WALES", "NORTHERN IRELAND" -> "GB";
            case "SPAIN" -> "ES";
            case "GERMANY" -> "DE";
            case "FRANCE" -> "FR";
            case "ITALY" -> "IT";
            case "JAPAN" -> "JP";
            case "CHINA" -> "CN";
            case "INDIA" -> "IN";
            case "CANADA" -> "CA";
            case "AUSTRALIA" -> "AU";
            case "MEXICO" -> "MX";
            case "BRAZIL" -> "BR";
            default -> null;
        };
    }
}
