const state = { csrf: null, inventory: [], backorders: [], purchaseOrders: [] };

document.addEventListener('DOMContentLoaded', async () => {
  document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => show(button.dataset.view)));
  document.querySelector('#refresh-inventory').addEventListener('click', loadInventory);
  document.querySelector('#refresh-backorders').addEventListener('click', loadBackorders);
  document.querySelector('#refresh-orders').addEventListener('click', loadPurchaseOrders);
  try {
    const session = await api('/api/v1/session');
    document.querySelector('#store-name').textContent = session.store;
    document.querySelector('#session-actions').innerHTML = `<span>${escapeHtml(session.username)}</span> · <a href="/">Storefront</a> · <button id="sign-out" class="login-link nav-link">Sign out</button>`;
    document.querySelector('#sign-out').addEventListener('click', signOut);
    await Promise.all([loadInventory(), loadBackorders(), loadPurchaseOrders()]);
  } catch (error) { toast(error.message, true); }
});

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (options.method && options.method !== 'GET') {
    if (!state.csrf) state.csrf = await fetch('/api/v1/csrf', { credentials: 'same-origin' }).then(response => response.json());
    headers[state.csrf.headerName] = state.csrf.token;
  }
  const response = await fetch(path, { credentials: 'same-origin', ...options, headers });
  if (response.status === 401 || response.redirected && response.url.includes('/login')) throw new Error('Please sign in as the supplier.');
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `Request failed (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}

async function loadInventory() {
  state.inventory = await api('/api/v1/supplier/inventory');
  document.querySelector('#inventory-body').innerHTML = state.inventory.map(product => `<tr>
    <td><strong>${escapeHtml(product.name)}</strong><br><small>${escapeHtml(product.id)}</small></td>
    <td>${escapeHtml(product.categoryName)}</td><td>${product.version}</td>
    <td><input class="inventory-input" type="number" min="0" max="1000000" value="${product.stock}" data-quantity="${escapeHtml(product.id)}" aria-label="Quantity for ${escapeHtml(product.name)}"></td>
    <td><button class="save-inventory" data-save="${escapeHtml(product.id)}">Update</button></td></tr>`).join('');
  document.querySelectorAll('[data-save]').forEach(button => button.addEventListener('click', () => updateInventory(button.dataset.save, button)));
}

async function updateInventory(productId, button) {
  const product = state.inventory.find(item => item.id === productId);
  const quantity = Number(document.querySelector(`[data-quantity="${CSS.escape(productId)}"]`).value);
  button.disabled = true;
  try {
    await api(`/api/v1/supplier/inventory/${encodeURIComponent(productId)}`, {
      method: 'PUT', headers: { 'Idempotency-Key': crypto.randomUUID() },
      body: JSON.stringify({ expectedVersion: product.version, quantity })
    });
    await Promise.all([loadInventory(), loadBackorders(), loadPurchaseOrders()]);
    toast(`${product.name} inventory updated and waiting orders checked.`);
  } catch (error) { toast(error.message, true); await loadInventory().catch(() => {}); }
  finally { button.disabled = false; }
}

async function loadBackorders() {
  state.backorders = await api('/api/v1/supplier/backorders');
  document.querySelector('#backorder-count').textContent = state.backorders.length;
  document.querySelector('#backorder-list').innerHTML = state.backorders.map(order => `<article class="purchase-order backorder-card">
    <div class="purchase-order-head"><div><p class="eyebrow">${new Date(order.createdAt).toLocaleString()}</p><h3>Order ${escapeHtml(order.id.slice(0, 8))}</h3><small>Customer ${escapeHtml(order.customerId)}</small></div>
      <span class="status backordered">BACKORDERED</span></div>
    <div class="po-lines">${order.lines.map(line => `${escapeHtml(line.productName)} × ${line.quantity}`).join(' · ')}</div>
  </article>`).join('') || '<p class="empty">No orders are waiting for inventory.</p>';
}

async function loadPurchaseOrders() {
  state.purchaseOrders = await api('/api/v1/supplier/purchase-orders');
  document.querySelector('#purchase-order-list').innerHTML = state.purchaseOrders.map(po => `<article class="purchase-order">
    <div class="purchase-order-head"><div><p class="eyebrow">${new Date(po.createdAt).toLocaleString()}</p><h3>PO ${escapeHtml(po.id.slice(0, 8))}</h3><small>Customer ${escapeHtml(po.customerId)}</small></div>
      <span class="status ${po.status === 'PROCESSED' ? 'processed' : ''}">${escapeHtml(po.status)}</span></div>
    <div class="po-lines">${po.lines.map(line => `${escapeHtml(line.productName)} × ${line.quantity}`).join(' · ')}</div>
    <button class="process-order" data-process="${escapeHtml(po.id)}" ${po.status !== 'READY' ? 'disabled' : ''}>${po.status === 'READY' ? 'Process purchase order' : escapeHtml(po.status === 'PROCESSED' ? 'Processed' : 'Cancelled by customer')}</button>
  </article>`).join('') || '<p class="empty">No supplier purchase orders yet. Place a storefront order to create one.</p>';
  document.querySelectorAll('[data-process]').forEach(button => button.addEventListener('click', () => processPurchaseOrder(button.dataset.process, button)));
}

async function processPurchaseOrder(id, button) {
  const purchaseOrder = state.purchaseOrders.find(po => po.id === id);
  button.disabled = true;
  try {
    await api(`/api/v1/supplier/purchase-orders/${encodeURIComponent(id)}/process`, { method: 'POST', body: JSON.stringify({ expectedVersion: purchaseOrder.version }) });
    await loadPurchaseOrders(); toast(`Purchase order ${id.slice(0, 8)} processed.`);
  } catch (error) { toast(error.message, true); await loadPurchaseOrders().catch(() => {}); }
}

async function show(view) {
  document.querySelectorAll('.view').forEach(section => section.classList.toggle('hidden', section.id !== view));
  if (view === 'inventory' && state.inventory.length === 0) await loadInventory().catch(error => toast(error.message, true));
  if (view === 'backorders' && state.backorders.length === 0) await loadBackorders().catch(error => toast(error.message, true));
  if (view === 'purchase-orders' && state.purchaseOrders.length === 0) await loadPurchaseOrders().catch(error => toast(error.message, true));
}

async function signOut() {
  if (!state.csrf) state.csrf = await fetch('/api/v1/csrf', { credentials: 'same-origin' }).then(response => response.json());
  await fetch('/logout', { method: 'POST', credentials: 'same-origin', headers: { [state.csrf.headerName]: state.csrf.token } });
  location.href = '/';
}

function toast(message, error = false) { const element = document.querySelector('#toast'); element.textContent = message; element.className = error ? 'show error' : 'show'; setTimeout(() => element.className = '', 3500); }
function escapeHtml(value) { const span = document.createElement('span'); span.textContent = String(value); return span.innerHTML; }
