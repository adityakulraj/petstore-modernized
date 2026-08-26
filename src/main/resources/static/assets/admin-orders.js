const state = { csrf: null, orders: [] };

document.addEventListener('DOMContentLoaded', async () => {
  document.querySelector('#refresh-orders').addEventListener('click', loadOrders);
  try {
    const session = await api('/api/v1/session');
    document.querySelector('#store-name').textContent = session.store;
    document.querySelector('#session-actions').innerHTML = `<span>${escapeHtml(session.username)}</span> · <a href="/">Storefront</a> · <button id="sign-out" class="login-link nav-link">Sign out</button>`;
    document.querySelector('#sign-out').addEventListener('click', signOut);
    await loadOrders();
  } catch (error) { toast(error.message, true); }
});

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (options.method && options.method !== 'GET') {
    if (!state.csrf) state.csrf = await fetch('/api/v1/csrf', { credentials: 'same-origin' }).then(response => response.json());
    headers[state.csrf.headerName] = state.csrf.token;
  }
  const response = await fetch(path, { credentials: 'same-origin', ...options, headers });
  if (response.status === 401 || response.redirected && response.url.includes('/login')) throw new Error('Please sign in as the administrator.');
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `Request failed (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}

async function loadOrders() {
  state.orders = await api('/api/v1/admin/orders');
  for (const status of ['PENDING', 'APPROVED', 'DENIED', 'COMPLETED']) {
    document.querySelector(`#${status.toLowerCase()}-count`).textContent = state.orders.filter(order => order.status === status).length;
  }
  const pending = state.orders.filter(order => order.status === 'PENDING');
  document.querySelector('#pending-orders').innerHTML = pending.map(order => `<article class="approval-card" data-order="${escapeHtml(order.id)}">
    <div class="approval-head"><div><p class="eyebrow">${new Date(order.createdAt).toLocaleString()}</p><h3>Order ${escapeHtml(order.id.slice(0, 8))}</h3><span class="order-meta">Customer ${escapeHtml(order.customerId)} · Version ${order.version}</span></div><strong class="approval-total">${money(order.total)}</strong></div>
    <div class="order-lines">${order.lines.map(line => `${escapeHtml(line.productName)} × ${line.quantity} — ${money(line.subtotal)}`).join('<br>')}</div>
    <div class="approval-actions"><button class="decision deny" data-decision="DENIED" data-id="${escapeHtml(order.id)}">Deny &amp; release stock</button><button class="decision approve" data-decision="APPROVED" data-id="${escapeHtml(order.id)}">Approve for supplier</button></div>
  </article>`).join('') || '<p class="empty">Nothing is waiting for review.</p>';
  document.querySelectorAll('[data-decision]').forEach(button => button.addEventListener('click', () => review(button.dataset.id, button.dataset.decision)));

  const history = state.orders.filter(order => order.status !== 'PENDING');
  document.querySelector('#history-body').innerHTML = history.map(order => `<tr><td><strong>${escapeHtml(order.id.slice(0, 8))}</strong><br><small>${new Date(order.createdAt).toLocaleString()}</small></td><td>${escapeHtml(order.customerId)}</td><td>${money(order.total)}</td><td><span class="status ${order.status.toLowerCase()}">${escapeHtml(order.status)}</span></td><td>${order.reviewedAt ? new Date(order.reviewedAt).toLocaleString() : 'Auto-approved'}</td><td>${escapeHtml(order.reviewedBy || 'Policy')}</td></tr>`).join('') || '<tr><td colspan="6">No processed orders yet.</td></tr>';
}

async function review(id, decision) {
  const order = state.orders.find(item => item.id === id);
  document.querySelectorAll(`[data-id="${CSS.escape(id)}"]`).forEach(button => button.disabled = true);
  try {
    await api(`/api/v1/admin/orders/${encodeURIComponent(id)}/decision`, { method: 'POST', body: JSON.stringify({ expectedVersion: order.version, decision }) });
    await loadOrders();
    toast(decision === 'APPROVED' ? 'Order approved and released to the supplier.' : 'Order denied and reserved inventory restored.');
  } catch (error) { toast(error.message, true); await loadOrders().catch(() => {}); }
}

async function signOut() {
  if (!state.csrf) state.csrf = await fetch('/api/v1/csrf', { credentials: 'same-origin' }).then(response => response.json());
  await fetch('/logout', { method: 'POST', credentials: 'same-origin', headers: { [state.csrf.headerName]: state.csrf.token } });
  location.href = '/';
}

function money(value) { return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value); }
function toast(message, error = false) { const element = document.querySelector('#toast'); element.textContent = message; element.className = error ? 'show error' : 'show'; setTimeout(() => element.className = '', 4000); }
function escapeHtml(value) { const span = document.createElement('span'); span.textContent = String(value); return span.innerHTML; }
