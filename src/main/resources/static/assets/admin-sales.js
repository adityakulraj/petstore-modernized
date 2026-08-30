const state = { csrf: null, report: null };
const STATUS_COLORS = { COMPLETED: '#0f5b42', APPROVED: '#77a96c', PENDING: '#e6ad35', BACKORDERED: '#ff7657', DENIED: '#a53b35' };

document.addEventListener('DOMContentLoaded', async () => {
  document.querySelector('#analytics-filters').addEventListener('submit', event => { event.preventDefault(); loadAnalytics(); });
  document.querySelector('#reset-filters').addEventListener('click', () => { setDefaultDates(); document.querySelector('#category-filter').value = ''; loadAnalytics(); });
  document.querySelector('#back-to-categories').addEventListener('click', () => { document.querySelector('#category-filter').value = ''; loadAnalytics(); });
  setDefaultDates();
  try {
    const session = await api('/api/v1/session');
    document.querySelector('#store-name').textContent = session.store;
    document.querySelector('#session-actions').innerHTML = `<span>${escapeHtml(session.username)}</span> · <a href="/">Storefront</a> · <button id="sign-out" class="login-link nav-link">Sign out</button>`;
    document.querySelector('#sign-out').addEventListener('click', signOut);
    await loadAnalytics();
  } catch (error) { toast(error.message, true); }
});

/** Calls an application API and attaches CSRF credentials to protected mutations. */
async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const response = await fetch(path, { credentials: 'same-origin', ...options, headers });
  if (response.status === 401 || response.redirected && response.url.includes('/login')) throw new Error('Please sign in as the administrator.');
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `Request failed (${response.status})`);
  }
  return response.json();
}

/** Loads analytics from the API and refreshes the corresponding client state. */
async function loadAnalytics() {
  const form = document.querySelector('#analytics-filters');
  const button = form.querySelector('[type="submit"]');
  button.disabled = true;
  try {
    const params = new URLSearchParams({ from: form.from.value, to: form.to.value });
    if (form.category.value) params.set('category', form.category.value);
    state.report = await api(`/api/v1/admin/analytics/sales?${params}`);
    populateCategories(state.report.categories);
    render(state.report);
  } catch (error) { toast(error.message, true); }
  finally { button.disabled = false; }
}

/** Rebuilds the category filter while preserving the administrator's current selection. */
function populateCategories(categories) {
  const select = document.querySelector('#category-filter');
  const selected = select.value;
  select.innerHTML = '<option value="">All categories</option>' + categories.map(category => `<option value="${escapeHtml(category.id)}">${escapeHtml(category.name)}</option>`).join('');
  select.value = selected;
}

/** Renders summary cards, trends, status mix, and drill-down rows from the analytics report. */
function render(report) {
  document.querySelector('#analytics-scope').textContent = `${report.from} through ${report.to} (UTC) · ${report.categoryId || 'all categories'} · generated ${new Date(report.generatedAt).toLocaleTimeString()}`;
  document.querySelector('#revenue-total').textContent = money(report.summary.revenue);
  document.querySelector('#accepted-orders').textContent = number(report.summary.acceptedOrders);
  document.querySelector('#units-sold').textContent = number(report.summary.unitsSold);
  document.querySelector('#average-order').textContent = money(report.summary.averageOrderValue);
  document.querySelector('#pending-value').textContent = money(report.summary.pendingValue);
  renderTrend(report.daily);
  renderStatuses(report.statuses);
  renderBreakdown(report);
}

/** Renders trend from the current client-side state. */
function renderTrend(points) {
  const svg = document.querySelector('#revenue-trend');
  const width = 760, height = 280, left = 56, right = 18, top = 28, bottom = 40;
  const plotWidth = width - left - right, plotHeight = height - top - bottom;
  const maximum = Math.max(1, ...points.map(point => Number(point.revenue)));
  const coordinates = points.map((point, index) => ({
    ...point, x: left + (points.length === 1 ? plotWidth / 2 : index * plotWidth / (points.length - 1)),
    y: top + plotHeight - Number(point.revenue) / maximum * plotHeight
  }));
  const line = coordinates.map(point => `${point.x.toFixed(1)},${point.y.toFixed(1)}`).join(' ');
  const area = coordinates.length ? `${left},${top + plotHeight} ${line} ${coordinates.at(-1).x},${top + plotHeight}` : '';
  const grid = [0,.25,.5,.75,1].map(ratio => { const y = top + plotHeight * ratio; return `<line class="chart-grid-line" x1="${left}" y1="${y}" x2="${width-right}" y2="${y}"/><text class="chart-label" x="${left-8}" y="${y+4}" text-anchor="end">${compactMoney(maximum*(1-ratio))}</text>`; }).join('');
  const visibleDots = coordinates.filter((_, index) => coordinates.length <= 31 || index % Math.ceil(coordinates.length / 30) === 0 || index === coordinates.length - 1);
  svg.innerHTML = `<defs><linearGradient id="revenue-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6ba67e" stop-opacity=".42"/><stop offset="1" stop-color="#6ba67e" stop-opacity=".03"/></linearGradient></defs>${grid}<polygon class="chart-area" points="${area}"/><polyline class="chart-line" points="${line}"/>${visibleDots.map(point => `<circle class="chart-dot" cx="${point.x}" cy="${point.y}" r="5"><title>${point.date}: ${money(point.revenue)} · ${point.unitsSold} units</title></circle>`).join('')}<text class="chart-label" x="${left}" y="${height-8}">${points[0]?.date || ''}</text><text class="chart-label" text-anchor="end" x="${width-right}" y="${height-8}">${points.at(-1)?.date || ''}</text>`;
  const peak = points.reduce((best, point) => Number(point.revenue) > Number(best?.revenue || 0) ? point : best, null);
  document.querySelector('#trend-peak').textContent = peak && Number(peak.revenue) > 0 ? `Peak ${money(peak.revenue)} on ${peak.date}` : 'No recognized revenue';
}

/** Renders statuses from the current client-side state. */
function renderStatuses(statuses) {
  document.querySelector('#status-list').innerHTML = statuses.map(item => `<div class="status-row"><span class="status-swatch" style="background:${STATUS_COLORS[item.status] || '#617068'}"></span><div><strong>${escapeHtml(item.status.replaceAll('_',' '))}</strong><small>${money(item.value)}</small></div><span class="status-count">${number(item.orderCount)}</span></div>`).join('') || '<p class="empty-row">No orders in this period.</p>';
}

/** Renders breakdown from the current client-side state. */
function renderBreakdown(report) {
  const itemMode = report.dimension === 'ITEM';
  document.querySelector('#breakdown-kicker').textContent = itemMode ? 'ITEM PERFORMANCE' : 'CATEGORY PERFORMANCE';
  document.querySelector('#breakdown-title').textContent = itemMode ? `Items in ${report.categoryId}` : 'Revenue by category';
  document.querySelector('#breakdown-help').textContent = itemMode ? 'Each variant retains its own SKU, quantity, and revenue.' : 'Select a category to drill into its individual item variants.';
  document.querySelector('#dimension-heading').textContent = itemMode ? 'Item variant' : 'Category';
  document.querySelector('#back-to-categories').classList.toggle('hidden', !itemMode);
  const maximum = Math.max(1, ...report.breakdown.map(item => Number(item.revenue)));
  document.querySelector('#breakdown-bars').innerHTML = report.breakdown.map(item => `<div class="bar-row"><button class="bar-label" ${itemMode ? 'disabled' : ''} data-category="${escapeHtml(item.key)}">${escapeHtml(item.label)}</button><span class="bar-track"><span class="bar-fill" style="width:${Math.max(1, Number(item.revenue)/maximum*100)}%"></span></span><span class="bar-value">${money(item.revenue)}</span></div>`).join('') || '<p class="empty-row">No recognized sales in this period.</p>';
  document.querySelectorAll('.bar-label:not(:disabled)').forEach(button => button.addEventListener('click', () => { document.querySelector('#category-filter').value = button.dataset.category; loadAnalytics(); }));
  document.querySelector('#breakdown-body').innerHTML = report.breakdown.map(item => `<tr><td><strong>${escapeHtml(item.label)}</strong><br><small>${escapeHtml(item.key)}</small></td><td>${number(item.orderCount)}</td><td>${number(item.unitsSold)}</td><td>${money(item.revenue)}</td></tr>`).join('') || '<tr><td class="empty-row" colspan="4">No recognized sales in this period.</td></tr>';
}

/** Initializes the report to a useful recent date range. */
function setDefaultDates() {
  const today = new Date();
  const from = new Date(today); from.setDate(from.getDate() - 29);
  document.querySelector('#from-date').value = localDate(from);
  document.querySelector('#to-date').value = localDate(today);
}

/** Performs CSRF-protected logout and returns to the storefront. */
async function signOut() {
  if (!state.csrf) state.csrf = await fetch('/api/v1/csrf', { credentials: 'same-origin' }).then(response => response.json());
  await fetch('/logout', { method: 'POST', credentials: 'same-origin', headers: { [state.csrf.headerName]: state.csrf.token } });
  location.href = '/';
}

/** Formats a date as a local ISO calendar value without a timezone shift. */
function localDate(date) { return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`; }
/** Formats a numeric amount as a US-dollar price. */
function money(value) { return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value)); }
/** Formats large monetary chart values in compact US-dollar notation. */
function compactMoney(value) { return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1 }).format(value); }
/** Formats a count using locale-aware digit grouping. */
function number(value) { return new Intl.NumberFormat('en-US').format(Number(value)); }
/** Displays a transient success or error message to the user. */
function toast(message, error = false) { const element = document.querySelector('#toast'); element.textContent = message; element.className = error ? 'show error' : 'show'; setTimeout(() => element.className = '', 4000); }
/** Escapes untrusted text before it is inserted into generated HTML. */
function escapeHtml(value) { const span = document.createElement('span'); span.textContent = String(value); return span.innerHTML; }
