import type { LoginResponse } from './authService'
import { apiRequest, saveAuthTokens } from './apiClient'

export interface DevTestUser {
  login_identifier: string
  label: string
  full_name: string
  role_code: string
}

export async function fetchDevTestUsers() {
  return apiRequest<DevTestUser[]>('/api/v1/dev/test-users')
}

export async function switchDevUser(loginIdentifier: string) {
  const response = await apiRequest<LoginResponse>('/api/v1/dev/switch-user', {
    method: 'POST',
    body: JSON.stringify({ login_identifier: loginIdentifier }),
  })
  saveAuthTokens(response.access_token, response.refresh_token)
  return response
}
