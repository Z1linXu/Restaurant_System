import type { BackendApiResponse } from '../types/ordering'

const ACCESS_TOKEN_KEY = 'restaurant_pos_access_token'
const REFRESH_TOKEN_KEY = 'restaurant_pos_refresh_token'

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

export function buildApiHeaders(extraHeaders: HeadersInit = {}) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(extraHeaders as Record<string, string>),
  }
  const accessToken = getAccessToken()
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }
  return headers
}

export async function apiRequest<T>(input: string, init: RequestInit = {}) {
  const response = await fetch(input, {
    ...init,
    headers: buildApiHeaders(init.headers),
  })
  if (!response.ok) {
    throw new Error(`Request failed (${response.status})`)
  }
  const payload = (await response.json()) as BackendApiResponse<T>
  if (!payload.success) {
    throw new Error(payload.message || 'Request failed')
  }
  return payload.data
}
