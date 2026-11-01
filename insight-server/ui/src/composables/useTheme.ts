import { computed } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

function applyTheme(effective: 'light' | 'dark') {
  document.documentElement.setAttribute('data-theme', effective)
}

const effectiveTheme = computed<'light' | 'dark'>(() => 'dark')

export function useTheme() {
  return {
    theme: computed<'light' | 'dark' | 'system'>(() => 'dark'),
    effectiveTheme,
    setTheme: (_mode: ThemeMode) => {},
    cycleTheme: () => {}
  }
}

export function initTheme() {
  applyTheme('dark')
}
