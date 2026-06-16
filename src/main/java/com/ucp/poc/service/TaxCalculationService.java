package com.ucp.poc.service;

import com.ucp.poc.dto.PostalAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Tax Calculation Service
 *
 * Calculates sales tax based on jurisdiction (country, state/region).
 * Supports multiple tax jurisdictions and rates.
 *
 * NOTE: This is a simplified implementation for POC purposes.
 * Production systems should integrate with dedicated tax calculation services like:
 * - Avalara AvaTax
 * - TaxJar
 * - Vertex
 * - Or consult with tax professionals for compliance
 *
 * Tax rates and rules vary significantly by jurisdiction and change frequently.
 */
@Service
@Slf4j
public class TaxCalculationService {

    /**
     * US state tax rates (simplified - actual rates vary by locality)
     * Format: State code -> Tax rate (as decimal)
     */
    private static final Map<String, BigDecimal> US_STATE_TAX_RATES = Map.ofEntries(
            Map.entry("CA", new BigDecimal("0.0725")), // California: 7.25%
            Map.entry("NY", new BigDecimal("0.04")),   // New York: 4%
            Map.entry("TX", new BigDecimal("0.0625")), // Texas: 6.25%
            Map.entry("FL", new BigDecimal("0.06")),   // Florida: 6%
            Map.entry("IL", new BigDecimal("0.0625")), // Illinois: 6.25%
            Map.entry("PA", new BigDecimal("0.06")),   // Pennsylvania: 6%
            Map.entry("OH", new BigDecimal("0.0575")), // Ohio: 5.75%
            Map.entry("GA", new BigDecimal("0.04")),   // Georgia: 4%
            Map.entry("NC", new BigDecimal("0.0475")), // North Carolina: 4.75%
            Map.entry("MI", new BigDecimal("0.06")),   // Michigan: 6%
            Map.entry("WA", new BigDecimal("0.065")),  // Washington: 6.5%
            Map.entry("AZ", new BigDecimal("0.056")),  // Arizona: 5.6%
            Map.entry("MA", new BigDecimal("0.0625")), // Massachusetts: 6.25%
            Map.entry("VA", new BigDecimal("0.053")),  // Virginia: 5.3%
            Map.entry("CO", new BigDecimal("0.029"))   // Colorado: 2.9%
    );

    /**
     * Country-level tax rates for countries without regional variations
     * Format: Country code -> Tax rate (VAT/GST)
     */
    private static final Map<String, BigDecimal> COUNTRY_TAX_RATES = Map.ofEntries(
            Map.entry("GB", new BigDecimal("0.20")),   // UK VAT: 20%
            Map.entry("DE", new BigDecimal("0.19")),   // Germany VAT: 19%
            Map.entry("FR", new BigDecimal("0.20")),   // France VAT: 20%
            Map.entry("ES", new BigDecimal("0.21")),   // Spain VAT: 21%
            Map.entry("IT", new BigDecimal("0.22")),   // Italy VAT: 22%
            Map.entry("CA", new BigDecimal("0.05")),   // Canada GST: 5% (provinces add PST/HST)
            Map.entry("AU", new BigDecimal("0.10")),   // Australia GST: 10%
            Map.entry("JP", new BigDecimal("0.10")),   // Japan consumption tax: 10%
            Map.entry("MX", new BigDecimal("0.16")),   // Mexico VAT: 16%
            Map.entry("BR", new BigDecimal("0.17"))    // Brazil (simplified): 17%
    );

    /**
     * Default tax rate if jurisdiction not found
     */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.08"); // 8%

    /**
     * Calculates sales tax based on delivery address and amount
     *
     * @param address  Delivery address (determines jurisdiction)
     * @param amount   Taxable amount
     * @return Tax amount rounded to 2 decimal places
     */
    public BigDecimal calculateTax(PostalAddress address, BigDecimal amount) {
        if (address == null) {
            log.warn("Address is null, using default tax rate");
            return calculateWithRate(amount, DEFAULT_TAX_RATE);
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal taxRate = getTaxRate(address);
        BigDecimal taxAmount = calculateWithRate(amount, taxRate);

        log.debug("Tax calculation: Country={}, Region={}, Amount={}, Rate={}, Tax={}",
                address.getCountryCode(), address.getRegion(), amount, taxRate, taxAmount);

        return taxAmount;
    }

    /**
     * Gets the applicable tax rate for a given address
     *
     * @param address Delivery address
     * @return Tax rate as decimal (e.g., 0.08 for 8%)
     */
    public BigDecimal getTaxRate(PostalAddress address) {
        if (address == null || address.getCountryCode() == null) {
            log.warn("Invalid address for tax calculation, using default rate");
            return DEFAULT_TAX_RATE;
        }

        String country = address.getCountryCode().toUpperCase();
        String region = address.getRegion() != null ? address.getRegion().toUpperCase() : null;

        // US: State-level tax
        if ("US".equals(country) && region != null) {
            BigDecimal stateRate = US_STATE_TAX_RATES.get(region);
            if (stateRate != null) {
                return stateRate;
            }
            log.warn("Unknown US state: {}, using default rate", region);
            return DEFAULT_TAX_RATE;
        }

        // Other countries: Country-level tax (VAT/GST)
        BigDecimal countryRate = COUNTRY_TAX_RATES.get(country);
        if (countryRate != null) {
            return countryRate;
        }

        log.warn("Unknown country: {}, using default rate", country);
        return DEFAULT_TAX_RATE;
    }

    /**
     * Calculates tax amount with a specific rate
     *
     * @param amount  Taxable amount
     * @param taxRate Tax rate (as decimal)
     * @return Tax amount rounded to 2 decimal places
     */
    private BigDecimal calculateWithRate(BigDecimal amount, BigDecimal taxRate) {
        return amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Checks if an address is in a tax-free jurisdiction
     *
     * @param address Delivery address
     * @return true if tax-free, false otherwise
     */
    public boolean isTaxFree(PostalAddress address) {
        if (address == null || address.getCountryCode() == null) {
            return false;
        }

        String country = address.getCountryCode().toUpperCase();
        String region = address.getRegion() != null ? address.getRegion().toUpperCase() : null;

        // US states with no sales tax
        if ("US".equals(country) && region != null) {
            return "DE".equals(region) || "MT".equals(region) ||
                   "NH".equals(region) || "OR".equals(region);
        }

        return false;
    }

    /**
     * Gets tax rate as percentage string for display
     *
     * @param address Delivery address
     * @return Tax rate as percentage (e.g., "8.0%")
     */
    public String getTaxRateDisplay(PostalAddress address) {
        BigDecimal rate = getTaxRate(address);
        BigDecimal percentage = rate.multiply(new BigDecimal("100"));
        return percentage.stripTrailingZeros().toPlainString() + "%";
    }
}
