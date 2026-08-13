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

export function isSizeOption(option: OrderedMenuOption) {
  return normalized(option.option_group) === 'SIZE'
    || normalized(option.option_type) === 'SIZE'
}

export function sortMenuOptions<T extends OrderedMenuOption>(options: T[]) {
  return [...options].sort((left, right) => (
    (left.sort_order ?? Number.MAX_SAFE_INTEGER) - (right.sort_order ?? Number.MAX_SAFE_INTEGER)
    || left.id - right.id
  ))
}

function defaultOptionId<T extends OrderedMenuOption>(
  options: T[],
  predicate: (option: T) => boolean,
) {
  return sortMenuOptions(options).find((option) => option.is_active && predicate(option))?.id ?? null
}

function buildDefaultOptionOrder<T extends OrderedMenuOption>(
  options: T[],
  targetId: number,
  predicate: (option: T) => boolean,
) {
  const groupOptions = sortMenuOptions(options.filter(predicate))
  const target = groupOptions.find((option) => option.id === targetId && option.is_active)
  if (!target) return null
  return [target, ...groupOptions.filter((option) => option.id !== targetId)]
    .map((option, index) => ({ id: option.id, sort_order: (index + 1) * 10 }))
}

export function defaultNoodleTypeOptionId<T extends OrderedMenuOption>(options: T[]) {
  return defaultOptionId(options, isNoodleTypeOption)
}

export function buildDefaultNoodleTypeOrder<T extends OrderedMenuOption>(options: T[], targetId: number) {
  return buildDefaultOptionOrder(options, targetId, isNoodleTypeOption)
}

export function defaultSizeOptionId<T extends OrderedMenuOption>(options: T[]) {
  return defaultOptionId(options, isSizeOption)
}

export function buildDefaultSizeOrder<T extends OrderedMenuOption>(options: T[], targetId: number) {
  return buildDefaultOptionOrder(options, targetId, isSizeOption)
}
