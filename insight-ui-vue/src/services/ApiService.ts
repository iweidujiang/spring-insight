import axios, { AxiosRequestConfig, AxiosError } from 'axios'
import { getUiToken, setUiToken } from './AuthService'

export interface ApiResponse<T> {
  data: T
  status: number
  statusText: string
}

const apiClient = axios.create({
  baseURL: '/api/v1/ui',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

apiClient.interceptors.request.use((config) => {
  const token = getUiToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      setUiToken('')
      if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.assign(`/login?redirect=${redirect}`)
      }
    }
    return Promise.reject(error)
  }
)

async function request<T>(url: string, options: AxiosRequestConfig = {}): Promise<T> {
  try {
    const response = await apiClient<T>({
      url,
      ...options
    })
    return response.data
  } catch (error) {
    console.error(`请求失败 [${url}]:`, error)
    throw error
  }
}

async function requestWithDefault<T>(url: string, defaultValue: T, options: AxiosRequestConfig = {}): Promise<T> {
  try {
    const response = await apiClient<T>({
      url,
      ...options
    })
    return response.data
  } catch (error) {
    console.error(`请求失败 [${url}]:`, error)
    return defaultValue
  }
}

/** 后端 Map 使用 snake_case，统一转成前端 camelCase */
function normalizeDependency(raw: any) {
  return {
    sourceService: raw.source_service ?? raw.sourceService ?? '',
    targetService: raw.target_service ?? raw.targetService ?? '',
    callCount: Number(raw.call_count ?? raw.callCount ?? 0),
    avgDuration: Number(raw.avg_duration ?? raw.avgDuration ?? 0)
  }
}

function normalizeServiceStat(raw: any) {
  return {
    serviceName: raw.service_name ?? raw.serviceName ?? '',
    totalSpans: Number(raw.span_count ?? raw.totalSpans ?? 0)
  }
}

function normalizeErrorRow(raw: any) {
  const rate = Number(raw.error_rate ?? raw.errorRate ?? 0)
  return {
    serviceName: raw.service_name ?? raw.serviceName ?? '',
    totalCalls: Number(raw.total_calls ?? raw.totalCalls ?? 0),
    errorCalls: Number(raw.error_calls ?? raw.errorCalls ?? 0),
    errorRate: rate
  }
}

function normalizeLatencyRow(raw: any) {
  return {
    serviceName: raw.service_name ?? raw.serviceName ?? '',
    spanCount: Number(raw.span_count ?? raw.spanCount ?? 0),
    errorCount: Number(raw.error_count ?? raw.errorCount ?? 0),
    errorRate: Number(raw.error_rate ?? raw.errorRate ?? 0),
    avgMs: Number(raw.avg_ms ?? raw.avgMs ?? 0),
    p50Ms: Number(raw.p50_ms ?? raw.p50Ms ?? 0),
    p95Ms: Number(raw.p95_ms ?? raw.p95Ms ?? 0),
    maxMs: Number(raw.max_ms ?? raw.maxMs ?? 0)
  }
}

export class ApiService {
  static async getServiceNames(): Promise<string[]> {
    return requestWithDefault<string[]>('/services', [])
  }

  static async getServiceDependencies(hours: number = 24): Promise<any[]> {
    const rows = await requestWithDefault<any[]>(`/dependencies?hours=${hours}`, [])
    return rows.map(normalizeDependency)
  }

  static async getServiceStats(hours: number = 0): Promise<any[]> {
    const rows = await requestWithDefault<any[]>(`/services/stats?hours=${hours}`, [])
    return rows.map(normalizeServiceStat)
  }

  static async getServiceLatency(hours: number = 24, limit: number = 20): Promise<any[]> {
    const rows = await requestWithDefault<any[]>(`/services/latency?hours=${hours}&limit=${limit}`, [])
    return rows.map(normalizeLatencyRow)
  }

  static async getErrorAnalysis(hours: number = 24): Promise<any[]> {
    const rows = await requestWithDefault<any[]>(`/errors/analysis?hours=${hours}`, [])
    return rows.map(normalizeErrorRow)
  }

  /** 错误分类增强：状态码 / 异常类 / 服务级 */
  static async getErrorBreakdown(hours: number = 24): Promise<{
    hours: number
    totalErrorSpans: number
    byService: any[]
    byStatusCode: any[]
    byException: any[]
    byOther: any[]
  }> {
    const raw = await requestWithDefault<any>(`/errors/breakdown?hours=${hours}`, {})
    const normalizeBucket = (row: any) => ({
      category: row.category ?? '',
      key: row.key ?? '',
      label: row.label ?? row.key ?? '',
      count: Number(row.count ?? 0),
      serviceCount: Number(row.service_count ?? row.serviceCount ?? 0),
      services: Array.isArray(row.services) ? row.services : [],
      sampleMessage: row.sample_message ?? row.sampleMessage ?? '',
      sampleTraceId: row.sample_trace_id ?? row.sampleTraceId ?? '',
      sampleService: row.sample_service ?? row.sampleService ?? ''
    })
    return {
      hours: Number(raw.hours ?? hours),
      totalErrorSpans: Number(raw.total_error_spans ?? raw.totalErrorSpans ?? 0),
      byService: Array.isArray(raw.by_service)
        ? raw.by_service.map(normalizeErrorRow)
        : Array.isArray(raw.byService)
          ? raw.byService.map(normalizeErrorRow)
          : [],
      byStatusCode: (raw.by_status_code ?? raw.byStatusCode ?? []).map(normalizeBucket),
      byException: (raw.by_exception ?? raw.byException ?? []).map(normalizeBucket),
      byOther: (raw.by_other ?? raw.byOther ?? []).map(normalizeBucket)
    }
  }

  /** 返回 Collector 内部统计对象（非外层 wrapper） */
  static async getCollectorStats(): Promise<any> {
    const raw = await requestWithDefault<any>('/stats', {})
    if (raw && typeof raw === 'object' && raw.collectorStats) {
      return raw.collectorStats
    }
    return raw && typeof raw === 'object' ? raw : {}
  }

  static async getRecentSpans(hours: number = 24, limit: number = 50): Promise<any[]> {
    return this.getRecentTraces({ hours, limit })
  }

  static async getRecentSpansByService(serviceName: string, limit: number = 50): Promise<any[]> {
    return this.getRecentTraces({ service: serviceName, limit })
  }

  /** 按 Trace 聚合的链路列表 */
  static async getRecentTraces(params: {
    hours?: number
    limit?: number
    service?: string
    status?: string
    q?: string
    minDurationMs?: number
  } = {}): Promise<any[]> {
    const qs = new URLSearchParams()
    qs.set('hours', String(params.hours ?? 24))
    qs.set('limit', String(params.limit ?? 50))
    if (params.service) qs.set('service', params.service)
    if (params.status && params.status !== 'all') qs.set('status', params.status)
    if (params.q && params.q.trim()) qs.set('q', params.q.trim())
    if (params.minDurationMs && params.minDurationMs > 0) {
      qs.set('minDurationMs', String(params.minDurationMs))
    }
    return requestWithDefault<any[]>(`/traces/recent?${qs.toString()}`, [])
  }

  static async getTraceDetail(traceId: string): Promise<any[]> {
    return requestWithDefault<any[]>(`/traces/${encodeURIComponent(traceId)}`, [])
  }
}

export { apiClient }

export type {
  AxiosRequestConfig,
  AxiosResponse,
  AxiosError
}
