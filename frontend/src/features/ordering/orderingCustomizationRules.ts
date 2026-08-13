import type { MenuItem } from '../../types/ordering'

const QUICK_ADD_DRINK_CATEGORY_CODES = new Set(['DRINK', 'ALCOHOL', 'MILK_TEA'])
const QUICK_ADD_DRINK_ITEM_TYPES = new Set(['DRINK', 'BEVERAGE'])

function normalizeCode(value: string | null | undefined) {
  return (value ?? '').trim().toUpperCase()
}

export function hasRequiredCustomization(item: MenuItem) {
  const sizeRequiresChoice = Boolean(
    item.customization?.sizes?.required
    && (item.customization.sizes.options.length > 1),
  )
  return Boolean(sizeRequiresChoice || item.customization?.soupBases?.required)
}

export function isQuickAddItem(item: MenuItem) {
  const categoryCode = normalizeCode(item.categoryCode)
  const itemType = normalizeCode(item.itemType)
  if (categoryCode === 'FRIED') {
    return !item.customization
  }
  if (QUICK_ADD_DRINK_CATEGORY_CODES.has(categoryCode) || QUICK_ADD_DRINK_ITEM_TYPES.has(itemType)) {
    return !hasRequiredCustomization(item)
  }
  return false
}
