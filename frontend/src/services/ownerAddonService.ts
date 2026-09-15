import { apiRequest } from './apiClient'

export interface StoreAddon {
  id: number
  store_id: number
  code: string
  name_zh: string
  name_en: string
  price: number
  active: boolean
  printing_configured: boolean
}

export interface AddonConflict {
  code: string | null
  option_ids: number[]
  names_zh: string[]
  names_en: string[]
  prices: number[]
  reason: string
}

export interface StoreAddonCatalog {
  addons: StoreAddon[]
  conflicts: AddonConflict[]
}

export interface ItemAddon extends StoreAddon {
  enabled: boolean
}

export type AddonValues = Pick<StoreAddon, 'name_zh' | 'name_en' | 'price' | 'active'>

export function fetchStoreAddons(storeId: number) {
  return apiRequest<StoreAddonCatalog>(`/api/v1/admin/menu/addons?store_id=${storeId}`)
}

export function createStoreAddon(payload: AddonValues & { store_id: number; code: string }) {
  return apiRequest<StoreAddon>('/api/v1/admin/menu/addons', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateStoreAddon(id: number, payload: AddonValues) {
  return apiRequest<StoreAddon>(`/api/v1/admin/menu/addons/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function fetchItemAddons(itemId: number) {
  return apiRequest<ItemAddon[]>(`/api/v1/admin/menu/items/${itemId}/addons`)
}

export function updateItemAddon(itemId: number, addonId: number, enabled: boolean) {
  return apiRequest<void>(`/api/v1/admin/menu/items/${itemId}/addons/${addonId}`, {
    method: 'PUT', body: JSON.stringify({ enabled }),
  })
}
