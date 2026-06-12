import type { BackendDiningTableConfig } from '../types/dinein'

const DEFAULT_STORE_ID = 1
const DEFAULT_USER_ID = '1'

interface BackendApiResponse<T> {
  success: boolean
  message?: string
  data: T
}

async function request<T>(input: string, init?: RequestInit) {
  const response = await fetch(input, init)
  if (!response.ok) {
    throw new Error(`Request failed (${response.status})`)
  }

  const payload = (await response.json()) as BackendApiResponse<T>
  if (!payload.success) {
    throw new Error(payload.message || 'Request failed')
  }

  return payload.data
}

export async function fetchDiningTables(storeId = DEFAULT_STORE_ID) {
  const params = new URLSearchParams({
    store_id: String(storeId),
  })

  return request<BackendDiningTableConfig[]>(`/api/v1/frontdesk/dining-tables?${params.toString()}`, {
    headers: {
      'X-User-Id': DEFAULT_USER_ID,
    },
  })
}
