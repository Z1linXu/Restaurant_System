import type { BackendMenuCatalog } from '../types/ordering'
import { apiRequest } from './apiClient'

export async function fetchMenuCatalog(storeId: number | string) {
  return apiRequest<BackendMenuCatalog>(`/api/v1/menu/catalog?store_id=${storeId}`)
}
