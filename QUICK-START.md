# UCP POC - Quick Start Guide

Get up and running with the Unified Commerce Protocol proof of concept in under 5 minutes.

## 🎯 What You'll Get

A fully functional e-commerce demo with:
- ✅ UCP-compliant REST API (JSON)
- ✅ Interactive chat-based shopping UI
- ✅ 6 pre-loaded Adidas products
- ✅ Multi-item shopping cart
- ✅ Complete checkout flow
- ✅ Order tracking
- ✅ Mock payment processing
- ✅ Personal Agent Protocol (Poppy) authorization with two demo accounts

## 📋 Prerequisites

- **Java 21** (LTS)
- **Maven 3.6+**
- Web browser (Chrome, Firefox, Safari, Edge)
- `curl` and [`jq`](https://jqlang.github.io/jq/) for the API tests below

### Verify Installation
```bash
java -version  # Should show Java 21
mvn -version   # Should show Maven 3.6+
jq --version
```

## 🚀 Quick Start (2 Minutes)

### 1. Clone and Build
```bash
cd /path/to/ucp-demo
mvn clean package
```

### 2. Start the Server
```bash
mvn spring-boot:run
```

Wait for:
```
Started UcpDemoApplication in X.XXX seconds
```

### 3. Open the UI
Open your browser and navigate to:
```
http://localhost:8080
```

You should see the **UCP Store Chat Interface** with:
- Chat window in the center
- Product catalog sidebar (click "▶ Show Catalog" to expand)
- Shopping cart (appears when you add items), with the billing address form and payment method selector

### 4. Try It Out

**Browse Products**:
Type in chat: `Show me all products`

**Add to Cart**:
- Click "Add to Cart" on any product in the sidebar, OR
- Type: `I want to buy shoes`

**View Cart**:
Type: `Show my cart`

**Checkout**:
1. Fill in the billing address in the cart panel
2. Pick a payment method: **Visa** or **American Express** succeed, **Mastercard** always fails
3. Type `Checkout` or click the **Checkout** button
4. The **Authorize Personal Agent** dialog appears. Enter `buyer@example.com` and click **Authorize Agent**
5. The order confirmation shows the order ID

**Pay Now** (single item):
Click "Pay Now" on a product, fill in the billing address and card details (e.g. `4111 1111 1111 1111`), then click **Complete Purchase**. It asks for the same Poppy authorization as checkout.

**Track Order**:
Copy the order ID from the confirmation and type it in chat, e.g. `order-1a2b3c4d`. This needs `poppy:read`, which both demo accounts have.

### 5. Poppy Demo Accounts

The consent dialog decides what the assistant may do based on the email you enter:

| Email | Scopes | Track orders | Checkout / Pay Now |
|-------|--------|:------------:|:------------------:|
| `reader@example.com` | `poppy:read` | ✅ | ❌ "has not granted the 'poppy:write' scope" |
| `buyer@example.com` | `poppy:read poppy:write` | ✅ | ✅ |

Any other email is rejected ("Unknown account"). The token lives in the page's memory: reloading the page asks for authorization again. Try signing in as `reader@example.com` first to see the checkout rejected, then check out again and switch to `buyer@example.com`.

## 🧪 Verify Everything Works

### Test 1: Discovery Endpoints
```bash
# Agent discovery (primary UCP endpoint)
curl http://localhost:8080/.well-known/ucp

# Agent card
curl http://localhost:8080/.well-known/agent.json

# Personal Agent Protocol (Poppy) manifest
curl http://localhost:8080/.well-known/poppy.json

# UCP profile
curl http://localhost:8080/ucp/profile

# Protocol info
curl http://localhost:8080/api/protocol/info
```

### Test 2: Product Catalog
```bash
# List all products
curl http://localhost:8080/ucp/catalog/search

# Search by text
curl 'http://localhost:8080/ucp/catalog/search?q=samba'

# Filter by category, sorted by price
curl 'http://localhost:8080/ucp/catalog/search?category=Footwear&sort_by=price&sort_order=asc'

# Get specific product
curl 'http://localhost:8080/ucp/catalog/lookup?id=prod-001'
```

### Test 3: Get a Poppy Token
Checkout, order and payment endpoints need a Bearer token.
```bash
# 1. Authorize as the demo buyer; the code comes back in the redirect URL
CODE=$(curl -s -o /dev/null -w '%{redirect_url}' \
  'http://localhost:8080/auth/authorize?response_type=code&client_id=personal-agent-frontend&redirect_uri=http://localhost:8080&scope=poppy:read%20poppy:write&state=xyz&login_hint=buyer@example.com' \
  | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')

echo "Code: $CODE"

# 2. Exchange the code (single use) for a token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -d grant_type=authorization_code -d code="$CODE" | jq -r '.access_token')

echo "Token: $TOKEN"
```

### Test 4: Complete Checkout Flow
```bash
# 1. Create checkout session
SESSION_ID=$(curl -s -X POST http://localhost:8080/checkout-sessions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "line_items": [
      { "item": { "id": "prod-001" }, "quantity": 1 }
    ],
    "buyer": { "first_name": "John", "last_name": "Doe", "email": "john@example.com" },
    "context": { "currency": "USD", "language": "en", "address_country": "US" }
  }' | jq -r '.id')

echo "Session ID: $SESSION_ID"

# 2. (Optional) Check the session
curl -s "http://localhost:8080/checkout-sessions/$SESSION_ID" \
  -H "Authorization: Bearer $TOKEN" | jq

# 3. Complete checkout with payment and shipping
ORDER_ID=$(curl -s -X POST "http://localhost:8080/checkout-sessions/$SESSION_ID/complete" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "buyer": { "first_name": "John", "last_name": "Doe", "email": "john@example.com" },
    "payment": {
      "instruments": [{
        "id": "pm_1",
        "handler_id": "8c9202bd-63cc-4241-8d24-d57ce69ea31c",
        "type": "card",
        "selected": true,
        "credential": { "type": "PAYMENT_GATEWAY", "token": "tok_test123" },
        "billing_address": {
          "street_address": "123 Main St", "address_locality": "San Francisco",
          "address_region": "CA", "postal_code": "94102", "address_country": "US"
        },
        "display": { "brand": "visa", "last_digits": "4242" }
      }]
    },
    "fulfillment": {
      "methods": [{
        "type": "shipping",
        "selected": true,
        "destinations": [{
          "first_name": "John", "last_name": "Doe",
          "street_address": "123 Main St", "address_locality": "San Francisco",
          "address_region": "CA", "postal_code": "94102", "address_country": "US"
        }]
      }]
    }
  }' | jq -r '.order.id')

echo "Order ID: $ORDER_ID"

# 4. Check the order (needs poppy:read)
curl -s "http://localhost:8080/ucp/orders/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN" | jq
```

`handler_id` must match the payment handler advertised in `/ucp/profile`. Change `display.brand` to `mastercard` to test a declined payment.

### Test 5: Poppy Rejections
```bash
# No token → 401 invalid_token
curl -i -X POST http://localhost:8080/checkout-sessions \
  -H "Content-Type: application/json" -d '{}'

# Read-only account → token with poppy:read only
READER_CODE=$(curl -s -o /dev/null -w '%{redirect_url}' \
  'http://localhost:8080/auth/authorize?response_type=code&client_id=personal-agent-frontend&redirect_uri=http://localhost:8080&scope=poppy:read%20poppy:write&login_hint=reader@example.com' \
  | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
READER_TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -d grant_type=authorization_code -d code="$READER_CODE" | jq -r '.access_token')

# Reading an order works...
curl -s "http://localhost:8080/ucp/orders/$ORDER_ID" -H "Authorization: Bearer $READER_TOKEN" | jq

# ...but checkout → 403 insufficient_scope
curl -i -X POST http://localhost:8080/checkout-sessions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $READER_TOKEN" -d '{}'
```

### Test 6: Cart Operations
Carts are public (no token needed).
```bash
# Create a cart
CART_ID=$(curl -s -X POST http://localhost:8080/carts \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "customer-123",
    "line_items": [
      { "product_id": "prod-001", "quantity": 2, "unit_price": { "amount": 11000, "currency": "USD" } }
    ]
  }' | jq -r '.id')

echo "Cart ID: $CART_ID"

# Update the cart (replaces its line items)
curl -s -X PUT "http://localhost:8080/carts/$CART_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "line_items": [
      { "product_id": "prod-003", "quantity": 1, "unit_price": { "amount": 10000, "currency": "USD" } }
    ]
  }' | jq

# Cancel the cart
curl -s -X POST "http://localhost:8080/carts/$CART_ID/cancel" | jq
```

## ✅ Success Indicators

### UI Should Show:
- ✅ 6 Adidas products in catalog
- ✅ Chat responds to commands
- ✅ Cart updates in real-time
- ✅ Authorize Personal Agent dialog on first checkout or order lookup
- ✅ Checkout completes successfully with `buyer@example.com`
- ✅ Order ID displayed after purchase

### API Should Return:
- ✅ `200 OK` / `201 Created` for valid requests
- ✅ UCP-compliant JSON responses
- ✅ Order IDs starting with `order-`
- ✅ Checkout session IDs starting with `session_`
- ✅ Payment IDs starting with `pay-`
- ✅ `401` without a token and `403` with a read-only token on checkout

### Logs Should Show:
```
INFO  c.u.p.UcpDemoApplication - Started UcpDemoApplication in X.XXX seconds
INFO  c.u.p.s.UcpDiscoveryService - ✅ UCP local discovery service registered successfully
INFO  c.u.p.c.UcpCheckoutSessionController - Order created: order-1a2b3c4d for session: session_...
```

## 🎯 Sample Products

The store comes with 6 Adidas products:

| ID | Name | Price | Category |
|----|------|-------|----------|
| prod-001 | SL 72 RS Shoes | $110.00 | Footwear |
| prod-002 | Handball Spezial Shoes | $100.00 | Footwear |
| prod-003 | Samba OG Shoes | $100.00 | Footwear |
| prod-004 | Climacool T-Shirt | $35.00 | Apparel |
| prod-005 | Terrex Multi T-Shirt | $40.00 | Apparel |
| prod-006 | 3-Stripes Crew Socks (3 Pairs) | $18.00 | Accessories |

## 💳 Test Payments

Payment is mocked for POC purposes. Only the card **brand** matters:

| Brand | Cart checkout | Pay Now card number | Result |
|-------|---------------|---------------------|--------|
| VISA | Select "Visa" | starts with `4` (e.g. `4111111111111111`) | ✅ Always succeeds |
| AMEX | Select "American Express" | starts with `34`/`37` (e.g. `378282246310005`) | ✅ Always succeeds |
| MASTERCARD | Select "Mastercard" | starts with `5` (e.g. `5555555555554444`) | ❌ Always fails |

Use MASTERCARD to test error handling scenarios.

## 📱 UI Chat Commands

Try these natural language commands:

**Product Discovery**:
- `Show me all products`
- `Show me shoes` / `Show me t-shirts` / `Find socks`
- `Show me footwear` / `Show me apparel` / `Show me accessories`
- `Show catalog` / `Hide catalog`

**Cart Management**:
- `Show my cart`
- `I want to buy Samba shoes`
- `Remove ... from cart` / `Empty my cart`
- Products can also be added via sidebar buttons

**Checkout**:
- `Checkout` - Starts checkout flow (asks for Poppy authorization)
- **Pay Now** button on a product - Single-item instant purchase with card details

**Order Tracking**:
- Type any order ID: `order-1a2b3c4d`
- `What's my order status?` - The assistant asks for the order ID

**Other**:
- `Help` - List available commands

## 🔍 Troubleshooting

### Port 8080 Already in Use
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or change port in application.yml
server:
  port: 8081
```

If you change the port, also update the URLs in `src/main/resources/static/.well-known/poppy.json`, which are hardcoded to `localhost:8080`.

### Maven Build Fails
```bash
# Clean and rebuild
mvn clean install -U

# Skip tests if needed
mvn clean install -DskipTests
```

### UI Not Loading
1. Check server is running: `curl http://localhost:8080/api/protocol/info`
2. Clear browser cache (Ctrl+Shift+Delete)
3. Try incognito/private mode
4. Check browser console for errors (F12)

### Products Not Showing
1. Test API directly: `curl http://localhost:8080/ucp/catalog/search`
2. Check server logs for errors during startup
3. Verify Java 21 is being used: `java -version`

### Checkout Fails
1. Verify cart has items: `Show my cart`
2. Fill all required billing address fields
3. Select Visa or American Express (Mastercard always fails)
4. Check server logs for error details

### Authorization (Poppy) Problems
| Message | Cause | Fix |
|---------|-------|-----|
| "Unknown account: ..." | Email isn't a demo account | Use `buyer@example.com` or `reader@example.com` |
| "... has not granted the 'poppy:write' scope" | Signed in as `reader@example.com` | Checkout again and sign in as `buyer@example.com` |
| "Poppy access token is not valid" / `401` | No token, or the server restarted (tokens are in memory) | Reload the page and authorize again; for curl, rerun Test 3 |
| `invalid_grant` from `/auth/token` | Code already used | Request a new code from `/auth/authorize` |
| "Checkout cancelled: User cancelled agent delegation request." | **Deny** was clicked | Checkout again and click **Authorize Agent** |

## 📚 Next Steps

### Learn More
- **[README.md](README.md)** - Full project documentation and API reference
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System design and components
- **[POPPY.md](POPPY.md)** - Personal Agent Protocol integration, flows and examples
- **[UCP Specification](https://ucp.dev/latest/specification/)** - Protocol details

### Explore the API
- Open Developer Tools (F12) → Network tab
- Watch API calls as you interact with the UI, including the `Authorization: Bearer` header on checkout
- See UCP-compliant JSON structures
- Browse the OpenAPI docs at http://localhost:8080/swagger-ui.html

### Customize
- Add more products in `ProductService.java`
- Add or change Poppy demo accounts in `PoppyTokenStore.java`
- Modify tax rates in `TaxCalculationService.java`
- Adjust cart expiry in `application.yml`
- Customize UI styling in `static/css/chat.css`

### Test Protocol Compliance
```bash
# Enable schema validation in application.yml
ucp:
  validation:
    enabled: true
    strict: true
    management:
      enabled: true

# Check validation stats
curl http://localhost:8080/admin/schema-validation/stats
```

## 🎉 You're Ready!

You now have a fully functional UCP-compliant e-commerce POC running locally.

**Key URLs**:
- **UI**: http://localhost:8080
- **UCP discovery**: http://localhost:8080/.well-known/ucp
- **Poppy manifest**: http://localhost:8080/.well-known/poppy.json
- **API docs (OpenAPI)**: http://localhost:8080/v3/api-docs
- **Health**: http://localhost:8080/api/protocol/info

**Happy shopping! 🛍️**

---

## 🆘 Need Help?

- Review the [Architecture Documentation](ARCHITECTURE.md)
- Check the full [README](README.md)
- See [POPPY.md](POPPY.md) for authorization flows
- Browse the [UCP Specification](https://ucp.dev/latest/specification/)

**Protocol Mode**: The POC uses **REST** with JSON payloads only (`ucp.protocol.mode: REST` in `application.yml`). Both the chat UI and external agents call the same REST endpoints.
