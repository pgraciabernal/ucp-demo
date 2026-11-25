package com.ucp.poc.service;

import com.ucp.poc.model.Payment;
import com.ucp.poc.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderService orderService;
    private final Map<String, Payment> payments = new ConcurrentHashMap<>();

    /**
     * Creates a payment with secure tokenization
     *
     * @param orderId Order identifier
     * @param amount Payment amount
     * @param currency ISO 4217 currency code
     * @param paymentMethodType Type: "credit_card", "paypal", etc.
     * @param cardBrand Card brand (for credit cards): "VISA", "MASTERCARD", etc.
     * @return Created payment
     */
    public Payment createPayment(String orderId, BigDecimal amount, String currency,
                                String paymentMethodType, String cardBrand) {
        String paymentId = "pay-" + UUID.randomUUID().toString().substring(0, 8);

        // Generate mock payment token (in production, this comes from payment processor)
        String paymentToken = "tok_" + UUID.randomUUID().toString().replace("-", "");

        // Mock card last 4 digits
        String cardLast4 = String.format("%04d", new Random().nextInt(10000));

        log.debug("Creating payment: orderId={}, amount={}, currency={}, methodType={}, brand={}, token={}",
                orderId, amount, currency, paymentMethodType, cardBrand,
                paymentToken.length() > 20 ? paymentToken.substring(0, 20) + "..." : paymentToken);

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .paymentMethodType(paymentMethodType)
                .paymentToken(paymentToken)
                .cardBrand(cardBrand)
                .cardLast4(cardLast4)
                .paymentProvider("mock-processor")
                .createdAt(LocalDateTime.now())
                .build();

        payment.normalize();

        try {
            payment.validate();
        } catch (IllegalArgumentException e) {
            log.error("Payment validation failed for orderId={}: {}", orderId, e.getMessage());
            throw e;
        }

        payments.put(paymentId, payment);
        return payment;
    }

    /**
     * Legacy method for backward compatibility
     */
    @Deprecated
    public Payment createPayment(String orderId, BigDecimal amount, String currency, String paymentMethod) {
        // Convert old paymentMethod to new structure
        return createPayment(orderId, amount, currency, "credit_card", paymentMethod);
    }

    public Payment authorizePayment(String paymentId) {
        Payment payment = payments.get(paymentId);
        if (payment == null) {
            throw new IllegalArgumentException("Payment not found: " + paymentId);
        }

        // Mock authorization - MASTERCARD always fails
        if ("MASTERCARD".equalsIgnoreCase(payment.getCardBrand())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorCode("card_declined");
            payment.setErrorMessage("Card declined by issuer");
            throw new IllegalStateException("Payment declined: MASTERCARD authorization failed");
        }

        // VISA and AMEX succeed
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setTransactionId("txn-" + UUID.randomUUID().toString().substring(0, 8));
        payment.setAuthorizationCode("auth_" + UUID.randomUUID().toString().substring(0, 8));
        payment.setAuthorizedAmount(payment.getAmount());
        payment.setAuthorizedAt(LocalDateTime.now());

        return payment;
    }

    public Payment capturePayment(String paymentId) {
        Payment payment = payments.get(paymentId);
        if (payment == null) {
            throw new IllegalArgumentException("Payment not found: " + paymentId);
        }

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment must be authorized before capture");
        }

        // Mock capture - always succeeds in POC
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAmount(payment.getAuthorizedAmount());
        payment.setCapturedAt(LocalDateTime.now());

        // Confirm the order
        orderService.confirmOrder(payment.getOrderId());

        return payment;
    }

    public Optional<Payment> getPayment(String paymentId) {
        return Optional.ofNullable(payments.get(paymentId));
    }

    public Optional<Payment> getPaymentByOrderId(String orderId) {
        return payments.values().stream()
                .filter(p -> p.getOrderId().equals(orderId))
                .findFirst();
    }

    public Payment refundPayment(String paymentId) {
        Payment payment = payments.get(paymentId);
        if (payment == null) {
            throw new IllegalArgumentException("Payment not found: " + paymentId);
        }

        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new IllegalStateException("Only captured payments can be refunded");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        orderService.cancelOrder(payment.getOrderId());

        return payment;
    }
}
