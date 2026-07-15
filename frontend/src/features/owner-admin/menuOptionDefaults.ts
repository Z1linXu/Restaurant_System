export interface OrderedMenuOption {
  id: number
  option_type: string
  option_group: string | null
  sort_order: number | null
  is_active: boolean
}

function normalized(value: string | null | undefined) {
  return value?.trim().toUpperCase() ?? ''
}

export function isNoodleTypeOption(option: OrderedMenuOption) {
  return normalized(option.option_group) === 'NOODLE_TYPE'
    || normalized(option.option_type) === 'NOODLE_TYPE'
}

export function sortMenuOptions<T extends OrderedMenuOption>(options: T[]) {
  return [...options].sort((left, right) => (
    (left.sort_order ?? Number.MAX_SAFE_INTEGER) - (right.sort_order ?? Number.MAX_SAFE_INTEGER)
    || left.id - right.id
  ))
}

export function defaultNoodleTypeOptionId<T extends OrderedMenuOption>(options: T[]) {
  return sortMenuOptions(options).find((option) => option.is_active && isNoodleTypeOption(option))?.id ?? null
}

export function buildDefaultNoodleTypeOrder<T extends OrderedMenuOption>(options: T[], targetId: number) {
  const noodleTypes = sortMenuOptions(options.filter(isNoodleTypeOption))
  const target = noodleTypes.find((option) => option.id === targetId && option.is_active)
  if (!target) return null
  return [target, ...noodleTypes.filter((option) => option.id !== targetId)]
    .map((option, index) => ({ id: option.id, sort_order: (index + 1) * 10 }))
}
