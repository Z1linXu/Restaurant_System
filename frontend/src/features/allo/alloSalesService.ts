import { apiRequest } from '../../services/apiClient'
export interface Amounts { gross: number; discount: number; net: number; tax: number; total: number; collected: number }
export interface FinancialItem { id: number; quantity: number; voided_quantity: number; combo_role: string | null; status: string; item_name_snapshot_zh: string; item_name_snapshot_en: string; line_amount: number }
export interface Ledger { id: number; created_at: string; actor_name: string; order_no: string; order_type: string; table_no: string | null; pickup_no: string | null; action: string; reason: string | null; item_changes: string; before_snapshot: string; after_snapshot: string; collected_delta: number }
export interface FinancialDetail { order: { id: number; current_revision: number; status: string; discount_type: string | null; discount_value: number | null; discount_name: string | null }; amounts: Amounts; items: FinancialItem[]; editable: boolean; business_date: string; adjustments: Ledger[]; printing_notice: string }
export interface Adjustment { request_key: string; expected_revision: number; action: string; items?: {item_id: number; quantity: number}[]; discount_type?: string; discount_value?: number; discount_name?: string; reason?: string }
export interface Preset { id?: number; name: string; discount_type: string; value: number; enabled: boolean; revision?: number }
export interface Overview extends Amounts { business_date: string; timezone: string; generated_at: string; report_id?: string; rendered_text?: string; collected_note: string; order_count: number; dine_in: {count: number; total: number}; takeout: {count: number; total: number}; voided_orders: number; voided_quantity: number; voided_gross: number; adjustments: Ledger[] }
export const salesApi = <T,>(store: number, path: string, body?: unknown) => apiRequest<T>(`/api/v1/allo/stores/${store}/sales${path}`, body === undefined ? {} : { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(body) })
export const money = (n: unknown) => `$${Number(n ?? 0).toFixed(2)}`
export const notifySalesChanged = () => window.dispatchEvent(new Event('allo-sales-changed'))

export const actionLabel = (action:string) => ({VOID_ORDER:'作废整单',VOID_ITEMS:'作废菜品',DISCOUNT:'应用折扣',REMOVE_DISCOUNT:'取消折扣',TAKEOUT_REPAIR:'外带完成修复'} as Record<string,string>)[action] ?? action
export const localTime = (time:string) => new Date(time).toLocaleString('zh-CN',{timeZone:'America/Toronto',hour12:false})
