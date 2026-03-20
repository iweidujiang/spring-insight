import axios, { AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios'

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

export class ApiService {
  static async getServiceNames(): Promise<string[]> {
    return requestWithDefault<string[]>('/services', [])
  }

  static async getServiceDependencies(hours: number = 24): Promise<any[]> {
    const rows = await requestWithDefault<any[]>(`/dependencies?hours=${hours}`, [])
    return rows.map(normalizeDependency)
  }

  static async getServiceStats(): Promise<any[]> {
    const rows = await requestWithDefault<any[]>('/services/stats', [])
    return rows.map(normalizeServiceStat)
  }

  static async getErrorAnalysis(hours: number = 24): Promise<any[]> {
    const rows = await requestWithDefault<any[]>(`/errors/analysis?hours=${hours}`, [])
    return rows.map(normalizeErrorRow)
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
    return requestWithDefault<any[]>(`/traces/recent?hours=${hours}&limit=${limit}`, [])
  }

  static async getRecentSpansByService(serviceName: string, limit: number = 50): Promise<any[]> {
    return requestWithDefault<any[]>(`/services/${encodeURIComponent(serviceName)}/traces?limit=${limit}`, [])
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
