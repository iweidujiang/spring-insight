/**
 * ECharts 图表主题色工具
 * 根据当前亮/暗色主题提供调色板与样式 token
 */

const LIGHT_PALETTE = [
  '#00B8D4', '#10B981', '#00D2FF', '#F59E0B',
  '#EF4444', '#1d4ed8', '#EA580C', '#059669'
]

const DARK_PALETTE = [
  '#38BDF8', '#34D399', '#22D3EE', '#FBBF24',
  '#F87171', '#60A5FA', '#FB923C', '#34D399'
]

export interface ChartThemeColors {
  palette: string[]
  brand: string
  muted: string
  ink: string
  inkSoft: string
  surface: string
  axisLine: string
  splitLine: string
  tooltipBg: string
  tooltipBorder: string
  tooltipText: string
  danger: string
  warning: string
  /** 降级（橙色），介于 warning 与 danger 之间 */
  degraded: string
  success: string
}

const LIGHT: ChartThemeColors = {
  palette: LIGHT_PALETTE,
  brand: '#00B8D4',
  muted: '#94A3B8',
  ink: '#1E293B',
  inkSoft: '#475569',
  surface: 'rgba(255, 255, 255, 0.95)',
  axisLine: 'rgba(0, 184, 212, 0.20)',
  splitLine: 'rgba(0, 184, 212, 0.06)',
  tooltipBg: 'rgba(255, 255, 255, 0.95)',
  tooltipBorder: 'rgba(0, 184, 212, 0.10)',
  tooltipText: '#1E293B',
  danger: '#EF4444',
  warning: '#F59E0B',
  degraded: '#EA580C',
  success: '#10B981'
}

const DARK: ChartThemeColors = {
  palette: DARK_PALETTE,
  brand: '#38BDF8',
  muted: '#64748B',
  ink: '#E2E8F0',
  inkSoft: '#94A3B8',
  surface: 'rgba(21, 27, 38, 0.95)',
  axisLine: 'rgba(56, 189, 248, 0.15)',
  splitLine: 'rgba(56, 189, 248, 0.04)',
  tooltipBg: 'rgba(21, 27, 38, 0.95)',
  tooltipBorder: 'rgba(56, 189, 248, 0.12)',
  tooltipText: '#E2E8F0',
  danger: '#F87171',
  warning: '#FBBF24',
  degraded: '#FB923C',
  success: '#34D399'
}

export function getChartThemeColors(isDark: boolean): ChartThemeColors {
  return isDark ? DARK : LIGHT
}

export function getChartPalette(isDark: boolean): string[] {
  return isDark ? DARK_PALETTE : LIGHT_PALETTE
}
