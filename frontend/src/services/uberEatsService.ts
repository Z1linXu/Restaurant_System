import { apiRequest } from './apiClient'
export interface UberItem { id: string; external_data: string; title: string; quantity: number; removed: boolean; notes: string; modifiers: UberItem[]; issues: string[] }
export interface UberOrder { id: number; status: string; display_id: string | null; uber_order_id: string; mapping_status: string; mapping_errors: string[]; last_error: string | null; local_order_id: number | null; placed_at: string | null; created_at: string; accepted_at: string | null; cancelled_at: string | null; snapshot: { notes: string; items: UberItem[] } | null }
export interface UberMapping { id?: number; kind: string; identifierType: string; uberIdentifier: string; uberItemId: string; localMenuItemId: number; localOptionCode?: string | null; localOptionGroup?: string | null; parentOptionCode?: string | null }
export interface UberConnection { unmapped_orders: number; enabled: boolean; environment: string; store: { id: number; uberStoreId: string; storeId: number } | null; webhook_status: string; last_event_at: string | null; mappings: UberMapping[] }
export interface UberChoice { id: number; code: string; group: string; zh: string; en: string; parentCode: string | null }
const base = (store: number) => `/api/v1/stores/${store}/integrations/uber-eats`
export const fetchUberOrders = (store: number) => apiRequest<UberOrder[]>(`${base(store)}/orders`)
export const fetchUberConnection = (store: number) => apiRequest<UberConnection>(`${base(store)}/connection`)
export const decideUberOrder = (store: number, id: number, action: 'accept' | 'deny' | 'retry-local', reason = 'OTHER') => apiRequest<UberOrder>(`${base(store)}/orders/${id}/${action}`, { method: 'POST', body: JSON.stringify({ reason_code: reason }) })
export const saveUberMapping = (store: number, mapping: UberMapping) => apiRequest<UberMapping>(`${base(store)}/mappings`, { method: 'PUT', body: JSON.stringify(mapping) })
export const bindUberStore = (store: number, id: string) => apiRequest(`${base(store)}/store`, { method: 'PUT', body: JSON.stringify({ uber_store_id: id }) })
export const fetchUberChoices = (store: number, item: number) => apiRequest<UberChoice[]>(`${base(store)}/mapping-options/${item}`)
export const canDecideUberOrder = (order: UberOrder) => ['PENDING', 'MAPPING_REQUIRED'].includes(order.status)
export const needsUberAttention = (order: UberOrder) => !['ACCEPTED', 'DENIED', 'CANCELLED'].includes(order.status)
