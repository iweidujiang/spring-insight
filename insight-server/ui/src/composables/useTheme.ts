import { computed, ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'si.ui.theme'

const theme = ref<ThemeMode>(loadStoredTheme())
const systemDark = ref(false)

let mediaQuery: MediaQueryList | null = null

function loadStoredTheme(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'light' || stored === 'dark' || stored === 'system') {
    return stored
  }
  return 'system'
}

function detectSystemDark(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function resolveEffectiveTheme(mode: ThemeMode, sysDark: boolean): 'light' | 'dark' {
  if (mode === 'system') return sysDark ? 'dark' : 'light'
  return mode
}

function applyTheme(effective: 'light' | 'dark') {
  document.documentElement.setAttribute('data-theme', effective)
}

function enableTransition() {
  document.documentElement.classList.add('si-theme-transitioning')
  setTimeout(() => document.documentElement.classList.remove('si-theme-transitioning'), 350)
}

function initSystemMediaListener() {
  if (typeof window === 'undefined' || !window.matchMedia) return
  mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  systemDark.value = mediaQuery.matches
  const handler = (e: MediaQueryListEvent) => {
    systemDark.value = e.matches
  }
  if (mediaQuery.addEventListener) {
    mediaQuery.addEventListener('change', handler)
  } else {
    mediaQuery.addListener(handler)
  }
}

const effectiveTheme = computed<'light' | 'dark'>(() =>
  resolveEffectiveTheme(theme.value, systemDark.value)
)

watch(
  [theme, systemDark],
  ([newMode, sysDark], [oldMode]) => {
    const effective = resolveEffectiveTheme(newMode, sysDark)
    if (oldMode !== undefined && oldMode !== newMode) {
      enableTransition()
    }
    applyTheme(effective)
    localStorage.setItem(STORAGE_KEY, newMode)
  },
  { immediate: true }
)

export function useTheme() {
  function setTheme(mode: ThemeMode) {
    theme.value = mode
  }

  function cycleTheme() {
    const order: ThemeMode[] = ['light', 'dark', 'system']
    const idx = order.indexOf(theme.value)
    theme.value = order[(idx + 1) % order.length]
  }

  return {
    theme,
    effectiveTheme,
    setTheme,
    cycleTheme
  }
}

export function initTheme() {
  systemDark.value = detectSystemDark()
  initSystemMediaListener()
}
