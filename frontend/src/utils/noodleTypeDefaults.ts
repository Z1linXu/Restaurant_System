import type { MenuItem } from '../types/ordering'

const COLD_CHICKEN_NOODLE_SKU = 'cold_noodle_shredded_chicken'
const THIN_NOODLE_OPTION_CODE = 'noodle_thin'

function normalized(value: string | null | undefined) {
  return value?.trim().toLowerCase() ?? ''
}

export function resolveDefaultNoodleTypeId(menuItem: MenuItem | undefined) {
  const noodleTypes = menuItem?.customization?.noodleTypes
  if (!noodleTypes?.length) {
    return undefined
  }

  if (normalized(menuItem?.sku) !== COLD_CHICKEN_NOODLE_SKU) {
    return noodleTypes[0]?.id
  }

  const stableMatch = noodleTypes.find(
    (option) => normalized(option.optionCode) === THIN_NOODLE_OPTION_CODE,
  )
  if (stableMatch) {
    return stableMatch.id
  }

  // Compatibility for menu rows created before stable option codes were populated.
  const legacyMatch = noodleTypes.find(
    (option) => option.labelZh.trim() === '细' || normalized(option.labelEn) === 'thin',
  )
  return legacyMatch?.id ?? noodleTypes[0]?.id
}

export function resolveNoodleTypeId(menuItem: MenuItem | undefined, savedOptionId?: string) {
  return savedOptionId ?? resolveDefaultNoodleTypeId(menuItem)
}
