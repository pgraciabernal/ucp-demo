// UCP Shopping Assistant Chat Client

class UcpChatClient {
    constructor() {
        this.apiBase = localStorage.getItem('ucpBaseUrl') || '';
        this.products = [];
        this.cart = [];  // Client-side cart (REST mode or A2A fallback)
        this.serverCartId = null;  // Server cart ID (A2A mode)
        this.customerId = 'customer-' + Math.random().toString(36).substr(2, 9);
        this.protocolInfo = null;
        this.discoveredBusinesses = [];
        this.selectedBusiness = null;

        this.init();
    }

    /**
     * Extract numeric price from product
     * Handles UCP format with minor units (cents)
     * @param {Object|String|Number} product - Product object or price value
     * @returns {Number} - Numeric price value in dollars
     */
    extractPrice(product) {
        let priceValue = product.price || product;

        if (priceValue && typeof priceValue === 'object') {
            // UCP format: {amount: 11000, currency: "USD"} (minor units - cents)
            priceValue = priceValue.amount;

            // Convert from minor units (cents) to dollars
            if (typeof priceValue === 'number') {
                return priceValue / 100;
            }
        }

        // Fallback for legacy format
        const parsed = parseFloat(priceValue);
        return isNaN(parsed) ? 0 : parsed;
    }

    /**
     * Format price for display
     * @param {Object|String|Number} product - Product object or price value
     * @returns {String} - Formatted price string (e.g., "110.00")
     */
    formatPrice(product) {
        return this.extractPrice(product).toFixed(2);
    }

    /**
     * Determines if we should use A2A protocol for operations
     * Returns true only if A2A is enabled AND REST is disabled
     */
    shouldUseA2A() {
        if (!this.protocolInfo) {
            console.warn('Protocol info not loaded, defaulting to REST');
            return false;
        }

        const useA2A = this.protocolInfo.a2aEnabled && !this.protocolInfo.restEnabled;
        console.log(`Protocol routing: ${useA2A ? 'A2A' : 'REST'} (mode: ${this.protocolInfo.mode}, a2aEnabled: ${this.protocolInfo.a2aEnabled}, restEnabled: ${this.protocolInfo.restEnabled})`);
        return useA2A;
    }

    /**
     * Ensures we have a server-side cart in A2A mode
     * Creates one if it doesn't exist
     */
    async ensureServerCart() {
        if (!this.shouldUseA2A()) {
            return null;  // Not needed in REST mode
        }

        if (this.serverCartId) {
            // Verify cart still exists
            try {
                const getCartRequest = {
                    jsonrpc: '2.0',
                    method: 'cart.get',
                    params: { cartId: this.serverCartId },
                    id: 'verify-cart-' + Date.now()
                };

                const response = await fetch(`${this.apiBase}/a2a/rpc`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(getCartRequest)
                });

                const rpcResponse = await response.json();
                if (!rpcResponse.error) {
                    return this.serverCartId;  // Cart exists
                }
            } catch (error) {
                console.warn('Server cart expired or invalid, creating new one');
            }
        }

        // Create new server cart
        console.log('Creating server-side cart for A2A mode');
        const createCartRequest = {
            jsonrpc: '2.0',
            method: 'cart.create',
            params: { customerId: this.customerId },
            id: 'create-cart-' + Date.now()
        };

        const response = await fetch(`${this.apiBase}/a2a/rpc`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(createCartRequest)
        });

        const rpcResponse = await response.json();
        if (rpcResponse.error) {
            throw new Error('Failed to create server cart: ' + rpcResponse.error.message);
        }

        this.serverCartId = rpcResponse.result.cartId;
        console.log('Server cart created:', this.serverCartId);
        return this.serverCartId;
    }

    async init() {
        // Show current connection
        this.updateConnectionDisplay();

        // If no base URL configured, prompt for discovery
        if (!this.apiBase) {
            console.log('No base URL configured, showing discovery modal');
            this.showDiscoveryModal();
        }

        await this.loadProtocolInfo();
        this.loadProducts();
        this.setupEventListeners();
        this.setupDiscoveryListeners();
    }

    async loadProtocolInfo() {
        try {
            console.log('Loading protocol info...');
            const response = await fetch(`${this.apiBase}/api/protocol/info`);

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            this.protocolInfo = await response.json();
            console.log('Protocol info loaded:', this.protocolInfo);
            console.log('Active protocol mode:', this.protocolInfo.mode);
            console.log('REST enabled:', this.protocolInfo.restEnabled);
            console.log('A2A enabled:', this.protocolInfo.a2aEnabled);

            this.updateProtocolDisplay();
        } catch (error) {
            console.error('Error loading protocol info:', error);

            // Default to REST mode if can't load config
            this.protocolInfo = {
                mode: 'REST',
                description: 'Fallback to REST mode (config unavailable)',
                restEnabled: true,
                a2aEnabled: false
            };

            console.warn('Using fallback protocol config:', this.protocolInfo);
        }
    }

    updateProtocolDisplay() {
        const ucpFlowHeader = document.querySelector('.ucp-flow-header h3');
        // UCP Flow header is set in HTML, no need to update dynamically
    }

    // Protocol info card removed - UCP-only mode, no need to display protocol switcher

    setupEventListeners() {
        const sendBtn = document.getElementById('send-btn');
        const userInput = document.getElementById('user-input');
        const checkoutBtn = document.getElementById('checkout-btn');
        const toggleSidebarBtn = document.getElementById('toggle-sidebar');
        const showSidebarBtn = document.getElementById('show-sidebar');
        const toggleUcpFlowBtn = document.getElementById('toggle-ucp-flow');

        sendBtn.addEventListener('click', () => this.handleSendMessage());
        userInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.handleSendMessage();
            }
        });

        checkoutBtn.addEventListener('click', () => this.handleCheckout());

        toggleSidebarBtn.addEventListener('click', () => this.toggleSidebar());
        showSidebarBtn.addEventListener('click', () => this.toggleSidebar());

        if (toggleUcpFlowBtn) {
            toggleUcpFlowBtn.addEventListener('click', () => this.toggleUcpFlow());
        }

        // Keyboard shortcut: Ctrl+B or Cmd+B to toggle sidebar
        document.addEventListener('keydown', (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 'b') {
                e.preventDefault();
                this.toggleSidebar();
            }
            // Keyboard shortcut: Ctrl+U or Cmd+U to toggle UCP flow
            if ((e.ctrlKey || e.metaKey) && e.key === 'u') {
                e.preventDefault();
                this.toggleUcpFlow();
            }
        });
    }

    setupDiscoveryListeners() {
        const settingsBtn = document.getElementById('settings-btn');
        if (settingsBtn) {
            settingsBtn.addEventListener('click', () => this.showDiscoveryModal());
        }

        const scanBtn = document.getElementById('scan-network-btn');
        if (scanBtn) {
            scanBtn.addEventListener('click', () => this.scanNetwork());
        }

        const connectBtn = document.getElementById('connect-manual-btn');
        if (connectBtn) {
            connectBtn.addEventListener('click', () => this.connectManual());
        }
    }

    showDiscoveryModal() {
        const modal = document.getElementById('discovery-modal');
        if (modal) {
            modal.style.display = 'flex';
            this.updateConnectionDisplay();
        }
    }

    closeDiscoveryModal() {
        const modal = document.getElementById('discovery-modal');
        if (modal) {
            modal.style.display = 'none';
        }
    }

    updateConnectionDisplay() {
        const connectionInfo = document.getElementById('current-connection-info');
        if (connectionInfo) {
            if (this.apiBase) {
                const protocol = this.protocolInfo ? this.protocolInfo.mode : 'Unknown';
                connectionInfo.textContent = `${this.apiBase} (${protocol})`;
                connectionInfo.style.color = '#4CAF50';
            } else {
                connectionInfo.textContent = 'Not connected - please discover or configure a business';
                connectionInfo.style.color = '#c62828';
            }
        }
    }

    async scanNetwork() {
        const businessesDiv = document.getElementById('discovered-businesses');
        const scanBtn = document.getElementById('scan-network-btn');

        if (!businessesDiv) return;

        // Show loading
        scanBtn.disabled = true;
        scanBtn.textContent = '⏳ Scanning...';
        businessesDiv.innerHTML = '<div class="loading-spinner">🔍 Scanning network for UCP businesses...</div>';

        try {
            console.log('Starting network scan...');
            const response = await fetch(`${this.apiBase || ''}/api/discovery/scan?timeout=5`);

            if (!response.ok) {
                throw new Error(`Scan failed: HTTP ${response.status}`);
            }

            let businesses = await response.json();

            // Client-side deduplication by URL (in case server-side dedup missed any)
            const seen = new Map();
            businesses = businesses.filter(business => {
                if (seen.has(business.url)) {
                    console.warn(`Duplicate business detected: ${business.name} at ${business.url}`);
                    return false;
                }
                seen.set(business.url, true);
                return true;
            });

            this.discoveredBusinesses = businesses;

            console.log(`Found ${businesses.length} unique businesses:`, businesses);

            if (businesses.length === 0) {
                businessesDiv.innerHTML = '<p class="text-muted">No UCP businesses found on the network.<br/>Make sure a UCP business is running and discovery is enabled.</p>';
            } else {
                businessesDiv.innerHTML = businesses.map((business, index) => `
                    <div class="business-item" data-index="${index}" onclick="chatClient.selectBusiness(${index})">
                        <div class="business-name">📦 ${business.name}</div>
                        <div class="business-url">${business.url}</div>
                        <div>
                            <span class="business-protocol">${business.protocol.toUpperCase()}</span>
                            <span class="business-protocol">${business.version}</span>
                        </div>
                    </div>
                `).join('');
            }

        } catch (error) {
            console.error('Network scan failed:', error);
            businessesDiv.innerHTML = `
                <div class="discovery-error">
                    ❌ Scan failed: ${error.message}
                    <br/><br/>
                    Make sure the server is running and accessible.
                </div>
            `;
        } finally {
            scanBtn.disabled = false;
            scanBtn.textContent = '🔍 Scan Network';
        }
    }

    selectBusiness(index) {
        this.selectedBusiness = this.discoveredBusinesses[index];

        // Update UI
        document.querySelectorAll('.business-item').forEach((item, i) => {
            if (i === index) {
                item.classList.add('selected');
            } else {
                item.classList.remove('selected');
            }
        });

        // Connect to the business
        this.connectToBusiness(this.selectedBusiness.url);
    }

    async connectToBusiness(url) {
        console.log('Connecting to business:', url);

        // Save to localStorage
        this.apiBase = url;
        localStorage.setItem('ucpBaseUrl', url);

        // Reload protocol info
        try {
            await this.loadProtocolInfo();
            await this.loadProducts();

            this.updateConnectionDisplay();
            this.closeDiscoveryModal();

            this.addMessage(`✅ Connected to business at ${url}`, 'bot');
            this.addMessage(`Protocol mode: ${this.protocolInfo.mode}`, 'bot');

        } catch (error) {
            console.error('Connection failed:', error);
            this.addMessage(`❌ Failed to connect: ${error.message}`, 'bot');
        }
    }

    connectManual() {
        const urlInput = document.getElementById('manual-url');
        if (!urlInput) return;

        let url = urlInput.value.trim();

        if (!url) {
            alert('Please enter a URL');
            return;
        }

        // Add http:// if not present
        if (!url.startsWith('http://') && !url.startsWith('https://')) {
            url = 'http://' + url;
        }

        this.connectToBusiness(url);
    }

    toggleUcpFlow() {
        const ucpPanel = document.getElementById('ucp-flow-panel');
        const toggleBtn = document.getElementById('toggle-ucp-flow');

        if (ucpPanel.classList.contains('hidden')) {
            // Show panel (on right side)
            ucpPanel.classList.remove('hidden');
            toggleBtn.innerHTML = '◀';
            toggleBtn.title = 'Hide UCP Flow';
        } else {
            // Hide panel
            ucpPanel.classList.add('hidden');
            toggleBtn.innerHTML = '▶';
            toggleBtn.title = 'Show UCP Flow';
        }
    }

    logUcpFlow(type, title, data, protocol = 'UCP') {
        const ucpFlowContent = document.getElementById('ucp-flow-content');

        // Remove empty state if present
        const emptyState = ucpFlowContent.querySelector('.ucp-flow-empty');
        if (emptyState) {
            emptyState.remove();
        }

        // Create flow item
        const flowItem = document.createElement('div');
        flowItem.className = `ucp-flow-item ${type}`;

        const timestamp = new Date().toLocaleTimeString();
        const badgeText = type === 'request' ? 'REQUEST' : type === 'response' ? 'RESPONSE' : 'ERROR';

        // Protocol badge color
        const protocolBadgeClass = 'protocol-rest'; // Always UCP/REST now
        const protocolIcon = protocol === 'A2A' ? '🤖' : '📡';

        flowItem.innerHTML = `
            <div class="ucp-flow-title">
                <span>${title}</span>
                <div>
                    <span class="ucp-protocol-badge ${protocolBadgeClass}">${protocolIcon} ${protocol}</span>
                    <span class="ucp-flow-badge ${type}">${badgeText}</span>
                </div>
            </div>
            <div class="ucp-flow-data">
                <pre>${JSON.stringify(data, null, 2)}</pre>
            </div>
            <div class="ucp-flow-timestamp">${timestamp}</div>
        `;

        // Add to top of flow (after protocol info card)
        const protocolCard = ucpFlowContent.querySelector('.protocol-info-card');
        if (protocolCard && protocolCard.nextSibling) {
            ucpFlowContent.insertBefore(flowItem, protocolCard.nextSibling);
        } else {
            ucpFlowContent.insertBefore(flowItem, ucpFlowContent.firstChild);
        }

        // Limit to 20 items
        const items = ucpFlowContent.querySelectorAll('.ucp-flow-item');
        if (items.length > 20) {
            items[items.length - 1].remove();
        }
    }

    toggleSidebar() {
        const sidebar = document.getElementById('sidebar');
        const showSidebarBtn = document.getElementById('show-sidebar');
        const toggleBtn = document.getElementById('toggle-sidebar');

        if (sidebar.classList.contains('hidden')) {
            // Show sidebar
            sidebar.classList.remove('hidden');
            showSidebarBtn.style.display = 'none';
            toggleBtn.innerHTML = '◀';
            toggleBtn.title = 'Hide catalog';
        } else {
            // Hide sidebar
            sidebar.classList.add('hidden');
            showSidebarBtn.style.display = 'flex';
            toggleBtn.innerHTML = '▶';
            toggleBtn.title = 'Show catalog';
        }
    }

    async loadProducts() {
        try {
            console.log('Loading products with protocol info:', this.protocolInfo);

            let products;
            let protocol;

            // Ensure protocol info is loaded
            if (!this.protocolInfo) {
                console.error('Protocol info not loaded yet!');
                throw new Error('Protocol configuration not available');
            }

            // Use appropriate protocol based on configuration
            if (this.shouldUseA2A()) {
                // Use A2A endpoint
                protocol = 'A2A';
                console.log('Loading products using A2A protocol');

                const rpcRequest = {
                    jsonrpc: '2.0',
                    method: 'product.list',
                    params: {},
                    id: 'load-products-' + Date.now()
                };

                this.logUcpFlow('request', 'POST /a2a/rpc (product.list)', rpcRequest, 'A2A');

                const response = await fetch(`${this.apiBase}/a2a/rpc`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(rpcRequest)
                });

                const rpcResponse = await response.json();

                if (rpcResponse.error) {
                    throw new Error(rpcResponse.error.message);
                }

                products = rpcResponse.result.products;
            } else {
                // Use REST endpoint - UCP standard /ucp/catalog/search
                protocol = 'REST';
                console.log('Loading products using REST protocol (UCP catalog.search)');

                this.logUcpFlow('request', 'GET /ucp/catalog/search', {
                    method: 'GET',
                    endpoint: '/ucp/catalog/search',
                    description: 'Search product catalog (empty query = all products)'
                }, 'REST');

                const response = await fetch(`${this.apiBase}/ucp/catalog/search`);
                const ucpResponse = await response.json();

                // Extract products from UCP search response
                products = ucpResponse.products || [];
                console.log('UCP Response:', ucpResponse);
                console.log('Products extracted:', products.length);

                // Log full UCP response
                this.logUcpFlow('response', 'Product Catalog', ucpResponse, protocol);
            }

            this.products = products;

            this.renderProducts();
        } catch (error) {
            console.error('Error loading products:', error);
            this.logUcpFlow('error', 'Failed to load products', { error: error.message });
            this.showError('Failed to load products');
        }
    }

    renderProducts() {
        const productList = document.getElementById('product-list');
        if (!productList) {
            console.error('Product list element not found!');
            return;
        }

        productList.innerHTML = '';

        if (!Array.isArray(this.products)) {
            console.error('this.products is not an array:', this.products);
            productList.innerHTML = '<div style="padding: 20px; color: red;">Error: Products data is invalid</div>';
            return;
        }

        if (this.products.length === 0) {
            console.log('No products to render');
            productList.innerHTML = '<div style="padding: 20px;">No products available</div>';
            return;
        }

        this.products.forEach((product, index) => {
            try {
                // Debug logging
                console.log(`[${index}] Rendering product:`, product.name, 'Price data:', product.price);

                // Handle UCP product structure (nested objects)
                const stockQuantity = product.inventory?.available || product.stockQuantity || 0;
                const imageUrl = product.images?.[0]?.url || product.imageUrl || '';
                const category = product.category || '';

                // Handle price: can be nested object {amount, currency} OR direct number/string
                let price = '0.00';  // Default
                let currency = 'USD';

                if (product.price) {
                    if (typeof product.price === 'object' && product.price !== null) {
                        // UCP format: {amount: 11000, currency: "USD"} (minor units - cents)
                        const priceAmount = product.price.amount;
                        currency = product.price.currency || 'USD';
                        console.log('  - Price amount from object:', priceAmount, 'Type:', typeof priceAmount);

                        if (priceAmount !== null && priceAmount !== undefined) {
                            if (typeof priceAmount === 'number') {
                                // Convert from minor units (cents) to dollars
                                price = (priceAmount / 100).toFixed(2);
                            } else {
                                // Legacy format: string like "29.99"
                                const parsed = parseFloat(priceAmount);
                                if (!isNaN(parsed)) {
                                    price = parsed.toFixed(2);
                                } else {
                                    console.error('  - Failed to parse price amount:', priceAmount);
                                }
                            }
                        }
                    } else {
                        // Direct format: 29.99 or "29.99"
                        console.log('  - Price direct:', product.price, 'Type:', typeof product.price);
                        const parsed = parseFloat(product.price);
                        if (!isNaN(parsed)) {
                            price = parsed.toFixed(2);
                        } else {
                            console.error('  - Failed to parse direct price:', product.price);
                        }
                    }
                }

                console.log('  - Final price:', price, 'Currency:', currency);

                const productCard = document.createElement('div');
                productCard.className = 'product-card';
                productCard.innerHTML = `
                    ${imageUrl ? `<img src="${imageUrl}" alt="${product.name}" class="product-image" onerror="this.style.display='none'">` : ''}
                    <div class="product-name">${product.name}</div>
                    <div class="product-price">$${price}</div>
                    ${category ? `<div class="product-category">${category}</div>` : ''}
                    <div class="product-stock">In stock: ${stockQuantity}</div>
                    <div class="product-actions">
                        <button class="add-to-cart-btn" data-product-id="${product.id}">
                            Add to Cart
                        </button>
                        <button class="pay-now-btn" data-product-id="${product.id}">
                            Pay Now
                        </button>
                    </div>
                `;

                productCard.querySelector('.add-to-cart-btn').addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.addToCart(product);
                });

                productCard.querySelector('.pay-now-btn').addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.payNow(product);
                });

                productList.appendChild(productCard);
            } catch (error) {
                console.error(`Error rendering product ${product?.name || 'unknown'}:`, error);
            }
        });
    }

    handleSendMessage() {
        const userInput = document.getElementById('user-input');
        const message = userInput.value.trim();

        if (!message) return;

        this.addMessage(message, 'user');
        userInput.value = '';

        this.processMessage(message);
    }

    async processMessage(message) {
        const lowerMessage = message.toLowerCase();

        if (lowerMessage.includes('hide') && (lowerMessage.includes('catalog') || lowerMessage.includes('sidebar') || lowerMessage.includes('products'))) {
            const sidebar = document.getElementById('sidebar');
            if (!sidebar.classList.contains('hidden')) {
                this.toggleSidebar();
                this.addMessage('Product catalog hidden. Say "show catalog" to display it again, or press Ctrl+B.', 'bot');
            } else {
                this.addMessage('Catalog is already hidden.', 'bot');
            }
        } else if (lowerMessage.includes('show') && (lowerMessage.includes('catalog') || lowerMessage.includes('sidebar')) && !lowerMessage.includes('product')) {
            const sidebar = document.getElementById('sidebar');
            if (sidebar.classList.contains('hidden')) {
                this.toggleSidebar();
                this.addMessage('Product catalog shown.', 'bot');
            } else {
                this.addMessage('Catalog is already visible.', 'bot');
            }
        } else if ((lowerMessage.includes('show') || lowerMessage.includes('give') || lowerMessage.includes('find') || lowerMessage.includes('search')) && (lowerMessage.includes('product') || lowerMessage.includes('catalog') || lowerMessage.includes('t-shirt') || lowerMessage.includes('tshirt') || lowerMessage.includes('shirt') || lowerMessage.includes('shoes') || lowerMessage.includes('shoe') || lowerMessage.includes('sock') || lowerMessage.includes('footwear') || lowerMessage.includes('apparel') || lowerMessage.includes('accessories'))) {
            await this.showProductCatalog(message);
        } else if ((lowerMessage.includes('empty') || lowerMessage.includes('clear')) && (lowerMessage.includes('cart') || lowerMessage.includes('basket'))) {
            this.emptyCart();
        } else if (lowerMessage.includes('remove') && (lowerMessage.includes('cart') || lowerMessage.includes('from cart'))) {
            this.handleRemoveFromCart(message);
        } else if (lowerMessage.includes('cart') || lowerMessage.includes('basket')) {
            this.showCart();
        } else if (lowerMessage.includes('buy') || lowerMessage.includes('purchase') || lowerMessage.includes('want')) {
            await this.handleBuyIntent(message);
        } else if (lowerMessage.includes('checkout') || lowerMessage.includes('pay')) {
            await this.handleCheckout();
        } else if (lowerMessage.includes('order') && lowerMessage.includes('status')) {
            this.addMessage('To check order status, please provide your order ID.', 'bot');
        } else if (lowerMessage.startsWith('order-')) {
            await this.checkOrderStatus(message.trim());
        } else if (lowerMessage.includes('help')) {
            this.showHelp();
        } else {
            this.handleGeneralQuery(message);
        }
    }

    /**
     * Extract search query from natural language message
     * Returns free-text query to pass to /ucp/catalog/search
     */
    extractSearchQuery(message) {
        const lowerMsg = message.toLowerCase().trim();

        // Remove common command words and extract the actual search terms
        const commandPatterns = [
            /^show\s+(me\s+)?/,
            /^find\s+(me\s+)?/,
            /^search\s+(for\s+)?/,
            /^looking\s+for\s+/,
            /^look\s+for\s+/,
            /^i\s+want\s+/,
            /^want\s+/,
            /^give\s+(me\s+)?/,
            /^get\s+(me\s+)?/
        ];

        let query = lowerMsg;

        // Remove command patterns from the beginning
        for (const pattern of commandPatterns) {
            query = query.replace(pattern, '');
        }

        // Remove filler words but keep the main search terms
        query = query.replace(/\b(the|a|an|some|any)\b/g, ' ').trim();

        // Remove product/catalog words that add no value
        query = query.replace(/\b(products?|items?|catalog)\b/g, ' ').trim();

        // Clean up extra spaces
        query = query.replace(/\s+/g, ' ').trim();

        // Return the cleaned query if it has meaningful content
        return query.length > 1 ? query : null;
    }

    /**
     * Search products using A2A protocol
     */
    async searchProductsA2A(query) {
        console.log('Searching products using A2A protocol:', query);

        const rpcRequest = {
            jsonrpc: '2.0',
            method: 'product.search',
            params: {
                query: query,
                limit: 100
            },
            id: 'search-products-' + Date.now()
        };

        this.logUcpFlow('request', 'A2A: product.search', rpcRequest, 'A2A');

        const response = await fetch(`${this.apiBase}/a2a/rpc`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(rpcRequest)
        });

        const rpcResponse = await response.json();

        if (rpcResponse.error) {
            throw new Error(rpcResponse.error.message);
        }

        this.logUcpFlow('response', 'Search Results', rpcResponse.result, 'A2A');

        return rpcResponse.result.products || [];
    }

    /**
     * Search products using UCP catalog search endpoint (REST mode)
     */
    async searchProductsREST(query) {
        console.log('Searching products using UCP catalog.search:', query);

        const searchUrl = `${this.apiBase}/ucp/catalog/search?q=${encodeURIComponent(query)}&limit=100`;

        this.logUcpFlow('request', 'GET /ucp/catalog/search', {
            method: 'GET',
            endpoint: '/ucp/catalog/search',
            query: query,
            url: searchUrl
        }, 'REST');

        const response = await fetch(searchUrl, {
            headers: {
                'UCP-Agent': 'UCP-POC-UI/1.0 (Web Browser)'
            }
        });

        if (!response.ok) {
            throw new Error(`Search failed: ${response.statusText}`);
        }

        const ucpResponse = await response.json();

        console.log('Search response:', ucpResponse);
        console.log('Products found:', ucpResponse.products?.length || 0);

        this.logUcpFlow('response', 'Search Results', ucpResponse, 'REST');

        // Extract products from UCP response
        return ucpResponse.products || [];
    }

    /**
     * Filter products client-side (for fallback)
     */
    filterProductsClientSide(filterMessage) {
        const lowerFilter = filterMessage.toLowerCase();
        return this.products.filter(product => {
            const productName = product.name.toLowerCase();
            const productCategory = product.category.toLowerCase();

            // Check for specific product keywords
            if (lowerFilter.includes('t-shirt') || lowerFilter.includes('tshirt') || lowerFilter.includes('shirt')) {
                return productName.includes('t-shirt');
            }
            if (lowerFilter.includes('shoes') || lowerFilter.includes('shoe') || lowerFilter.includes('sneaker')) {
                return productCategory === 'footwear' || productName.includes('shoes');
            }
            if (lowerFilter.includes('sock')) {
                return productName.includes('sock');
            }
            if (lowerFilter.includes('footwear')) {
                return productCategory === 'footwear';
            }
            if (lowerFilter.includes('apparel') || lowerFilter.includes('clothing')) {
                return productCategory === 'apparel';
            }
            if (lowerFilter.includes('accessories') || lowerFilter.includes('accessory')) {
                return productCategory === 'accessories';
            }

            return false;
        });
    }

    async showProductCatalog(filterMessage = null) {
        let productsToShow = this.products;
        let messagePrefix = 'Here are all our available products.';

        // If we have a search query, use server-side search (UCP or A2A)
        if (filterMessage) {
            try {
                const searchQuery = this.extractSearchQuery(filterMessage);
                console.log('Extracted search query:', searchQuery, 'from message:', filterMessage);
                if (searchQuery) {
                    if (this.shouldUseA2A()) {
                        productsToShow = await this.searchProductsA2A(searchQuery);
                    } else {
                        // Use UCP catalog search endpoint
                        productsToShow = await this.searchProductsREST(searchQuery);
                    }
                    messagePrefix = `Found ${productsToShow.length} product(s) matching your search.`;
                } else {
                    // Fallback to client-side filter
                    productsToShow = this.filterProductsClientSide(filterMessage);
                    if (productsToShow.length > 0 && productsToShow.length < this.products.length) {
                        messagePrefix = `Found ${productsToShow.length} product(s) matching your search.`;
                    }
                }
            } catch (error) {
                console.error('Server search failed, using client-side filter:', error);
                productsToShow = this.filterProductsClientSide(filterMessage);
                if (productsToShow.length > 0 && productsToShow.length < this.products.length) {
                    messagePrefix = `Found ${productsToShow.length} product(s) matching your search.`;
                }
            }
        }

        let productHtml = '<div class="product-grid">';

        productsToShow.forEach(product => {
            const productJson = JSON.stringify(product).replace(/"/g, '&quot;');
            const priceDisplay = this.formatPrice(product);
            productHtml += `
                <div class="product-grid-item">
                    ${product.imageUrl ? `<img src="${product.imageUrl}" alt="${product.name}" class="product-grid-image" onerror="this.style.display='none'">` : ''}
                    <div class="product-name">${product.name}</div>
                    <div class="product-price">$${priceDisplay}</div>
                    <div class="product-category">${product.category}</div>
                    <div class="product-grid-actions">
                        <button class="grid-add-btn" onclick="chatClient.addToCart(${productJson})">Add to Cart</button>
                        <button class="grid-pay-btn" onclick="chatClient.payNow(${productJson})">Pay Now</button>
                    </div>
                </div>
            `;
        });

        productHtml += '</div>';

        this.addMessage(messagePrefix + ' Choose an option:' + productHtml, 'bot');
    }

    async handleBuyIntent(message) {
        // Simple keyword matching to find products
        const foundProducts = this.products.filter(p =>
            message.toLowerCase().includes(p.name.toLowerCase()) ||
            message.toLowerCase().includes(p.category.toLowerCase())
        );

        if (foundProducts.length > 0) {
            for (const product of foundProducts) {
                await this.addToCart(product);
            }
        } else {
            this.addMessage(
                "I couldn't find that product. Here's what we have available:",
                'bot'
            );
            await this.showProductCatalog();
        }
    }

    async addToCart(product) {
        if (this.shouldUseA2A()) {
            // A2A mode: Use server-side cart
            await this.addToCartA2A(product);
        } else {
            // REST mode: Use UCP cart endpoints
            await this.addToCartREST(product);
        }
    }

    async addToCartREST(product) {
        try {
            const unitPrice = this.extractPrice(product);
            const existingItem = this.cart.find(item => item.productId === product.id);

            // Build line items for cart
            let lineItems = this.cart.map(item => ({
                product_id: item.productId,
                quantity: item.quantity,
                unit_price: {
                    amount: Math.round(item.unitPrice * 100), // Convert to cents
                    currency: item.currency
                }
            }));

            // Add or update the new item
            if (existingItem) {
                // Update existing item quantity
                const itemIndex = lineItems.findIndex(item => item.product_id === product.id);
                lineItems[itemIndex].quantity++;
            } else {
                // Add new item
                lineItems.push({
                    product_id: product.id,
                    quantity: 1,
                    unit_price: {
                        amount: Math.round(unitPrice * 100), // Convert to cents
                        currency: product.price?.currency || 'USD'
                    }
                });
            }

            if (!this.serverCartId) {
                // First cart operation - POST /carts
                const createCartRequest = {
                    customer_id: this.customerId,
                    line_items: lineItems
                };

                this.logUcpFlow('request', 'POST /carts', createCartRequest, 'REST');

                const response = await fetch(`${this.apiBase}/carts`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'UCP-Agent': 'UCP-Chat-Client/1.0'
                    },
                    body: JSON.stringify(createCartRequest)
                });

                const ucpResponse = await response.json();
                this.logUcpFlow('response', 'Cart Created', ucpResponse, 'REST');

                // Extract cart ID from UCP response
                this.serverCartId = ucpResponse.id;

                // Update client-side cart
                if (existingItem) {
                    existingItem.quantity++;
                    existingItem.totalPrice = existingItem.unitPrice * existingItem.quantity;
                } else {
                    this.cart.push({
                        productId: product.id,
                        productName: product.name || product.title,
                        quantity: 1,
                        unitPrice: unitPrice,
                        totalPrice: unitPrice,
                        currency: product.price?.currency || 'USD'
                    });
                }

            } else {
                // Cart exists - PUT /carts/{id}
                const updateCartRequest = {
                    line_items: lineItems
                };

                this.logUcpFlow('request', `PUT /carts/${this.serverCartId}`, updateCartRequest, 'REST');

                const response = await fetch(`${this.apiBase}/carts/${this.serverCartId}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'UCP-Agent': 'UCP-Chat-Client/1.0'
                    },
                    body: JSON.stringify(updateCartRequest)
                });

                const ucpResponse = await response.json();
                this.logUcpFlow('response', 'Cart Updated', ucpResponse, 'REST');

                // Update client-side cart
                if (existingItem) {
                    existingItem.quantity++;
                    existingItem.totalPrice = existingItem.unitPrice * existingItem.quantity;
                } else {
                    this.cart.push({
                        productId: product.id,
                        productName: product.name || product.title,
                        quantity: 1,
                        unitPrice: unitPrice,
                        totalPrice: unitPrice,
                        currency: product.price?.currency || 'USD'
                    });
                }
            }

            this.updateCartDisplay();
            this.addMessage(`Added ${product.name || product.title} to your cart! ($${this.formatPrice(product)})`, 'bot');

        } catch (error) {
            console.error('Error adding to cart:', error);
            this.logUcpFlow('error', 'Cart Error', { error: error.message }, 'REST');
            this.showError('Failed to add item to cart');
        }
    }

    async addToCartA2A(product) {
        try {
            console.log('Adding to cart using A2A protocol');

            // Ensure we have a server cart
            const cartId = await this.ensureServerCart();

            // Check if item already in cart (client-side tracking)
            const existingItem = this.cart.find(item => item.productId === product.id);
            const currentQuantity = existingItem ? existingItem.quantity : 0;

            // Add or update item on server
            const method = existingItem ? 'cart.update' : 'cart.add';
            const rpcRequest = {
                jsonrpc: '2.0',
                method: method,
                params: {
                    cartId: cartId,
                    productId: product.id,
                    quantity: currentQuantity + 1
                },
                id: 'add-to-cart-' + Date.now()
            };

            this.logUcpFlow('request', `A2A: ${method}`, rpcRequest, 'A2A');

            const response = await fetch(`${this.apiBase}/a2a/rpc`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(rpcRequest)
            });

            const rpcResponse = await response.json();

            if (rpcResponse.error) {
                throw new Error(rpcResponse.error.message);
            }

            this.logUcpFlow('response', 'Cart Updated', rpcResponse.result, 'A2A');

            // Update client-side cart for display
            const unitPrice = this.extractPrice(product);

            if (existingItem) {
                existingItem.quantity++;
                existingItem.totalPrice = existingItem.unitPrice * existingItem.quantity;
            } else {
                this.cart.push({
                    productId: product.id,
                    productName: product.name,
                    quantity: 1,
                    unitPrice: unitPrice,
                    totalPrice: unitPrice,
                    currency: product.price?.currency || 'USD'
                });
            }

            this.updateCartDisplay();
            this.addMessage(`Added ${product.name} to your cart! ($${this.formatPrice(product)})`, 'bot');

        } catch (error) {
            console.error('Error adding to cart via A2A:', error);
            this.addMessage(`Failed to add ${product.name} to cart: ${error.message}`, 'bot');
        }
    }

    async removeFromCart(productId) {
        if (this.shouldUseA2A()) {
            // A2A mode: Use server-side cart
            return await this.removeFromCartA2A(productId);
        } else {
            // REST mode: Use UCP cart endpoints
            return await this.removeFromCartREST(productId);
        }
    }

    async removeFromCartREST(productId) {
        try {
            const itemIndex = this.cart.findIndex(item => item.productId === productId);
            if (itemIndex === -1) {
                return null;
            }

            const item = this.cart[itemIndex];
            const newQuantity = item.quantity - 1;

            // Build updated line items
            let lineItems;
            if (newQuantity > 0) {
                // Update quantity
                lineItems = this.cart.map(cartItem => {
                    if (cartItem.productId === productId) {
                        return {
                            product_id: cartItem.productId,
                            quantity: newQuantity,
                            unit_price: {
                                amount: Math.round(cartItem.unitPrice * 100),
                                currency: cartItem.currency
                            }
                        };
                    }
                    return {
                        product_id: cartItem.productId,
                        quantity: cartItem.quantity,
                        unit_price: {
                            amount: Math.round(cartItem.unitPrice * 100),
                            currency: cartItem.currency
                        }
                    };
                });
            } else {
                // Remove item completely
                lineItems = this.cart
                    .filter(cartItem => cartItem.productId !== productId)
                    .map(cartItem => ({
                        product_id: cartItem.productId,
                        quantity: cartItem.quantity,
                        unit_price: {
                            amount: Math.round(cartItem.unitPrice * 100),
                            currency: cartItem.currency
                        }
                    }));
            }

            // Check if cart will be empty after removal
            if (lineItems.length === 0) {
                // Cancel the cart - POST /carts/{id}/cancel
                this.logUcpFlow('request', `POST /carts/${this.serverCartId}/cancel`, {}, 'REST');

                const response = await fetch(`${this.apiBase}/carts/${this.serverCartId}/cancel`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'UCP-Agent': 'UCP-Chat-Client/1.0'
                    },
                    body: JSON.stringify({})
                });

                const ucpResponse = await response.json();
                this.logUcpFlow('response', 'Cart Cancelled', ucpResponse, 'REST');

                // Reset cart
                this.cart = [];
                this.serverCartId = null;
                this.updateCartDisplay();

                return { ...item };
            }

            // Update cart - PUT /carts/{id}
            const updateCartRequest = {
                line_items: lineItems
            };

            this.logUcpFlow('request', `PUT /carts/${this.serverCartId}`, updateCartRequest, 'REST');

            const response = await fetch(`${this.apiBase}/carts/${this.serverCartId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'UCP-Agent': 'UCP-Chat-Client/1.0'
                },
                body: JSON.stringify(updateCartRequest)
            });

            const ucpResponse = await response.json();
            this.logUcpFlow('response', 'Cart Updated', ucpResponse, 'REST');

            // Update client-side cart
            const removedItem = { ...item };
            if (newQuantity > 0) {
                item.quantity = newQuantity;
                item.totalPrice = item.unitPrice * newQuantity;
            } else {
                this.cart.splice(itemIndex, 1);
            }

            this.updateCartDisplay();
            return removedItem;

        } catch (error) {
            console.error('Error removing from cart:', error);
            this.logUcpFlow('error', 'Cart Error', { error: error.message }, 'REST');
            this.showError('Failed to remove item from cart');
            return null;
        }
    }

    async removeFromCartA2A(productId) {
        try {
            console.log('Removing from cart using A2A protocol');

            const itemIndex = this.cart.findIndex(item => item.productId === productId);
            if (itemIndex === -1) {
                return null;
            }

            const item = this.cart[itemIndex];
            const newQuantity = item.quantity - 1;

            if (!this.serverCartId) {
                console.warn('No server cart ID, falling back to client-side');
                return this.removeFromCartClientSide(productId);
            }

            // Update or remove on server
            let rpcRequest;
            if (newQuantity > 0) {
                // Update quantity
                rpcRequest = {
                    jsonrpc: '2.0',
                    method: 'cart.update',
                    params: {
                        cartId: this.serverCartId,
                        productId: productId,
                        quantity: newQuantity
                    },
                    id: 'update-cart-' + Date.now()
                };
            } else {
                // Remove item
                rpcRequest = {
                    jsonrpc: '2.0',
                    method: 'cart.remove',
                    params: {
                        cartId: this.serverCartId,
                        productId: productId
                    },
                    id: 'remove-from-cart-' + Date.now()
                };
            }

            this.logUcpFlow('request', `A2A: ${rpcRequest.method}`, rpcRequest, 'A2A');

            const response = await fetch(`${this.apiBase}/a2a/rpc`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(rpcRequest)
            });

            const rpcResponse = await response.json();

            if (rpcResponse.error) {
                throw new Error(rpcResponse.error.message);
            }

            this.logUcpFlow('response', 'Cart Updated', rpcResponse.result, 'A2A');

            // Update client-side cart for display
            const removedItem = { ...item };
            if (newQuantity > 0) {
                item.quantity = newQuantity;
                item.totalPrice = item.unitPrice * newQuantity;
            } else {
                this.cart.splice(itemIndex, 1);
            }

            this.updateCartDisplay();
            return removedItem;

        } catch (error) {
            console.error('Error removing from cart via A2A:', error);
            this.addMessage(`Failed to remove item from cart: ${error.message}`, 'bot');
            return null;
        }
    }

    async emptyCart() {
        if (this.cart.length === 0) {
            this.addMessage('Your cart is already empty.', 'bot');
            return;
        }

        if (this.shouldUseA2A()) {
            // A2A mode: Use server-side cart
            await this.emptyCartA2A();
        } else {
            // REST mode: Use UCP cart endpoints
            await this.emptyCartREST();
        }
    }

    async emptyCartREST() {
        try {
            const itemCount = this.cart.length;

            if (this.serverCartId) {
                // Cancel the cart - POST /carts/{id}/cancel
                this.logUcpFlow('request', `POST /carts/${this.serverCartId}/cancel`, {}, 'REST');

                const response = await fetch(`${this.apiBase}/carts/${this.serverCartId}/cancel`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'UCP-Agent': 'UCP-Chat-Client/1.0'
                    },
                    body: JSON.stringify({})
                });

                const ucpResponse = await response.json();
                this.logUcpFlow('response', 'Cart Cancelled', ucpResponse, 'REST');
            }

            this.cart = [];
            this.serverCartId = null; // Reset cart ID
            this.updateCartDisplay();
            this.addMessage(`🗑️ Cart cleared! Removed ${itemCount} item${itemCount > 1 ? 's' : ''} from your cart.`, 'bot');

        } catch (error) {
            console.error('Error emptying cart:', error);
            this.logUcpFlow('error', 'Cart Error', { error: error.message }, 'REST');
            this.showError('Failed to empty cart');
        }
    }

    async emptyCartA2A() {
        try {
            console.log('Emptying cart using A2A protocol');

            const itemCount = this.cart.length;

            if (this.serverCartId) {
                const rpcRequest = {
                    jsonrpc: '2.0',
                    method: 'cart.clear',
                    params: {
                        cartId: this.serverCartId
                    },
                    id: 'clear-cart-' + Date.now()
                };

                this.logUcpFlow('request', 'A2A: cart.clear', rpcRequest, 'A2A');

                const response = await fetch(`${this.apiBase}/a2a/rpc`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(rpcRequest)
                });

                const rpcResponse = await response.json();

                if (rpcResponse.error) {
                    throw new Error(rpcResponse.error.message);
                }

                this.logUcpFlow('response', 'Cart Cleared', rpcResponse.result, 'A2A');
            }

            // Clear client-side cart
            this.cart = [];
            this.serverCartId = null;  // Reset server cart ID
            this.updateCartDisplay();
            this.addMessage(`🗑️ Cart cleared! Removed ${itemCount} item${itemCount > 1 ? 's' : ''} from your cart.`, 'bot');

        } catch (error) {
            console.error('Error clearing cart via A2A:', error);
            this.addMessage(`Failed to clear cart: ${error.message}`, 'bot');
        }
    }

    handleRemoveFromCart(message) {
        if (this.cart.length === 0) {
            this.addMessage('Your cart is empty. Nothing to remove!', 'bot');
            return;
        }

        // Try to match product name in the message
        const foundProducts = this.cart.filter(item =>
            message.toLowerCase().includes(item.productName.toLowerCase()) ||
            message.toLowerCase().includes(item.productName.toLowerCase().split(' ').pop())
        );

        if (foundProducts.length > 0) {
            const removedItem = this.removeFromCart(foundProducts[0].productId);
            if (removedItem) {
                if (this.cart.find(item => item.productId === removedItem.productId)) {
                    this.addMessage(`Removed 1x ${removedItem.productName} from your cart. (${removedItem.quantity} remaining)`, 'bot');
                } else {
                    this.addMessage(`Removed ${removedItem.productName} from your cart.`, 'bot');
                }
            }
        } else {
            // Show cart items with remove buttons
            this.showCartWithRemoveOptions();
        }
    }

    showCartWithRemoveOptions() {
        if (this.cart.length === 0) {
            this.addMessage('Your cart is empty.', 'bot');
            return;
        }

        let cartMessage = '<strong>Your Shopping Cart:</strong><br>';
        cartMessage += '<p>Click a button to remove an item:</p>';
        cartMessage += '<div class="cart-remove-options">';

        this.cart.forEach((item, index) => {
            cartMessage += `
                <div class="cart-remove-item">
                    <span>${item.productName} x${item.quantity} - $${item.totalPrice.toFixed(2)}</span>
                    <button class="remove-item-btn" onclick="chatClient.removeFromCart('${item.productId}')">Remove</button>
                </div>
            `;
        });

        cartMessage += '</div>';
        cartMessage += `<button class="empty-cart-btn" onclick="chatClient.emptyCart()">Empty Cart</button>`;

        this.addMessage(cartMessage, 'bot');
    }

    getMissingBillingFields() {
        const missing = [];
        const firstName = document.getElementById('first-name')?.value.trim();
        const lastName = document.getElementById('last-name')?.value.trim();
        const addressLine1 = document.getElementById('address-line1')?.value.trim();
        const city = document.getElementById('city')?.value.trim();
        const region = document.getElementById('region')?.value.trim();
        const postalCode = document.getElementById('postal-code')?.value.trim();
        const country = document.getElementById('country')?.value.trim();
        const email = document.getElementById('email')?.value?.trim();

        if (!firstName) missing.push('First Name');
        if (!lastName) missing.push('Last Name');
        if (!addressLine1) missing.push('Address');
        if (!city) missing.push('City');
        if (!postalCode) missing.push('Postal Code');
        if (!country) missing.push('Country');
        if (!email) missing.push('Email');

        // Region is required for US, CA, AU
        if ((country === 'US' || country === 'CA' || country === 'AU') && !region) {
            missing.push('State/Region');
        }

        return missing;
    }

    highlightMissingFields(missingFields) {
        // Clear previous highlights
        document.querySelectorAll('.form-input').forEach(input => {
            input.style.borderColor = '#e0e0e0';
        });

        // Map field names to input IDs
        const fieldMap = {
            'First Name': 'first-name',
            'Last Name': 'last-name',
            'Address': 'address-line1',
            'City': 'city',
            'State/Region': 'region',
            'Postal Code': 'postal-code',
            'Country': 'country',
            'Email': 'email'
        };

        // Highlight missing fields in red
        missingFields.forEach(fieldName => {
            const inputId = fieldMap[fieldName];
            if (inputId) {
                const input = document.getElementById(inputId);
                if (input) {
                    input.style.borderColor = '#ff4444';
                }
            }
        });
    }

    getBillingAddress() {
        const firstName = document.getElementById('first-name')?.value.trim();
        const lastName = document.getElementById('last-name')?.value.trim();
        const addressLine1 = document.getElementById('address-line1')?.value.trim();
        const addressLine2 = document.getElementById('address-line2')?.value.trim();
        const city = document.getElementById('city')?.value.trim();
        const region = document.getElementById('region')?.value.trim();
        const postalCode = document.getElementById('postal-code')?.value.trim();
        const country = document.getElementById('country')?.value.trim();
        const email = document.getElementById('email')?.value?.trim();
        const phoneNumber = document.getElementById('phone')?.value?.trim() || '';

        // Validate required fields
        if (!firstName || !lastName || !addressLine1 || !city || !postalCode || !country || !email) {
            return null;
        }

        // Region is required for US, CA, AU
        if ((country === 'US' || country === 'CA' || country === 'AU') && !region) {
            return null;
        }

        return {
            firstName: firstName,
            lastName: lastName,
            addressLine1: addressLine1,
            addressLine2: addressLine2 || '',
            city: city,
            region: region || '',
            postalCode: postalCode,
            countryCode: country,
            email: email,
            phoneNumber: phoneNumber
        };
    }

    detectCardType(cardNumber) {
        // Remove spaces
        const cleanNumber = cardNumber.replace(/\s/g, '');

        // Detect card type by first digits
        if (cleanNumber.startsWith('4')) {
            return 'VISA';
        } else if (cleanNumber.startsWith('5')) {
            return 'MASTERCARD';
        } else if (cleanNumber.startsWith('34') || cleanNumber.startsWith('37')) {
            return 'AMEX';
        } else if (cleanNumber.startsWith('6')) {
            return 'VISA'; // Default for Discover/other
        }

        return 'VISA'; // Default fallback
    }

    getPayNowCreditCard() {
        const cardNumber = document.getElementById('paynow-card-number')?.value.trim().replace(/\s/g, '');
        const cardExpiry = document.getElementById('paynow-card-expiry')?.value.trim();
        const cardCvv = document.getElementById('paynow-card-cvv')?.value.trim();
        const cardName = document.getElementById('paynow-card-name')?.value.trim();

        // Validate required fields
        if (!cardNumber || !cardExpiry || !cardCvv || !cardName) {
            return null;
        }

        // Basic validation
        if (cardNumber.length < 13 || cardNumber.length > 19) {
            return null;
        }

        if (!/^\d{2}\/\d{2}$/.test(cardExpiry)) {
            return null;
        }

        if (cardCvv.length < 3 || cardCvv.length > 4) {
            return null;
        }

        // Detect card type
        const cardType = this.detectCardType(cardNumber);

        return {
            cardNumber: cardNumber,
            expiryDate: cardExpiry,
            cvv: cardCvv,
            cardholderName: cardName,
            cardType: cardType
        };
    }

    getPayNowBillingAddress() {
        const firstName = document.getElementById('paynow-first-name')?.value.trim();
        const lastName = document.getElementById('paynow-last-name')?.value.trim();
        const addressLine1 = document.getElementById('paynow-address-line1')?.value.trim();
        const addressLine2 = document.getElementById('paynow-address-line2')?.value.trim();
        const city = document.getElementById('paynow-city')?.value.trim();
        const region = document.getElementById('paynow-region')?.value.trim();
        const postalCode = document.getElementById('paynow-postal-code')?.value.trim();
        const country = document.getElementById('paynow-country')?.value.trim();
        const email = document.getElementById('paynow-email')?.value?.trim();
        const phoneNumber = document.getElementById('paynow-phone')?.value?.trim() || '';

        // Validate required fields
        if (!firstName || !lastName || !addressLine1 || !city || !postalCode || !country || !email) {
            return null;
        }

        // Region is required for US, CA, AU
        if ((country === 'US' || country === 'CA' || country === 'AU') && !region) {
            return null;
        }

        return {
            firstName: firstName,
            lastName: lastName,
            addressLine1: addressLine1,
            addressLine2: addressLine2 || '',
            city: city,
            region: region || '',
            postalCode: postalCode,
            countryCode: country,
            email: email,
            phoneNumber: phoneNumber
        };
    }

    updateCartDisplay() {
        const cartSummary = document.getElementById('cart-summary');
        const cartItems = document.getElementById('cart-items');
        const cartTotal = document.getElementById('cart-total');

        if (this.cart.length === 0) {
            cartSummary.style.display = 'none';
            return;
        }

        cartSummary.style.display = 'block';
        cartItems.innerHTML = '';

        let total = 0;
        this.cart.forEach(item => {
            item.totalPrice = item.unitPrice * item.quantity;
            total += item.totalPrice;

            const cartItem = document.createElement('div');
            cartItem.className = 'cart-item';
            cartItem.innerHTML = `
                <div class="cart-item-info">
                    <span class="cart-item-name">${item.productName} x${item.quantity}</span>
                    <span class="cart-item-price">$${item.totalPrice.toFixed(2)}</span>
                </div>
                <button class="cart-item-remove" onclick="chatClient.removeFromCart('${item.productId}')">
                    ✕
                </button>
            `;
            cartItems.appendChild(cartItem);
        });

        cartTotal.textContent = `$${total.toFixed(2)}`;
    }

    showCart() {
        if (this.cart.length === 0) {
            this.addMessage('Your cart is empty. Browse our products to get started!', 'bot');
            return;
        }

        let cartMessage = '<strong>Your Shopping Cart:</strong><ul>';
        let total = 0;

        this.cart.forEach(item => {
            cartMessage += `<li>${item.productName} x${item.quantity} - $${item.totalPrice.toFixed(2)}</li>`;
            total += item.totalPrice;
        });

        cartMessage += `</ul><strong>Total: $${total.toFixed(2)}</strong>`;
        cartMessage += '<p>Ready to checkout? Just say "checkout" or click the checkout button!</p>';

        this.addMessage(cartMessage, 'bot');
    }

    async handleCheckout() {
        if (this.cart.length === 0) {
            this.addMessage('Your cart is empty! Add some products first.', 'bot');
            return;
        }

        // Validate billing address with detailed error message
        const billingAddress = this.getBillingAddress();
        if (!billingAddress) {
            const missingFields = this.getMissingBillingFields();
            this.addMessage(`❌ Please fill in the following required fields: ${missingFields.join(', ')}`, 'bot');
            // Highlight missing fields
            this.highlightMissingFields(missingFields);
            return;
        }

        // Get selected payment method
        const paymentMethodSelect = document.getElementById('payment-method');
        const selectedPaymentMethod = paymentMethodSelect ? paymentMethodSelect.value : 'VISA';

        this.addMessage(`Processing your order with ${selectedPaymentMethod}...`, 'bot');

        // Checkout requires the 'poppy:write' scope; wait for the user to complete the consent modal
        try {
            await ensurePoppyAccessToken('poppy:write');
        } catch (authError) {
            console.error("Poppy Agent: Authorization failed.", authError);
            this.addMessage(`❌ Checkout cancelled: ${authError.message}`, 'bot');
            return;
        }

        try {
            let result;
            let protocol;

            if (this.shouldUseA2A()) {
                // Use A2A protocol
                protocol = 'A2A';
                console.log('Checkout using A2A protocol');
                result = await this.checkoutViaA2A(selectedPaymentMethod, billingAddress);
            } else {
                // Use REST protocol
                protocol = 'REST';
                console.log('Checkout using REST protocol');
                result = await this.checkoutViaREST(selectedPaymentMethod, billingAddress);
            }

            // Result already logged by checkoutViaREST or checkoutViaA2A

            if (result.status === 'success') {
                this.showOrderConfirmation(result);
                this.cart = [];
                this.updateCartDisplay();
            } else {
                // Format error message with error code if available
                let errorMsg = `❌ Payment Failed: ${result.message}`;
                if (result.errorCode) {
                    errorMsg += ` (Error Code: ${result.errorCode})`;
                }
                this.addMessage(errorMsg, 'bot');
                this.logUcpFlow('error', 'Payment Failed', result, protocol);
            }
        } catch (error) {
            console.error('Checkout error:', error);
            this.logUcpFlow('error', 'Checkout Error', { error: error.message });
            this.addMessage('❌ Payment Failed: ' + error.message, 'bot');
        }
    }

    async checkoutViaREST(selectedPaymentMethod, billingAddress) {
        // UCP-compliant 2-step checkout flow:
        // Step 1: Create checkout session
        // Step 2: Complete session with payment

        // Step 1: Create session with line items and buyer info
        const createSessionRequest = {
            line_items: this.cart.map(item => ({
                item: {
                    id: item.productId
                },
                quantity: item.quantity
            })),
            buyer: {
                first_name: billingAddress.firstName,
                last_name: billingAddress.lastName,
                email: billingAddress.email || 'customer@example.com',
                phone_number: billingAddress.phoneNumber || ''
            },
            context: {
                currency: 'USD',
                language: 'en',
                address_country: billingAddress.countryCode || 'US'
            }
        };

        this.logUcpFlow('request', 'POST /checkout-sessions (Create)', {
            method: 'POST',
            endpoint: '/checkout-sessions',
            data: createSessionRequest
        }, 'UCP');

        const createResponse = await fetch(`${this.apiBase}/checkout-sessions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'UCP-Agent': 'UCP-POC-UI/1.0 (Web Browser)',
                ...poppyAuthHeaders()
            },
            body: JSON.stringify(createSessionRequest)
        });

        if (!createResponse.ok) {
            const error = await createResponse.json();
            throw new Error(error.error?.message || error.error_description || 'Failed to create checkout session');
        }

        const createResult = await createResponse.json();
        this.logUcpFlow('response', 'Session Created', createResult, 'UCP');

        // Extract session ID from UCP envelope
        const sessionId = createResult.id;
        console.log('Created session:', sessionId, 'Status:', createResult.status);

        // Step 2: Complete session with payment and fulfillment
        const completeRequest = {
            buyer: {
                first_name: billingAddress.firstName,
                last_name: billingAddress.lastName,
                email: billingAddress.email || 'customer@example.com',
                phone_number: billingAddress.phoneNumber || ''
            },
            payment: {
                instruments: [{
                    id: 'pm_' + Date.now(),
                    handler_id: '8c9202bd-63cc-4241-8d24-d57ce69ea31c', // From discovery
                    type: 'card',
                    selected: true,
                    credential: {
                        type: 'PAYMENT_GATEWAY',
                        token: 'tok_' + Math.random().toString(36).substring(2, 15)
                    },
                    billing_address: {
                        street_address: billingAddress.addressLine1,
                        address_locality: billingAddress.city,
                        address_region: billingAddress.region || '',
                        postal_code: billingAddress.postalCode,
                        address_country: billingAddress.countryCode || 'US'
                    },
                    display: {
                        brand: selectedPaymentMethod.toLowerCase(),
                        last_digits: '4242'
                    }
                }]
            },
            fulfillment: {
                methods: [{
                    type: 'shipping',
                    selected: true,
                    destinations: [{
                        first_name: billingAddress.firstName,
                        last_name: billingAddress.lastName,
                        street_address: billingAddress.addressLine1,
                        address_locality: billingAddress.city,
                        address_region: billingAddress.region || '',
                        postal_code: billingAddress.postalCode,
                        address_country: billingAddress.countryCode || 'US',
                        phone_number: billingAddress.phoneNumber || ''
                    }]
                }]
            }
        };

        this.logUcpFlow('request', `POST /checkout-sessions/${sessionId}/complete`, {
            method: 'POST',
            endpoint: `/checkout-sessions/${sessionId}/complete`,
            data: completeRequest
        }, 'UCP');

        const completeResponse = await fetch(`${this.apiBase}/checkout-sessions/${sessionId}/complete`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'UCP-Agent': 'UCP-POC-UI/1.0 (Web Browser)',
                ...poppyAuthHeaders()
            },
            body: JSON.stringify(completeRequest)
        });

        if (!completeResponse.ok) {
            const error = await completeResponse.json();
            throw new Error(error.error?.message || error.error_description || 'Failed to complete checkout');
        }

        const completeResult = await completeResponse.json();
        this.logUcpFlow('response', 'Session Completed', completeResult, 'UCP');

        console.log('Checkout completed:', completeResult);

        // Transform UCP response to expected format
        if (completeResult.status === 'completed' && completeResult.order) {
            // Convert cents to dollars for display
            const totalInCents = completeResult.totals?.find(t => t.type === 'total')?.amount || 0;
            const totalInDollars = totalInCents / 100;

            return {
                status: 'success',
                message: 'Order completed successfully',
                orderId: completeResult.order.id,
                orderNumber: completeResult.order.label,
                orderUrl: completeResult.order.permalink_url,
                paymentId: completeResult.payment?.id || 'N/A',
                transactionId: completeResult.payment?.transaction_id || 'N/A',
                orderStatus: completeResult.status || 'completed',
                total: totalInDollars.toFixed(2),
                currency: completeResult.currency || 'USD'
            };
        } else {
            // Handle error
            const errorMessage = completeResult.messages?.find(m => m.level === 'error')?.text || 'Checkout failed';
            return {
                status: 'error',
                message: errorMessage,
                errorCode: completeResult.error?.code || 'UNKNOWN'
            };
        }
    }

    async checkoutViaA2A(selectedPaymentMethod, billingAddress) {
        let cartId;
        let response;
        let rpcResponse;

        // Use existing server cart if available
        if (this.serverCartId) {
            console.log('Using existing server cart:', this.serverCartId);
            cartId = this.serverCartId;
        } else {
            console.log('Creating new cart for checkout (fallback)');
            // 1. Create cart
            const createCartRequest = {
                jsonrpc: '2.0',
                method: 'cart.create',
                params: {
                    customerId: this.customerId
                },
                id: 'create-cart-' + Date.now()
            };

            this.logUcpFlow('request', 'A2A: cart.create', createCartRequest, 'A2A');

            response = await fetch(`${this.apiBase}/a2a/rpc`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(createCartRequest)
            });
            rpcResponse = await response.json();

            if (rpcResponse.error) throw new Error(rpcResponse.error.message);

            cartId = rpcResponse.result.cartId;
            this.logUcpFlow('response', 'Cart Created', rpcResponse.result, 'A2A');

            // 2. Add all items to cart (only if creating new cart)
            for (const item of this.cart) {
                const addItemRequest = {
                    jsonrpc: '2.0',
                    method: 'cart.add',
                    params: {
                        cartId: cartId,
                        productId: item.productId,
                        quantity: item.quantity
                    },
                    id: 'add-item-' + Date.now()
                };

                this.logUcpFlow('request', 'A2A: cart.add', addItemRequest, 'A2A');

                response = await fetch(`${this.apiBase}/a2a/rpc`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(addItemRequest)
                });
                rpcResponse = await response.json();

                if (rpcResponse.error) throw new Error(rpcResponse.error.message);

                this.logUcpFlow('response', 'Item Added', rpcResponse.result, 'A2A');
            }
        }

        // 3. Process checkout
        const checkoutRequest = {
            jsonrpc: '2.0',
            method: 'checkout.process',
            params: {
                cartId: cartId,
                paymentMethod: selectedPaymentMethod,
                billingAddress: billingAddress,
                shippingMethod: 'standard'
            },
            id: 'checkout-' + Date.now()
        };

        this.logUcpFlow('request', 'A2A: checkout.process', checkoutRequest, 'A2A');

        response = await fetch(`${this.apiBase}/a2a/rpc`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(checkoutRequest)
        });
        rpcResponse = await response.json();

        // Handle JSON-RPC error response
        if (rpcResponse.error) {
            this.logUcpFlow('error', 'A2A Checkout Error', rpcResponse, 'A2A');

            // Convert JSON-RPC error to REST-compatible format
            return {
                status: 'error',
                message: rpcResponse.error.message,
                errorCode: rpcResponse.error.code,
                errorData: rpcResponse.error.data
            };
        }

        // Clear server cart ID after successful checkout
        this.serverCartId = null;

        this.logUcpFlow('response', 'A2A Checkout Success', rpcResponse.result, 'A2A');

        // Convert A2A response to REST format
        return {
            status: 'success',
            orderId: rpcResponse.result.orderId,
            paymentId: rpcResponse.result.paymentId,
            total: rpcResponse.result.total,
            currency: rpcResponse.result.currency,
            transactionId: rpcResponse.result.transactionId,
            orderStatus: rpcResponse.result.status,
            paymentStatus: 'CAPTURED',
            message: 'Checkout completed successfully'
        };
    }


    async payNow(product) {
        // Show billing address, credit card form, and payment method selection in chat
        const priceDisplay = this.formatPrice(product);
        const payNowHtml = `
            <div class="pay-now-form">
                <p><strong>Quick Checkout: ${product.name} ($${priceDisplay})</strong></p>

                <div class="inline-billing-form">
                    <h4>Billing Address</h4>
                    <div class="form-row-inline">
                        <input type="text" id="paynow-first-name" class="form-input-inline" placeholder="First Name *" required>
                        <input type="text" id="paynow-last-name" class="form-input-inline" placeholder="Last Name *" required>
                    </div>
                    <input type="text" id="paynow-address-line1" class="form-input-inline" placeholder="Address *" required>
                    <input type="text" id="paynow-address-line2" class="form-input-inline" placeholder="Apt, suite, etc. (optional)">
                    <div class="form-row-inline">
                        <input type="text" id="paynow-city" class="form-input-inline" placeholder="City *" required>
                        <input type="text" id="paynow-region" class="form-input-inline" placeholder="State/Region">
                    </div>
                    <div class="form-row-inline">
                        <input type="text" id="paynow-postal-code" class="form-input-inline" placeholder="Postal Code *" required>
                        <select id="paynow-country" class="form-input-inline" required>
                            <option value="">Country *</option>
                            <option value="US">United States</option>
                            <option value="GB">United Kingdom</option>
                            <option value="ES">Spain</option>
                            <option value="FR">France</option>
                            <option value="DE">Germany</option>
                            <option value="IT">Italy</option>
                            <option value="CA">Canada</option>
                            <option value="MX">Mexico</option>
                            <option value="BR">Brazil</option>
                            <option value="AU">Australia</option>
                            <option value="JP">Japan</option>
                            <option value="CN">China</option>
                            <option value="IN">India</option>
                        </select>
                    </div>
                    <input type="email" id="paynow-email" class="form-input-inline" placeholder="Email *" required>
                    <input type="tel" id="paynow-phone" class="form-input-inline" placeholder="Phone Number">
                </div>

                <div class="inline-card-form">
                    <h4>Credit Card Details</h4>
                    <input type="text" id="paynow-card-number" class="form-input-inline" placeholder="Card Number * (e.g., 4111 1111 1111 1111)" maxlength="19" required>
                    <div class="form-row-inline">
                        <input type="text" id="paynow-card-expiry" class="form-input-inline" placeholder="Expiry (MM/YY) *" maxlength="5" required>
                        <input type="text" id="paynow-card-cvv" class="form-input-inline" placeholder="CVV *" maxlength="4" required>
                    </div>
                    <input type="text" id="paynow-card-name" class="form-input-inline" placeholder="Cardholder Name *" required>
                </div>

                <button class="complete-purchase-btn" onclick="chatClient.processPayNow('${product.id}')">
                    💳 Complete Purchase
                </button>
            </div>
        `;

        this.addMessage(payNowHtml, 'bot');

        // Add input formatting after form is rendered
        setTimeout(() => {
            this.setupCardFormatting();
        }, 100);
    }

    setupCardFormatting() {
        const cardNumberInput = document.getElementById('paynow-card-number');
        const cardExpiryInput = document.getElementById('paynow-card-expiry');
        const cardCvvInput = document.getElementById('paynow-card-cvv');

        if (cardNumberInput) {
            cardNumberInput.addEventListener('input', (e) => {
                let value = e.target.value.replace(/\s/g, '');
                let formattedValue = value.match(/.{1,4}/g)?.join(' ') || value;
                e.target.value = formattedValue;
            });
        }

        if (cardExpiryInput) {
            cardExpiryInput.addEventListener('input', (e) => {
                let value = e.target.value.replace(/\D/g, '');
                if (value.length >= 2) {
                    value = value.slice(0, 2) + '/' + value.slice(2, 4);
                }
                e.target.value = value;
            });
        }

        if (cardCvvInput) {
            cardCvvInput.addEventListener('input', (e) => {
                e.target.value = e.target.value.replace(/\D/g, '');
            });
        }
    }

    async processPayNow(productId) {
        const product = this.products.find(p => p.id === productId);
        if (!product) {
            this.addMessage('Product not found.', 'bot');
            return;
        }

        // Get billing address from Pay Now form
        const billingAddress = this.getPayNowBillingAddress();
        if (!billingAddress) {
            this.addMessage('❌ Please fill in all required billing address fields.', 'bot');
            return;
        }

        // Get credit card details from Pay Now form
        const creditCard = this.getPayNowCreditCard();
        if (!creditCard) {
            this.addMessage('❌ Please fill in all required credit card fields correctly.', 'bot');
            return;
        }

        // Use detected card type as payment method
        const paymentMethod = creditCard.cardType;

        this.addMessage(`Processing instant purchase for ${product.name} with ${paymentMethod}...`, 'bot');

        // Pay Now creates a checkout session, which requires the 'poppy:write' scope
        try {
            await ensurePoppyAccessToken('poppy:write');
        } catch (authError) {
            console.error("Poppy Agent: Authorization failed.", authError);
            this.addMessage(`❌ Purchase cancelled: ${authError.message}`, 'bot');
            return;
        }

        try {
            let result;
            let protocol;

            // Ensure protocol info is loaded
            if (!this.protocolInfo) {
                throw new Error('Protocol configuration not available');
            }

            if (this.shouldUseA2A()) {
                // Use A2A protocol
                protocol = 'A2A';
                console.log('Pay Now using A2A protocol');

                // 1. Create cart
                const createCartRequest = {
                    jsonrpc: '2.0',
                    method: 'cart.create',
                    params: {
                        customerId: this.customerId
                    },
                    id: 'paynow-cart-' + Date.now()
                };

                this.logUcpFlow('request', 'A2A: cart.create (Pay Now)', createCartRequest, 'A2A');

                let response = await fetch(`${this.apiBase}/a2a/rpc`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(createCartRequest)
                });
                let rpcResponse = await response.json();

                if (rpcResponse.error) {
                    console.error('Cart creation failed:', rpcResponse.error);
                    throw new Error('Cart creation failed: ' + rpcResponse.error.message);
                }

                const cartId = rpcResponse.result.cartId;
                console.log('Pay Now: Created cart:', cartId);
                this.logUcpFlow('response', 'Cart Created', rpcResponse.result, 'A2A');

                // 2. Add item to cart
                const addItemRequest = {
                    jsonrpc: '2.0',
                    method: 'cart.add',
                    params: {
                        cartId: cartId,
                        productId: product.id,
                        quantity: 1
                    },
                    id: 'paynow-add-' + Date.now()
                };

                this.logUcpFlow('request', 'A2A: cart.add (Pay Now)', addItemRequest, 'A2A');

                response = await fetch(`${this.apiBase}/a2a/rpc`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(addItemRequest)
                });
                rpcResponse = await response.json();

                if (rpcResponse.error) {
                    console.error('Add item failed:', rpcResponse.error);
                    throw new Error('Add item failed: ' + rpcResponse.error.message);
                }

                console.log('Pay Now: Added item to cart:', cartId);
                this.logUcpFlow('response', 'Item Added', rpcResponse.result, 'A2A');

                // 3. Process checkout
                const checkoutRequest = {
                    jsonrpc: '2.0',
                    method: 'checkout.process',
                    params: {
                        cartId: cartId,
                        paymentMethod: paymentMethod,
                        billingAddress: billingAddress,
                        shippingMethod: 'standard'
                    },
                    id: 'paynow-checkout-' + Date.now()
                };

                console.log('Pay Now: Processing checkout with cart:', cartId);
                this.logUcpFlow('request', 'A2A: checkout.process (Pay Now)', checkoutRequest, 'A2A');

                response = await fetch(`${this.apiBase}/a2a/rpc`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(checkoutRequest)
                });
                rpcResponse = await response.json();

                if (rpcResponse.error) {
                    console.error('Checkout failed:', rpcResponse.error);
                    throw new Error('Checkout failed: ' + rpcResponse.error.message);
                }

                // Convert A2A response to common format
                result = {
                    status: 'success',
                    orderId: rpcResponse.result.orderId,
                    paymentId: rpcResponse.result.paymentId,
                    total: rpcResponse.result.total,
                    currency: rpcResponse.result.currency,
                    transactionId: rpcResponse.result.transactionId,
                    orderStatus: rpcResponse.result.status,
                    paymentStatus: 'CAPTURED',
                    message: 'Checkout completed successfully'
                };
            } else {
                // Use UCP Checkout Session protocol (REST)
                protocol = 'UCP';
                console.log('Pay Now using UCP Checkout Session');

                // Create a temporary cart with just this product
                const tempCart = [{
                    productId: product.id,
                    productName: product.name,
                    quantity: 1,
                    unitPrice: this.extractPrice(product),
                    totalPrice: this.extractPrice(product),
                    currency: product.price?.currency || 'USD'
                }];

                // Temporarily store current cart and use temp cart
                const originalCart = this.cart;
                this.cart = tempCart;

                try {
                    // Use the same checkout flow as normal checkout
                    result = await this.checkoutViaREST(paymentMethod, billingAddress);
                } finally {
                    // Restore original cart
                    this.cart = originalCart;
                }
            }

            // Log response
            this.logUcpFlow('response', 'Pay Now Response', result, protocol);

            if (result.status === 'success') {
                this.showOrderConfirmation(result);
            } else {
                this.addMessage(`❌ Payment Failed: ${result.message}`, 'bot');
                this.logUcpFlow('error', 'Payment Failed', result, protocol);
            }
        } catch (error) {
            console.error('Pay now error:', error);
            this.logUcpFlow('error', 'Pay Now Error', { error: error.message });
            this.addMessage('❌ Payment Failed: ' + error.message, 'bot');
        }
    }

    showOrderConfirmation(orderData) {
        const confirmationHtml = `
            <div class="order-summary">
                <div class="order-summary-header">✅ Order Confirmed!</div>
                <div class="order-detail">
                    <span>Order ID:</span>
                    <span><strong>${orderData.orderId}</strong></span>
                </div>
                <div class="order-detail">
                    <span>Payment ID:</span>
                    <span>${orderData.paymentId}</span>
                </div>
                <div class="order-detail">
                    <span>Transaction ID:</span>
                    <span>${orderData.transactionId}</span>
                </div>
                <div class="order-detail">
                    <span>Total:</span>
                    <span><strong>$${orderData.total}</strong></span>
                </div>
                <div class="order-detail">
                    <span>Status:</span>
                    <span>${orderData.orderStatus}</span>
                </div>
                <p style="margin-top: 10px; font-size: 0.9em;">
                    Your order has been confirmed and payment has been processed.
                    Save your order ID to check status later!
                </p>
            </div>
        `;

        this.addMessage(confirmationHtml, 'bot');
    }

    async checkOrderStatus(orderId) {
        try {
            let order;
            let protocol;

            if (!this.protocolInfo) {
                throw new Error('Protocol configuration not available');
            }

            if (this.shouldUseA2A()) {
                // Use A2A endpoint
                protocol = 'A2A';
                console.log('Checking order status using A2A protocol');

                const rpcRequest = {
                    jsonrpc: '2.0',
                    method: 'order.get',
                    params: {
                        orderId: orderId
                    },
                    id: 'order-status-' + Date.now()
                };

                this.logUcpFlow('request', 'A2A: order.get', rpcRequest, 'A2A');

                const response = await fetch(`${this.apiBase}/a2a/rpc`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(rpcRequest)
                });

                const rpcResponse = await response.json();

                if (rpcResponse.error) {
                    this.addMessage('Order not found. Please check your order ID.', 'bot');
                    return;
                }

                order = rpcResponse.result.order;
            } else {
                // Use REST endpoint
                protocol = 'REST';
                console.log('Checking order status using REST protocol');

                this.logUcpFlow('request', 'GET /ucp/orders/' + orderId, {
                    method: 'GET',
                    endpoint: '/ucp/orders/' + orderId
                }, 'REST');

                await ensurePoppyAccessToken('poppy:read');
                const response = await fetch(`${this.apiBase}/ucp/orders/${orderId}`, {
                    headers: poppyAuthHeaders()
                });

                if (response.status === 401 || response.status === 403) {
                    const authError = await response.json();
                    this.logUcpFlow('error', `Poppy authorization failed (${response.status})`, authError, protocol);
                    this.addMessage(`❌ Not authorized: ${authError.error_description || authError.error}`, 'bot');
                    return;
                }

                if (response.ok) {
                    const ucpResponse = await response.json();
                    this.logUcpFlow('response', 'Order Details', ucpResponse, protocol);

                    // Extract order from UCP envelope
                    order = {
                        orderId: ucpResponse.id,
                        orderNumber: ucpResponse.order_number,
                        status: ucpResponse.status,
                        subtotal: ((ucpResponse.totals?.find(t => t.type === 'subtotal')?.amount || 0) / 100).toFixed(2),
                        tax: ((ucpResponse.totals?.find(t => t.type === 'tax')?.amount || 0) / 100).toFixed(2),
                        total: ((ucpResponse.totals?.find(t => t.type === 'total')?.amount || 0) / 100).toFixed(2),
                        items: (ucpResponse.line_items || []).map(item => ({
                            productName: item.name || item.description || 'Unknown',
                            quantity: item.quantity || 0,
                            totalPrice: ((item.total_price || 0) / 100).toFixed(2)
                        })),
                        payment: ucpResponse.payment ? {
                            id: ucpResponse.payment.id,
                            status: ucpResponse.payment.status,
                            transactionId: ucpResponse.payment.transaction_id
                        } : null
                    };
                } else {
                    this.addMessage('Order not found. Please check your order ID.', 'bot');
                    return;
                }
            }

            this.showOrderDetails(order);

        } catch (error) {
            console.error('Error checking order:', error);
            this.addMessage('Failed to retrieve order status: ' + error.message, 'bot');
        }
    }

    showOrderDetails(order) {
        let orderHtml = `
            <div class="order-summary">
                <div class="order-summary-header">Order Details</div>
                <div class="order-detail">
                    <span>Order ID:</span>
                    <span><strong>${order.orderId}</strong></span>
                </div>
                <div class="order-detail">
                    <span>Status:</span>
                    <span>${order.status}</span>
                </div>
                <div class="order-detail">
                    <span>Subtotal:</span>
                    <span>$${order.subtotal}</span>
                </div>
                <div class="order-detail">
                    <span>Tax:</span>
                    <span>$${order.tax}</span>
                </div>
                <div class="order-detail">
                    <span>Total:</span>
                    <span><strong>$${order.total}</strong></span>
                </div>`;

        // Add payment info if available
        if (order.payment) {
            orderHtml += `
                <div style="margin-top: 10px; padding-top: 10px; border-top: 1px solid #eee;">
                    <strong>Payment Information:</strong>
                </div>
                <div class="order-detail">
                    <span>Payment ID:</span>
                    <span>${order.payment.id || 'N/A'}</span>
                </div>
                <div class="order-detail">
                    <span>Payment Status:</span>
                    <span>${order.payment.status || 'N/A'}</span>
                </div>
                <div class="order-detail">
                    <span>Transaction ID:</span>
                    <span>${order.payment.transactionId || 'N/A'}</span>
                </div>`;
        }

        orderHtml += `
                <div style="margin-top: 10px;">
                    <strong>Items:</strong>
                    <ul>
        `;

        if (order.items && order.items.length > 0) {
            order.items.forEach(item => {
                orderHtml += `<li>${item.productName} x${item.quantity} - $${item.totalPrice}</li>`;
            });
        } else {
            orderHtml += `<li>No items found</li>`;
        }

        orderHtml += `
                    </ul>
                </div>
            </div>
        `;

        this.addMessage(orderHtml, 'bot');
    }

    showHelp() {
        const helpMessage = `
            <strong>Here's what I can help you with:</strong>
            <ul>
                <li><strong>"Show me all products"</strong> - Browse the catalog</li>
                <li><strong>"Hide catalog"</strong> / <strong>"Show catalog"</strong> - Toggle sidebar</li>
                <li><strong>"I want to buy [product name]"</strong> - Add items to cart</li>
                <li><strong>"Show my cart"</strong> - View shopping cart</li>
                <li><strong>"Remove [product name]"</strong> - Remove specific item from cart</li>
                <li><strong>"Empty cart"</strong> or <strong>"Clear cart"</strong> - Remove all items</li>
                <li><strong>"Checkout"</strong> - Complete your purchase</li>
                <li><strong>"[order-id]"</strong> - Check order status</li>
            </ul>
            <p><strong>Tip:</strong> Press Ctrl+B (or Cmd+B) to quickly toggle the product catalog!</p>
            <p>You can also click "Add to Cart" on any product in the sidebar!</p>
        `;
        this.addMessage(helpMessage, 'bot');
    }

    handleGeneralQuery(message) {
        const responses = [
            "I'm here to help you shop! Try browsing our products or adding items to your cart.",
            "Not sure what you're looking for? Say 'show products' to see our catalog!",
            "I can help you find products, manage your cart, and checkout. What would you like to do?",
            "Need help? Just say 'help' to see what I can do!"
        ];

        const randomResponse = responses[Math.floor(Math.random() * responses.length)];
        this.addMessage(randomResponse, 'bot');
    }

    addMessage(content, sender) {
        const chatMessages = document.getElementById('chat-messages');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender}-message`;

        const avatar = sender === 'user' ? '👤' : '🤖';

        messageDiv.innerHTML = `
            <div class="message-avatar">${avatar}</div>
            <div class="message-content">${content}</div>
        `;

        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    showError(message) {
        this.addMessage(`❌ Error: ${message}`, 'bot');
    }
}

// --- GLOBAL POPPY PROTOCOL AGENT STATE ---
let poppyConfig = {
    discovered: false,
    endpoints: {
        authAuthorize: null,
        authToken: null
    },
    accessToken: null,
    grantedScopes: [],
    userEmail: null
};

// 1. DYNAMIC DISCOVERY LIFECYCLE
async function discoverPoppyCapabilities() {
    try {
        const response = await fetch('/.well-known/poppy.json');
        if (!response.ok) throw new Error("Failed to load manifest");

        const manifest = await response.json();

        // Extract OAuth endpoints natively from the poppy.json layout
        poppyConfig.endpoints.authAuthorize = manifest.authentication.authorization_endpoint;
        poppyConfig.endpoints.authToken = manifest.authentication.token_endpoint;

        poppyConfig.discovered = true;
        console.log("Poppy Agent: System initialized in Guest mode.");
    } catch (error) {
        console.error("Poppy Agent Error: Fallback to default OAuth endpoints.", error);
    }
}

// 2. TRIGGER INTERACTIVE OAUTH CONTEXT
function initiateOAuthLoginFlow(requiredScope) {
    return new Promise((resolve, reject) => {
        const modal = document.getElementById('poppy-oauth-modal');
        const emailInput = document.getElementById('poppy-login-email');
        if (!modal || !emailInput) return reject(new Error("Modal elements missing from DOM"));

        const requiredScopeNotice = document.getElementById('poppy-required-scope');
        if (requiredScopeNotice) {
            requiredScopeNotice.textContent = `This action requires the '${requiredScope}' scope.`;
        }
        emailInput.value = poppyConfig.userEmail || '';
        modal.style.display = 'flex'; // Display the UI consent interface

        // Handle structural approval action
        document.getElementById('poppy-auth-approve').onclick = async () => {
            const email = emailInput.value.trim();
            if (!email) {
                emailInput.focus();
                return;
            }
            modal.style.display = 'none';
            try {
                // Step A: Hit /auth/authorize with the account email; the server decides which scopes it can grant
                const authUrl = `${poppyConfig.endpoints.authAuthorize || '/auth/authorize'}?response_type=code&client_id=personal-agent-frontend&redirect_uri=${encodeURIComponent(window.location.origin)}&scope=poppy:read%20poppy:write&state=xyz123&login_hint=${encodeURIComponent(email)}`;

                const authResponse = await fetch(authUrl);
                // The MockAuthController sends a RedirectView, parse the target URL query params
                const redirectedUrl = new URL(authResponse.url);
                const authError = redirectedUrl.searchParams.get('error');
                if (authError) {
                    throw new Error(redirectedUrl.searchParams.get('error_description') || authError);
                }
                const code = redirectedUrl.searchParams.get('code');

                if (!code) throw new Error("OAuth authorization code was not returned by server");

                // Step B: Exchange the mock authorization code for a Bearer token via POST
                const params = new URLSearchParams();
                params.append('grant_type', 'authorization_code');
                params.append('code', code);
                params.append('redirect_uri', window.location.origin);
                params.append('client_id', 'personal-agent-frontend');

                const tokenResponse = await fetch(poppyConfig.endpoints.authToken || '/auth/token', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: params
                });

                if (!tokenResponse.ok) throw new Error("Token exchange failed");

                const tokenData = await tokenResponse.json();
                poppyConfig.accessToken = tokenData.access_token;
                poppyConfig.grantedScopes = (tokenData.scope || '').split(' ').filter(Boolean);
                poppyConfig.userEmail = tokenData.email || email;
                console.log(`Poppy Agent: ${poppyConfig.userEmail} granted scopes [${poppyConfig.grantedScopes.join(', ')}].`);
                resolve(true);

            } catch (err) {
                reject(err);
            }
        };

        // Handle cancellation action
        document.getElementById('poppy-auth-cancel').onclick = () => {
            modal.style.display = 'none';
            reject(new Error("User cancelled agent delegation request."));
        };
    });
}

// 3. SECURE ESCALATION: prompt the user for agent authorization if the token lacks the required scope
async function ensurePoppyAccessToken(requiredScope) {
    if (!poppyConfig.discovered) await discoverPoppyCapabilities();
    if (poppyConfig.accessToken && poppyConfig.grantedScopes.includes(requiredScope)) return;

    console.log(`Poppy Agent: Action requires '${requiredScope}' scope. Launching login challenge...`);
    await initiateOAuthLoginFlow(requiredScope);

    // The request is still sent: the store enforces scopes and answers 403 if the account lacks them
    if (!poppyConfig.grantedScopes.includes(requiredScope)) {
        console.warn(`Poppy Agent: ${poppyConfig.userEmail} did not grant '${requiredScope}'.`);
    }
}

function poppyAuthHeaders() {
    return poppyConfig.accessToken ? { 'Authorization': `Bearer ${poppyConfig.accessToken}` } : {};
}

// Initialize the chat client when the page loads
let chatClient;
document.addEventListener('DOMContentLoaded', () => {
    chatClient = new UcpChatClient();
    // Automatically initialize discovery routines when DOM components register
    discoverPoppyCapabilities();

    // Make region required for US, CA, AU
    const countrySelect = document.getElementById('country');
    const regionInput = document.getElementById('region');
    const regionRequired = document.getElementById('region-required');

    if (countrySelect && regionInput && regionRequired) {
        countrySelect.addEventListener('change', (e) => {
            const country = e.target.value;
            if (country === 'US' || country === 'CA' || country === 'AU') {
                regionInput.setAttribute('required', 'required');
                regionRequired.style.display = 'inline';
            } else {
                regionInput.removeAttribute('required');
                regionRequired.style.display = 'none';
            }
        });
    }
});
