const state = { products: [], cart: null, notifications: [], payments: [], myList: null, session: null, csrf: null, variantSelections: {} };
const petIcons = { FISH: '🐠', DOGS: '🐕', CATS: '🐈', BIRDS: '🦜', REPTILES: '🦎' };

document.addEventListener('DOMContentLoaded', async () => {
  document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => show(button.dataset.view)));
  document.querySelector('#search').addEventListener('input', renderProducts);
  document.querySelector('#category').addEventListener('change', renderProducts);
  document.querySelector('#close-checkout').addEventListener('click', () => document.querySelector('#checkout-dialog').close());
  document.querySelector('#checkout-form').addEventListener('submit', checkout);
  document.querySelector('#account-form').addEventListener('submit', saveAccount);
  await Promise.all([loadCatalog(), loadSession()]);
  if (isCustomer()) await Promise.all([loadCart(), loadNotifications(), loadMyList()]);
});

/** Calls an application API and attaches CSRF credentials to protected mutations. */
async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const publicRegistration = path === '/api/v1/accounts' && options.method === 'POST' && !state.session?.authenticated;
  if (options.method && options.method !== 'GET' && !publicRegistration) {
    if (!state.csrf) state.csrf = await fetch('/api/v1/csrf').then(r => r.json());
    headers[state.csrf.headerName] = state.csrf.token;
  }
  const response = await fetch(path, { credentials: 'same-origin', ...options, headers });
  if (response.status === 401 || response.redirected && response.url.includes('/login')) throw new Error('Please sign in to continue.');
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `Request failed (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}

/** Loads catalog from the API and refreshes the corresponding client state. */
async function loadCatalog() {
  state.products = await api('/api/v1/catalog/products');
  const categories = [...new Map(state.products.map(p => [p.categoryId, p.categoryName])).entries()];
  const categorySelect = document.querySelector('#category');
  const selectedCategory = categorySelect.value;
  categorySelect.innerHTML = '<option value="">All categories</option>' + categories.map(([id,name]) => `<option value="${escapeHtml(id)}">${escapeHtml(name)}</option>`).join('');
  if (categories.some(([id]) => id === selectedCategory)) categorySelect.value = selectedCategory;
  renderProducts();
}
/** Loads session from the API and refreshes the corresponding client state. */
async function loadSession() {
  state.session = await api('/api/v1/session').catch(() => ({ authenticated: false }));
  const operations = state.session.admin ? ' · <a class="login-link" href="/admin/orders.html">Approvals</a> · <a class="login-link" href="/admin/sales.html">Sales</a> · <a class="login-link" href="/admin/health.html">Health</a>' : '';
  document.querySelector('#session-actions').innerHTML = state.session.authenticated
    ? `<span>Hi, ${escapeHtml(state.session.username)}</span> · <button id="open-account" class="login-link nav-link">Account</button>${operations} · <button id="sign-out" class="login-link nav-link">Sign out</button>`
    : '<a class="login-link" href="/login">Sign in</a> · <button id="open-account" class="login-link nav-link">Create account</button>';
  document.querySelector('#sign-out')?.addEventListener('click', signOut);
  document.querySelector('#open-account')?.addEventListener('click', () => show('account'));
  document.querySelector('#my-list-nav').classList.toggle('hidden', !isCustomer());
}
/** Reports whether the current session belongs to a storefront customer. */
function isCustomer() { return state.session?.authenticated && !state.session.admin && !state.session.supplier; }
/** Performs CSRF-protected logout and returns to the anonymous storefront. */
async function signOut() {
  if (!state.csrf) state.csrf = await fetch('/api/v1/csrf').then(r => r.json());
  await fetch('/logout', { method: 'POST', credentials: 'same-origin', headers: { [state.csrf.headerName]: state.csrf.token } });
  location.href = '/';
}
/** Loads cart from the API and refreshes the corresponding client state. */
async function loadCart() { state.cart = await api('/api/v1/cart'); renderCart(); }

/** Renders products from the current client-side state. */
function renderProducts() {
  const query = document.querySelector('#search').value.toLowerCase().trim();
  const category = document.querySelector('#category').value;
  const groups = [...groupProducts(state.products).entries()]
    .filter(([, variants]) => (!category || variants[0].categoryId === category)
      && (!query || variants.some(p => `${p.variantName} ${p.name} ${p.description}`.toLowerCase().includes(query))));
  document.querySelector('#product-grid').innerHTML = groups.map(([groupId, variants]) => {
    const selected = variants.find(item => item.id === state.variantSelections[groupId]) || variants[0];
    state.variantSelections[groupId] = selected.id;
    const favorite = state.myList?.favorites.some(item => item.id === selected.id);
    const selector = variants.length > 1 ? `<label class="variant-picker">Choose item variant<select data-variant="${escapeHtml(groupId)}">
      ${variants.map(item => `<option value="${escapeHtml(item.id)}" ${item.id === selected.id ? 'selected' : ''}>${escapeHtml(item.variantName)} · ${money(item.price)} · ${item.stock} available</option>`).join('')}
    </select></label>` : '';
    return `<article class="product-card" data-product-group="${escapeHtml(groupId)}">
      <div class="pet-art" aria-hidden="true">${petIcons[selected.categoryId] || '🐾'}</div><div class="product-copy">
      <div class="product-meta"><span class="eyebrow">${escapeHtml(selected.categoryName)} · ${selected.stock} AVAILABLE</span>
      <button class="favorite-button ${favorite ? 'selected' : ''}" data-favorite-group="${escapeHtml(groupId)}" aria-label="${favorite ? 'Remove from' : 'Add to'} MyList">${favorite ? '♥' : '♡'}</button></div>
      <h3>${escapeHtml(selected.name)}</h3><p>${escapeHtml(selected.description)}</p>${selector}
      <div class="card-bottom"><span class="price">${money(selected.price)}</span>
      <button class="add-button" data-add-group="${escapeHtml(groupId)}">${selected.stock < 1 ? 'Backorder' : 'Add to cart'}</button></div></div></article>`;
  }).join('') || '<p class="empty">No pets match that search.</p>';
  document.querySelectorAll('[data-variant]').forEach(select => select.addEventListener('change', () => {
    state.variantSelections[select.dataset.variant] = select.value; renderProducts();
  }));
  document.querySelectorAll('[data-add-group]').forEach(button => button.addEventListener('click', () => addToCart(state.variantSelections[button.dataset.addGroup])));
  document.querySelectorAll('[data-favorite-group]').forEach(button => button.addEventListener('click', () => toggleFavorite(state.variantSelections[button.dataset.favoriteGroup])));
}

/** Loads my list from the API and refreshes the corresponding client state. */
async function loadMyList() {
  if (!isCustomer()) return;
  state.myList = await api('/api/v1/my-list');
  document.querySelector('#favorite-count').textContent = state.myList.favorites.length;
  renderMyList(); renderProducts();
}
/** Toggles favorite and reconciles the client with server state. */
async function toggleFavorite(itemId) {
  if (!isCustomer()) { location.href = '/login'; return; }
  const favorite = state.myList?.favorites.some(item => item.id === itemId);
  try {
    state.myList = await api(`/api/v1/my-list/items/${encodeURIComponent(itemId)}`, { method: favorite ? 'DELETE' : 'POST' });
    document.querySelector('#favorite-count').textContent = state.myList.favorites.length;
    renderMyList(); renderProducts(); toast(favorite ? 'Removed from MyList.' : 'Saved to MyList.');
  } catch (error) { toast(error.message, true); await loadMyList().catch(() => {}); }
}
/** Renders my list from the current client-side state. */
function renderMyList() {
  const panel = document.querySelector('#my-list-panel');
  if (!state.myList) { panel.innerHTML = '<p class="empty">Sign in to build your personal list.</p>'; return; }
  const preference = state.myList.enabled ? '' : '<p class="preference-note">Personal recommendations are paused. Enable “Keep a personal pet list” in Account to turn them back on; saved items are preserved.</p>';
  const favorites = state.myList.favorites.length ? itemCards(state.myList.favorites, true) : '<p class="empty">Use the heart on any catalogue item to save it here.</p>';
  const recommendations = state.myList.enabled
    ? (state.myList.recommendations.length ? itemCards(state.myList.recommendations, false) : '<p class="empty">Save an item to improve your recommendations.</p>') : '';
  panel.innerHTML = `${preference}<h3 class="list-heading">Your favourites</h3>${favorites}
    ${state.myList.enabled ? `<h3 class="list-heading">Recommended for you</h3>${recommendations}` : ''}`;
  panel.querySelectorAll('[data-list-add]').forEach(button => button.addEventListener('click', () => addToCart(button.dataset.listAdd)));
  panel.querySelectorAll('[data-list-favorite]').forEach(button => button.addEventListener('click', () => toggleFavorite(button.dataset.listFavorite)));
}
/** Builds reusable MyList and recommendation cards for a product collection. */
function itemCards(products, saved) {
  return `<div class="recommendation-grid">${products.map(product => `<article class="recommendation-card">
    <span class="recommendation-icon" aria-hidden="true">${petIcons[product.categoryId] || '🐾'}</span><div>
    <span class="eyebrow">${escapeHtml(product.categoryName)} · ${product.stock} AVAILABLE</span><h4>${escapeHtml(productDisplayName(product))}</h4>
    <p>${escapeHtml(product.description)}</p><strong>${money(product.price)}</strong></div><div class="recommendation-actions">
    <button class="favorite-button ${saved ? 'selected' : ''}" data-list-favorite="${escapeHtml(product.id)}" aria-label="${saved ? 'Remove from' : 'Add to'} MyList">${saved ? '♥' : '♡'}</button>
    <button class="add-button" data-list-add="${escapeHtml(product.id)}">${product.stock < 1 ? 'Backorder' : 'Add to cart'}</button></div></article>`).join('')}</div>`;
}
/** Adds a non-standard variant prefix to the base catalog name. */
function productDisplayName(product) { return !product.variantName || product.variantName === 'Standard' ? product.name : `${product.variantName} ${product.name}`; }
/** Groups independently stocked item variants under their shared product identifier. */
function groupProducts(products) {
  return products.reduce((groups, product) => {
    if (!groups.has(product.productGroupId)) groups.set(product.productGroupId, []);
    groups.get(product.productGroupId).push(product);
    return groups;
  }, new Map());
}

/** Adds to cart through the API and refreshes the view. */
async function addToCart(productId) {
  if (!state.session?.authenticated) { location.href = '/login'; return; }
  try {
    if (!state.cart) await loadCart();
    state.cart = await api('/api/v1/cart/items', { method: 'POST', body: JSON.stringify({ productId, quantity: 1, expectedVersion: state.cart.version }) });
    renderCart(); toast('Added to your cart.');
  } catch (error) { await recoverCart(error); }
}
/** Renders cart from the current client-side state. */
function renderCart() {
  const panel = document.querySelector('#cart-panel');
  if (!state.cart || !state.cart.lines.length) { panel.innerHTML = '<p class="empty">Your cart is ready for a new companion.</p>'; document.querySelector('#cart-count').textContent = '0'; return; }
  document.querySelector('#cart-count').textContent = state.cart.lines.reduce((sum,line) => sum + line.quantity, 0);
  panel.innerHTML = `<div class="cart-shell">${state.cart.lines.map(line => `<div class="cart-line"><div><strong>${escapeHtml(line.productName)}</strong><br><small>${money(line.unitPrice)} each</small></div>
    <label>Qty <input class="qty" type="number" min="1" max="99" value="${line.quantity}" data-qty="${escapeHtml(line.productId)}" aria-label="Quantity for ${escapeHtml(line.productName)}"></label>
    <button class="remove" data-remove="${escapeHtml(line.productId)}">Remove</button></div>`).join('')}
    <div class="cart-summary"><strong>Total ${money(state.cart.total)}</strong><button id="checkout" class="primary">Checkout</button></div></div>`;
  document.querySelectorAll('[data-qty]').forEach(input => input.addEventListener('change', () => updateQuantity(input.dataset.qty, Number(input.value))));
  document.querySelectorAll('[data-remove]').forEach(button => button.addEventListener('click', () => removeItem(button.dataset.remove)));
  document.querySelector('#checkout').addEventListener('click', () => document.querySelector('#checkout-dialog').showModal());
}
/** Updates quantity and refreshes the displayed state. */
async function updateQuantity(productId, quantity) {
  try { state.cart = await api(`/api/v1/cart/items/${encodeURIComponent(productId)}`, { method:'PUT', body:JSON.stringify({quantity, expectedVersion:state.cart.version}) }); renderCart(); }
  catch (error) { await recoverCart(error); }
}
/** Removes item through the API and refreshes the view. */
async function removeItem(productId) {
  try { state.cart = await api(`/api/v1/cart/items/${encodeURIComponent(productId)}?expectedVersion=${state.cart.version}`, { method:'DELETE' }); renderCart(); }
  catch (error) { await recoverCart(error); }
}
/** Reports a cart conflict and refreshes client state from the server. */
async function recoverCart(error) { toast(error.message, true); if (state.session?.authenticated) await loadCart().catch(() => {}); }

/** Submits one idempotent checkout request and reconciles catalog, cart, orders, and notifications. */
async function checkout(event) {
  event.preventDefault();
  const fields = Object.fromEntries(new FormData(event.currentTarget));
  const paymentToken = fields.paymentToken;
  delete fields.paymentToken;
  const address = fields;
  const idempotencyKey = crypto.randomUUID();
  const submit = event.currentTarget.querySelector('[type=submit]'); submit.disabled = true;
  try {
    const order = await api('/api/v1/orders', { method:'POST', headers:{'Idempotency-Key':idempotencyKey}, body:JSON.stringify({ expectedCartVersion:state.cart.version, address, paymentToken }) });
    const message = order.status === 'BACKORDERED'
      ? `Order ${order.id.slice(0,8)} is backordered and will resume automatically after replenishment.`
      : order.status === 'PENDING'
        ? `Order ${order.id.slice(0,8)} placed and is awaiting administrator approval.`
        : `Order ${order.id.slice(0,8)} placed. Supplier fulfilment started.`;
    document.querySelector('#checkout-dialog').close(); toast(message); await Promise.all([loadCatalog(), loadCart(), loadOrders()]); show('orders');
  } catch (error) { await recoverCart(error); } finally { submit.disabled = false; }
}
/** Loads orders from the API and refreshes the corresponding client state. */
async function loadOrders() {
  if (!state.session?.authenticated) return;
  const [orders, payments] = await Promise.all([api('/api/v1/orders'), api('/api/v1/payments'), loadNotifications()]);
  state.payments = payments;
  document.querySelector('#orders-panel').innerHTML = orders.map(order => {
    const payment = payments.find(value => value.orderId === order.id);
    const action = ['BACKORDERED','PENDING','APPROVED'].includes(order.status)
      ? `<button class="order-action cancel" data-order-action="cancel" data-order-id="${escapeHtml(order.id)}" data-order-version="${order.version}">Cancel order</button>`
      : order.status === 'COMPLETED' && payment?.status === 'CAPTURED'
        ? `<button class="order-action refund" data-order-action="refund" data-order-id="${escapeHtml(order.id)}" data-order-version="${order.version}">Request refund</button>` : '';
    return `<article class="order-card"><div><span class="eyebrow">${new Date(order.createdAt).toLocaleString()}</span><h3>Order ${escapeHtml(order.id.slice(0,8))}</h3></div><strong>${money(order.total)} · ${escapeHtml(order.status)}</strong>
    <div class="order-lines">${order.lines.map(line => `${escapeHtml(line.productName)} × ${line.quantity}`).join(' · ')}</div>
    <div class="payment-state">Payment: <strong>${escapeHtml(payment?.status || 'LEGACY / NOT RECORDED')}</strong>${payment ? ` · ${escapeHtml(payment.methodLabel)}` : ''}</div>
    ${action}${renderTimeline(order.id)}</article>`;
  }).join('') || '<p class="empty">No orders yet.</p>';
  document.querySelectorAll('[data-order-action]').forEach(button => button.addEventListener('click', () => customerOrderAction(button)));
}
/** Confirms and submits an idempotent customer cancellation or refund request. */
async function customerOrderAction(button) {
  const action = button.dataset.orderAction;
  const verb = action === 'cancel' ? 'cancel this order and void its authorization' : 'refund this completed order';
  if (!window.confirm(`Are you sure you want to ${verb}?`)) return;
  button.disabled = true;
  try {
    await api(`/api/v1/orders/${encodeURIComponent(button.dataset.orderId)}/${action}`, {
      method: 'POST', headers: {'Idempotency-Key': crypto.randomUUID()},
      body: JSON.stringify({ expectedVersion: Number(button.dataset.orderVersion),
        reason: action === 'cancel' ? 'Customer requested cancellation from storefront' : 'Customer requested refund from storefront' })
    });
    await Promise.all([loadCatalog(), loadOrders()]);
    toast(action === 'cancel' ? 'Order cancelled and payment authorization voided.' : 'Payment refunded.');
  } catch (error) { toast(error.message, true); await loadOrders().catch(() => {}); }
  finally { button.disabled = false; }
}
/** Loads notifications from the API and refreshes the corresponding client state. */
async function loadNotifications() {
  if (!state.session?.authenticated || state.session.admin || state.session.supplier) return [];
  state.notifications = await api('/api/v1/notifications');
  document.querySelector('#notification-count').textContent = state.notifications.filter(item => !item.readAt).length;
  renderNotifications();
  return state.notifications;
}
/** Renders notifications from the current client-side state. */
function renderNotifications() {
  const panel = document.querySelector('#notifications-panel');
  panel.innerHTML = state.notifications.map(item => `<article class="notification-card ${item.readAt ? '' : 'unread'}">
    <div class="notification-icon" aria-hidden="true">${notificationIcon(item.type)}</div>
    <div><span class="eyebrow">${new Date(item.createdAt).toLocaleString()} · ${escapeHtml(item.deliveryStatus)}</span>
      <h3>${escapeHtml(item.title)}</h3><p>${escapeHtml(item.message)}</p></div>
    ${item.readAt ? '<span class="read-label">Read</span>' : `<button class="mark-read" data-read="${escapeHtml(item.id)}" data-version="${item.version}">Mark read</button>`}
  </article>`).join('') || '<p class="empty">No order updates yet.</p>';
  document.querySelectorAll('[data-read]').forEach(button => button.addEventListener('click', () => markRead(button)));
}
/** Builds the ordered notification timeline for one customer order. */
function renderTimeline(orderId) {
  const events = state.notifications.filter(item => item.orderId === orderId).sort((a,b) => new Date(a.createdAt) - new Date(b.createdAt));
  if (!events.length) return '';
  return `<ol class="order-timeline">${events.map(item => `<li class="complete"><span>${notificationIcon(item.type)}</span><div><strong>${escapeHtml(item.title)}</strong><small>${new Date(item.createdAt).toLocaleString()}</small></div></li>`).join('')}</ol>`;
}
/** Marks a notification read with optimistic-lock recovery after a stale-version conflict. */
async function markRead(button) {
  const notificationId = button.dataset.read;
  button.disabled = true;
  try {
    try {
      await api(`/api/v1/notifications/${encodeURIComponent(notificationId)}/read`, {
        method: 'POST', body: JSON.stringify({ expectedVersion: Number(button.dataset.version) })
      });
    } catch (firstFailure) {
      const refreshed = await loadNotifications();
      const current = refreshed.find(item => item.id === notificationId);
      if (!current || current.readAt) return;
      await api(`/api/v1/notifications/${encodeURIComponent(notificationId)}/read`, {
        method: 'POST', body: JSON.stringify({ expectedVersion: current.version })
      });
    }
    await loadNotifications();
  } catch (error) {
    await loadNotifications().catch(() => {});
    toast(error.message, true);
  } finally { button.disabled = false; }
}
/** Maps a notification type to the icon shown in the customer timeline. */
function notificationIcon(type) {
  return ({ORDER_BACKORDERED:'↻', ORDER_INVENTORY_ALLOCATED:'✓', ORDER_PENDING:'⏳', ORDER_APPROVED:'✓', ORDER_DENIED:'×', ORDER_COMPLETED:'📦',
    ORDER_CANCELLED:'×', ORDER_REFUNDED:'↩', PAYMENT_AUTHORIZED:'💳', PAYMENT_CAPTURED:'✓', PAYMENT_VOIDED:'⊘', PAYMENT_REFUNDED:'↩'})[type] || '•';
}
/** Loads account from the API and refreshes the corresponding client state. */
async function loadAccount() {
  const form = document.querySelector('#account-form');
  const signedIn = state.session?.authenticated;
  document.querySelector('#account-title').textContent = signedIn ? 'Your account' : 'Create account';
  document.querySelector('#account-submit').textContent = signedIn ? 'Save profile' : 'Create account';
  document.querySelector('#username-field').classList.toggle('hidden', signedIn);
  document.querySelector('#password-field').classList.toggle('hidden', signedIn);
  form.elements.password.required = !signedIn;
  if (!signedIn) { form.reset(); return; }
  const account = await api('/api/v1/accounts/me');
  for (const [name, value] of Object.entries({...account, ...account.address})) {
    if (form.elements[name] && form.elements[name].type !== 'checkbox') form.elements[name].value = value ?? '';
  }
  form.elements.myListPreference.checked = account.myListPreference;
  form.elements.bannerPreference.checked = account.bannerPreference;
}
/** Validates and saves account through the API. */
async function saveAccount(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const fields = Object.fromEntries(new FormData(form));
  const body = {
    username: fields.username,
    password: fields.password,
    fullName: fields.fullName,
    email: fields.email,
    phone: fields.phone,
    preferredLanguage: fields.preferredLanguage,
    favoriteCategory: fields.favoriteCategory,
    myListPreference: form.elements.myListPreference.checked,
    bannerPreference: form.elements.bannerPreference.checked,
    address: { fullName: fields.fullName, line1: fields.line1, line2: fields.line2, city: fields.city,
      state: fields.state, postalCode: fields.postalCode, country: fields.country }
  };
  try {
    if (state.session?.authenticated) {
      delete body.username;
      delete body.password;
    }
    const account = await api(state.session?.authenticated ? '/api/v1/accounts/me' : '/api/v1/accounts',
      { method: state.session?.authenticated ? 'PUT' : 'POST', body: JSON.stringify(body) });
    if (!state.session?.authenticated) { toast(`Account ${account.username} created. Please sign in.`); location.href = '/login'; return; }
    toast('Account updated.'); await loadMyList();
  } catch (error) { toast(error.message, true); }
}
/** Switches the storefront to the requested view and loads any view-specific data. */
async function show(view) {
  if ((view === 'cart' || view === 'orders' || view === 'notifications' || view === 'my-list') && !state.session?.authenticated) { location.href='/login'; return; }
  if (view === 'cart') await loadCart().catch(error => toast(error.message,true));
  if (view === 'orders') await loadOrders().catch(error => toast(error.message,true));
  if (view === 'notifications') await loadNotifications().catch(error => toast(error.message,true));
  if (view === 'my-list') await loadMyList().catch(error => toast(error.message,true));
  if (view === 'account') await loadAccount().catch(error => toast(error.message,true));
  document.querySelectorAll('.view').forEach(section => section.classList.toggle('hidden', section.id !== view));
  document.querySelector(`#${view}`)?.scrollIntoView({ behavior:'smooth', block:'start' });
}
/** Displays a transient success or error message to the user. */
function toast(message, error=false) { const el=document.querySelector('#toast'); el.textContent=message; el.className=error?'show error':'show'; setTimeout(()=>el.className='',3500); }
/** Formats a numeric amount as a US-dollar price. */
function money(value) { return new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(value); }
/** Escapes untrusted text before it is inserted into generated HTML. */
function escapeHtml(value) { const span=document.createElement('span');span.textContent=String(value);return span.innerHTML; }
