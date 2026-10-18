import type { ChoiceOption, ItemCustomizationDraft, MenuItem } from '../types/ordering'
import { resolveComboGroupOptionId } from './comboSelection'

export function isToggleAddOn(option: ChoiceOption) {
  if (option.maxQuantity != null) return option.maxQuantity === 1
  // Preserve the legacy menu presentation when there is no typed quantity rule.
  return (option.priceDelta ?? 0) === 0 && (option.labelZh === '加香菜' || option.labelZh === '加葱')
}

export function customizationValidationMessage(menuItem: MenuItem, draft: ItemCustomizationDraft): string | null {
  const customization = menuItem.customization
  if (customization?.sizes?.required && !customization.sizes.options.some((option) => option.id === draft.sizeId)) {
    return '请选择规格。'
  }
  if (customization?.soupBases?.required && !customization.soupBases.options.some((option) => option.id === draft.soupBaseId)) {
    return '请选择汤底。'
  }
  if (customization?.noodleTypeRequired && !customization.noodleTypes?.some((option) => option.id === draft.noodleTypeId)) {
    return '请选择面型。'
  }
  for (const group of customization?.modifierGroups ?? []) {
    const selected = draft.modifierSelections?.[group.groupCode] ?? []
    if (selected.some((id) => !group.options.some((option) => option.id === id))
      || (group.distinct && new Set(selected).size !== selected.length)
      || selected.length < group.minSelect || selected.length > group.maxSelect) {
      const countLabel = group.minSelect === group.maxSelect
        ? `${group.minSelect} 项`
        : `${group.minSelect}–${group.maxSelect} 项`
      return `请选择${countLabel}${group.distinct ? '不同的' : ''}${group.labelZh}。`
    }
  }
  for (const option of customization?.addOns ?? []) {
    const quantity = draft.addOnQuantities[option.id] ?? 0
    if (!Number.isInteger(quantity) || quantity < 0 || (option.maxQuantity != null && quantity > option.maxQuantity)) {
      return `${option.labelZh}数量不符合设置。`
    }
  }
  if (draft.comboEnabled && !customization?.combo) return '此菜品不支持套餐。'
  return null
}

function cents(amount: number) {
  return Math.round(amount * 100)
}

/** Catalog prices are two-decimal CAD amounts; sum in minor units to avoid drift. */
export function calculateItemUnitPrice(menuItem: MenuItem, draft: ItemCustomizationDraft) {
  const customization = menuItem.customization
  const selectedOptionIds = new Set([
    draft.sizeId, draft.soupBaseId, draft.noodleTypeId, draft.spicyLevelId,
    ...draft.removeIds,
    ...Object.values(draft.modifierSelections ?? {}).flat(),
    ...(draft.comboEnabled ? draft.comboSideRemoveIds : []),
  ].filter((id): id is string => Boolean(id)))
  const choiceOptions = [
    ...(customization?.sizes?.options ?? []),
    ...(customization?.soupBases?.options ?? []),
    ...(customization?.noodleTypes ?? []),
    ...(customization?.spicyLevels ?? []),
    ...(customization?.modifierGroups?.flatMap((group) => group.options) ?? []),
    ...(customization?.removeOptions ?? []),
    ...(customization?.combo?.sideRemoveOptions ?? []),
  ]
  let result = cents(draft.comboEnabled && customization?.combo?.fixedPrice != null
    ? customization.combo.fixedPrice
    : menuItem.price + (draft.comboEnabled ? customization?.combo?.upcharge ?? 0 : 0))
  const seen = new Set<string>()
  for (const option of choiceOptions) {
    if (selectedOptionIds.has(option.id) && !seen.has(option.id)) {
      result += cents(option.priceDelta ?? 0)
      seen.add(option.id)
    }
  }
  if (draft.comboEnabled && customization?.combo?.fixedPrice == null) {
    for (const group of customization?.combo?.groups ?? []) {
      const selected = group.options.find((option) => option.id === resolveComboGroupOptionId(draft, group))
      result += cents(selected?.priceDelta ?? 0)
    }
  }
  for (const option of customization?.addOns ?? []) {
    result += cents(option.priceDelta ?? 0) * (draft.addOnQuantities[option.id] ?? 0)
  }
  return result / 100
}

export function calculateItemLineSubtotal(menuItem: MenuItem, draft: ItemCustomizationDraft) {
  return cents(calculateItemUnitPrice(menuItem, draft)) * draft.quantity / 100
}
