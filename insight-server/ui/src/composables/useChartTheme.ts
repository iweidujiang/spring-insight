import { computed } from 'vue'
import { useTheme } from './useTheme'
import { getChartThemeColors, getChartPalette, type ChartThemeColors } from '../utils/chartTheme'

export function useChartTheme() {
  const { effectiveTheme } = useTheme()
  const isDark = computed(() => effectiveTheme.value === 'dark')
  const colors = computed<ChartThemeColors>(() => getChartThemeColors(isDark.value))
  const palette = computed(() => getChartPalette(isDark.value))

  return { isDark, colors, palette }
}
