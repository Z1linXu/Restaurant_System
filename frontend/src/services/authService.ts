import { assertEnvironmentSession, captureEnvironmentSession, getEnvironmentId } from '../offline/environmentScope'
import { apiRequest, clearAuthTokens, getRefreshToken, saveAuthTokens } from './apiClient'

export interface AuthUser {
  id: number
  username: string
  full_name: string
  role_code: string
  store_id: number
  store_name?: string | null
  organization_id: number | null
}

export interface LoginResponse {
  access_token: string
  refresh_token?: string | null
  expires_in: number
  user: AuthUser
  features: Record<string, boolean>
  permissions: string[]
}

export async function login(loginIdentifier: string, password: string) {
  const environmentSession = captureEnvironmentSession()
  const response = await apiRequest<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      login_identifier: loginIdentifier,
      password,
    }),
  })
  assertEnvironmentSession(environmentSession)
  saveAuthTokens(response.access_token, response.refresh_token)
  return response
}

export async function fetchCurrentUser() {
  return apiRequest<LoginResponse>('/api/v1/auth/me')
}

export async function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('Refresh token is missing')
  }
  const environmentSession = captureEnvironmentSession()
  const response = await apiRequest<LoginResponse>('/api/v1/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refresh_token: refreshToken }),
  })
  assertEnvironmentSession(environmentSession)
  saveAuthTokens(response.access_token, response.refresh_token)
  return response
}

export async function logout() {
  const environmentId = getEnvironmentId()
  const refreshToken = getRefreshToken()
  // Clear locally before waiting for revocation. Pending orders remain in IndexedDB.
  clearAuthTokens()
  if (refreshToken) {
    await apiRequest<void>(`${environmentId}/api/v1/auth/logout`, {
      method: 'POST',
      body: JSON.stringify({ refresh_token: refreshToken }),
    }).catch(() => undefined)
  }
}
