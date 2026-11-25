# Architecture

## Overview

UCP POC is a Spring Boot 3.2 application demonstrating the Unified Commerce Protocol (UCP) for AI-enabled commerce operations. It implements a mock e-commerce store with full UCP protocol support, enabling AI agents to discover and interact with commerce capabilities.

It also implements the **Personal Agent Protocol (Poppy)**. A personal agent acting for an end user discovers the store through a manifest, obtains a user-delegated OAuth 2.0 token with limited scopes, and presents it to call protected commerce operations (see [Component 10](#10-personal-agent-authorization-poppy) and [POPPY.md](POPPY.md)).

## Tech Stack

### Core Framework
- **Spring Boot**: 3.2.5
- **Java**: 21 (LTS)
- **Build Tool**: Maven 3.6+

### Key Libraries
- **Spring Boot Web**: REST API framework
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
**Purpose**: Expose every commerce operation as a REST endpoint with JSON request and response bodies

**Components**:
- `ProtocolConfiguration`: Holds `ucp.protocol.mode`; `REST` is the only supported value
- `ProtocolCondition.RestEnabled`: Enables the cart and checkout controllers when the mode is `REST`
- `ProtocolInfoController`: `GET /api/protocol/info` reports the active mode to the UI

Agents find the REST endpoints through `/.well-known/ucp`, `/.well-known/agent.json`, `/.well-known/poppy.json` and the OpenAPI document at `/v3/api-docs`. A2A messaging (HTTP and WebSocket) was removed because it never worked end to end.

**Configuration**:
```yaml
ucp:
  protocol:
    mode: REST
```

### 1. Discovery Layer
**Purpose**: Enable AI agents to discover and understand commerce capabilities

**Components**:
- `UcpDiscoveryService`: Manages UCP profile and capability declarations
- `UcpProfileService`: Generates dynamic UCP profile configuration
- `AgentCardController`: Exposes `/.well-known/agent.json` (advertises the REST endpoints)
- `UcpProfileController`: Exposes `/ucp/profile` (multi-protocol aware)
- `UcpWellKnownController`: Exposes `/.well-known/ucp`
- `DiscoveryStatusController`: `GET /api/discovery/status` (mDNS status)
- `DiscoveryHelperController`: `GET /api/discovery/scan`, `GET /api/discovery/clear-cache` (debugging)
- `ProtocolInfoController`: `GET /api/protocol/info`
- `PoppyDiscoveryController`: Exposes the Poppy manifest `/.well-known/poppy.json`

**Key Endpoints**:
- `GET /.well-known/ucp` - Primary UCP discovery endpoint (standard)
- `GET /.well-known/agent.json` - Agent card for capability discovery (REST endpoints)
- `GET /ucp/profile` - Detailed UCP protocol configuration (shows active protocols)
- `GET /api/protocol/info` - Current protocol mode (REST)
- `GET /.well-known/poppy.json` - Poppy manifest: organization, OAuth endpoints, scopes, and channels (OpenAPI at `/v3/api-docs`, company agent at `/api/protocol/info`)

**Network Discovery**:
- mDNS/Bonjour service registration for local network discovery
- Service type: `_ucp-poc._tcp.local`
- Advertises the REST base path in TXT records
- Configurable via `ucp.discovery.enabled` in application.yml

### 2. Product Catalog
**Purpose**: Manage product information and search capabilities

**Components**:
- `ProductService`: Business logic for product management
- `CatalogSearchController`: UCP-compliant product search and lookup (the only catalog REST controller)

**Domain Models**:
- `Product`: Core product entity with UCP-compliant fields
- `UcpProduct`: DTO for UCP protocol responses

**Features**:
- Product listing (search with no query returns every product)
- Category and price-range filtering
- Search by name/description with sorting and pagination
- 6 pre-loaded Adidas products (shoes, apparel, accessories)

**Key Endpoints**:
- `GET /ucp/catalog/search` - UCP-compliant search. Query parameters: `q`, `category`, `min_price`, `max_price`, `limit` (default 20), `offset`, `sort_by` (`relevance`/`price`/`title`), `sort_order` (`asc`/`desc`)
- `GET /ucp/catalog/lookup?id={productId}` - Single product with options and variants

### 3. Shopping Cart
**Purpose**: Session-based multi-item cart management

**Components**:
- `CartController`: Cart operations (create, update, cancel)
- `CheckoutSessionService`: Session state management with TTL

**Features**:
- In-memory cart sessions with configurable expiry (60 minutes default)
- Multi-item support with quantity management
- Real-time subtotal calculation
- Session-based isolation (no cross-session leakage)

**Key Endpoints**:
- `POST /carts` - Create a cart from `line_items`
- `PUT /carts/{cartId}` - Replace the cart's line items (add, remove, change quantity)
- `POST /carts/{cartId}/cancel` - Cancel (clear) the cart

### 4. Checkout & Payment
**Purpose**: UCP-compliant checkout flow with payment processing

**Components**:
- `UcpCheckoutSessionController`: Multi-step checkout orchestration
- `CheckoutSessionService`: Session state and validation
- `PaymentService`: Mock payment processing with configurable outcomes
- `TaxCalculationService`: Tax calculation based on shipping address

**Checkout Flow** (🔒 all steps require Poppy `poppy:write`):
1. **Session Creation**: `POST /checkout-sessions`
   - Validates line items, buyer and context
   - Calculates subtotal, tax, shipping, total
   - Creates checkout session with TTL

2. **Session Update** (optional): `PUT /checkout-sessions/{id}`
   - Updates buyer, addresses and line items
   - Recalculates tax based on destination

3. **Session Completion**: `POST /checkout-sessions/{id}/complete`
   - Carries `buyer`, `payment.instruments` and `fulfillment.methods`
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

**Key Endpoints** (🔒 `GET` requires `poppy:read`, others `poppy:write`):
- `POST /checkout-sessions` - Create checkout session
- `GET /checkout-sessions/{id}` - Get session details
- `PUT /checkout-sessions/{id}` - Update session
- `POST /checkout-sessions/{id}/complete` - Complete checkout (response includes the created `order`)
- `POST /checkout-sessions/{id}/cancel` - Cancel session

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

**Key Endpoints** (🔒 Poppy `poppy:read` required):
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

**Key Endpoints** (🔒 Poppy: `GET` requires `poppy:read`, refund requires `poppy:write`):
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

**Key Endpoints** (when `ucp.validation.management.enabled: true`):
- `GET /admin/schema-validation/stats` - Schema cache statistics
- `POST /admin/schema-validation/clear-cache` - Clear schema cache
- `POST /admin/schema-validation/validate/{capabilityName}` - Validate a JSON body against a capability schema
- `POST /admin/schema-validation/load-resource` - Load a schema from a classpath resource

### 8. Configuration Layer
**Purpose**: Dynamic protocol and feature configuration

**Components**:
- `ProtocolConfiguration`: Protocol mode and feature flags
- `ProtocolCondition`: Conditional bean loading
- `UcpWebConfig`: Web MVC interceptor registration
- `WebMvcConfig`: Registers `PoppySecurityInterceptor` on `/checkout-sessions/**`, `/ucp/orders/**`, `/ucp/payments/**`

**Configuration**:
```yaml
ucp:
  protocol:
    mode: REST
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

### 9. Web UI (Chat Client)
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
- Poppy consent dialog (`#poppy-oauth-modal`) with account email field

**User Commands**:
- "Show me [category]" - Browse by category
- "Show me all products" - View entire catalog
- "Show my cart" - View cart contents
- "Checkout" - Start checkout flow (requires `poppy:write` authorization)
- "[order-id]" - Track order status (requires `poppy:read` authorization)

**Poppy client** (`chat.js`): `discoverPoppyCapabilities()` reads the manifest on page load. `ensurePoppyAccessToken(scope)` opens the consent dialog when the current token lacks the required scope. `initiateOAuthLoginFlow()` runs the authorize/token exchange, and `poppyAuthHeaders()` adds the Bearer header to protected calls. The token and granted scopes are kept in memory (`poppyConfig`) for the page session.

### 10. Personal Agent Authorization (Poppy)
**Purpose**: Let a personal agent act on behalf of a user with explicit, scope-limited consent

**Components**:
- `PoppyDiscoveryController`: Serves the manifest `static/.well-known/poppy.json`
- `MockAuthController`: Mock OAuth 2.0 authorization server (authorization code grant)
- `PoppyTokenStore`: In-memory demo accounts, single-use authorization codes and access tokens with their granted scopes
- `PoppySecurityInterceptor`: Spring MVC `HandlerInterceptor` that validates the Bearer token and the required scope
- `WebMvcConfig`: Registers the interceptor on protected paths

**Scopes**:
| Scope | Required for |
|-------|--------------|
| `poppy:read` | `GET` on protected paths (orders, payments, checkout session status) |
| `poppy:write` | Any other method on protected paths (create/complete checkout, refunds) |

**Demo Accounts** (`PoppyTokenStore.DEMO_USERS`):
| Email | Allowed scopes |
|-------|----------------|
| `reader@example.com` | `poppy:read` |
| `buyer@example.com` | `poppy:read`, `poppy:write` |

The account is identified by the OIDC `login_hint` parameter on `/auth/authorize`. Granted scopes are the requested scopes **intersected** with the account's allowed scopes. Unknown accounts are redirected back with `error=access_denied`.

**Key Endpoints**:
- `GET /.well-known/poppy.json` - Manifest (public)
- `GET /auth/authorize` - Issues a single-use code via redirect to `redirect_uri`
- `POST /auth/token` - Exchanges the code for a Bearer token (`scope` and `email` included in the response)

**Enforcement**:
- `OPTIONS` (CORS preflight) always passes
- Missing, malformed or unknown token → `401 invalid_token` + `WWW-Authenticate: Bearer error="invalid_token"`
- Valid token without the required scope → `403 insufficient_scope` + `WWW-Authenticate: Bearer error="insufficient_scope", scope="..."`
- Catalog (`/ucp/catalog/**`), carts (`/carts/**`) and discovery endpoints remain public (Guest mode)

**Design Notes**:
- Authorization lives in a `HandlerInterceptor`, not in controllers, so UCP controllers stay unaware of Poppy
- The store, not the client, is the authority on scopes: the UI still sends the request and surfaces the server's `403` message
- Required scope is derived from the HTTP method (coarse-grained by design for the POC)

**Limitations**:
- Mock server: no passwords, opaque tokens, no expiry enforcement, refresh token unused, state lost on restart

## Data Flow

### Product Browsing Flow
```
User → UI Chat Client → CatalogSearchController → ProductService → In-Memory Storage
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

Steps 2–4 go through `PoppySecurityInterceptor` first: the UI must hold a token with `poppy:write` (see the Poppy Authorization Flow below).

### Order Tracking Flow
```
User → UI Chat Client → PoppySecurityInterceptor (poppy:read) → OrderController → OrderService → In-Memory Storage
                                                                ↓
                                                          Order Details
```

### Poppy Authorization Flow
```
1. Discovery (page load, Guest mode):
   UI → GET /.well-known/poppy.json → PoppyDiscoveryController
                                     ↓
                     authorization_endpoint, token_endpoint, scopes

2. Consent (first protected action):
   UI consent dialog (email) → GET /auth/authorize?login_hint=<email>&scope=poppy:read poppy:write
                                     ↓
                    MockAuthController → PoppyTokenStore (allowed scopes for email)
                                     ↓
                    302 redirect_uri?code=...   |   302 redirect_uri?error=access_denied

3. Token exchange:
   UI → POST /auth/token (code) → PoppyTokenStore (redeem code, issue token)
                                     ↓
                    { access_token, scope, email }

4. Protected call:
   UI → Authorization: Bearer <token> → PoppySecurityInterceptor
                                     ↓
           unknown token → 401   |   missing scope → 403   |   OK → Controller
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
- Resource-based URLs (`/carts`, `/checkout-sessions`, `/ucp/orders`)
- Proper HTTP methods (GET, POST, PUT); state transitions use action sub-resources (`/complete`, `/cancel`)
- Standard status codes (200, 201, 400, 404, 500)

### Error Handling
- Consistent error response format
- Descriptive error messages
- Appropriate HTTP status codes

## Security Considerations

### Current State (POC)
- **Poppy authorization** on checkout sessions, orders and payments: Bearer tokens with `poppy:read`/`poppy:write` scopes, issued by a **mock** OAuth server (no passwords, opaque in-memory tokens). See [Component 10](#10-personal-agent-authorization-poppy)
- Catalog, cart and discovery endpoints have no authentication (intentionally for demo)
- No HTTPS/TLS (local development only)
- No rate limiting
- No input sanitization beyond validation

### Production Recommendations
- Replace `MockAuthController`/`PoppyTokenStore` with a real authorization server (Spring Authorization Server, Keycloak, Auth0) issuing signed JWTs with a `scope` claim
- Replace `PoppySecurityInterceptor` with Spring Security resource-server validation and per-endpoint scope rules (e.g. `hasAuthority('SCOPE_poppy:write')`)
- Use PKCE and real browser redirects for browser-based personal agents
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
└── UcpDemoApplicationTests.java
```

### Testing Recommendations
- Poppy tests (none yet): `MockAuthController` scope granting per account, single-use codes, and `PoppySecurityInterceptor` `401`/`403` responses via MockMvc
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

## Future Enhancements

### Protocol Support
- ✅ Personal Agent Protocol (Poppy) discovery and scoped authorization - **IMPLEMENTED** (mock OAuth server)
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
- Agent registry and capability tracking

### Technical Improvements
- Database persistence layer
- Distributed session management
- Production-grade authentication (REST is covered by mock Poppy authorization)
- API rate limiting (per agent)
- Webhook support for external integrations
- Comprehensive API documentation (OpenAPI/Swagger)

## References

### Internal Documentation
- [UI Guide](QUICK-START.md)
- [Project README](README.md)
- [Personal Agent Protocol (Poppy) Integration](POPPY.md)

### External Resources
- [UCP Official Website](https://ucp.dev)
- [UCP Specification](https://ucp.dev/latest/specification/)
- [UCP GitHub](https://github.com/Universal-Commerce-Protocol/ucp)
- [Google Merchant UCP](https://developers.google.com/merchant/ucp)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
