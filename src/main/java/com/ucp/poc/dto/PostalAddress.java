package com.ucp.poc.dto;

import com.ucp.poc.util.ISOCountryValidator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Postal Address DTO following Schema.org PostalAddress specification
 *
 * Represents a physical address for billing and shipping purposes.
 * Complies with ISO 3166-1 alpha-2 for country codes.
 *
 * @see <a href="https://schema.org/PostalAddress">Schema.org PostalAddress</a>
 * @see <a href="https://www.iso.org/iso-3166-country-codes.html">ISO 3166-1</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostalAddress {
    /**
     * First name of the recipient
     */
    private String firstName;

    /**
     * Last name of the recipient
     */
    private String lastName;

    /**
     * Street address line 1 (required)
     * Example: "123 Main Street"
     */
    private String addressLine1;

    /**
     * Street address line 2 (optional)
     * Example: "Apt 4B", "Suite 200"
     */
    private String addressLine2;

    /**
     * City or locality (required)
     * Example: "San Francisco", "London"
     */
    private String city;

    /**
     * State, province, or administrative region (required for most countries)
     * Example: "CA" (California), "TX" (Texas), "ON" (Ontario)
     *
     * Also known as: addressRegion in Schema.org
     */
    private String region;

    /**
     * Postal code or ZIP code (required)
     * Example: "94103", "SW1A 1AA", "75001"
     */
    private String postalCode;

    /**
     * ISO 3166-1 alpha-2 country code (required)
     * Must be a 2-letter uppercase code: US, GB, FR, DE, etc.
     *
     * @see ISOCountryValidator for validation
     */
    private String countryCode;

    /**
     * Email address for delivery notifications (recommended)
     */
    private String email;

    /**
     * Phone number for delivery contact (recommended)
     * Useful for courier delivery issues
     */
    private String phoneNumber;

    /**
     * Validates the postal address
     *
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public void validate() {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }
        if (addressLine1 == null || addressLine1.trim().isEmpty()) {
            throw new IllegalArgumentException("Address line 1 is required");
        }
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("City is required");
        }
        if (postalCode == null || postalCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Postal code is required");
        }
        if (countryCode == null || countryCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Country code is required");
        }

        // Validate country code against ISO 3166-1
        ISOCountryValidator.validate(countryCode);

        // Region is required for most countries (especially US, CA, AU)
        if ((countryCode.equals("US") || countryCode.equals("CA") || countryCode.equals("AU"))
                && (region == null || region.trim().isEmpty())) {
            throw new IllegalArgumentException("Region/state is required for " + countryCode);
        }
    }

    /**
     * Normalizes the address data (uppercase country code, trim whitespace)
     */
    public void normalize() {
        if (firstName != null) firstName = firstName.trim();
        if (lastName != null) lastName = lastName.trim();
        if (addressLine1 != null) addressLine1 = addressLine1.trim();
        if (addressLine2 != null) addressLine2 = addressLine2.trim();
        if (city != null) city = city.trim();
        if (region != null) region = region.trim().toUpperCase();
        if (postalCode != null) postalCode = postalCode.trim().toUpperCase();
        if (countryCode != null) countryCode = ISOCountryValidator.normalize(countryCode);
        if (phoneNumber != null) phoneNumber = phoneNumber.trim();
    }

    /**
     * Returns a formatted single-line address string
     */
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(addressLine1);
        if (addressLine2 != null && !addressLine2.isEmpty()) {
            sb.append(", ").append(addressLine2);
        }
        sb.append(", ").append(city);
        if (region != null && !region.isEmpty()) {
            sb.append(", ").append(region);
        }
        sb.append(" ").append(postalCode);
        sb.append(", ").append(ISOCountryValidator.getDisplayName(countryCode));
        return sb.toString();
    }
}
