import type { MenuItem } from '../../types/ordering'

export function requiresCustomization(item: MenuItem) {
  const config = item.customization
  if (!config) return false
  const sizes = config.sizes?.options ?? []
  return Boolean(
    // A fixed conventional size is filled in by buildDefaultDraft.
    sizes.length > 1
    || sizes.some((size) => size.portionPrice != null)
    || config.soupBases?.options.length
    || config.noodleTypes?.length
    || config.spicyLevels?.length
    || config.combo
    || config.addOns?.length
    || config.removeOptions?.length,
  )
}
