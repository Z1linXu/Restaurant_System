import type { BackendDiningTableConfig } from '../types/dinein'
import { apiRequest } from './apiClient'

export async function fetchDiningTables(storeId: number) {
  const params = new URLSearchParams({
    store_id: String(storeId),
  })

  return apiRequest<BackendDiningTableConfig[]>(`/api/v1/frontdesk/dining-tables?${params.toString()}`)
}
