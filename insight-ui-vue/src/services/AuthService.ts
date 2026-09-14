import axios from 'axios'

const UI_TOKEN_KEY = 'spring-insight-ui-token'
const UI_USER_KEY = 'spring-insight-ui-username'

const authClient = axios.create({
  baseURL: '/api/v1/auth',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * @returns 本地保存的 UI 会话 token
 */
export function getUiToken(): string {
  return localStorage.getItem(UI_TOKEN_KEY) || ''
}

/**
 * @param token 会话 token；空则清除
 */
export function setUiToken(token: string) {
  if (!token) {
    localStorage.removeItem(UI_TOKEN_KEY)
    localStorage.removeItem(UI_USER_KEY)
    return
  }
  localStorage.setItem(UI_TOKEN_KEY, token)
}

/**
 * @returns 最近一次成功登录的用户名
 */
export function getUiUsername(): string {
  return localStorage.getItem(UI_USER_KEY) || ''
}

/**
 * @param username 登录用户名
 */
export function setUiUsername(username: string) {
  const value = (username || '').trim()
  if (!value) {
    localStorage.removeItem(UI_USER_KEY)
    return
  }
  localStorage.setItem(UI_USER_KEY, value)
}

/**
 * 查询 Server 是否启用 UI 登录。
 */
export async function fetchAuthStatus(): Promise<{ uiAuthEnabled: boolean; ingestAuthEnabled: boolean }> {
  const { data } = await authClient.get('/status')
  return {
    uiAuthEnabled: !!data.uiAuthEnabled,
    ingestAuthEnabled: !!data.ingestAuthEnabled
  }
}

/**
 * 登录并保存 token。
 */
export async function login(username: string, password: string): Promise<void> {
  const { data } = await authClient.post('/login', { username, password })
  if (!data?.success) {
    throw new Error(data?.message || '登录失败')
  }
  if (data.uiAuthEnabled === false) {
    setUiToken('')
    setUiUsername('')
    return
  }
  if (!data.token) {
    throw new Error(data?.message || '未返回 token')
  }
  setUiToken(data.token)
  setUiUsername(username)
}

/**
 * 注销本地会话。
 */
export async function logout(): Promise<void> {
  const token = getUiToken()
  try {
    if (token) {
      await authClient.post('/logout', null, {
        headers: { Authorization: `Bearer ${token}` }
      })
    }
  } finally {
    setUiToken('')
    setUiUsername('')
  }
}
