import { apiRequest } from './apiClient'

export interface FinishAllPreview {
  batch_id: string
  store_id: number
  store_name: string
  expires_at: number
  order_count: number
  table_slot_count: number
  orders: { order_id: number; table_no: string; table_display_name: string }[]
  blocked_tables: string[]
  preview_token: string
}
export interface FinishAllResult {
  batch_id: string
  completed: number
  skipped: number
  failed: number
  released_slot_count: number
  results: { order_id: number; table_no: string; outcome: string; reason: string | null }[]
  remaining_tables: string[]
}
export function previewFinishAllTables(storeId: number) {
  return apiRequest<FinishAllPreview>(`/api/v1/stores/${storeId}/orders/finish-all-tables/preview`, { cache: 'no-store' })
}
export function finishAllTables(storeId: number, previewToken: string) {
  return apiRequest<FinishAllResult>(`/api/v1/stores/${storeId}/orders/finish-all-tables`, {
    method: 'POST', body: JSON.stringify({ preview_token: previewToken }),
  })
}
