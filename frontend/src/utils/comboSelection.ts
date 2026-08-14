import type { ComboChoiceGroup, ItemCustomizationDraft } from '../types/ordering'

function optionExists(group: ComboChoiceGroup, optionId: string | undefined) {
  return Boolean(optionId && group.options.some((option) => option.id === optionId))
}

function legacySelectionId(draft: ItemCustomizationDraft, group: ComboChoiceGroup) {
  if (group.groupCode === 'COMBO_EGG') {
    return draft.comboEggId
  }
  if (group.groupCode === 'COMBO_SIDE') {
    return draft.comboSideId
  }
  return undefined
}

export function resolveComboGroupOptionId(draft: ItemCustomizationDraft, group: ComboChoiceGroup) {
  const candidates = [
    draft.comboSelections?.[group.groupCode],
    legacySelectionId(draft, group),
    group.defaultOptionId,
    group.required ? group.options[0]?.id : undefined,
  ]
  return candidates.find((optionId) => optionExists(group, optionId))
}

export function defaultComboSelections(groups: ComboChoiceGroup[]) {
  return Object.fromEntries(
    groups
      .map((group) => {
        const optionId = optionExists(group, group.defaultOptionId)
          ? group.defaultOptionId
          : group.required
            ? group.options[0]?.id
            : undefined
        return [group.groupCode, optionId] as const
      })
      .filter((entry): entry is readonly [string, string] => Boolean(entry[1])),
  )
}

export function resolveComboDraftSelections(
  draft: ItemCustomizationDraft,
  groups: ComboChoiceGroup[],
): ItemCustomizationDraft {
  const comboSelections = { ...(draft.comboSelections ?? {}) }
  groups.forEach((group) => {
    const selectedOptionId = resolveComboGroupOptionId(draft, group)
    if (selectedOptionId) {
      comboSelections[group.groupCode] = selectedOptionId
    } else {
      delete comboSelections[group.groupCode]
    }
  })

  const eggGroup = groups.find((group) => group.groupCode === 'COMBO_EGG')
  const sideGroup = groups.find((group) => group.groupCode === 'COMBO_SIDE')

  return {
    ...draft,
    comboEggId: eggGroup ? comboSelections.COMBO_EGG : draft.comboEggId,
    comboSideId: sideGroup ? comboSelections.COMBO_SIDE : draft.comboSideId,
    comboSelections,
  }
}
