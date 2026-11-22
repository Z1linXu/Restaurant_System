import { apiRequest } from '../../services/apiClient'

export interface Amounts {
  gross: number
  discount: number
  net: number
  tax: number
  total: number
  collected: number
}

interface FinancialName {
  item_name_snapshot_zh: string | null
  item_name_snapshot_en: string | null
  item_name_snapshot_fr: string | null
}

export interface FinancialOption {
  id: number
  option_name_snapshot_zh: string | null
  option_name_snapshot_en: string | null
  option_name_snapshot_fr: string | null
  quantity: number
  per_serving: number
  voided_quantity: number
  standalone_voided_quantity: number
  is_addon: boolean
}

export interface FinancialItem extends FinancialName {
  id: number
  quantity: number
  voided_quantity: number
  combo_role: string | null
  combo_group_no: number | null
  status: string
  item_sku_snapshot: string | null
  notes: string | null
  line_amount: number
  effective_line_amount: number
  options: FinancialOption[]
}

export interface Ledger {
  id: number
  display_time: string
  actor_name: string
  order_type: string
  table_no: string | null
  pickup_no: string | null
  action: string
  reason: string | null
  item_changes: string
  before_snapshot: string
  after_snapshot: string
  collected_delta: number
}

export interface FinancialDetail {
  order: {
    id: number
    current_revision: number
    status: string
    order_notes: string | null
    discount_type: string | null
    discount_name: string | null
    discount_coupon_id: number | null
  }
  amounts: Amounts
  items: FinancialItem[]
  editable: boolean
  business_date: string
  adjustments: Ledger[]
  printing_notice: string
}

export interface Adjustment {
  request_key: string
  expected_revision: number
  action: string
  items?: { item_id: number; quantity: number }[]
  addons?: { option_id: number; quantity: number }[]
  coupon?: { id: number; revision: number }
  discount_type?: string
  discount_value?: number
  discount_name?: string
  reason?: string
}

export interface AdjustmentPreview {
  before: Amounts
  after: Amounts
  items: { name: string; quantity: number; option_id?: number }[]
  discount_name: string
}

export interface Preset {
  id: number
  name: string
  discount_type: string
  value: number
  enabled: boolean
  revision: number
}

export type PresetDraft = Omit<Preset, 'id' | 'revision'> & Partial<Pick<Preset, 'id' | 'revision'>>

export interface Overview extends Amounts {
  business_date: string
  report_id?: string
  rendered_text?: string
  collected_note: string
  order_count: number
  dine_in: { count: number; total: number }
  takeout: { count: number; total: number }
  voided_orders: number
  voided_quantity: number
  voided_gross: number
  voided_addon_quantity: number
  adjustments: Ledger[]
}

export interface RecentOrder {
  id: number
  order_type: string
  table_no: string | null
  pickup_no: string | null
  status: string
  submitted_time: string
  total_amount: number
  discount_amount: number
  has_voids: boolean
}

export interface RecentOrders {
  date: string
  earliest_date: string
  latest_date: string
  orders: RecentOrder[]
  has_more: boolean
}

export const salesApi = <T,>(store: number, path: string, body?: unknown) =>
  apiRequest<T>(`/api/v1/allo/stores/${store}/sales${path}`, body === undefined ? {} : {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

export const money = (n: unknown) => `$${Number(n ?? 0).toFixed(2)}`
export const notifySalesChanged = () => window.dispatchEvent(new Event('allo-sales-changed'))

const actionLabels: Record<string, string> = {
  VOID_ORDER: '作废整单',
  VOID_ITEMS: '作废菜品或加料',
  DISCOUNT: '应用折扣',
  REMOVE_DISCOUNT: '取消折扣',
  APPLY_COUPON: '应用 Coupon',
  REMOVE_COUPON: '移除 Coupon',
  TAKEOUT_REPAIR: '外带完成修复',
}

export const actionLabel = (action: string) => actionLabels[action] ?? action
