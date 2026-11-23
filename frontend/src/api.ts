export type Aggregation = "AVG" | "MIN" | "MAX" | "P95";

export interface Metric {
  id: number;
  name: string;
  unit: string | null;
  host: string;
  createdAt: string;
  pointCount: number;
}

export interface Page<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface AggregatedPoint {
  bucket: string;
  value: number;
}

export interface HistoryResponse {
  metricId: number;
  metricName: string;
  aggregation: string;
  step: string;
  from: string;
  to: string;
  points: AggregatedPoint[];
}

const API_BASE = import.meta.env.VITE_API_BASE ?? "";

async function request<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, { signal });
  if (!response.ok) {
    let detail = response.statusText;
    try {
      const body = await response.json();
      detail = body.detail ?? body.title ?? detail;
    } catch {
      /* ignore */
    }
    throw new Error(detail);
  }
  return response.json() as Promise<T>;
}

export function listMetrics(host?: string, signal?: AbortSignal): Promise<Page<Metric>> {
  const params = new URLSearchParams({ size: "100", sort: "name,asc" });
  if (host?.trim()) {
    params.set("host", host.trim());
  }
  return request(`/api/v1/metrics?${params}`, signal);
}

export function getHistory(
  metricId: number,
  from: string,
  to: string,
  step: string,
  agg: Aggregation,
  signal?: AbortSignal,
): Promise<HistoryResponse> {
  const params = new URLSearchParams({ from, to, step, agg });
  return request(`/api/v1/metrics/${metricId}/history?${params}`, signal);
}

export function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError";
}
