/**
 * ECharts 图表主题色工具
 * 根据当前亮/暗色主题提供调色板与样式 token
 */

const LIGHT_PALETTE = [
  '#0f766e', '#15803d', '#0d9488', '#b45309',
  '#b91c1c', '#1d4ed8', '#c2410c', '#047857'
]

const DARK_PALETTE = [
  '#2dd4bf', '#4ade80', '#22d3ee', '#fbbf24',
  '#f87171', '#60a5fa', '#fb923c', '#34d399'
]

export interface ChartThemeColors {
  palette: string[]
  teal: string
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
  teal: '#0f766e',
  muted: '#6b7f76',
  ink: '#15241f',
  inkSoft: '#3d524a',
  surface: 'rgba(255, 252, 250, 0.96)',
  axisLine: 'rgba(20, 83, 45, 0.25)',
  splitLine: 'rgba(20, 83, 45, 0.1)',
  tooltipBg: 'rgba(255, 252, 250, 0.96)',
  tooltipBorder: 'rgba(20, 83, 45, 0.15)',
  tooltipText: '#15241f',
  danger: '#b91c1c',
  warning: '#b45309',
  degraded: '#c2410c',
  success: '#15803d'
}

const DARK: ChartThemeColors = {
  palette: DARK_PALETTE,
  teal: '#2dd4bf',
  muted: '#7d8a96',
  ink: '#e6edf3',
  inkSoft: '#b0bac5',
  surface: 'rgba(26, 31, 38, 0.96)',
  axisLine: 'rgba(45, 212, 191, 0.2)',
  splitLine: 'rgba(45, 212, 191, 0.06)',
  tooltipBg: 'rgba(26, 31, 38, 0.96)',
  tooltipBorder: 'rgba(45, 212, 191, 0.15)',
  tooltipText: '#e6edf3',
  danger: '#f87171',
  warning: '#fbbf24',
  degraded: '#fb923c',
  success: '#4ade80'
}

export function getChartThemeColors(isDark: boolean): ChartThemeColors {
  return isDark ? DARK : LIGHT
}

export function getChartPalette(isDark: boolean): string[] {
  return isDark ? DARK_PALETTE : LIGHT_PALETTE
}
