import axios from 'axios'

const UI_TOKEN_KEY = 'spring-insight-ui-token'

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
    return
  }
  localStorage.setItem(UI_TOKEN_KEY, token)
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
    return
  }
  if (!data.token) {
    throw new Error(data?.message || '未返回 token')
  }
  setUiToken(data.token)
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
  }
}
