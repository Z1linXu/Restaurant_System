import type { BackendApiResponse } from '../types/ordering'

const ACCESS_TOKEN_KEY = 'restaurant_pos_access_token'
const REFRESH_TOKEN_KEY = 'restaurant_pos_refresh_token'
const REFRESH_ENDPOINT = '/api/v1/auth/refresh'

let refreshInFlight: Promise<void> | null = null

export function getAccessToken() {
  return window.localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken() {
  return window.localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function saveAuthTokens(accessToken: string, refreshToken?: string | null) {
  window.localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  if (refreshToken) {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  }
}

export function clearAuthTokens() {
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export class ApiRequestError extends Error {
  status: number
  code?: string
  userMessage: string
  raw?: unknown

  constructor(status: number, message: string, userMessage?: string, raw?: unknown, code?: string) {
    super(message)
    this.status = status
    this.userMessage = userMessage ?? message
    this.raw = raw
    this.code = code
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isSafeBackendMessage(message: string | undefined) {
  if (!message) return false
  const normalized = message.toLowerCase()
  return !normalized.includes('exception')
    && !normalized.includes('stacktrace')
    && !normalized.includes('java.')
    && !normalized.includes('org.springframework')
}

export function userMessageForStatus(status: number, backendMessage?: string) {
  if (status === 400 && isSafeBackendMessage(backendMessage)) {
    return backendMessage!
  }
  if (status === 401) {
    return '登录已过期，请重新登录 / Session expired. Please sign in again.'
  }
  if (status === 403) {
    if (backendMessage?.toLowerCase().includes('store')) {
      return '你没有权限访问这家门店 / You do not have access to this store.'
    }
    return '没有权限访问 / Access denied.'
  }
  if (status >= 500) {
    return '系统错误，请稍后重试或联系管理员 / System error. Please try again or contact support.'
  }
  if (isSafeBackendMessage(backendMessage)) {
    return backendMessage!
  }
  return `请求失败 (${status}) / Request failed (${status})`
}

export function getApiUserMessage(error: unknown, fallback = '请求失败，请稍后重试 / Request failed. Please try again.') {
  if (error instanceof ApiRequestError) {
    return error.userMessage
  }
  if (error instanceof Error) {
    return error.message
  }
  return fallback
}

async function readResponsePayload(response: Response) {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

function messageFromPayload(payload: unknown) {
  if (!isRecord(payload)) return undefined
  const message = payload.message
  return typeof message === 'string' ? message : undefined
}

export function buildApiHeaders(extraHeaders: HeadersInit = {}, includeAuth = true) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(extraHeaders as Record<string, string>),
  }
  const accessToken = includeAuth ? getAccessToken() : null
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }
  delete headers['X-User-Id']
  return headers
}

function requestPath(input: string) {
  try {
    return new URL(input, window.location.origin).pathname
  } catch {
    return input
  }
}

function isAuthCredentialEndpoint(input: string) {
  const path = requestPath(input)
  return path.startsWith('/api/v1/auth/login')
    || path.startsWith('/api/v1/auth/refresh')
    || path.startsWith('/api/v1/auth/logout')
}

function canAttemptRefresh(input: string) {
  return Boolean(getRefreshToken()) && !isAuthCredentialEndpoint(input)
}

async function refreshSessionOnce() {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = getRefreshToken()
      if (!refreshToken) {
        throw new ApiRequestError(401, 'Refresh token is missing', userMessageForStatus(401))
      }

      const response = await fetch(REFRESH_ENDPOINT, {
        method: 'POST',
        headers: buildApiHeaders({}, false),
        body: JSON.stringify({ refresh_token: refreshToken }),
      })
      const payload = await readResponsePayload(response)
      if (!response.ok) {
        const backendMessage = messageFromPayload(payload)
        throw new ApiRequestError(
          response.status,
          backendMessage || `Request failed (${response.status})`,
          userMessageForStatus(response.status, backendMessage),
          payload,
        )
      }

      const apiPayload = payload as BackendApiResponse<{
        access_token: string
        refresh_token?: string | null
      }>
      if (!apiPayload.success || !apiPayload.data?.access_token) {
        const backendMessage = apiPayload.message || 'Session refresh failed'
        throw new ApiRequestError(401, backendMessage, userMessageForStatus(401), apiPayload)
      }
      saveAuthTokens(apiPayload.data.access_token, apiPayload.data.refresh_token)
      window.dispatchEvent(new CustomEvent('restaurant-auth-updated'))
    })().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function fetchWithAuth(input: string, init: RequestInit = {}) {
  const includeAuth = !isAuthCredentialEndpoint(input)
  const accessToken = includeAuth ? getAccessToken() : null
  const response = await fetch(input, {
    ...init,
    headers: buildApiHeaders(init.headers, includeAuth),
  })
  return {
    response,
    payload: await readResponsePayload(response),
    accessToken,
  }
}

export async function apiRequest<T>(input: string, init: RequestInit = {}) {
  let { response, payload, accessToken } = await fetchWithAuth(input, init)

  if (response.status === 401 && canAttemptRefresh(input)) {
    try {
      if (accessToken && getAccessToken() && getAccessToken() !== accessToken) {
        ;({ response, payload, accessToken } = await fetchWithAuth(input, init))
      } else {
        await refreshSessionOnce()
        ;({ response, payload, accessToken } = await fetchWithAuth(input, init))
      }
    } catch (refreshError) {
      clearAuthTokens()
      window.dispatchEvent(new CustomEvent('restaurant-auth-expired'))
      throw refreshError
    }
  }

  if (!response.ok) {
    if (response.status === 401) {
      clearAuthTokens()
      window.dispatchEvent(new CustomEvent('restaurant-auth-expired'))
    }
    const backendMessage = messageFromPayload(payload)
    const userMessage = userMessageForStatus(response.status, backendMessage)
    console.error('[apiRequest] request failed', {
      input,
      status: response.status,
      backendMessage,
      payload,
    })
    throw new ApiRequestError(
      response.status,
      backendMessage || `Request failed (${response.status})`,
      userMessage,
      payload,
    )
  }
  const apiPayload = payload as BackendApiResponse<T>
  if (!apiPayload.success) {
    const backendMessage = apiPayload.message || 'Request failed'
    throw new ApiRequestError(200, backendMessage, userMessageForStatus(400, backendMessage), apiPayload)
  }
  return apiPayload.data
}
