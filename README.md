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
- **Personal Agent Protocol (Poppy)**: Personal agents discover the store via `/.well-known/poppy.json` and get user-delegated, scope-limited access (`poppy:read` / `poppy:write`) through a mock OAuth 2.0 flow before calling checkout, order and payment endpoints. See [POPPY.md](POPPY.md)

## Architecture

- **Spring Boot 3.2** with Java 21
- **UCP Spring Boot Starter** for protocol support
- **In-memory storage** for POC simplicity (concurrent hash maps)
- **REST API** following UCP conventions
- **Poppy authorization layer**: mock OAuth server (`/auth/*`) plus a Spring MVC interceptor that checks Bearer tokens and scopes on protected paths

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

#### Agent authorization (Poppy)

Browsing and cart operations work anonymously (Guest mode). When you **checkout** or **look up an order**, the assistant opens an **Authorize Personal Agent** dialog and asks for your store account email:

| Demo account | Scopes granted | Look up orders | Checkout |
|--------------|----------------|:--------------:|:--------:|
| `reader@example.com` | `poppy:read` | ✅ | ❌ (403 `insufficient_scope`) |
| `buyer@example.com` | `poppy:read poppy:write` | ✅ | ✅ |

Any other email is denied. The token is kept in memory for the page session, so reloading the page asks again. If an action needs a scope the current token lacks, the dialog reopens so you can switch accounts.

## API Endpoints

### Discovery

- `GET /.well-known/ucp` - **Primary UCP discovery endpoint (Standard)**
- `GET /.well-known/agent.json` - Agent card for capability discovery
- `GET /ucp/profile` - UCP protocol configuration
- `GET /api/protocol/info` - Current protocol mode (REST)
- `GET /.well-known/poppy.json` - Personal Agent Protocol (Poppy) manifest: OAuth endpoints, scopes and channels

### Personal Agent Authorization (Poppy)

- `GET /auth/authorize` - Mock authorization endpoint. Takes `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state` and `login_hint` (account email), and redirects back with a single-use `code` or `error=access_denied`
- `POST /auth/token` - Exchanges the code (`grant_type=authorization_code`) for a Bearer token. The response includes the granted `scope` and `email`

**Protected endpoints** need `Authorization: Bearer <token>`:

| Paths | `GET` requires | Other methods require |
|-------|----------------|-----------------------|
| `/checkout-sessions/**`, `/ucp/orders/**`, `/ucp/payments/**` | `poppy:read` | `poppy:write` |

Missing or unknown token → `401 invalid_token`. Token without the required scope → `403 insufficient_scope`. Catalog, cart and discovery endpoints stay public.

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

🔒 Poppy-protected: requires `poppy:read`.

- `GET /ucp/orders/{orderId}` - Get order details
- `GET /ucp/orders/customer/{customerId}` - Get customer orders

### Payments

🔒 Poppy-protected: `GET` requires `poppy:read`, refund requires `poppy:write`.

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

4. **Authorize as a personal agent (Poppy)**:
   ```bash
   # Discover the OAuth endpoints and scopes
   curl http://localhost:8080/.well-known/poppy.json

   # Sign in as the demo buyer; the redirect Location header carries the code
   curl -i 'http://localhost:8080/auth/authorize?response_type=code&client_id=personal-agent-frontend&redirect_uri=http://localhost:8080&scope=poppy:read%20poppy:write&login_hint=buyer@example.com'

   # Exchange the code for a Bearer token
   curl -X POST http://localhost:8080/auth/token \
     -d grant_type=authorization_code -d code={code}
   ```

5. **Check order** (requires a token with `poppy:read`):
   ```bash
   curl http://localhost:8080/ucp/orders/{orderId} \
     -H "Authorization: Bearer {access_token}"
   ```

See [POPPY.md](POPPY.md) for the full flow, including the read-only `reader@example.com` example and the `401`/`403` responses.

## Development

### Project Structure

```
src/main/
├── java/com/ucp/poc/
│   ├── config/             # Web configuration
│   │   ├── WebMvcConfig.java              # Registers the Poppy interceptor on protected paths
│   │   └── PoppySecurityInterceptor.java  # Bearer token + scope check (401/403)
│   ├── controller/          # REST controllers
│   │   ├── AgentCardController.java
│   │   ├── UcpProfileController.java
│   │   ├── ProductController.java
│   │   ├── CheckoutController.java
│   │   ├── OrderController.java
│   │   ├── PaymentController.java
│   │   ├── PoppyDiscoveryController.java  # GET /.well-known/poppy.json
│   │   └── MockAuthController.java        # Mock OAuth: /auth/authorize, /auth/token
│   ├── service/            # Business logic
│   │   ├── ProductService.java
│   │   ├── OrderService.java
│   │   ├── PaymentService.java
│   │   └── PoppyTokenStore.java           # Demo accounts, codes and tokens (in memory)
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
    │   ├── .well-known/poppy.json   # Poppy manifest
    │   ├── index.html               # Includes the Poppy consent dialog
    │   ├── css/chat.css
    │   └── js/chat.js               # Poppy discovery, consent and token handling
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

### Personal Agent Protocol (Poppy)
- **Integration guide and examples**: [POPPY.md](POPPY.md)
- **Note**: the OAuth server is a mock (no passwords, opaque in-memory tokens). See the limitations section in POPPY.md before reusing this pattern

### Additional Resources
- [Google Merchant UCP Docs](https://developers.google.com/merchant/ucp)
- [UCP Java Implementation](https://www.ucomprotocol.dev/index.html)
- [Spring AI A2A](https://github.com/spring-ai-community/spring-ai-a2a)

## License

POC/Demo purposes only
