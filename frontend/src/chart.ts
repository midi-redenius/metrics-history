import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  TimeScale,
  Title,
  Tooltip,
  Filler,
  Legend,
  CategoryScale,
} from "chart.js";
import "chartjs-adapter-date-fns";
import type { HistoryResponse } from "./api";

Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  TimeScale,
  CategoryScale,
  Title,
  Tooltip,
  Filler,
  Legend,
);

let chart: Chart | null = null;
let spanMs = 0;

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const DAY_MS = 24 * 60 * 60 * 1000;

function formatUtcTick(ms: number): string {
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  const time = `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
  if (spanMs <= 1.5 * DAY_MS) return time;
  const date = `${MONTHS[d.getUTCMonth()]} ${d.getUTCDate()}`;
  return time === "00:00" ? date : `${date} ${time}`;
}

function spanOf(history: HistoryResponse): number {
  const points = history.points;
  if (points.length < 2) return 0;
  return Date.parse(points[points.length - 1].bucket) - Date.parse(points[0].bucket);
}

const LINE = "#3dcfb6";
const GRID = "rgba(232, 240, 236, 0.08)";
const TICK = "rgba(232, 240, 236, 0.55)";

export function renderHistoryChart(
  canvas: HTMLCanvasElement,
  history: HistoryResponse,
  { inPlace = false }: { inPlace?: boolean } = {},
): void {
  const labels = history.points.map((p) => p.bucket);
  const values = history.points.map((p) => p.value);
  spanMs = spanOf(history);

  if (chart && inPlace) {
    const dataset = chart.data.datasets[0];
    chart.data.labels = labels;
    dataset.data = values;
    dataset.label = `${history.metricName} · ${history.aggregation}`;
    if ("pointRadius" in dataset) {
      dataset.pointRadius = values.length > 40 ? 0 : 3;
    }
    chart.update("none");
    return;
  }

  if (chart) {
    chart.destroy();
    chart = null;
  }

  chart = new Chart(canvas, {
    type: "line",
    data: {
      labels,
      datasets: [
        {
          label: `${history.metricName} · ${history.aggregation}`,
          data: values,
          borderColor: LINE,
          backgroundColor: "rgba(61, 207, 182, 0.12)",
          borderWidth: 2,
          pointRadius: values.length > 40 ? 0 : 3,
          pointHoverRadius: 5,
          tension: 0.25,
          fill: true,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: {
        duration: 450,
        easing: "easeOutQuart",
      },
      interaction: {
        mode: "index",
        intersect: false,
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: "#14201c",
          titleColor: "#e8f0ec",
          bodyColor: "#9db5ab",
          borderColor: "rgba(61, 207, 182, 0.35)",
          borderWidth: 1,
          padding: 10,
          displayColors: false,
          callbacks: {
            title: (items) => {
              const raw = items[0]?.label;
              if (!raw) return "";
              return new Date(raw).toISOString().replace(".000Z", "Z");
            },
            label: (item) => {
              const v = item.parsed.y;
              return typeof v === "number" ? v.toFixed(3) : String(v);
            },
          },
        },
      },
      scales: {
        x: {
          type: "time",
          time: { minUnit: "minute" },
          title: { display: true, text: "UTC", color: TICK, font: { size: 10 } },
          grid: { color: GRID },
          ticks: {
            color: TICK,
            maxRotation: 0,
            autoSkipPadding: 16,
            callback: (value) => formatUtcTick(Number(value)),
          },
          border: { color: GRID },
        },
        y: {
          grid: { color: GRID },
          ticks: { color: TICK },
          border: { color: GRID },
        },
      },
    },
  });
}

export function clearChart(): void {
  if (chart) {
    chart.destroy();
    chart = null;
  }
}
