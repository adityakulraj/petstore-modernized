const state = { products: [], cart: null, session: null, csrf: null };
const petIcons = { FISH: '🐠', DOGS: '🐕', CATS: '🐈', BIRDS: '🦜', REPTILES: '🦎' };

document.addEventListener('DOMContentLoaded', async () => {
  document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => show(button.dataset.view)));
  document.querySelector('#search').addEventListener('input', renderProducts);
  document.querySelector('#category').addEventListener('change', renderProducts);
  document.querySelector('#close-checkout').addEventListener('click', () => document.querySelector('#checkout-dialog').close());
  document.querySelector('#checkout-form').addEventListener('submit', checkout);
  await Promise.all([loadCatalog(), loadSession()]);
  if (state.session?.authenticated) await loadCart();
});

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (options.method && options.method !== 'GET') {
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

async function loadCatalog() {
  state.products = await api('/api/v1/catalog/products');
  const categories = [...new Map(state.products.map(p => [p.categoryId, p.categoryName])).entries()];
  const categorySelect = document.querySelector('#category');
  const selectedCategory = categorySelect.value;
  categorySelect.innerHTML = '<option value="">All categories</option>' + categories.map(([id,name]) => `<option value="${escapeHtml(id)}">${escapeHtml(name)}</option>`).join('');
  if (categories.some(([id]) => id === selectedCategory)) categorySelect.value = selectedCategory;
  renderProducts();
}
async function loadSession() {
  state.session = await api('/api/v1/session').catch(() => ({ authenticated: false }));
  const operations = state.session.admin ? ' · <a class="login-link" href="/admin/health.html">Health</a>' : '';
  document.querySelector('#session-actions').innerHTML = state.session.authenticated
    ? `<span>Hi, ${escapeHtml(state.session.username)}</span>${operations} · <button id="sign-out" class="login-link nav-link">Sign out</button>`
    : '<a class="login-link" href="/login">Sign in</a>';
  document.querySelector('#sign-out')?.addEventListener('click', signOut);
}
async function signOut() {
  if (!state.csrf) state.csrf = await fetch('/api/v1/csrf').then(r => r.json());
  await fetch('/logout', { method: 'POST', credentials: 'same-origin', headers: { [state.csrf.headerName]: state.csrf.token } });
  location.href = '/';
}
async function loadCart() { state.cart = await api('/api/v1/cart'); renderCart(); }

function renderProducts() {
  const query = document.querySelector('#search').value.toLowerCase().trim();
  const category = document.querySelector('#category').value;
  const filtered = state.products.filter(p => (!category || p.categoryId === category) && (!query || `${p.name} ${p.description}`.toLowerCase().includes(query)));
  document.querySelector('#product-grid').innerHTML = filtered.map(p => `<article class="product-card">
    <div class="pet-art" aria-hidden="true">${petIcons[p.categoryId] || '🐾'}</div><div class="product-copy"><span class="eyebrow">${escapeHtml(p.categoryName)} · ${p.stock} AVAILABLE</span>
    <h3>${escapeHtml(p.name)}</h3><p>${escapeHtml(p.description)}</p><div class="card-bottom"><span class="price">${money(p.price)}</span>
    <button class="add-button" data-add="${escapeHtml(p.id)}" ${p.stock < 1 ? 'disabled' : ''}>Add to cart</button></div></div></article>`).join('') || '<p class="empty">No pets match that search.</p>';
  document.querySelectorAll('[data-add]').forEach(button => button.addEventListener('click', () => addToCart(button.dataset.add)));
}

async function addToCart(productId) {
  if (!state.session?.authenticated) { location.href = '/login'; return; }
  try {
    if (!state.cart) await loadCart();
    state.cart = await api('/api/v1/cart/items', { method: 'POST', body: JSON.stringify({ productId, quantity: 1, expectedVersion: state.cart.version }) });
    renderCart(); toast('Added to your cart.');
  } catch (error) { await recoverCart(error); }
}
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
async function updateQuantity(productId, quantity) {
  try { state.cart = await api(`/api/v1/cart/items/${encodeURIComponent(productId)}`, { method:'PUT', body:JSON.stringify({quantity, expectedVersion:state.cart.version}) }); renderCart(); }
  catch (error) { await recoverCart(error); }
}
async function removeItem(productId) {
  try { state.cart = await api(`/api/v1/cart/items/${encodeURIComponent(productId)}?expectedVersion=${state.cart.version}`, { method:'DELETE' }); renderCart(); }
  catch (error) { await recoverCart(error); }
}
async function recoverCart(error) { toast(error.message, true); if (state.session?.authenticated) await loadCart().catch(() => {}); }

async function checkout(event) {
  event.preventDefault();
  const address = Object.fromEntries(new FormData(event.currentTarget));
  const idempotencyKey = crypto.randomUUID();
  const submit = event.currentTarget.querySelector('[type=submit]'); submit.disabled = true;
  try {
    const order = await api('/api/v1/orders', { method:'POST', headers:{'Idempotency-Key':idempotencyKey}, body:JSON.stringify({ expectedCartVersion:state.cart.version, address }) });
    document.querySelector('#checkout-dialog').close(); toast(`Order ${order.id.slice(0,8)} placed.`); await Promise.all([loadCatalog(), loadCart(), loadOrders()]); show('orders');
  } catch (error) { await recoverCart(error); } finally { submit.disabled = false; }
}
async function loadOrders() {
  if (!state.session?.authenticated) return;
  const orders = await api('/api/v1/orders');
  document.querySelector('#orders-panel').innerHTML = orders.map(order => `<article class="order-card"><div><span class="eyebrow">${new Date(order.createdAt).toLocaleString()}</span><h3>Order ${escapeHtml(order.id.slice(0,8))}</h3></div><strong>${money(order.total)} · ${escapeHtml(order.status)}</strong>
    <div class="order-lines">${order.lines.map(line => `${escapeHtml(line.productName)} × ${line.quantity}`).join(' · ')}</div></article>`).join('') || '<p class="empty">No orders yet.</p>';
}
async function show(view) {
  if ((view === 'cart' || view === 'orders') && !state.session?.authenticated) { location.href='/login'; return; }
  document.querySelectorAll('.view').forEach(section => section.classList.toggle('hidden', section.id !== view));
  if (view === 'cart') await loadCart().catch(error => toast(error.message,true));
  if (view === 'orders') await loadOrders().catch(error => toast(error.message,true));
  document.querySelector(`#${view}`)?.scrollIntoView({ behavior:'smooth', block:'start' });
}
function toast(message, error=false) { const el=document.querySelector('#toast'); el.textContent=message; el.className=error?'show error':'show'; setTimeout(()=>el.className='',3500); }
function money(value) { return new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(value); }
function escapeHtml(value) { const span=document.createElement('span');span.textContent=String(value);return span.innerHTML; }
