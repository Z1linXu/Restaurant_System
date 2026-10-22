import type { ChoiceOption, ItemCustomizationDraft } from '../types/ordering'

/** Same stable-code ingredient vocabulary as RemoveSelectionValidator; never infer from labels. */
export function modifierIngredient(code?: string | null): string | null {
  const value = (code ?? '').trim().toLowerCase().replace(/^(remove_|extra_|add_|addon_)/, '')
  if (!value || value === 'all_vegetables') return null
  return ({ carrot_slice: 'carrot', crushed_peanut: 'peanut', beef: 'meat' } as Record<string, string>)[value] ?? value
}

export function selectAddOn(draft: ItemCustomizationDraft, option: ChoiceOption, quantity: number, removes: ChoiceOption[] = []): ItemCustomizationDraft {
  const ingredient = modifierIngredient(option.optionCode)
  const conflicting = new Set(quantity > 0 && ingredient
    ? removes.filter((remove) => modifierIngredient(remove.optionCode) === ingredient).map((remove) => remove.id) : [])
  return { ...draft, addOnQuantities: { ...draft.addOnQuantities, [option.id]: quantity },
    removeIds: draft.removeIds.filter((id) => !conflicting.has(id)) }
}

export function selectRemove(draft: ItemCustomizationDraft, option: ChoiceOption, addOns: ChoiceOption[] = []): ItemCustomizationDraft {
  if (draft.removeIds.includes(option.id)) return { ...draft, removeIds: draft.removeIds.filter((id) => id !== option.id) }
  const ingredient = modifierIngredient(option.optionCode)
  const quantities = { ...draft.addOnQuantities }
  if (ingredient) for (const addon of addOns) {
    if (modifierIngredient(addon.optionCode) === ingredient) quantities[addon.id] = 0
  }
  return { ...draft, addOnQuantities: quantities, removeIds: [...draft.removeIds, option.id] }
}
