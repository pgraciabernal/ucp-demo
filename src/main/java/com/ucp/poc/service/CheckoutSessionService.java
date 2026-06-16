package com.ucp.poc.service;

import com.ucp.poc.dto.PostalAddress;
import com.ucp.poc.model.CartItem;
import com.ucp.poc.model.CheckoutSession;
import com.ucp.poc.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checkout Session Service
 *
 * Manages UCP checkout session lifecycle with progressive disclosure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutSessionService {

    private final ProductService productService;

    // In-memory storage (replace with database in production)
    private final Map<String, CheckoutSession> sessions = new ConcurrentHashMap<>();

    /**
     * Create a new checkout session
     */
    public CheckoutSession createSession(List<CartItem> items, String currency) {
        String sessionId = "session_" + UUID.randomUUID().toString().replace("-", "");

        CheckoutSession session = CheckoutSession.builder()
                .sessionId(sessionId)
                .status(CheckoutSession.SessionStatus.INCOMPLETE)
                .currency(currency != null ? currency : "USD")
                .items(new ArrayList<>(items))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        session.calculateTotals();
        session.updateStatus();

        sessions.put(sessionId, session);

        log.info("Created checkout session: {} with {} items", sessionId, items.size());
        return session;
    }

    /**
     * Get session by ID
     */
    public Optional<CheckoutSession> getSession(String sessionId) {
        CheckoutSession session = sessions.get(sessionId);
        if (session != null && session.isExpired()) {
            session.setStatus(CheckoutSession.SessionStatus.EXPIRED);
        }
        return Optional.ofNullable(session);
    }

    /**
     * Update session with buyer information
     */
    public CheckoutSession updateBuyer(String sessionId,
                                       String firstName,
                                       String lastName,
                                       String email,
                                       String phone) {
        CheckoutSession session = getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() == CheckoutSession.SessionStatus.COMPLETED ||
                session.getStatus() == CheckoutSession.SessionStatus.CANCELED) {
            throw new IllegalStateException("Cannot update completed or canceled session");
        }

        session.setBuyerFirstName(firstName);
        session.setBuyerLastName(lastName);
        session.setBuyerEmail(email);
        session.setBuyerPhone(phone);
        session.setUpdatedAt(LocalDateTime.now());

        session.updateStatus();

        log.info("Updated buyer info for session: {}", sessionId);
        return session;
    }

    /**
     * Update session with fulfillment information
     */
    public CheckoutSession updateFulfillment(String sessionId,
                                             PostalAddress shippingAddress,
                                             String shippingMethod) {
        CheckoutSession session = getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() == CheckoutSession.SessionStatus.COMPLETED ||
                session.getStatus() == CheckoutSession.SessionStatus.CANCELED) {
            throw new IllegalStateException("Cannot update completed or canceled session");
        }

        session.setShippingAddress(shippingAddress);
        session.setShippingMethod(shippingMethod);
        session.setUpdatedAt(LocalDateTime.now());

        session.calculateTotals(); // Recalculate with shipping cost
        session.updateStatus();

        log.info("Updated fulfillment for session: {}", sessionId);
        return session;
    }

    /**
     * Update session with payment instrument
     */
    public CheckoutSession updatePayment(String sessionId,
                                         String instrumentId,
                                         String handlerId,
                                         String paymentType) {
        CheckoutSession session = getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() == CheckoutSession.SessionStatus.COMPLETED ||
                session.getStatus() == CheckoutSession.SessionStatus.CANCELED) {
            throw new IllegalStateException("Cannot update completed or canceled session");
        }

        session.setPaymentInstrumentId(instrumentId);
        session.setPaymentHandlerId(handlerId);
        session.setPaymentType(paymentType);
        session.setUpdatedAt(LocalDateTime.now());

        session.updateStatus();

        log.info("Updated payment for session: {}", sessionId);
        return session;
    }

    /**
     * Mark session as complete
     */
    public CheckoutSession completeSession(String sessionId, String orderId) {
        CheckoutSession session = getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!session.isReadyForCompletion()) {
            throw new IllegalStateException("Session is not ready for completion");
        }

        session.setStatus(CheckoutSession.SessionStatus.COMPLETED);
        session.setOrderId(orderId);
        session.setCompletedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        log.info("Completed session: {} -> order: {}", sessionId, orderId);
        return session;
    }

    /**
     * Mark session as in progress
     */
    public CheckoutSession markInProgress(String sessionId) {
        CheckoutSession session = getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setStatus(CheckoutSession.SessionStatus.COMPLETE_IN_PROGRESS);
        session.setUpdatedAt(LocalDateTime.now());

        return session;
    }

    /**
     * Cancel session
     */
    public CheckoutSession cancelSession(String sessionId) {
        CheckoutSession session = getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setStatus(CheckoutSession.SessionStatus.CANCELED);
        session.setUpdatedAt(LocalDateTime.now());

        log.info("Canceled session: {}", sessionId);
        return session;
    }

    /**
     * Build cart items from product IDs
     */
    public List<CartItem> buildCartItems(List<Map.Entry<String, Integer>> productQuantities) {
        List<CartItem> cartItems = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : productQuantities) {
            String productId = entry.getKey();
            Integer quantity = entry.getValue();

            Product product = productService.getProduct(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            BigDecimal totalPrice = product.getPrice().multiply(new BigDecimal(quantity));

            CartItem cartItem = CartItem.builder()
                    .productId(product.getId())
                    .sku(product.getSku())
                    .productName(product.getName())
                    .brand(product.getBrand())
                    .imageUrl(product.getPrimaryImage())
                    .quantity(quantity)
                    .unitPrice(product.getPrice())
                    .currency(product.getCurrency())
                    .totalPrice(totalPrice)
                    .inStock(product.isInStock())
                    .availableQuantity(product.getStockQuantity())
                    .build();

            cartItems.add(cartItem);
        }

        return cartItems;
    }

    /**
     * Clean up expired sessions (call periodically)
     */
    public int cleanupExpiredSessions() {
        int count = 0;
        LocalDateTime now = LocalDateTime.now();

        Iterator<Map.Entry<String, CheckoutSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            CheckoutSession session = iterator.next().getValue();
            if (session.getExpiresAt() != null && now.isAfter(session.getExpiresAt())) {
                iterator.remove();
                count++;
            }
        }

        if (count > 0) {
            log.info("Cleaned up {} expired sessions", count);
        }

        return count;
    }
}
