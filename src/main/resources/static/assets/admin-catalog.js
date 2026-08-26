const state = { csrf: null, items: [], changes: [], editing: null };

document.addEventListener('DOMContentLoaded', async () => {
  document.querySelector('#new-item').addEventListener('click', () => openDialog());
  document.querySelector('#close-dialog').addEventListener('click', closeDialog);
  document.querySelector('#catalog-form').addEventListener('submit', saveItem);
  document.querySelector('#catalog-search').addEventListener('input', renderItems);
  document.querySelector('#refresh-catalog').addEventListener('click', loadCatalog);
  try {
    const session = await api('/api/v1/session');
    document.querySelector('#store-name').textContent = session.store;
    document.querySelector('#session-actions').innerHTML = `<span>${escapeHtml(session.username)}</span> · <a href="/">Storefront</a> · <button id="sign-out" class="login-link nav-link">Sign out</button>`;
    document.querySelector('#sign-out').addEventListener('click', signOut);
    await loadCatalog();
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
    throw new Error(problem.detail || problem.title || `Request failed (${response.status})`);
  }
  return response.json();
}

async function loadCatalog() {
  [state.items, state.changes] = await Promise.all([
    api('/api/v1/admin/catalog/items'), api('/api/v1/admin/catalog/changes')
  ]);
  document.querySelector('#item-count').textContent = state.items.length;
  document.querySelector('#active-count').textContent = state.items.filter(item => item.active).length;
  renderItems(); renderChanges();
}

function renderItems() {
  const needle = document.querySelector('#catalog-search').value.trim().toLowerCase();
  const filtered = state.items.filter(item => `${item.id} ${item.productGroupId} ${item.variantName} ${item.name} ${item.categoryName}`.toLowerCase().includes(needle));
  document.querySelector('#catalog-items').innerHTML = filtered.map(item => `<article class="catalog-card ${item.active ? '' : 'archived'}" data-item="${escapeHtml(item.id)}">
    <div class="catalog-card-head"><div><span class="sku">${escapeHtml(item.id)} · ${escapeHtml(item.categoryId)}</span><h3>${escapeHtml(displayName(item))}</h3></div><span class="publish-state ${item.active ? '' : 'archived'}">${item.active ? 'PUBLISHED' : 'ARCHIVED'}</span></div>
    <p>${escapeHtml(item.description)}</p>
    <div class="catalog-price-row"><div><strong>${money(item.price)}</strong><small>${item.stock} supplier-managed in stock · v${item.version}</small></div><button class="secondary" data-edit="${escapeHtml(item.id)}" type="button">Edit item</button></div>
  </article>`).join('') || '<p class="empty">No catalog items match your search.</p>';
  document.querySelectorAll('[data-edit]').forEach(button => button.addEventListener('click', () => openDialog(state.items.find(item => item.id === button.dataset.edit))));
}

function renderChanges() {
  document.querySelector('#change-history').innerHTML = state.changes.slice(0, 50).map(change => `<tr><td>${new Date(change.occurredAt).toLocaleString()}</td><td><strong>${escapeHtml(change.productId)}</strong><br><small>v${change.newVersion}</small></td><td>${escapeHtml(change.action)}</td><td>${change.previousPrice == null ? '—' : money(change.previousPrice)} → ${money(change.newPrice)}</td><td>${change.previousActive == null ? '—' : status(change.previousActive)} → ${status(change.newActive)}</td><td>${escapeHtml(change.changedBy)}</td></tr>`).join('') || '<tr><td colspan="6">No administrative catalog changes yet.</td></tr>';
}

function openDialog(item = null) {
  state.editing = item;
  const form = document.querySelector('#catalog-form'); form.reset();
  document.querySelector('#dialog-kicker').textContent = item ? 'EDIT ITEM' : 'NEW ITEM';
  document.querySelector('#dialog-title').textContent = item ? `Edit ${displayName(item)}` : 'Add catalog item';
  document.querySelector('#save-item').textContent = item ? 'Save catalog change' : 'Create item';
  document.querySelector('#item-id').disabled = Boolean(item);
  if (item) for (const field of ['id','productGroupId','variantName','categoryId','categoryName','name','description','price','expectedVersion']) {
    form.elements[field].value = field === 'expectedVersion' ? item.version : item[field];
  }
  form.elements.active.checked = item ? item.active : true;
  document.querySelector('#catalog-dialog').showModal();
}

function closeDialog() { document.querySelector('#catalog-dialog').close(); state.editing = null; }

async function saveItem(event) {
  event.preventDefault();
  const form = event.currentTarget; const button = document.querySelector('#save-item'); button.disabled = true;
  const editing = state.editing;
  const payload = {
    productGroupId: form.productGroupId.value, variantName: form.variantName.value,
    categoryId: form.categoryId.value, categoryName: form.categoryName.value,
    name: form.name.value, description: form.description.value,
    price: Number(form.price.value), active: form.active.checked
  };
  try {
    if (editing) {
      payload.expectedVersion = Number(form.expectedVersion.value);
      await api(`/api/v1/admin/catalog/items/${encodeURIComponent(editing.id)}`, { method: 'PUT', body: JSON.stringify(payload) });
    } else {
      payload.id = form.id.value;
      await api('/api/v1/admin/catalog/items', { method: 'POST', body: JSON.stringify(payload) });
    }
    closeDialog(); await loadCatalog();
    toast(editing ? 'Catalog item updated.' : 'Catalog item created with supplier inventory set to 0.');
  } catch (error) { toast(error.message, true); await loadCatalog().catch(() => {}); }
  finally { button.disabled = false; }
}

async function signOut() {
  if (!state.csrf) state.csrf = await fetch('/api/v1/csrf', { credentials: 'same-origin' }).then(response => response.json());
  await fetch('/logout', { method: 'POST', credentials: 'same-origin', headers: { [state.csrf.headerName]: state.csrf.token } });
  location.href = '/';
}

function displayName(item) { return !item.variantName || item.variantName.toLowerCase() === 'standard' ? item.name : `${item.variantName} ${item.name}`; }
function money(value) { return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value)); }
function status(active) { return active ? 'Published' : 'Archived'; }
function toast(message, error = false) { const element = document.querySelector('#toast'); element.textContent = message; element.className = error ? 'show error' : 'show'; setTimeout(() => element.className = '', 4000); }
function escapeHtml(value) { const span = document.createElement('span'); span.textContent = String(value); return span.innerHTML; }
