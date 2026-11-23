import "./style.css";
import {
  type Aggregation,
  type Metric,
  getHistory,
  isAbortError,
  listMetrics,
} from "./api";
import { clearChart, renderHistoryChart } from "./chart";

const STEPS = ["1 minute", "5 minutes", "15 minutes", "1 hour", "6 hours", "1 day"];
const STEP_SECONDS: Record<string, number> = {
  "1 minute": 60,
  "5 minutes": 300,
  "15 minutes": 900,
  "1 hour": 3600,
  "6 hours": 21600,
  "1 day": 86400,
};
const LIVE_WINDOW_MS = 60 * 60 * 1000;
const LIVE_REFRESH_MS = 10_000;
const AGGS: Aggregation[] = ["AVG", "MIN", "MAX", "P95"];

const app = document.querySelector<HTMLDivElement>("#app");
if (!app) {
  throw new Error("#app not found");
}

app.innerHTML = `
  <header class="topbar">
    <div class="brand">
      <strong>Metrics History</strong>
      <span>Time-series explorer</span>
    </div>
    <div id="status" class="status">connecting…</div>
  </header>
  <div class="layout">
    <aside class="sidebar">
      <div class="sidebar-head">
        <h2>Metrics</h2>
        <div class="field">
          <label for="hostFilter">Host filter</label>
          <input id="hostFilter" type="text" placeholder="e.g. app-01" />
        </div>
      </div>
      <ul id="metricList" class="metric-list"></ul>
    </aside>
    <section class="main">
      <div class="controls">
        <div class="field grow">
          <label for="from">From (UTC)</label>
          <input id="from" type="datetime-local" />
        </div>
        <div class="field grow">
          <label for="to">To (UTC)</label>
          <input id="to" type="datetime-local" />
        </div>
        <div class="field">
          <label for="step">Step</label>
          <select id="step"></select>
        </div>
        <div class="field">
          <label for="agg">Aggregation</label>
          <select id="agg"></select>
        </div>
        <div class="presets">
          <button id="presetLive" class="btn ghost" type="button" title="Last 1 hour (UTC)">Last 1h</button>        </div>
        <button id="loadBtn" class="btn" type="button" disabled>Load</button>
      </div>
      <div class="chart-panel">
        <div id="chartMeta" class="chart-meta"></div>
        <div class="chart-wrap">
          <canvas id="chart"></canvas>
          <div id="empty" class="empty">Select a metric to plot history</div>
          <div id="error" class="error hidden"></div>
        </div>
      </div>
    </section>
  </div>
`;

const statusEl = must<HTMLDivElement>("#status");
const hostFilter = must<HTMLInputElement>("#hostFilter");
const metricList = must<HTMLUListElement>("#metricList");
const fromInput = must<HTMLInputElement>("#from");
const toInput = must<HTMLInputElement>("#to");
const stepSelect = must<HTMLSelectElement>("#step");
const aggSelect = must<HTMLSelectElement>("#agg");
const loadBtn = must<HTMLButtonElement>("#loadBtn");
const presetLive = must<HTMLButtonElement>("#presetLive");
const chartCanvas = must<HTMLCanvasElement>("#chart");
const emptyEl = must<HTMLDivElement>("#empty");
const errorEl = must<HTMLDivElement>("#error");
const chartMeta = must<HTMLDivElement>("#chartMeta");

let metrics: Metric[] = [];
let selectedId: number | null = null;
let hostTimer: number | undefined;
let metricsRequest: AbortController | null = null;
let historyRequest: AbortController | null = null;
let live = false;
let liveTimer: number | undefined;

stepSelect.innerHTML = STEPS.map((s) => `<option value="${s}">${s}</option>`).join("");
stepSelect.value = "1 minute";
aggSelect.innerHTML = AGGS.map((a) => `<option value="${a}">${a}</option>`).join("");

setLive(true);

hostFilter.addEventListener("input", () => {
  window.clearTimeout(hostTimer);
  hostTimer = window.setTimeout(() => {
    refreshMetrics().catch((err: unknown) => {
      if (isAbortError(err)) return;
      showError(err instanceof Error ? err.message : "Failed to load metrics");
    });
  }, 250);
});

presetLive.addEventListener("click", () => {
  if (live) {
    setLive(false);
    return;
  }
  stepSelect.value = "1 minute";
  setLive(true);
  void loadHistory();
});

for (const input of [fromInput, toInput]) {
  input.addEventListener("input", () => setLive(false));
}

for (const select of [stepSelect, aggSelect]) {
  select.addEventListener("change", () => {
    if (live) void loadHistory();
  });
}

document.addEventListener("visibilitychange", () => {
  if (live && !document.hidden) void loadHistory({ quiet: true });
});

loadBtn.addEventListener("click", () => {
  void loadHistory();
});

void bootstrap();

async function bootstrap(): Promise<void> {
  try {
    await refreshMetrics();
    setStatus("api ok", true);
    if (live) void loadHistory();
  } catch (err) {
    setStatus("api offline", false);
    showError(err instanceof Error ? err.message : "Failed to reach API");
  }
}

async function refreshMetrics(): Promise<void> {
  metricsRequest?.abort();
  const request = new AbortController();
  metricsRequest = request;

  const page = await listMetrics(hostFilter.value, request.signal);
  metrics = page.content;
  renderMetricList();
  if (selectedId != null && !metrics.some((m) => m.id === selectedId)) {
    selectedId = null;
    loadBtn.disabled = true;
    clearChart();
    emptyEl.classList.remove("hidden");
    emptyEl.textContent = "Select a metric to plot history";
    chartMeta.innerHTML = "";
  }
  if (selectedId == null && metrics.length > 0) {
    selectMetric(metrics[0].id);
  }
}

function renderMetricList(): void {
  if (metrics.length === 0) {
    metricList.innerHTML = `<li class="empty" style="position:static">No metrics found</li>`;
    return;
  }
  metricList.innerHTML = metrics
    .map(
      (m) => `
      <li>
        <button type="button" class="metric-btn ${m.id === selectedId ? "active" : ""}" data-id="${m.id}">
          <span class="name">${escapeHtml(m.name)}</span>
          <span class="meta">
            <span>${escapeHtml(m.host)}</span>
            <span>${m.pointCount} pts</span>
          </span>
        </button>
      </li>`,
    )
    .join("");

  metricList.querySelectorAll<HTMLButtonElement>(".metric-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const id = Number(btn.dataset.id);
      selectMetric(id);
      void loadHistory();
    });
  });
}

function selectMetric(id: number): void {
  selectedId = id;
  loadBtn.disabled = false;
  renderMetricList();
}

async function loadHistory({ quiet = false }: { quiet?: boolean } = {}): Promise<void> {
  if (selectedId == null) return;
  historyRequest?.abort();
  const request = new AbortController();
  historyRequest = request;

  if (live) applyLiveRange();
  if (!quiet) {
    hideError();
    emptyEl.classList.add("hidden");
    loadBtn.disabled = true;
    loadBtn.textContent = "Loading…";
  }

  try {
    const from = localToIso(fromInput.value);
    const to = localToIso(toInput.value);
    const step = stepSelect.value;
    const agg = aggSelect.value as Aggregation;
    const history = await getHistory(selectedId, from, to, step, agg, request.signal);

    if (history.points.length === 0) {
      clearChart();
      emptyEl.classList.remove("hidden");
      emptyEl.textContent = "No points in this range. The collector writes current data — try Last 1h.";
      chartMeta.innerHTML = metaHtml(history, 0);
      return;
    }

    hideError();
    emptyEl.classList.add("hidden");
    renderHistoryChart(chartCanvas, history, { inPlace: quiet });
    chartMeta.innerHTML = metaHtml(history, history.points.length);
    setStatus(live ? "live" : "api ok", true);
  } catch (err) {
    if (isAbortError(err)) return;
    setStatus("request failed", false);
    if (quiet) return;
    clearChart();
    emptyEl.classList.add("hidden");
    showError(err instanceof Error ? err.message : "Failed to load history");
  } finally {
    if (historyRequest === request) {
      historyRequest = null;
      loadBtn.disabled = selectedId == null;
      loadBtn.textContent = "Load";
    }
  }
}

function metaHtml(
  history: { metricName: string; aggregation: string; step: string; from: string; to: string },
  count: number,
): string {
  return `
    <span><strong>${escapeHtml(history.metricName)}</strong></span>
    <span>agg <strong>${escapeHtml(history.aggregation)}</strong></span>
    <span>step <strong>${escapeHtml(history.step)}</strong></span>
    <span>points <strong>${count}</strong></span>
    <span>${escapeHtml(history.from)} → ${escapeHtml(history.to)}</span>
  `;
}

function setLive(on: boolean): void {
  live = on;
  presetLive.classList.toggle("active", on);
  presetLive.textContent = on ? "Live · 1h" : "Last 1h";
  presetLive.title = on ? "Auto-refreshing every 10s — click to pause" : "Last 1 hour (UTC), auto-refresh";
  window.clearInterval(liveTimer);
  liveTimer = undefined;
  if (on) {
    applyLiveRange();
    liveTimer = window.setInterval(() => {
      if (!document.hidden) void loadHistory({ quiet: true });
    }, LIVE_REFRESH_MS);
  }
}

/**
 * Bucket-aligned so every viewer within one step sends the same from/to and shares the server cache;
 * the window ends at the next boundary to include the bucket that is still filling.
 */
function applyLiveRange(): void {
  const stepMs = (STEP_SECONDS[stepSelect.value] ?? 60) * 1000;
  const to = Math.ceil(Date.now() / stepMs) * stepMs;
  const from = to - Math.max(LIVE_WINDOW_MS, stepMs);
  fromInput.value = toDatetimeLocalUtc(new Date(from));
  toInput.value = toDatetimeLocalUtc(new Date(to));
}

function toDatetimeLocalUtc(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

function localToIso(value: string): string {
  // datetime-local has no TZ; treat as UTC for API consistency
  if (!value) throw new Error("from/to are required");
  const normalized = value.length === 16 ? `${value}:00` : value;
  return `${normalized}Z`;
}

function setStatus(text: string, ok: boolean | null): void {
  statusEl.textContent = text;
  statusEl.classList.remove("ok", "err");
  if (ok === true) statusEl.classList.add("ok");
  if (ok === false) statusEl.classList.add("err");
}

function showError(message: string): void {
  errorEl.textContent = message;
  errorEl.classList.remove("hidden");
}

function hideError(): void {
  errorEl.classList.add("hidden");
  errorEl.textContent = "";
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function must<T extends Element>(selector: string): T {
  const el = document.querySelector<T>(selector);
  if (!el) throw new Error(`Missing ${selector}`);
  return el;
}
