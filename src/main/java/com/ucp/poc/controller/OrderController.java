package com.ucp.poc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucp.poc.config.ProtocolCondition;
import com.ucp.poc.dto.UcpOrder;
import com.ucp.poc.model.Order;
import com.ucp.poc.model.Payment;
import com.ucp.poc.service.OrderService;
import com.ucp.poc.service.PaymentService;
import com.ucp.poc.util.UcpResponseWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Order Controller - UCP Compliant
 *
 * Returns orders in UCP-compliant format with proper envelope and schema.
 */
@RestController
@Conditional(ProtocolCondition.RestEnabled.class)
@RequestMapping("/ucp/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Value("${ucp.business.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Get order by ID (UCP-compliant)
     *
     * Returns single order wrapped in UCP envelope
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(
            @PathVariable String orderId,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {
        return orderService.getOrder(orderId)
                .map(order -> {
                    // Convert to UCP format
                    UcpOrder ucpOrder = UcpOrder.fromOrder(order, baseUrl);

                    // Add payment information if available
                    paymentService.getPaymentByOrderId(orderId).ifPresent(payment -> {
                        UcpOrder.withPayment(ucpOrder, payment);
                    });

                    // Convert to Map and wrap in UCP envelope (flat structure)
                    @SuppressWarnings("unchecked")
                    Map<String, Object> orderMap = objectMapper.convertValue(ucpOrder, Map.class);

                    Map<String, Object> response = UcpResponseWrapper.wrap(
                            orderMap,
                            "dev.ucp.shopping.order"
                    );

                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> error = UcpResponseWrapper.error(
                            "not_found",
                            "Order not found: " + orderId
                    );
                    return ResponseEntity.status(404).body(error);
                });
    }

    /**
     * Get orders by customer ID (UCP-compliant)
     *
     * Returns list of orders wrapped in UCP envelope
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<Map<String, Object>> getOrdersByCustomer(@PathVariable String customerId) {
        List<Order> orders = orderService.getOrdersByCustomer(customerId);

        // Convert to UCP format
        List<UcpOrder> ucpOrders = orders.stream()
                .map(order -> {
                    UcpOrder ucpOrder = UcpOrder.fromOrder(order, baseUrl);
                    // Optionally add payment info
                    paymentService.getPaymentByOrderId(order.getOrderId()).ifPresent(payment -> {
                        UcpOrder.withPayment(ucpOrder, payment);
                    });
                    return ucpOrder;
                })
                .collect(Collectors.toList());

        // Wrap in UCP envelope
        Map<String, Object> response = UcpResponseWrapper.wrapList(
                ucpOrders,
                "orders",
                "dev.ucp.shopping.orders"
        );

        return ResponseEntity.ok(response);
    }
}

