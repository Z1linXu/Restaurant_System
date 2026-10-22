import { apiRequest } from './apiClient'

export interface StoreRemove {
  id: number
  store_id: number
  code: string
  name_zh: string
  name_en: string
  active: boolean
  sort_order: number
  item_ids: number[]
}

export interface RemoveConflict {
  code: string | null
  option_ids: number[]
  names_zh: string[]
  names_en: string[]
  reason: string
}

export interface RemoveCatalog {
  removes: StoreRemove[]
  conflicts: RemoveConflict[]
  legacy_options?: Array<{ option_id: number; item_id: number; code: string | null; name_zh: string; name_en: string; active: boolean }>
}

export type RemoveMode = 'ALLOW' | 'DENY' | 'INHERIT'
export interface RemoveApplicability {
  remove_id: number
  store_id: number
  category_ids: number[]
  items: Array<{ item_id: number; name_zh: string; name_en: string; category_id: number | null; mode: RemoveMode; effective: boolean }>
}

export type RemoveValues = Pick<StoreRemove, 'name_zh' | 'name_en' | 'active' | 'sort_order'>
const base = '/api/v1/admin/menu/removes'

export function fetchStoreRemoves(storeId: number) {
  return apiRequest<RemoveCatalog>(`${base}?store_id=${storeId}`)
}

export function createStoreRemove(payload: RemoveValues & { store_id: number; code: string }) {
  return apiRequest<StoreRemove>(base, { method: 'POST', body: JSON.stringify(payload) })
}

export function updateStoreRemove(id: number, storeId: number, payload: RemoveValues) {
  return apiRequest<StoreRemove>(`${base}/${id}?store_id=${storeId}`, {
    method: 'PUT', body: JSON.stringify({ ...payload, store_id: storeId }),
  })
}

export function reorderStoreRemoves(storeId: number, ids: number[]) {
  return apiRequest<void>(`${base}/reorder`, { method: 'PUT', body: JSON.stringify({ store_id: storeId, ids }) })
}

export function fetchRemoveApplicability(id: number, storeId: number) {
  return apiRequest<RemoveApplicability>(`${base}/${id}/applicability?store_id=${storeId}`)
}

export function updateRemoveApplicability(id: number, storeId: number, value: RemoveApplicability) {
  return apiRequest<RemoveApplicability>(`${base}/${id}/applicability?store_id=${storeId}`, {
    method: 'PUT', body: JSON.stringify({ store_id: storeId, category_ids: value.category_ids,
      items: value.items.map(({ item_id, mode }) => ({ item_id, mode })) }),
  })
}
