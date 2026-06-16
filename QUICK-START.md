# UCP POC - Quick Start Guide

Get up and running with the Unified Commerce Protocol proof of concept in under 5 minutes.

## 🎯 What You'll Get

A fully functional e-commerce demo with:
- ✅ UCP-compliant REST API
- ✅ Interactive chat-based shopping UI
- ✅ 6 pre-loaded Adidas products
- ✅ Multi-item shopping cart
- ✅ Complete checkout flow
- ✅ Order tracking
- ✅ Mock payment processing

## 📋 Prerequisites

- **Java 21** (LTS)
- **Maven 3.6+**
- Web browser (Chrome, Firefox, Safari, Edge)

### Verify Installation
```bash
java -version  # Should show Java 21
mvn -version   # Should show Maven 3.6+
```

## 🚀 Quick Start (2 Minutes)

### 1. Clone and Build
```bash
cd /path/to/ucp-poc
mvn clean package
```

### 2. Start the Server
```bash
mvn spring-boot:run
```

Wait for:
```
Started UcpPocApplication in X.XXX seconds
```

### 3. Open the UI
Open your browser and navigate to:
```
http://localhost:8080
```

You should see the **UCP Store Chat Interface** with:
- Chat window in the center
- Product catalog sidebar (click "▶ Show Catalog" to expand)
- Shopping cart (appears when you add items)

### 4. Try It Out

**Browse Products**:
Type in chat: `Show me all products`

**Add to Cart**:
- Click "Add to Cart" on any product in the sidebar, OR
- Type: `I want to buy shoes`

**View Cart**:
Type: `Show my cart`

**Checkout**:
- Type: `Checkout`
- Fill in shipping address
- Fill in payment details (use card number: 4111111111111111 for VISA)
- Click "Complete Purchase"

**Track Order**:
Copy the order ID from confirmation and type it in chat: `order-abc123`

## 🧪 Verify Everything Works

### Test 1: Discovery Endpoints
```bash
# Agent discovery (primary UCP endpoint)
curl http://localhost:8080/.well-known/ucp

# Agent card
curl http://localhost:8080/.well-known/agent.json

# UCP profile
curl http://localhost:8080/ucp/profile

# Protocol info
curl http://localhost:8080/api/protocol/info
```

### Test 2: Product Catalog
```bash
# List all products
curl http://localhost:8080/ucp/products

# Get specific product
curl http://localhost:8080/ucp/products/prod-001

# Filter by category
curl http://localhost:8080/ucp/products/category/Footwear
```

### Test 3: Complete Checkout Flow
```bash
# 1. Create checkout session
SESSION_ID=$(curl -s -X POST http://localhost:8080/ucp/checkout/session \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"offerId": "prod-001", "quantity": 1}
    ]
  }' | jq -r '.sessionId')

echo "Session ID: $SESSION_ID"

# 2. Update with shipping address
curl -X POST "http://localhost:8080/ucp/checkout/session/$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "shippingAddress": {
      "streetAddress": "123 Main St",
      "addressLocality": "San Francisco",
      "addressRegion": "CA",
      "postalCode": "94102",
      "addressCountry": "US"
    },
    "paymentMethod": "VISA"
  }'

# 3. Complete checkout
curl -X POST "http://localhost:8080/ucp/checkout/session/$SESSION_ID/complete" \
  -H "Content-Type: application/json" \
  -d '{
    "billingAddress": {
      "streetAddress": "123 Main St",
      "addressLocality": "San Francisco",
      "addressRegion": "CA",
      "postalCode": "94102",
      "addressCountry": "US"
    },
    "paymentDetails": {
      "cardNumber": "4111111111111111",
      "cardholderName": "John Doe",
      "expiryMonth": "12",
      "expiryYear": "2025",
      "cvv": "123"
    }
  }'
```

### Test 4: Cart Operations
```bash
# Add item to cart
CART_ID=$(curl -s -X POST http://localhost:8080/ucp/cart/add \
  -H "Content-Type: application/json" \
  -d '{
    "offerId": "prod-001",
    "quantity": 2
  }' | jq -r '.cartId')

echo "Cart ID: $CART_ID"

# View cart
curl "http://localhost:8080/ucp/cart?cartId=$CART_ID"

# Remove item
curl -X DELETE "http://localhost:8080/ucp/cart/remove?cartId=$CART_ID&offerId=prod-001"

# Clear cart
curl -X POST "http://localhost:8080/ucp/cart/clear?cartId=$CART_ID"
```

## ✅ Success Indicators

### UI Should Show:
- ✅ 6 Adidas products in catalog
- ✅ Chat responds to commands
- ✅ Cart updates in real-time
- ✅ Checkout completes successfully
- ✅ Order ID displayed after purchase

### API Should Return:
- ✅ `200 OK` for valid requests
- ✅ UCP-compliant JSON responses
- ✅ Valid order IDs starting with `order-`
- ✅ Valid session IDs starting with `session-`

### Logs Should Show:
```
INFO  c.u.p.UcpPocApplication - Started UcpPocApplication
INFO  c.u.p.s.ProductService - Initializing product catalog with 6 products
INFO  c.u.p.s.UcpDiscoveryService - UCP Discovery Service initialized
DEBUG c.u.p.c.ProductController - GET /ucp/products - returning 6 products
```

## 🎯 Sample Products

The store comes with 6 Adidas products:

| ID | Name | Price | Category |
|----|------|-------|----------|
| prod-001 | Adidas SL72 Shoes | $110.00 | Footwear |
| prod-002 | Adidas Handball Spezial Shoes | $100.00 | Footwear |
| prod-003 | Adidas Samba OG Shoes | $100.00 | Footwear |
| prod-004 | Adidas Climacool T-Shirt | $35.00 | Apparel |
| prod-005 | Adidas Terrex T-Shirt | $40.00 | Apparel |
| prod-006 | Adidas 3-Stripes Socks | $18.00 | Accessories |

## 💳 Test Payment Cards

Payment is mocked for POC purposes:

| Card Type | Test Number | Result |
|-----------|-------------|--------|
| VISA | 4111111111111111 | ✅ Always succeeds |
| AMEX | 378282246310005 | ✅ Always succeeds |
| MASTERCARD | 5555555555554444 | ❌ Always fails |

Use MASTERCARD to test error handling scenarios.

## 📱 UI Chat Commands

Try these natural language commands:

**Product Discovery**:
- `Show me all products`
- `Show me shoes` / `Show me t-shirts`
- `What's in the Footwear category?`

**Cart Management**:
- `Show my cart`
- `Add Samba shoes to cart`
- Products can be added via sidebar buttons

**Checkout**:
- `Checkout` - Starts checkout flow
- `I want to pay now` - For single-item instant purchase

**Order Tracking**:
- Type any order ID: `order-abc123`
- `What's my order status?` (if you have an order ID)

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
1. Check logs for: `Initializing product catalog with 6 products`
2. Test API directly: `curl http://localhost:8080/ucp/products`
3. Verify Java 21 is being used: `java -version`

### Checkout Fails
1. Verify cart has items: `Show my cart`
2. Use valid VISA test card: `4111111111111111`
3. Fill all required address fields
4. Check server logs for error details

## 📚 Next Steps

### Learn More
- **[README.md](README.md)** - Full project documentation
- **[Architecture](docs/foundation/architecture.md)** - System design and components
- **[UCP Reference](docs/UCP-REFERENCE.md)** - Protocol details

### Explore the API
- Open Developer Tools (F12) → Network tab
- Watch API calls as you interact with the UI
- See UCP-compliant JSON structures

### Customize
- Add more products in `ProductService.java`
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

# Check validation stats
curl http://localhost:8080/api/validation/stats
```

## 🎉 You're Ready!

You now have a fully functional UCP-compliant e-commerce POC running locally.

**Key URLs**:
- **UI**: http://localhost:8080
- **API Docs**: http://localhost:8080/.well-known/ucp
- **Health**: http://localhost:8080/api/protocol/info

**Happy shopping! 🛍️**

---

## 🆘 Need Help?

- Review the [Architecture Documentation](docs/foundation/architecture.md)
- Check the full [README](README.md)
- Examine [CLAUDE.md](CLAUDE.md) for project context
- Browse the [UCP Specification](https://ucp.dev/latest/specification/)

**Protocol Mode**: This POC currently runs in **REST mode only**. Future versions will support A2A (Agent-to-Agent) and MCP (Model Context Protocol).
