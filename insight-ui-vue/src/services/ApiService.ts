import axios, { AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios'

// 类型定义
export interface ApiResponse<T> {
  data: T
  status: number
  statusText: string
}

// 创建Axios实例
const apiClient = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 通用请求方法
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

// 带默认值的请求方法
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

// API服务类
export class ApiService {
  // 获取服务列表
  static async getServiceNames(): Promise<string[]> {
    return requestWithDefault<string[]>('/api/services', [])
  }

  // 获取服务依赖关系
  static async getServiceDependencies(hours: number = 24): Promise<any[]> {
    return requestWithDefault<any[]>(`/api/dependencies?hours=${hours}`, [])
  }

  // 获取服务统计信息
  static async getServiceStats(): Promise<any[]> {
    return requestWithDefault<any[]>('/api/services/stats', [])
  }

  // 获取错误分析
  static async getErrorAnalysis(hours: number = 24): Promise<any[]> {
    return requestWithDefault<any[]>(`/api/errors/analysis?hours=${hours}`, [])
  }

  // 获取Collector统计信息
  static async getCollectorStats(): Promise<any> {
    return requestWithDefault<any>('/api/stats', {})
  }

  // 获取最近链路
  static async getRecentSpans(hours: number = 24, limit: number = 50): Promise<any[]> {
    return requestWithDefault<any[]>(`/api/traces/recent?hours=${hours}&limit=${limit}`, [])
  }

  // 获取指定服务的最近链路
  static async getRecentSpansByService(serviceName: string, limit: number = 50): Promise<any[]> {
    return requestWithDefault<any[]>(`/api/services/${serviceName}/traces?limit=${limit}`, [])
  }

  // 获取链路详情
  static async getTraceDetail(traceId: string): Promise<any[]> {
    return requestWithDefault<any[]>(`/api/traces/${traceId}`, [])
  }
}

// 导出请求实例，方便其他地方直接使用
export { apiClient }

// 导出类型
export type {
  AxiosRequestConfig,
  AxiosResponse,
  AxiosError
}
