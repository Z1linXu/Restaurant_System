import type { BackendDiningTableConfig } from '../types/dinein'
import { apiRequest } from './apiClient'

export async function fetchDiningTables(storeId: number) {
  const params = new URLSearchParams({
    store_id: String(storeId),
  })

  return apiRequest<BackendDiningTableConfig[]>(`/api/v1/frontdesk/dining-tables?${params.toString()}`, { cache: 'no-store' })
}

export const DINING_TABLES_CHANGED_EVENT = 'restaurant-dining-tables-changed'

export function notifyDiningTablesChanged(storeId: number) {
  window.dispatchEvent(new CustomEvent(DINING_TABLES_CHANGED_EVENT, { detail: { storeId } }))
}

/** Always reads the server. Cached display configuration cannot authorize ordering. */
export async function requireActiveDiningTable(storeId: number, rawTableNo: string) {
  if (typeof navigator !== 'undefined' && !navigator.onLine) throw new Error('无法确认桌台状态，请联网后重试；本机草稿已保留。')
  let tables: BackendDiningTableConfig[]
  try {
    tables = (await fetchDiningTables(storeId)).filter((table) => table.store_id === storeId && table.is_active !== false)
  } catch {
    throw new Error('无法确认桌台状态，请检查网络后重试；本机草稿已保留。')
  }
  const raw = rawTableNo.trim()
  const base = raw.match(/^(.*)-(A|B)$/)?.[1]
  const exact = tables.filter((table) => (table.table_name || table.table_code) === raw)
  const names = exact.length ? exact : base ? tables.filter((table) => (table.table_name || table.table_code) === base) : []
  const matches = names.length ? names : tables.filter((table) => table.table_code === raw || base != null && table.table_code === base)
  if (matches.length !== 1) throw new Error(matches.length > 1
    ? '桌台身份不明确，请刷新桌台配置；本机草稿已保留。'
    : '该桌台已删除或不可用，请返回桌台列表；本机草稿已保留。')
  return matches[0]
}
