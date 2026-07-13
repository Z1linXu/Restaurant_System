import type { ItemCustomizationDraft, MenuItemCustomizationConfig } from '../types/ordering'

type ComboConfig = MenuItemCustomizationConfig['combo']

export interface ResolvedComboSelection {
  enabled: boolean
  isNoEgg: boolean
  optionIds: string[]
}

/**
 * A selected combo side is the stable source of truth for a combo. A side-only
 * selection intentionally represents a combo without an egg (走蛋).
 */
export function isComboSelected(draft: ItemCustomizationDraft) {
  return Boolean(draft.comboSideId)
}

export function normalizeComboDraft(draft: ItemCustomizationDraft): ItemCustomizationDraft {
  const enabled = isComboSelected(draft)
  return {
    ...draft,
    comboEnabled: enabled,
    comboEggId: enabled ? draft.comboEggId : undefined,
    comboSideRemoveIds: enabled ? draft.comboSideRemoveIds : [],
  }
}

export function toggleComboSide(draft: ItemCustomizationDraft, sideId: string): ItemCustomizationDraft {
  const isRemovingSide = draft.comboSideId === sideId
  return normalizeComboDraft({
    ...draft,
    comboSideId: isRemovingSide ? undefined : sideId,
    comboEggId: isRemovingSide ? undefined : draft.comboEggId,
    comboSideRemoveIds: [],
  })
}

export function resolveComboSelection(
  draft: ItemCustomizationDraft,
  combo: ComboConfig | undefined,
): ResolvedComboSelection {
  if (!combo || !isComboSelected(draft) || !draft.comboSideId) {
    return {
      enabled: false,
      isNoEgg: false,
      optionIds: [],
    }
  }

  const optionIds = [combo.optionId, draft.comboEggId, draft.comboSideId, ...draft.comboSideRemoveIds]
    .filter((optionId): optionId is string => Boolean(optionId))

  return {
    enabled: true,
    isNoEgg: !draft.comboEggId,
    optionIds,
  }
}

export function resolveComboUpcharge(draft: ItemCustomizationDraft, combo: ComboConfig | undefined) {
  return resolveComboSelection(draft, combo).enabled ? (combo?.upcharge ?? 0) : 0
}
