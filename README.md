# UCP POC - Unified Commerce Protocol Proof of Concept

A Spring Boot application demonstrating the Unified Commerce Protocol (UCP) for AI-enabled commerce operations.

## Overview

This POC implements a mock e-commerce store with UCP protocol support, enabling AI agents to discover and interact with commerce capabilities.

## Features

- **Agent Discovery**: `.well-known/agent.json` endpoint for AI agent capability discovery
- **UCP Profile**: Detailed protocol configuration at `/ucp/profile`
- **Product Catalog**: Browse and search products
- **Checkout**: Multi-item cart checkout with payment processing
- **Order Management**: Track orders and view order history
- **Payment Processing**: Mock payment with multiple methods (VISA, AMEX, MASTERCARD)
  - VISA and AMEX: Always succeed
  - MASTERCARD: Always fails (for testing error scenarios)

## Architecture

- **Spring Boot 3.2** with Java 21
- **UCP Spring Boot Starter** for protocol support
- **In-memory storage** for POC simplicity (concurrent hash maps)
- **REST API** following UCP conventions

## Getting Started

### Prerequisites

- Java 21
- Maven 3.6+

### Build and Run

```bash
# Build the application
mvn clean package

# Run the application
mvn spring-boot:run
```

The server starts on `http://localhost:8080`

### Using the UI Chat Client

Once the server is running, open your browser and navigate to:

```
http://localhost:8080
```

The chat client provides:
- **Interactive Chat Interface** - Natural language interaction with the shopping assistant
- **Product Catalog Sidebar** - Browse and add products directly (hidden by default, click "▶ Show Catalog")
- **Dual Purchase Options** - Add to cart OR pay now instantly
- **Shopping Cart** - Real-time cart management for multiple items
- **Checkout Flow** - Complete purchase with order confirmation
- **Order Status** - Track orders by ID

Try these commands in the chat:
- "Show me t-shirts" or "Show me shoes"
- "Show me all products"
- "Show my cart"
- "Checkout"
- Type an order ID (e.g., "order-abc123") to check status

## API Endpoints

### Discovery

- `GET /.well-known/ucp` - **Primary UCP discovery endpoint (Standard)**
- `GET /.well-known/agent.json` - Agent card for capability discovery
- `GET /ucp/profile` - UCP protocol configuration
- `GET /api/protocol/info` - Current protocol mode (REST)

### Products

- `GET /ucp/products` - List all products
- `GET /ucp/products/{productId}` - Get product details
- `GET /ucp/products/category/{category}` - Get products by category

### Checkout

- `POST /ucp/checkout` - Process checkout

Example request:
```json
{
  "customerId": "customer-123",
  "paymentMethod": "credit_card",
  "shippingMethod": "standard",
  "items": [
    {
      "productId": "prod-001",
      "quantity": 1
    },
    {
      "productId": "prod-003",
      "quantity": 2
    }
  ]
}
```

### Orders

- `GET /ucp/orders/{orderId}` - Get order details
- `GET /ucp/orders/customer/{customerId}` - Get customer orders

### Payments

- `GET /ucp/payments/{paymentId}` - Get payment details
- `GET /ucp/payments/order/{orderId}` - Get payment by order
- `POST /ucp/payments/{paymentId}/refund` - Refund a payment

## Sample Products

The application initializes with 6 Adidas products:

1. **Adidas SL72 Shoes** - $110.00 (Footwear)
2. **Adidas Handball Spezial Shoes** - $100.00 (Footwear)
3. **Adidas Samba OG Shoes** - $100.00 (Footwear)
4. **Adidas Climacool T-Shirt** - $35.00 (Apparel)
5. **Adidas Terrex T-Shirt** - $40.00 (Apparel)
6. **Adidas 3-Stripes Socks** - $18.00 (Accessories)

## Testing the Flow

1. **Discover capabilities**:
   ```bash
   curl http://localhost:8080/.well-known/agent.json
   ```

2. **Browse products**:
   ```bash
   curl http://localhost:8080/ucp/products
   ```

3. **Checkout**:
   ```bash
   curl -X POST http://localhost:8080/ucp/checkout \
     -H "Content-Type: application/json" \
     -d '{
       "customerId": "customer-123",
       "paymentMethod": "credit_card",
       "shippingMethod": "standard",
       "items": [
         {"productId": "prod-001", "quantity": 1}
       ]
     }'
   ```

4. **Check order**:
   ```bash
   curl http://localhost:8080/ucp/orders/{orderId}
   ```

## Development

### Project Structure

```
src/main/
├── java/com/ucp/poc/
│   ├── controller/          # REST controllers
│   │   ├── AgentCardController.java
│   │   ├── UcpProfileController.java
│   │   ├── ProductController.java
│   │   ├── CheckoutController.java
│   │   ├── OrderController.java
│   │   └── PaymentController.java
│   ├── service/            # Business logic
│   │   ├── ProductService.java
│   │   ├── OrderService.java
│   │   └── PaymentService.java
│   ├── model/              # Domain models
│   │   ├── Product.java
│   │   ├── Order.java
│   │   ├── Payment.java
│   │   └── CartItem.java
│   ├── dto/                # Data transfer objects
│   │   ├── CheckoutRequest.java
│   │   └── CheckoutResponse.java
│   └── UcpPocApplication.java
└── resources/
    ├── static/             # Web UI
    │   ├── index.html
    │   ├── css/chat.css
    │   └── js/chat.js
    └── application.yml
```

### Configuration

Configuration is in `src/main/resources/application.yml`:

- Server port: 8080
- Business name and description
- Logging levels

## References

### UCP Protocol
- **Official UCP Website**: https://ucp.dev
- **UCP Specification**: https://ucp.dev/latest/specification/
- **UCP GitHub**: https://github.com/Universal-Commerce-Protocol/ucp
- **Our UCP Reference**: [docs/UCP-REFERENCE.md](docs/UCP-REFERENCE.md)

### Additional Resources
- [Google Merchant UCP Docs](https://developers.google.com/merchant/ucp)
- [UCP Java Implementation](https://www.ucomprotocol.dev/index.html)
- [Spring AI A2A](https://github.com/spring-ai-community/spring-ai-a2a)
- [A2A Implementation Spec](docs/A2A-SPECIFICATION.md) - Complete A2A protocol guide

## License

POC/Demo purposes only
