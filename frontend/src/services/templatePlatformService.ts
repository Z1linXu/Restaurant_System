import { apiRequest } from './apiClient'
import type { OrderOptionPayload } from '../utils/orderPricing'

export interface TemplateOption {
  id?: number
  option_type: string
  option_code: string | null
  option_group: string | null
  parent_option_id: number | null
  sort_order: number | null
  name_zh: string
  name_en: string
  name_fr?: string | null
  price_delta: number
  is_active: boolean
  is_default?: boolean
}

export interface TemplateOptionGroup {
  group_instance_code: string
  name: string
  name_translations?: Partial<Record<'zh-CN' | 'en' | 'fr-CA', string>>
  kind: 'SPICY_LEVEL' | 'ADD_ON' | 'REMOVE' | 'SIZE' | 'CUSTOM' | 'COMBO'
  selection_mode: 'SINGLE' | 'MULTIPLE'
  required: boolean
  min_select?: number | null
  max_select?: number | null
  sort_order: number
  is_active: boolean
  source_library_code?: string
  default_option_ids: number[]
  options: TemplateOption[]
}

export interface TemplateOptionGroups {
  groups: TemplateOptionGroup[]
  menu_revision?: number
}

export interface KitchenTicketGroup {
  group_code: string
  name: string
  enabled: boolean
  sort_order: number
  category_ids: number[]
  item_ids: number[]
  combo_component_codes?: string[]
  logical_target: string
  printer_id: number | null
  mock_failure?: 'FAILED' | 'UNCERTAIN' | null
}

export interface KitchenRoutingConfig {
  enabled: boolean
  revision?: number
  groups: KitchenTicketGroup[]
  item_overrides: { menu_item_id: number; group_codes: string[] }[]
}

export interface TemplatePrinter { id: number; name: string; is_active: boolean }
export interface RoutingDiagnostic { code: string; message: string; source_key?: string | null }
export interface KitchenPreviewItem { menu_item_id: number; quantity: number; options: OrderOptionPayload[] }
export interface KitchenRoutingPreview {
  tickets: {
    group_code: string
    group_name: string
    logical_target: string
    printer_id: number | null
    rendered_text: string
    source_keys: string[]
    diagnostics: RoutingDiagnostic[]
  }[]
  diagnostics: RoutingDiagnostic[]
  coverage: { source_key: string; group_codes: string[]; disposition: string; name?: string; menu_item_id?: number; parent_source_key?: string | null }[]
  menu_revision: number
  routing_revision: number
}

export interface TemplatePrintTicket {
  intent_id: number | string
  source_key: string
  order_id: number
  order_update_batch_id: number | null
  group_code: string
  group_name: string
  logical_target: string
  job_id: number | null
  state: string
  reason: string | null
  error_code: string | null
  rendered_text: string | null
  menu_revision: number | null
  routing_revision: number | null
  execution_mode: 'MOCK'
}

const base = (storeId: number) => `/api/v1/stores/${storeId}/template`
const put = <T>(url: string, value: unknown) => apiRequest<T>(url, { method: 'PUT', body: JSON.stringify(value) })

export const fetchOptionLibrary = (storeId: number) => apiRequest<TemplateOptionGroups>(`${base(storeId)}/option-library`)
export const saveOptionLibrary = (storeId: number, value: TemplateOptionGroups) => put<TemplateOptionGroups>(`${base(storeId)}/option-library`, value)
export const fetchItemOptionGroups = (storeId: number, itemId: number) => apiRequest<TemplateOptionGroups>(`${base(storeId)}/items/${itemId}/option-groups`)
export const saveItemOptionGroups = (storeId: number, itemId: number, value: TemplateOptionGroups) => put<TemplateOptionGroups>(`${base(storeId)}/items/${itemId}/option-groups`, value)
export const fetchKitchenRouting = (storeId: number) => apiRequest<KitchenRoutingConfig>(`${base(storeId)}/kitchen-routing`)
export const saveKitchenRouting = (storeId: number, value: KitchenRoutingConfig) => put<KitchenRoutingConfig>(`${base(storeId)}/kitchen-routing`, value)
export const fetchTemplatePrinters = (storeId: number) => apiRequest<TemplatePrinter[]>(`${base(storeId)}/printers`)
export const previewKitchenRouting = (storeId: number, input: { items: KitchenPreviewItem[] }) => apiRequest<KitchenRoutingPreview>(`${base(storeId)}/kitchen-routing/preview`, { method: 'POST', body: JSON.stringify(input) })
export const fetchTemplatePrintDiagnostics = (storeId: number, input: { orderId?: number; batchId?: number | null; signal?: AbortSignal } = {}) => {
  const params = new URLSearchParams()
  if (input.orderId != null) params.set('order_id', String(input.orderId))
  if (input.batchId != null) params.set('batch_id', String(input.batchId))
  return apiRequest<{ tickets: TemplatePrintTicket[] }>(`${base(storeId)}/printing-diagnostics${params.size ? `?${params}` : ''}`, { signal: input.signal })
}

export function templateTicketNeedsAttention(ticket: TemplatePrintTicket) {
  return ticket.job_id == null || ticket.state === 'UNRESOLVED' || ticket.state === 'FAILED' || ticket.state === 'CANCELLED'
    || ticket.state === 'UNCERTAIN' || Boolean(ticket.error_code || ticket.reason)
}
