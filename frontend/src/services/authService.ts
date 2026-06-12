import { apiRequest, clearAuthTokens, getRefreshToken, saveAuthTokens } from './apiClient'

export interface AuthUser {
  id: number
  username: string
  full_name: string
  role_code: string
  store_id: number
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
  const response = await apiRequest<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      login_identifier: loginIdentifier,
      password,
    }),
  })
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
  const response = await apiRequest<LoginResponse>('/api/v1/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refresh_token: refreshToken }),
  })
  saveAuthTokens(response.access_token, response.refresh_token)
  return response
}

export async function logout() {
  const refreshToken = getRefreshToken()
  if (refreshToken) {
    await apiRequest<void>('/api/v1/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refresh_token: refreshToken }),
    }).catch(() => undefined)
  }
  clearAuthTokens()
}
