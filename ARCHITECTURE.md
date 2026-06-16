# Architecture

## Overview

UCP POC is a Spring Boot 3.2 application demonstrating the Unified Commerce Protocol (UCP) for AI-enabled commerce operations. It implements a mock e-commerce store with full UCP protocol support, enabling AI agents to discover and interact with commerce capabilities.

## Tech Stack

### Core Framework
- **Spring Boot**: 3.2.5
- **Java**: 21 (LTS)
- **Build Tool**: Maven 3.6+

### Key Libraries
- **Spring Boot Web**: REST API framework
- **Spring Boot WebSocket**: WebSocket support for A2A protocol
- **Spring Boot DevTools**: Hot reload for development
- **Lombok**: Reduces boilerplate code
- **JmDNS**: 3.5.9 - Local network service discovery (mDNS/Bonjour)
- **JSON Schema Validator**: 1.4.0 (networknt) - UCP schema validation
- **JUnit 5**: Testing framework

### Storage
- **In-Memory**: Concurrent hash maps for POC simplicity
- **No Database**: Intentionally stateless for demonstration purposes

## Architecture Components

### 0. Protocol Layer
**Purpose**: Multi-protocol support for agent communication (REST and A2A)

**Components**:
- `ProtocolConfiguration`: Multi-protocol configuration management
- `ProtocolCondition`: Conditional bean loading based on protocol mode
- `A2aMessageRouter`: Routes A2A messages to internal REST handlers
- `A2aController`: HTTP POST endpoint for synchronous A2A messaging
- `A2aWebSocketController`: WebSocket handler for asynchronous A2A messaging
- `WebSocketConfig`: STOMP over WebSocket configuration

**Protocol Modes**:
- `REST`: Traditional REST API endpoints only
- `A2A`: Agent-to-Agent messaging only
- `BOTH`: Both protocols enabled (default) - agents choose their preference

**A2A Protocol Features**:
- **Message Correlation**: Each message has unique `messageId`, responses include `correlationId`
- **Structured Errors**: Consistent error format with retry hints (NO_RETRY, RETRY_WITH_BACKOFF, RETRY_IMMEDIATE, RETRY_AFTER_DELAY)
- **Operation Registry**: 28+ operations mapped to REST equivalents
- **Dual Transport**: HTTP POST for sync, WebSocket for async bidirectional communication
- **Agent Identity**: Built-in sender/receiver tracking via `from`/`to` fields
- **Internal Routing**: A2A messages route to existing REST handlers (no code duplication)

**A2A DTOs**:
- `A2aMessage`: Complete message structure with type (REQUEST/RESPONSE/ERROR/NOTIFICATION)
- `A2aError`: Structured error responses with retry hints and status codes
- `A2aOperationRegistry`: Central registry mapping operations to REST endpoints

**Supported A2A Operations**:
- **Catalog**: catalog.search, catalog.product.get, catalog.products.list, catalog.lookup
- **Cart**: cart.add, cart.get, cart.update, cart.remove, cart.clear
- **Checkout**: checkout.session.create, checkout.session.update, checkout.session.complete, checkout.session.get
- **Orders**: order.get, order.list
- **Payments**: payment.process, payment.get
- **Discovery**: discovery.profile, discovery.capabilities

**Key A2A Endpoints**:
- `POST /a2a/message` - Send A2A message (synchronous HTTP)
- `WS /a2a/ws` - WebSocket connection (asynchronous, STOMP-based)
- `GET /a2a/capabilities` - List all supported operations
- `GET /a2a/health` - A2A protocol health check

**Configuration**:
```yaml
ucp:
  protocol:
    mode: BOTH  # REST, A2A, or BOTH
    a2a:
      enabled: true
      websocket-enabled: true
      http-enabled: true
      max-message-size: 1048576  # 1MB
      message-ttl: 300  # 5 minutes
```

**Message Format Example**:
```json
{
  "messageId": "uuid",
  "type": "REQUEST",
  "from": "agent-id",
  "to": "ucp-demo-store",
  "operation": "catalog.search",
  "payload": {"q": "shoes", "limit": 5},
  "metadata": {
    "timestamp": "2026-06-16T10:30:00Z",
    "protocolVersion": "2026-04-08"
  }
}
```

### 1. Discovery Layer
**Purpose**: Enable AI agents to discover and understand commerce capabilities

**Components**:
- `UcpDiscoveryService`: Manages UCP profile and capability declarations
- `UcpProfileService`: Generates dynamic UCP profile configuration
- `AgentCardController`: Exposes `/.well-known/agent.json` (advertises REST + A2A endpoints)
- `UcpProfileController`: Exposes `/ucp/profile` (multi-protocol aware)
- `DiscoveryStatusController`: Status endpoint for health checks
- `DiscoveryHelperController`: Helper endpoints for debugging

**Key Endpoints**:
- `GET /.well-known/ucp` - Primary UCP discovery endpoint (standard)
- `GET /.well-known/agent.json` - Agent card for capability discovery (includes REST and A2A endpoints)
- `GET /ucp/profile` - Detailed UCP protocol configuration (shows active protocols)
- `GET /api/protocol/info` - Current protocol mode (REST, A2A, or BOTH)

**Network Discovery**:
- mDNS/Bonjour service registration for local network discovery
- Service type: `_ucp-poc._tcp.local`
- Advertises both REST and A2A endpoints in TXT records
- Configurable via `ucp.discovery.enabled` in application.yml

### 2. Product Catalog
**Purpose**: Manage product information and search capabilities

**Components**:
- `ProductService`: Business logic for product management
- `ProductController`: REST endpoints for product access
- `CatalogSearchController`: UCP-compliant product search

**Domain Models**:
- `Product`: Core product entity with UCP-compliant fields
- `UcpProduct`: DTO for UCP protocol responses

**Features**:
- Product listing and detail views
- Category-based filtering
- Search by name/description
- 6 pre-loaded Adidas products (shoes, apparel, accessories)

**Key Endpoints**:
- `GET /ucp/products` - List all products
- `GET /ucp/products/{productId}` - Product details
- `GET /ucp/products/category/{category}` - Category filter
- `GET /ucp/catalog/search` - UCP-compliant search
- `GET /ucp/catalog/lookup` - Product lookup by ID

**A2A Operations**:
- `catalog.search` - Routes to GET /ucp/catalog/search
- `catalog.product.get` - Routes to GET /ucp/products/{id}
- `catalog.products.list` - Routes to GET /ucp/products
- `catalog.lookup` - Routes to GET /ucp/catalog/lookup

### 3. Shopping Cart
**Purpose**: Session-based multi-item cart management

**Components**:
- `CartController`: Cart operations (add, remove, view, clear)
- `CheckoutSessionService`: Session state management with TTL

**Features**:
- In-memory cart sessions with configurable expiry (60 minutes default)
- Multi-item support with quantity management
- Real-time subtotal calculation
- Session-based isolation (no cross-session leakage)

**Key Endpoints**:
- `POST /ucp/cart/add` - Add item to cart
- `DELETE /ucp/cart/remove` - Remove item from cart
- `GET /ucp/cart` - View cart contents
- `POST /ucp/cart/clear` - Clear cart

### 4. Checkout & Payment
**Purpose**: UCP-compliant checkout flow with payment processing

**Components**:
- `UcpCheckoutSessionController`: Multi-step checkout orchestration
- `CheckoutSessionService`: Session state and validation
- `PaymentService`: Mock payment processing with configurable outcomes
- `TaxCalculationService`: Tax calculation based on shipping address

**Checkout Flow**:
1. **Session Creation**: `POST /ucp/checkout/session`
   - Validates cart contents
   - Calculates subtotal, tax, shipping, total
   - Creates checkout session with TTL

2. **Session Update**: `POST /ucp/checkout/session/{sessionId}`
   - Updates shipping/billing addresses
   - Recalculates tax based on destination
   - Updates payment method

3. **Session Completion**: `POST /ucp/checkout/session/{sessionId}/complete`
   - Processes payment
   - Creates order
   - Generates payment record
   - Returns order confirmation

**Payment Mock**:
- **VISA**: Always succeeds
- **AMEX**: Always succeeds
- **MASTERCARD**: Always fails (for testing error scenarios)

**Tax Calculation**:
- Basic percentage-based tax (8% default)
- US state-specific rates supported
- International support (configurable per country)

**Key Endpoints**:
- `POST /ucp/checkout/session` - Create checkout session
- `POST /ucp/checkout/session/{sessionId}` - Update session
- `POST /ucp/checkout/session/{sessionId}/complete` - Complete checkout
- `GET /ucp/checkout/session/{sessionId}` - Get session details

### 5. Order Management
**Purpose**: Order tracking and history

**Components**:
- `OrderService`: Order lifecycle management
- `OrderController`: Order query endpoints

**Domain Models**:
- `Order`: Order entity with items, totals, status
- `UcpOrder`: DTO for UCP protocol responses

**Features**:
- Order creation from checkout
- Order status tracking
- Customer order history
- Detailed order line items

**Key Endpoints**:
- `GET /ucp/orders/{orderId}` - Order details
- `GET /ucp/orders/customer/{customerId}` - Customer orders

### 6. Payment Processing
**Purpose**: Payment record management and refund operations

**Components**:
- `PaymentService`: Payment state management
- `PaymentController`: Payment query and refund endpoints

**Domain Models**:
- `Payment`: Payment entity with method, amount, status

**Features**:
- Payment record creation
- Payment status tracking
- Refund processing (mock)
- Payment-to-order linking

**Key Endpoints**:
- `GET /ucp/payments/{paymentId}` - Payment details
- `GET /ucp/payments/order/{orderId}` - Get payment by order
- `POST /ucp/payments/{paymentId}/refund` - Process refund

### 7. Validation Layer
**Purpose**: Enforce UCP protocol compliance

**Components**:
- `UcpSchemaValidator`: JSON Schema validation against UCP specs
- `UcpValidationInterceptor`: Request/response validation interceptor
- `SchemaValidationController`: Management endpoints for validation control

**Features**:
- Request schema validation (requests, addresses, items)
- Response schema validation (products, orders, payments)
- Configurable strict/lenient modes
- Schema caching for performance
- Management endpoints (stats, cache control, manual validation)

**Configuration**:
```yaml
ucp:
  validation:
    enabled: false  # Toggle validation
    strict: false   # Fail on errors vs log warnings
    management:
      enabled: true # Enable management endpoints
```

**Key Endpoints**:
- `GET /api/validation/stats` - Validation statistics
- `POST /api/validation/cache/clear` - Clear schema cache
- `POST /api/validation/validate` - Manual schema validation

### 8. Configuration Layer
**Purpose**: Dynamic protocol and feature configuration

**Components**:
- `ProtocolConfiguration`: Protocol mode and feature flags
- `ProtocolCondition`: Conditional bean loading
- `UcpWebConfig`: Web MVC interceptor registration

**Configuration**:
```yaml
ucp:
  protocol:
    mode: BOTH  # REST, A2A, or BOTH
    a2a:
      enabled: true
      websocket-enabled: true
      http-enabled: true
  cart:
    expiry-minutes: 60
  payment:
    mock:
      enabled: true
  discovery:
    enabled: true
  validation:
    enabled: false
    strict: false
```

### 9. A2A Message Routing

**Purpose**: Route A2A messages to internal REST handlers

**Components**:
- `A2aMessageRouter`: Central routing logic
- `A2aOperationRegistry`: Operation-to-endpoint mapping
- `RestTemplate`: Internal HTTP client for routing

**Routing Flow**:
1. Receive A2A message with operation name (e.g., "catalog.search")
2. Look up operation in registry to get REST endpoint (e.g., "GET /ucp/catalog/search")
3. Extract payload parameters and resolve path/query parameters
4. Execute internal REST call via RestTemplate
5. Convert REST response to A2A response message
6. Return with correlation ID matching original request

**Error Handling**:
- Operation not found → OPERATION_NOT_FOUND error
- Invalid payload → VALIDATION_ERROR error
- Handler exception → HANDLER_ERROR error
- All errors include retry hints and HTTP status code equivalents

**Path Parameter Resolution**:
- Operation payload fields map to path parameters
- Example: `{"id": "prod-001"}` → `/ucp/products/prod-001`

**Query Parameter Handling**:
- Payload fields not in path become query parameters for GET requests
- Example: `{"q": "shoes", "limit": 5}` → `/ucp/catalog/search?q=shoes&limit=5`

### 10. Web UI (Chat Client)
**Purpose**: User-facing shopping interface

**Location**: `src/main/resources/static/`

**Components**:
- `index.html`: Chat-based shopping interface
- `css/chat.css`: Styling and layout
- `js/chat.js`: Frontend logic and API integration

**Features**:
- Interactive chat interface with natural language commands
- Product catalog sidebar (collapsible)
- Real-time shopping cart
- Dual purchase options (add to cart OR pay now)
- Checkout flow with address and payment forms
- Order status tracking
- Responsive design

**User Commands**:
- "Show me [category]" - Browse by category
- "Show me all products" - View entire catalog
- "Show my cart" - View cart contents
- "Checkout" - Start checkout flow
- "[order-id]" - Track order status

## Data Flow

### Product Browsing Flow
```
User → UI Chat Client → ProductController → ProductService → In-Memory Storage
                                           ↓
                                    UCP Response Format
```

### Checkout Flow
```
1. Cart Management:
   UI → CartController → CheckoutSessionService → Session Store

2. Checkout Initiation:
   UI → UcpCheckoutSessionController → CheckoutSessionService
                                      ↓
                                 Create Session
                                      ↓
                                Calculate Totals

3. Address & Payment:
   UI → UcpCheckoutSessionController → Update Session
                                      ↓
                                TaxCalculationService
                                      ↓
                                Recalculate Totals

4. Order Completion:
   UI → UcpCheckoutSessionController → PaymentService → Create Payment
                                      ↓
                                OrderService → Create Order
                                      ↓
                               Return Confirmation
```

### Order Tracking Flow
```
User → UI Chat Client → OrderController → OrderService → In-Memory Storage
                                        ↓
                                  Order Details
```

## Storage Strategy

### In-Memory Storage (POC)
All data stored in concurrent hash maps for thread-safety:
- `ConcurrentHashMap<String, Product>` - Products by ID
- `ConcurrentHashMap<String, Order>` - Orders by ID
- `ConcurrentHashMap<String, Payment>` - Payments by ID
- `ConcurrentHashMap<String, CheckoutSession>` - Active sessions

### Session Management
- Checkout sessions with TTL (60 minutes default)
- Automatic expiry via timestamp checking
- Manual cleanup on completion

### Data Initialization
- 6 sample Adidas products pre-loaded on startup
- Product IDs: `prod-001` through `prod-006`
- Categories: Footwear, Apparel, Accessories

## API Design Principles

### UCP Compliance
- All responses follow UCP schema specifications
- Proper use of UCP field names (`offerId`, `priceSpecification`, etc.)
- Schema validation against official UCP JSON schemas

### REST Conventions
- Resource-based URLs (`/ucp/products`, `/ucp/orders`)
- Proper HTTP methods (GET, POST, DELETE)
- Standard status codes (200, 201, 400, 404, 500)

### Error Handling
- Consistent error response format
- Descriptive error messages
- Appropriate HTTP status codes

## Security Considerations

### Current State (POC)
- No authentication/authorization (intentionally for demo)
- No HTTPS/TLS (local development only)
- No rate limiting
- No input sanitization beyond validation

### Production Recommendations
- Implement OAuth2/JWT authentication
- Add HTTPS/TLS termination
- Rate limiting per customer/session
- Input sanitization and SQL injection prevention (if DB added)
- CORS configuration for production domains
- Secure payment handling (PCI DSS compliance)

## Performance Considerations

### Current Optimizations
- In-memory storage for fast access
- Schema caching in validator
- Concurrent data structures for thread-safety
- Efficient session TTL management

### Scalability Limitations
- In-memory storage doesn't scale horizontally
- No session persistence across restarts
- Single-node deployment only

### Production Recommendations
- Redis for distributed session storage
- Database for persistent data (PostgreSQL recommended)
- Load balancer for horizontal scaling
- CDN for static assets
- Connection pooling for database

## Testing Strategy

### Current Coverage
- Unit tests for services (`ProductServiceTest`, `UcpSchemaValidatorTest`)
- Mock-based testing with JUnit 5
- Application context smoke tests

### Test Structure
```
src/test/java/com/ucp/poc/
├── service/
│   ├── ProductServiceTest.java
│   └── UcpSchemaValidatorTest.java
└── UcpPocApplicationTests.java
```

### Testing Recommendations
- Integration tests for API endpoints
- Contract tests for UCP compliance
- End-to-end tests for complete flows
- Load testing for performance validation

## Deployment

### Local Development
```bash
mvn spring-boot:run
```
Access at: http://localhost:8080

### Build for Production
```bash
mvn clean package
java -jar target/ucp-poc-0.0.1-SNAPSHOT.jar
```

### Configuration
Environment-specific configuration via:
- `application.yml` (default)
- `application-{profile}.yml` (profile-specific)
- Environment variables
- Command-line arguments

## Monitoring & Observability

### Current State
- Spring Boot Actuator endpoints (potential)
- Console logging via SLF4J/Logback
- Log levels configurable per package

### Logging Configuration
```yaml
logging:
  level:
    com.ucp.poc: INFO
    com.ucp.poc.controller: DEBUG
    com.ucp.poc.service: INFO
```

### Production Recommendations
- Add Spring Boot Actuator for metrics
- Integrate with ELK stack or similar
- Distributed tracing (Zipkin/Jaeger)
- APM tools (New Relic, Datadog)
- Business metrics tracking

## Protocol Comparison: REST vs A2A

| Aspect | REST | A2A |
|--------|------|-----|
| **Transport** | HTTP/HTTPS only | HTTP POST + WebSocket |
| **Message Format** | Direct JSON payload | Wrapped in A2A envelope |
| **Correlation** | None (stateless) | Built-in via messageId/correlationId |
| **Error Format** | HTTP status + body | Structured error with retry hints |
| **Agent Identity** | Via headers (optional) | Built-in via from/to fields |
| **Async Support** | Limited (polling) | Native via WebSocket |
| **Operation Discovery** | OpenAPI/docs | Dynamic via capabilities endpoint |
| **Use Case** | Simple request-response | Complex agent orchestration |

**When to Use REST**:
- Browser-based clients
- Simple stateless requests
- Low message volume
- Standard HTTP tooling

**When to Use A2A**:
- Agent-to-agent communication
- Need message correlation
- WebSocket for real-time updates
- Complex error recovery with retry hints
- Agent identity tracking

## Future Enhancements

### Protocol Support
- ✅ A2A (Agent-to-Agent) protocol - **IMPLEMENTED**
- MCP (Model Context Protocol) integration
- GraphQL API for flexible queries

### Features
- Product inventory management
- Customer accounts and profiles
- Order modification and cancellation
- Product reviews and ratings
- Wishlist functionality
- Multi-currency support
- Real-time order tracking
- Email notifications
- A2A message persistence for replay/debugging
- Agent registry and capability tracking

### Technical Improvements
- Database persistence layer
- Distributed session management
- Authentication and authorization (REST and A2A)
- API rate limiting (per agent for A2A)
- Webhook support for external integrations
- Comprehensive API documentation (OpenAPI/Swagger)
- A2A message tracing and metrics
- WebSocket connection pooling

## References

### Internal Documentation
- [UI Guide](../../QUICK-START.md)
- [Project README](../../README.md)

### External Resources
- [UCP Official Website](https://ucp.dev)
- [UCP Specification](https://ucp.dev/latest/specification/)
- [UCP GitHub](https://github.com/Universal-Commerce-Protocol/ucp)
- [Google Merchant UCP](https://developers.google.com/merchant/ucp)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
