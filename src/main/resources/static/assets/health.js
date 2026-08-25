const REFRESH_MILLIS = 5000;
const svgNs = 'http://www.w3.org/2000/svg';

document.addEventListener('DOMContentLoaded', () => {
  refresh();
  setInterval(() => { if (!document.hidden) refresh(); }, REFRESH_MILLIS);
});

async function refresh() {
  try {
    const response = await fetch('/api/v1/admin/health', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
    if (response.redirected || response.status === 401) { location.href = '/login'; return; }
    if (!response.ok) throw new Error(response.status === 403 ? 'The ADMIN role is required.' : `Health request failed (${response.status})`);
    render(await response.json());
    document.querySelector('#dashboard-error').classList.add('hidden');
  } catch (error) {
    const banner = document.querySelector('#dashboard-error');
    banner.textContent = error.message;
    banner.classList.remove('hidden');
    setStatus('UNAVAILABLE');
  }
}

function render(data) {
  setStatus(data.status);
  text('#store-name', data.store.toUpperCase());
  text('#updated-at', new Date().toLocaleTimeString());
  text('#uptime', duration(data.uptimeSeconds));
  text('#started-at', `Started ${new Date(data.startedAt).toLocaleString()}`);
  text('#throughput', number(data.traffic.requestsThisMinute));
  text('#server-errors', percent(data.traffic.serverErrorRatePercent));
  text('#server-error-count', `${number(data.traffic.windowServerErrors)} 5xx · ${number(data.traffic.windowClientErrors)} 4xx · last 60m`);
  text('#latency', milliseconds(data.traffic.averageLatencyMs));
  text('#max-latency', `Maximum ${milliseconds(data.traffic.maxLatencyMs)}`);
  const heapPercent = data.jvm.heapMaxBytes > 0 ? data.jvm.heapUsedBytes * 100 / data.jvm.heapMaxBytes : 0;
  text('#heap', bytes(data.jvm.heapUsedBytes));
  text('#threads', `${percent(heapPercent)} of max · ${number(data.jvm.liveThreads)} live threads`);

  renderBarChart('#throughput-chart', data.traffic.series, point => point.requests);
  renderLineChart('#error-chart', data.traffic.series, [
    { className: 'client-line', value: point => point.requests ? point.clientErrors * 100 / point.requests : 0 },
    { className: 'server-line', value: point => point.requests ? point.serverErrors * 100 / point.requests : 0 }
  ], '%');
  renderLineChart('#latency-chart', data.traffic.series, [
    { className: 'line', value: point => point.averageLatencyMs }
  ], 'ms');
  renderPool(data.database.pool);
  renderJvm(data.jvm);
  renderOperations(data.database.operations);
  renderPlans(data.database.queryPlans);
}

function setStatus(status) {
  text('#health-status', status);
  const dot = document.querySelector('#health-dot');
  dot.className = `status-dot ${status === 'UP' ? 'up' : status === 'Loading' ? 'pending' : 'down'}`;
}

function renderPool(pool) {
  text('#pool-provider', pool.provider);
  text('#pool-active', number(pool.active));
  text('#pool-idle', number(pool.idle));
  text('#pool-total', number(pool.total));
  text('#pool-range', `${pool.configuredMin}–${pool.configuredMax}`);
  text('#pool-waiting', number(pool.awaiting));
  text('#pool-failures', number(pool.acquisitionFailures));
  const usage = pool.configuredMax ? Math.min(100, pool.active * 100 / pool.configuredMax) : 0;
  document.querySelector('#pool-used').style.width = `${usage}%`;
}

function renderJvm(jvm) {
  text('#jvm-heap-used', bytes(jvm.heapUsedBytes));
  text('#jvm-heap-committed', bytes(jvm.heapCommittedBytes));
  text('#jvm-heap-max', bytes(jvm.heapMaxBytes));
  text('#jvm-live-threads', number(jvm.liveThreads));
  text('#jvm-peak-threads', number(jvm.peakThreads));
  text('#jvm-processors', number(jvm.processors));
}

function renderOperations(operations) {
  const body = document.querySelector('#operation-rows');
  body.replaceChildren();
  if (!operations.length) {
    body.append(row(['No operations observed yet.', '', '', '', '', '']));
    return;
  }
  operations.forEach(operation => body.append(row([
    operation.operation, number(operation.calls), number(operation.failures), number(operation.retries),
    milliseconds(operation.averageLatencyMs), milliseconds(operation.maxLatencyMs)
  ])));
}

function renderPlans(report) {
  text('#plan-captured', `Read-only explain diagnostics · captured ${new Date(report.capturedAt).toLocaleTimeString()} · cached 60s`);
  const body = document.querySelector('#plan-rows');
  body.replaceChildren();
  report.plans.forEach(plan => {
    const tr = document.createElement('tr');
    const operation = document.createElement('td');
    const strong = document.createElement('strong'); strong.textContent = plan.operation;
    const query = document.createElement('div'); query.className = 'query'; query.textContent = plan.query;
    operation.append(strong, query); tr.append(operation);
    const scan = document.createElement('td');
    const badge = document.createElement('span'); badge.className = `scan ${plan.scanType.toLowerCase()}`; badge.textContent = plan.scanType;
    scan.append(badge); tr.append(scan);
    [plan.indexName || '—', `${number(plan.documentsExamined)} / ${number(plan.keysExamined)}`, number(plan.rowsReturned), plan.detail]
      .forEach(value => { const td = document.createElement('td'); td.textContent = value; tr.append(td); });
    body.append(tr);
  });
}

function renderBarChart(selector, points, value) {
  const svg = chartBase(selector, points, value, '');
  const { width, height, left, top, innerWidth, innerHeight, max } = svg.chart;
  const barWidth = Math.max(1, innerWidth / points.length - 1);
  points.forEach((point, index) => {
    const amount = value(point);
    const bar = element('rect', { class: 'bar', x: left + index * innerWidth / points.length,
      y: top + innerHeight - amount / max * innerHeight, width: barWidth, height: amount / max * innerHeight });
    bar.append(element('title', {}, `${new Date(point.timestamp).toLocaleTimeString()}: ${number(amount)} requests`));
    svg.append(bar);
  });
}

function renderLineChart(selector, points, series, suffix) {
  const combined = point => Math.max(...series.map(item => item.value(point)));
  const svg = chartBase(selector, points, combined, suffix);
  const { left, top, innerWidth, innerHeight, max } = svg.chart;
  series.forEach(item => {
    const coordinates = points.map((point, index) => {
      const x = left + (points.length === 1 ? 0 : index * innerWidth / (points.length - 1));
      const y = top + innerHeight - item.value(point) / max * innerHeight;
      return `${x},${y}`;
    }).join(' ');
    svg.append(element('polyline', { class: item.className, points: coordinates }));
  });
}

function chartBase(selector, points, value, suffix) {
  const svg = document.querySelector(selector);
  svg.replaceChildren();
  const width = 800, height = 190, left = 38, right = 8, top = 8, bottom = 24;
  const innerWidth = width - left - right, innerHeight = height - top - bottom;
  const maximum = Math.max(1, ...points.map(value));
  const max = niceMaximum(maximum);
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  [0, .5, 1].forEach(position => {
    const y = top + innerHeight * position;
    svg.append(element('line', { class: 'grid', x1: left, y1: y, x2: width - right, y2: y }));
    const label = element('text', { class: 'axis-label', x: 0, y: y + 4 });
    label.textContent = `${round(max * (1 - position))}${suffix}`; svg.append(label);
  });
  const earliest = element('text', { class: 'axis-label', x: left, y: height - 3 });
  earliest.textContent = points.length ? new Date(points[0].timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
  const latest = element('text', { class: 'axis-label', x: width - right, y: height - 3, 'text-anchor': 'end' });
  latest.textContent = 'now'; svg.append(earliest, latest);
  svg.chart = { width, height, left, top, innerWidth, innerHeight, max };
  return svg;
}

function row(values) {
  const tr = document.createElement('tr');
  values.forEach(value => { const td = document.createElement('td'); td.textContent = value; tr.append(td); });
  return tr;
}
function element(name, attributes) {
  const node = document.createElementNS(svgNs, name);
  Object.entries(attributes).forEach(([key, value]) => node.setAttribute(key, value));
  return node;
}
function text(selector, value) { document.querySelector(selector).textContent = value; }
function number(value) { return new Intl.NumberFormat().format(value || 0); }
function round(value) { return value >= 100 ? Math.round(value) : Math.round(value * 10) / 10; }
function percent(value) { return `${(value || 0).toFixed(2)}%`; }
function milliseconds(value) { return `${(value || 0).toFixed(value >= 100 ? 0 : 1)} ms`; }
function bytes(value) {
  if (value < 0) return 'unbounded';
  const units = ['B','KB','MB','GB']; let amount = value || 0, unit = 0;
  while (amount >= 1024 && unit < units.length - 1) { amount /= 1024; unit++; }
  return `${amount.toFixed(unit > 1 ? 1 : 0)} ${units[unit]}`;
}
function duration(seconds) {
  const days = Math.floor(seconds / 86400), hours = Math.floor(seconds % 86400 / 3600), minutes = Math.floor(seconds % 3600 / 60);
  return [days && `${days}d`, (hours || days) && `${hours}h`, `${minutes}m`].filter(Boolean).join(' ');
}
function niceMaximum(value) { const power = 10 ** Math.floor(Math.log10(value)); return Math.ceil(value / power) * power; }
