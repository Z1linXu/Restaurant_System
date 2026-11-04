import type { MenuItem, OrderLineItem } from '../../types/ordering'

/** Display convenience only. The server remains authoritative for eligibility. */
export function canShowRefill(item: MenuItem, orderType: 'dine_in' | 'pickup', lines: OrderLineItem[] | undefined) {
  if (!item.refillCategoryCode) return true
  if (orderType !== 'dine_in') return false
  return (lines ?? []).some((line) => line.quantity > 0 && line.unitPrice > 0 && line.categoryCodeSnapshot === item.refillCategoryCode)
}
