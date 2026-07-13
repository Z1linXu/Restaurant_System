import type { BackendMenuCatalog } from '../types/ordering'
import { apiRequest } from './apiClient'

export interface MenuRevisionResponse {
  store_id: number
  organization_id: number
  menu_revision: number
  menu_updated_at: string
  catalog_version: string
  tax_policy_version: string
  etag: string
}

export async function fetchMenuCatalog(storeId: number | string) {
  return apiRequest<BackendMenuCatalog>(`/api/v1/menu/catalog?store_id=${storeId}`)
}

export async function fetchMenuRevision(storeId: number | string) {
  return apiRequest<MenuRevisionResponse>(`/api/v1/menu/catalog/revision?store_id=${storeId}`)
}
