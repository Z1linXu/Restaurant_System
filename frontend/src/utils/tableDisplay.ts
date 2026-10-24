import type { BackendDiningTableConfig, SplitLabelMode, TableSeatCode } from '../types/dinein'

export function normalizeSplitLabelMode(value: unknown): SplitLabelMode {
  return value === 'INSIDE_OUTSIDE' ? 'INSIDE_OUTSIDE' : 'LEFT_RIGHT'
}

export function splitSideLabel(seat: TableSeatCode, mode?: SplitLabelMode) {
  return normalizeSplitLabelMode(mode) === 'INSIDE_OUTSIDE'
    ? seat === 'A' ? '里' : '外'
    : seat === 'A' ? '左' : '右'
}

export function formatConfiguredSplitLabel(baseLabel: string, seat: TableSeatCode, mode?: SplitLabelMode) {
  const separator = normalizeSplitLabelMode(mode) === 'INSIDE_OUTSIDE' ? '' : '-'
  return `${baseLabel}${separator}${splitSideLabel(seat, mode)}`
}

/** Legacy fallback only. Never use the result as a slot ID, route value or order table_no. */
export function formatSplitSlotLabel(value: string | null | undefined) {
  if (!value) {
    return value ?? ''
  }

  return value
    .replace(/-A\b/g, '-左')
    .replace(/-B\b/g, '-右')
}

export function findConfiguredTable(value: string, tables: readonly BackendDiningTableConfig[]) {
  const exact = tables.filter((table) => (table.table_name || table.table_code) === value)
  if (exact.length > 0) return exact.length === 1 ? exact[0] : undefined
  const split = value.match(/^(.*)-(A|B)$/)
  const matches = split ? tables.filter((table) => (table.table_name || table.table_code) === split[1]) : []
  return matches.length === 1 ? matches[0] : undefined
}

export function formatDiningTableLabel(value: string | null | undefined, tables: readonly BackendDiningTableConfig[]) {
  if (!value) return value ?? ''
  // A literal full-table name (including 1-左 / 1里 / 1-A) wins over suffix interpretation.
  const exact = tables.find((table) => (table.table_name || table.table_code) === value)
  if (exact) return value
  const split = value.match(/^(.*)-(A|B)$/)
  const table = split ? findConfiguredTable(value, tables) : undefined
  return split && table
    ? formatConfiguredSplitLabel(split[1], split[2] as TableSeatCode, normalizeSplitLabelMode(table.split_label_mode))
    : formatSplitSlotLabel(value)
}

export function orderLocationDisplay(order: {
  table_no?: string | null
  table_display_name?: string | null
  pickup_no?: string | null
}, fallback: string) {
  return order.table_no
    ? order.table_display_name || formatSplitSlotLabel(order.table_no)
    : order.pickup_no || fallback
}
