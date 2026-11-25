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
- `GET /api/discovery/status` - mDNS/Bonjour discovery status
- `GET /api/discovery/scan?timeout=5` - Scan the local network for UCP services
- `GET /api/discovery/clear-cache` - Clear the discovery cache
- `GET /v3/api-docs` - OpenAPI specification (springdoc)

### Personal Agent Authorization (Poppy)

- `GET /auth/authorize` - Mock authorization endpoint. Takes `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state` and `login_hint` (account email), and redirects back with a single-use `code` or `error=access_denied`
- `POST /auth/token` - Exchanges the code (`grant_type=authorization_code`) for a Bearer token. The response includes the granted `scope` and `email`

**Protected endpoints** need `Authorization: Bearer <token>`:

| Paths | `GET` requires | Other methods require |
|-------|----------------|-----------------------|
| `/checkout-sessions/**`, `/ucp/orders/**`, `/ucp/payments/**` | `poppy:read` | `poppy:write` |

Missing or unknown token → `401 invalid_token`. Token without the required scope → `403 insufficient_scope`. Catalog, cart and discovery endpoints stay public.

### Catalog

- `GET /ucp/catalog/search` - Search products. Optional query parameters: `q`, `category`, `min_price`, `max_price`, `limit` (default 20), `offset`, `sort_by` (`relevance`, `price`, `title`), `sort_order` (`asc`, `desc`). With no parameters it lists every product
- `GET /ucp/catalog/lookup?id={productId}` - Get one product with its options and variants

### Carts

- `POST /carts` - Create a cart from `line_items`
- `PUT /carts/{cartId}` - Replace the cart's line items
- `POST /carts/{cartId}/cancel` - Cancel a cart

### Checkout

🔒 Poppy-protected: `GET` requires `poppy:read`, everything else requires `poppy:write`.

UCP checkout is a two-step session flow:

- `POST /checkout-sessions` - Create a checkout session
- `GET /checkout-sessions/{id}` - Get session status
- `PUT /checkout-sessions/{id}` - Update the session (buyer, addresses, line items)
- `POST /checkout-sessions/{id}/complete` - Complete with payment and fulfillment; the response includes the created `order`
- `POST /checkout-sessions/{id}/cancel` - Cancel the session

Example create request:
```json
{
  "line_items": [
    { "item": { "id": "prod-001" }, "quantity": 1 },
    { "item": { "id": "prod-003" }, "quantity": 2 }
  ],
  "buyer": {
    "first_name": "Jane",
    "last_name": "Doe",
    "email": "jane@example.com"
  },
  "context": {
    "currency": "USD",
    "language": "en",
    "address_country": "US"
  }
}
```

The complete request carries `buyer`, `payment.instruments` and `fulfillment.methods`. See `checkoutViaREST()` in `src/main/resources/static/js/chat.js` for a full example.

### Orders

🔒 Poppy-protected: requires `poppy:read`.

- `GET /ucp/orders/{orderId}` - Get order details
- `GET /ucp/orders/customer/{customerId}` - Get customer orders

### Payments

🔒 Poppy-protected: `GET` requires `poppy:read`, refund requires `poppy:write`.

- `GET /ucp/payments/{paymentId}` - Get payment details
- `GET /ucp/payments/order/{orderId}` - Get payment by order
- `POST /ucp/payments/{paymentId}/refund` - Refund a payment

### A2A (Agent-to-Agent)

- `POST /a2a/message` - Send an A2A message (synchronous HTTP)
- `WS /a2a/ws` - STOMP WebSocket endpoint. Send to `/app/a2a/message`, receive on `/topic/a2a/responses`
- `GET /a2a/capabilities` - List supported A2A operations
- `GET /a2a/health` - A2A health check

### Schema Validation (admin)

Available when `ucp.validation.management.enabled: true`:

- `GET /admin/schema-validation/stats` - Schema cache statistics
- `POST /admin/schema-validation/clear-cache` - Clear the schema cache
- `POST /admin/schema-validation/validate/{capabilityName}` - Validate a JSON body against a capability schema
- `POST /admin/schema-validation/load-resource` - Load a schema from a classpath resource

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
   curl 'http://localhost:8080/ucp/catalog/search?q=shoes'
   curl 'http://localhost:8080/ucp/catalog/lookup?id=prod-001'
   ```

3. **Authorize as a personal agent (Poppy)**. Checkout and orders require a token:
   ```bash
   # Discover the OAuth endpoints and scopes
   curl http://localhost:8080/.well-known/poppy.json

   # Sign in as the demo buyer; the redirect Location header carries the code
   curl -i 'http://localhost:8080/auth/authorize?response_type=code&client_id=personal-agent-frontend&redirect_uri=http://localhost:8080&scope=poppy:read%20poppy:write&login_hint=buyer@example.com'

   # Exchange the code for a Bearer token
   curl -X POST http://localhost:8080/auth/token \
     -d grant_type=authorization_code -d code={code}
   ```

4. **Checkout** (requires `poppy:write`):
   ```bash
   # Create the checkout session; note the returned "id"
   curl -X POST http://localhost:8080/checkout-sessions \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer {access_token}" \
     -d '{
       "line_items": [{ "item": { "id": "prod-001" }, "quantity": 1 }],
       "buyer": { "first_name": "Jane", "last_name": "Doe", "email": "jane@example.com" },
       "context": { "currency": "USD", "language": "en", "address_country": "US" }
     }'

   # Complete it with payment and fulfillment (full body: checkoutViaREST() in chat.js)
   curl -X POST http://localhost:8080/checkout-sessions/{sessionId}/complete \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer {access_token}" \
     -d '{ "buyer": { ... }, "payment": { "instruments": [ ... ] }, "fulfillment": { "methods": [ ... ] } }'
   ```
   The response's `order.id` is the order ID.

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
│   │   ├── AgentCardController.java           # GET /.well-known/agent.json
│   │   ├── UcpProfileController.java          # GET /ucp/profile
│   │   ├── CatalogSearchController.java       # /ucp/catalog/search, /ucp/catalog/lookup
│   │   ├── CartController.java                # /carts
│   │   ├── UcpCheckoutSessionController.java  # /checkout-sessions
│   │   ├── OrderController.java               # /ucp/orders
│   │   ├── PaymentController.java             # /ucp/payments
│   │   ├── A2aController.java                 # /a2a/message, /a2a/capabilities, /a2a/health
│   │   ├── A2aWebSocketController.java        # STOMP /app/a2a/message
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
│   └── UcpDemoApplication.java
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
