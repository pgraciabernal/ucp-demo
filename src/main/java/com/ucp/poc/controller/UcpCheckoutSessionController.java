package com.ucp.poc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucp.poc.config.ProtocolCondition;
import com.ucp.poc.dto.*;
import com.ucp.poc.model.CartItem;
import com.ucp.poc.model.CheckoutSession;
import com.ucp.poc.model.Order;
import com.ucp.poc.model.Payment;
import com.ucp.poc.service.CheckoutSessionService;
import com.ucp.poc.service.OrderService;
import com.ucp.poc.service.PaymentService;
import com.ucp.poc.service.UcpProfileService;
import com.ucp.poc.util.UcpResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UCP Checkout Session Controller
 *
 * Implements official UCP checkout session specification:
 * - POST /checkout-sessions - Create session
 * - GET /checkout-sessions/{id} - Get session
 * - PUT /checkout-sessions/{id} - Update session
 * - POST /checkout-sessions/{id}/complete - Complete session
 * - POST /checkout-sessions/{id}/cancel - Cancel session
 *
 * Spec: https://ucp.dev/schemas/checkout.json
 */
@Slf4j
@RestController
@RequestMapping("/checkout-sessions")
@RequiredArgsConstructor
@Conditional(ProtocolCondition.RestEnabled.class)
public class UcpCheckoutSessionController {

    private final CheckoutSessionService sessionService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final UcpProfileService profileService;
    private final ObjectMapper objectMapper;

    @Value("${ucp.business.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Create checkout session
     * POST /checkout-sessions
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestBody UcpCheckoutSessionRequest request,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {
        try {
            log.info("Creating checkout session with {} line items (UCP-Agent: {})",
                    request.getLine_items().size(), ucpAgent != null ? ucpAgent : "not specified");

            // Validate request
            request.validate();

            // Extract currency from context or default
            String currency = request.getContext() != null && request.getContext().getCurrency() != null
                    ? request.getContext().getCurrency()
                    : "USD";

            // Build cart items
            List<Map.Entry<String, Integer>> productQuantities = request.getLine_items().stream()
                    .map(item -> new AbstractMap.SimpleEntry<>(
                            item.getItem().getId(),
                            item.getQuantity()))
                    .collect(Collectors.toList());

            List<CartItem> cartItems = sessionService.buildCartItems(productQuantities);

            // Create session
            CheckoutSession session = sessionService.createSession(cartItems, currency);

            // Apply buyer info if provided
            if (request.getBuyer() != null) {
                session = sessionService.updateBuyer(
                        session.getSessionId(),
                        request.getBuyer().getFirst_name(),
                        request.getBuyer().getLast_name(),
                        request.getBuyer().getEmail(),
                        request.getBuyer().getPhone_number()
                );
            }

            // Apply context if provided
            if (request.getContext() != null) {
                session.setContextCountry(request.getContext().getAddress_country());
                session.setContextLanguage(request.getContext().getLanguage());
                session.setContextCurrency(request.getContext().getCurrency());
            }

            // Build response
            UcpCheckoutSessionResponse response = buildSessionResponse(session);

            // Convert to Map and wrap in UCP envelope (flat structure)
            Map<String, Object> envelope = UcpResponseWrapper.wrap(
                    toMap(response),
                    "dev.ucp.shopping.checkout"
            );

            // Add success status to UCP envelope
            @SuppressWarnings("unchecked")
            Map<String, Object> ucpEnvelope = (Map<String, Object>) envelope.get("ucp");
            ucpEnvelope.put("status", "success");

            return ResponseEntity.ok(envelope);

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            Map<String, Object> error = UcpResponseWrapper.error(
                    "validation_error",
                    e.getMessage()
            );
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            log.error("Error creating session", e);
            Map<String, Object> error = UcpResponseWrapper.error(
                    "session_creation_error",
                    "Failed to create checkout session: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Get checkout session
     * GET /checkout-sessions/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable String id,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {
        try {
            log.info("Getting checkout session: {} (UCP-Agent: {})", id, ucpAgent != null ? ucpAgent : "not specified");
            CheckoutSession session = sessionService.getSession(id)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));

            UcpCheckoutSessionResponse response = buildSessionResponse(session);

            // Convert to Map and wrap in UCP envelope (flat structure)
            Map<String, Object> envelope = UcpResponseWrapper.wrap(
                    toMap(response),
                    "dev.ucp.shopping.checkout"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> ucpEnvelope = (Map<String, Object>) envelope.get("ucp");
            ucpEnvelope.put("status", "success");

            return ResponseEntity.ok(envelope);

        } catch (IllegalArgumentException e) {
            Map<String, Object> error = UcpResponseWrapper.error(
                    "session_not_found",
                    e.getMessage()
            );
            return ResponseEntity.status(404).body(error);

        } catch (Exception e) {
            log.error("Error retrieving session", e);
            Map<String, Object> error = UcpResponseWrapper.error(
                    "session_retrieval_error",
                    "Failed to retrieve session: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Update checkout session
     * PATCH /checkout-sessions/{id}
     */
    /**
     * Update checkout session
     * PUT /checkout-sessions/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateSession(
            @PathVariable String id,
            @RequestBody UcpCheckoutSessionRequest request,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {
        try {
            log.info("Updating checkout session: {} (UCP-Agent: {})", id, ucpAgent != null ? ucpAgent : "not specified");
            CheckoutSession session = sessionService.getSession(id)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));

            // Update buyer if provided
            if (request.getBuyer() != null) {
                session = sessionService.updateBuyer(
                        id,
                        request.getBuyer().getFirst_name(),
                        request.getBuyer().getLast_name(),
                        request.getBuyer().getEmail(),
                        request.getBuyer().getPhone_number()
                );
            }

            UcpCheckoutSessionResponse response = buildSessionResponse(session);

            // Convert to Map and wrap in UCP envelope (flat structure)
            Map<String, Object> envelope = UcpResponseWrapper.wrap(
                    toMap(response),
                    "dev.ucp.shopping.checkout"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> ucpEnvelope = (Map<String, Object>) envelope.get("ucp");
            ucpEnvelope.put("status", "success");

            return ResponseEntity.ok(envelope);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Update error: {}", e.getMessage());
            Map<String, Object> error = UcpResponseWrapper.error(
                    "update_error",
                    e.getMessage()
            );
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            log.error("Error updating session", e);
            Map<String, Object> error = UcpResponseWrapper.error(
                    "session_update_error",
                    "Failed to update session: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Complete checkout session
     * POST /checkout-sessions/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeSession(
            @PathVariable String id,
            @RequestBody UcpCompleteRequest request,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {
        try {
            log.info("Completing checkout session: {} (UCP-Agent: {})", id, ucpAgent != null ? ucpAgent : "not specified");

            // Validate request
            request.validate();

            CheckoutSession session = sessionService.getSession(id)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));

            // Mark as in progress
            session = sessionService.markInProgress(id);

            // Update buyer if provided
            if (request.getBuyer() != null) {
                session = sessionService.updateBuyer(
                        id,
                        request.getBuyer().getFirst_name(),
                        request.getBuyer().getLast_name(),
                        request.getBuyer().getEmail(),
                        request.getBuyer().getPhone_number()
                );
            }

            // Update fulfillment if provided
            if (request.getFulfillment() != null && request.getFulfillment().getMethods() != null) {
                PostalAddress shippingAddr = extractShippingAddress(request.getFulfillment());
                String shippingMethod = extractShippingMethod(request.getFulfillment());
                session = sessionService.updateFulfillment(id, shippingAddr, shippingMethod);
            }

            // Extract selected payment instrument
            UcpCompleteRequest.PaymentRequest.PaymentInstrument selectedInstrument =
                    request.getPayment().getInstruments().stream()
                            .filter(i -> Boolean.TRUE.equals(i.getSelected()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("No payment instrument selected"));

            // Validate payment handler ID against discovered profile
            if (!profileService.isValidPaymentHandler(selectedInstrument.getHandler_id())) {
                throw new IllegalArgumentException(
                        "Invalid payment handler ID: " + selectedInstrument.getHandler_id() +
                        ". Valid handlers: " + profileService.getPaymentHandlerIds());
            }

            // Update payment info
            session = sessionService.updatePayment(
                    id,
                    selectedInstrument.getId(),
                    selectedInstrument.getHandler_id(),
                    selectedInstrument.getType()
            );

            // Create order
            Order order = orderService.createOrder(
                    "customer-" + session.getBuyerEmail(),
                    session.getItems(),
                    extractBillingAddress(selectedInstrument, session),
                    session.getShippingAddress(),
                    session.getShippingMethod()
            );

            order.setCustomerEmail(session.getBuyerEmail());
            order.setCustomerName(session.getBuyerFirstName() + " " + session.getBuyerLastName());

            log.info("Order created: {} for session: {}", order.getOrderId(), id);

            // Process payment
            // Extract card brand from display (if available)
            String paymentMethod = selectedInstrument.getType();
            if (selectedInstrument.getDisplay() != null &&
                selectedInstrument.getDisplay().getBrand() != null) {
                paymentMethod = selectedInstrument.getDisplay().getBrand().toUpperCase();
            }

            Payment payment = paymentService.createPayment(
                    order.getOrderId(),
                    order.getTotal(),
                    order.getCurrency(),
                    paymentMethod
            );

            payment = paymentService.authorizePayment(payment.getPaymentId());
            payment = paymentService.capturePayment(payment.getPaymentId());

            log.info("Payment completed: {}", payment.getPaymentId());

            // Complete session
            session = sessionService.completeSession(id, order.getOrderId());

            // Build response
            UcpCheckoutSessionResponse response = buildSessionResponse(session);

            // Add order details
            response.setOrder(UcpCheckoutSessionResponse.OrderResponse.builder()
                    .id(order.getOrderId())
                    .label(order.getOrderNumber())
                    .permalink_url(baseUrl + "/ucp/orders/" + order.getOrderId())
                    .build());

            // Add payment details
            response.setPayment(UcpCheckoutSessionResponse.PaymentResponse.builder()
                    .id(payment.getPaymentId())
                    .status(payment.getStatus().name().toLowerCase())
                    .build());

            // Wrap in UCP envelope
            // Convert to Map and wrap in UCP envelope (flat structure)
            Map<String, Object> envelope = UcpResponseWrapper.wrap(
                    toMap(response),
                    "dev.ucp.shopping.checkout"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> ucpEnvelope = (Map<String, Object>) envelope.get("ucp");
            ucpEnvelope.put("status", "success");

            return ResponseEntity.ok(envelope);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Completion error: {}", e.getMessage());
            Map<String, Object> error = UcpResponseWrapper.error(
                    "completion_error",
                    e.getMessage()
            );
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            log.error("Error completing session", e);
            Map<String, Object> error = UcpResponseWrapper.error(
                    "session_completion_error",
                    "Failed to complete session: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Cancel checkout session
     * POST /checkout-sessions/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelSession(
            @PathVariable String id,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {
        try {
            log.info("Cancelling checkout session: {} (UCP-Agent: {})", id, ucpAgent != null ? ucpAgent : "not specified");

            CheckoutSession session = sessionService.getSession(id)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));

            // Check if session can be cancelled
            if (session.getStatus() == CheckoutSession.SessionStatus.COMPLETED) {
                throw new IllegalStateException("Cannot cancel completed session");
            }

            // Cancel the session
            session = sessionService.cancelSession(id);

            // Build response
            UcpCheckoutSessionResponse response = buildSessionResponse(session);

            // Wrap in UCP envelope
            Map<String, Object> envelope = UcpResponseWrapper.wrap(
                    toMap(response),
                    "dev.ucp.shopping.checkout"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> ucpEnvelope = (Map<String, Object>) envelope.get("ucp");
            ucpEnvelope.put("status", "success");

            return ResponseEntity.ok(envelope);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Cancel error: {}", e.getMessage());
            Map<String, Object> error = UcpResponseWrapper.error(
                    "cancel_error",
                    e.getMessage()
            );
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            log.error("Error cancelling session", e);
            Map<String, Object> error = UcpResponseWrapper.error(
                    "session_cancel_error",
                    "Failed to cancel session: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Build UCP response from session
     */
    private UcpCheckoutSessionResponse buildSessionResponse(CheckoutSession session) {
        // Map internal status to UCP status (already matches UCP spec)
        String ucpStatus = session.getStatus().name().toLowerCase();

        UcpCheckoutSessionResponse.UcpCheckoutSessionResponseBuilder builder =
                UcpCheckoutSessionResponse.builder()
                        .id(session.getSessionId())
                        .status(ucpStatus)
                        .currency(session.getCurrency())
                        .expires_at(session.getExpiresAt() != null ? session.getExpiresAt().toString() : null);

        // Line items (convert prices to cents as integers per UCP spec)
        List<UcpCheckoutSessionResponse.LineItemResponse> lineItems = session.getItems().stream()
                .map(item -> {
                    List<UcpCheckoutSessionResponse.TotalItemResponse> itemTotals = new ArrayList<>();
                    itemTotals.add(UcpCheckoutSessionResponse.TotalItemResponse.builder()
                            .type("subtotal")
                            .amount(convertToCents(item.getTotalPrice()))
                            .build());
                    itemTotals.add(UcpCheckoutSessionResponse.TotalItemResponse.builder()
                            .type("total")
                            .amount(convertToCents(item.getTotalPrice()))
                            .build());

                    return UcpCheckoutSessionResponse.LineItemResponse.builder()
                            .id("li_" + UUID.randomUUID().toString().substring(0, 8))
                            .item(UcpCheckoutSessionResponse.LineItemResponse.ItemDetails.builder()
                                    .id(item.getProductId())
                                    .title(item.getProductName())
                                    .price(convertToCents(item.getUnitPrice())) // Integer cents, not nested object
                                    .image_url(item.getImageUrl())
                                    .build())
                            .quantity(item.getQuantity())
                            .totals(itemTotals)
                            .build();
                })
                .collect(Collectors.toList());

        builder.line_items(lineItems);

        // Totals (convert to cents)
        List<UcpCheckoutSessionResponse.TotalItemResponse> totals = new ArrayList<>();
        totals.add(UcpCheckoutSessionResponse.TotalItemResponse.builder()
                .type("subtotal")
                .amount(convertToCents(session.getSubtotal()))
                .build());

        if (session.getTax() != null && session.getTax().compareTo(BigDecimal.ZERO) > 0) {
            totals.add(UcpCheckoutSessionResponse.TotalItemResponse.builder()
                    .type("tax")
                    .amount(convertToCents(session.getTax()))
                    .build());
        }

        if (session.getShippingCost() != null && session.getShippingCost().compareTo(BigDecimal.ZERO) > 0) {
            totals.add(UcpCheckoutSessionResponse.TotalItemResponse.builder()
                    .type("fulfillment")
                    .amount(convertToCents(session.getShippingCost()))
                    .display_text("Shipping")
                    .build());
        }

        totals.add(UcpCheckoutSessionResponse.TotalItemResponse.builder()
                .type("total")
                .amount(convertToCents(session.getTotal()))
                .build());

        builder.totals(totals);

        // Links (legal/policy)
        builder.links(Arrays.asList(
                UcpCheckoutSessionResponse.LinkResponse.builder()
                        .type("terms")
                        .url(baseUrl + "/terms")
                        .title("Terms of Service")
                        .build(),
                UcpCheckoutSessionResponse.LinkResponse.builder()
                        .type("privacy")
                        .url(baseUrl + "/privacy")
                        .title("Privacy Policy")
                        .build()
        ));

        // Buyer (if available)
        if (session.getBuyerEmail() != null) {
            builder.buyer(UcpCheckoutSessionResponse.BuyerResponse.builder()
                    .first_name(session.getBuyerFirstName())
                    .last_name(session.getBuyerLastName())
                    .email(session.getBuyerEmail())
                    .phone_number(session.getBuyerPhone())
                    .build());
        }

        // Payment object (REQUIRED in UCP spec)
        // Get available payment handlers from profile service
        List<Object> handlers = profileService.getPaymentHandlers();
        builder.payment(UcpCheckoutSessionResponse.PaymentResponse.builder()
                .handlers(handlers != null ? handlers : new ArrayList<>())
                .instruments(new ArrayList<>())  // Empty until complete
                .build());

        return builder.build();
    }

    /**
     * Convert BigDecimal to cents (signed integer)
     */
    private Long convertToCents(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.multiply(new BigDecimal("100")).longValue();
    }

    /**
     * Extract shipping address from fulfillment request
     */
    private PostalAddress extractShippingAddress(UcpCompleteRequest.FulfillmentRequest fulfillment) {
        if (fulfillment == null || fulfillment.getMethods() == null || fulfillment.getMethods().isEmpty()) {
            return null;
        }

        for (UcpCompleteRequest.FulfillmentRequest.FulfillmentMethod method : fulfillment.getMethods()) {
            if (method.getDestinations() != null && !method.getDestinations().isEmpty()) {
                UcpCompleteRequest.FulfillmentRequest.FulfillmentMethod.Destination dest =
                        method.getDestinations().get(0);

                PostalAddress address = new PostalAddress();
                address.setFirstName(dest.getFirst_name());
                address.setLastName(dest.getLast_name());
                address.setAddressLine1(dest.getStreet_address());
                address.setCity(dest.getAddress_locality());
                address.setRegion(dest.getAddress_region());
                address.setPostalCode(dest.getPostal_code());
                address.setCountryCode(dest.getAddress_country());
                address.setPhoneNumber(dest.getPhone_number());

                return address;
            }
        }

        return null;
    }

    /**
     * Extract shipping method from fulfillment request
     */
    private String extractShippingMethod(UcpCompleteRequest.FulfillmentRequest fulfillment) {
        if (fulfillment == null || fulfillment.getMethods() == null || fulfillment.getMethods().isEmpty()) {
            return "standard";
        }

        for (UcpCompleteRequest.FulfillmentRequest.FulfillmentMethod method : fulfillment.getMethods()) {
            if (Boolean.TRUE.equals(method.getSelected())) {
                return method.getType();
            }
        }

        return fulfillment.getMethods().get(0).getType();
    }

    /**
     * Extract billing address from payment instrument
     */
    private PostalAddress extractBillingAddress(
            UcpCompleteRequest.PaymentRequest.PaymentInstrument instrument,
            CheckoutSession session) {
        if (instrument.getBilling_address() != null) {
            UcpCompleteRequest.PaymentRequest.PaymentInstrument.Address addr =
                    instrument.getBilling_address();

            PostalAddress address = new PostalAddress();
            address.setFirstName(session.getBuyerFirstName());
            address.setLastName(session.getBuyerLastName());
            address.setAddressLine1(addr.getStreet_address());
            address.setCity(addr.getAddress_locality());
            address.setRegion(addr.getAddress_region());
            address.setPostalCode(addr.getPostal_code());
            address.setCountryCode(addr.getAddress_country());
            address.setEmail(session.getBuyerEmail());
            address.setPhoneNumber(session.getBuyerPhone());

            return address;
        }

        // Default to shipping address if no billing address
        return session.getShippingAddress();
    }

    /**
     * Convert response object to Map for flat UCP response
     * This ensures the response is flat with ucp at root, not wrapped
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        return objectMapper.convertValue(obj, Map.class);
    }
}
