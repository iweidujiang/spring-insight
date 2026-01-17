import axios from 'axios'

// 创建Axios实例
const apiClient = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// API服务类
export class ApiService {
  // 获取服务列表
  static async getServiceNames(): Promise<string[]> {
    try {
      const response = await apiClient.get('/api/services')
      return response.data
    } catch (error) {
      console.error('获取服务列表失败:', error)
      return []
    }
  }

  // 获取服务依赖关系
  static async getServiceDependencies(hours: number = 24): Promise<any[]> {
    try {
      const response = await apiClient.get(`/api/dependencies?hours=${hours}`)
      return response.data
    } catch (error) {
      console.error('获取服务依赖关系失败:', error)
      return []
    }
  }

  // 获取服务统计信息
  static async getServiceStats(): Promise<any[]> {
    try {
      const response = await apiClient.get('/api/services/stats')
      return response.data
    } catch (error) {
      console.error('获取服务统计信息失败:', error)
      return []
    }
  }

  // 获取错误分析
  static async getErrorAnalysis(hours: number = 24): Promise<any[]> {
    try {
      const response = await apiClient.get(`/api/errors/analysis?hours=${hours}`)
      return response.data
    } catch (error) {
      console.error('获取错误分析失败:', error)
      return []
    }
  }

  // 获取Collector统计信息
  static async getCollectorStats(): Promise<any> {
    try {
      const response = await apiClient.get('/api/stats')
      return response.data
    } catch (error) {
      console.error('获取Collector统计信息失败:', error)
      return {}
    }
  }

  // 获取最近链路
  static async getRecentSpans(hours: number = 24, limit: number = 50): Promise<any[]> {
    try {
      const response = await apiClient.get(`/api/traces/recent?hours=${hours}&limit=${limit}`)
      return response.data
    } catch (error) {
      console.error('获取最近链路失败:', error)
      return []
    }
  }

  // 获取指定服务的最近链路
  static async getRecentSpansByService(serviceName: string, limit: number = 50): Promise<any[]> {
    try {
      const response = await apiClient.get(`/api/services/${serviceName}/traces?limit=${limit}`)
      return response.data
    } catch (error) {
      console.error(`获取服务${serviceName}的最近链路失败:`, error)
      return []
    }
  }

  // 获取链路详情
  static async getTraceDetail(traceId: string): Promise<any[]> {
    try {
      const response = await apiClient.get(`/api/traces/${traceId}`)
      return response.data
    } catch (error) {
      console.error(`获取链路${traceId}详情失败:`, error)
      return []
    }
  }
}
