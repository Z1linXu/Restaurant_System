import type { TemplateOptionGroup } from '../services/templatePlatformService'
import type { ChoiceOption, ItemCustomizationDraft, MenuItem } from '../types/ordering'
import type { TemplateOption } from '../services/templatePlatformService'
import { modifierIngredient } from './modifierSelection'
import { appI18n, translate } from '../i18n/engine'
import { businessLabel, supportedLocale } from '../i18n/locale'

export function templateChoiceOption(option: TemplateOption): ChoiceOption {
  return { id: String(option.id), labelEn: option.name_en, labelZh: option.name_zh, labelFr: option.name_fr,
    priceDelta: Number(option.price_delta), optionType: option.option_type,
    optionCode: option.option_code, optionGroup: option.option_group,
    parentOptionId: option.parent_option_id == null ? null : String(option.parent_option_id), sortOrder: option.sort_order }
}

export function activeTemplateGroups(item: MenuItem) {
  return [...(item.optionGroups ?? [])].filter((group) => group.is_active && group.kind !== 'COMBO')
    .sort((a, b) => a.sort_order - b.sort_order)
}

export function templateDefaultSelections(groups: TemplateOptionGroup[]) {
  return Object.fromEntries(groups.filter((group) => group.is_active && group.kind !== 'COMBO').map((group) => {
    const options = group.options.filter((option) => option.is_active && option.id != null)
    const defaults = options.filter((option) => option.is_default || group.default_option_ids.includes(option.id!))
    return [group.group_instance_code, Object.fromEntries((group.selection_mode === 'SINGLE' ? defaults.slice(0, 1) : defaults).map((option) => [String(option.id), 1]))]
  }))
}

export function templateSelectionError(item: MenuItem, draft: ItemCustomizationDraft): string | null {
  if (item.optionGroups == null) return null
  for (const group of activeTemplateGroups(item)) {
    const selected = Object.entries(draft.groupSelections?.[group.group_instance_code] ?? {}).filter(([, qty]) => qty > 0)
    const name = businessLabel({ name: group.name, translations: group.name_translations }, supportedLocale(appI18n.resolvedLanguage) ?? 'en')
    if (group.required && selected.length === 0) return translate('options.chooseRequired', { name })
    if (group.selection_mode === 'SINGLE' && (selected.length > 1 || selected.some(([, qty]) => qty !== 1))) return translate('options.chooseSingle', { name })
    const minimum = group.min_select ?? (group.required ? 1 : 0)
    const maximum = group.max_select ?? (group.selection_mode === 'SINGLE' ? 1 : null)
    if (selected.length < minimum) return translate('options.atLeast', { count: minimum })
    if (maximum != null && selected.length > maximum) return translate('options.atMost', { count: maximum })
    if (group.kind !== 'ADD_ON' && (group.min_select != null || group.max_select != null) && selected.some(([, qty]) => qty !== 1)) return translate('options.quantityOne', { name })
    if (selected.some(([id, qty]) => !Number.isSafeInteger(qty) || qty < 1 || !group.options.some((option) => option.is_active && String(option.id) === id))) return translate('options.changed', { name })
  }
  return null
}

export function selectTemplateOption(draft: ItemCustomizationDraft, group: TemplateOptionGroup, optionId: number, quantity: number, groups: TemplateOptionGroup[]): ItemCustomizationDraft {
  const selections = { ...(draft.groupSelections ?? {}) }
  selections[group.group_instance_code] = group.selection_mode === 'SINGLE'
    ? (quantity > 0 ? { [optionId]: 1 } : {})
    : { ...(selections[group.group_instance_code] ?? {}), [optionId]: quantity }
  const option = group.options.find((candidate) => candidate.id === optionId)
  const ingredient = modifierIngredient(option?.option_code)
  if (quantity > 0 && ingredient && (group.kind === 'ADD_ON' || group.kind === 'REMOVE')) {
    const opposite = group.kind === 'ADD_ON' ? 'REMOVE' : 'ADD_ON'
    for (const other of groups.filter((candidate) => candidate.kind === opposite)) {
      const next = { ...(selections[other.group_instance_code] ?? {}) }
      for (const candidate of other.options) if (candidate.id != null && modifierIngredient(candidate.option_code) === ingredient) delete next[String(candidate.id)]
      selections[other.group_instance_code] = next
    }
  }
  return { ...draft, groupSelections: selections }
}
