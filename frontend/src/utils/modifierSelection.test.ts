import { expect, it } from 'vitest'
import { modifierIngredient, selectAddOn, selectRemove } from './modifierSelection'
import type { ItemCustomizationDraft } from '../types/ordering'

const draft: ItemCustomizationDraft = { quantity: 1, notes: '', comboEnabled: false, comboSelections: {}, comboSideRemoveIds: [], addOnQuantities: { add: 2 }, removeIds: ['remove'] }
const addon = { id: 'add', optionCode: 'extra_meat', labelZh: '名称可修改', labelEn: 'Extra' }
const remove = { id: 'remove', optionCode: 'remove_beef', labelZh: '另一个名称', labelEn: 'Remove' }

it('selecting an Add-on replaces the conflicting Remove using code, preserving other choices', () => {
  const next = selectAddOn({ ...draft, removeIds: ['remove', 'other'] }, addon, 1, [remove])
  expect(next.removeIds).toEqual(['other'])
  expect(next.addOnQuantities.add).toBe(1)
  expect(draft.removeIds).toEqual(['remove'])
  expect(selectAddOn(draft, addon, 0, [remove]).removeIds).toEqual(['remove'])
})

it('selecting a Remove clears only its matching Add-on and toggling it off restores nothing', () => {
  const next = selectRemove({ ...draft, removeIds: [], addOnQuantities: { add: 2, other: 1 } }, remove, [addon])
  expect(next.addOnQuantities).toEqual({ add: 0, other: 1 })
  expect(next.removeIds).toEqual(['remove'])
  expect(selectRemove(next, remove, [addon]).addOnQuantities.add).toBe(0)
})

it('keeps all-vegetables independent and aligns explicit ingredient aliases', () => {
  expect(modifierIngredient('remove_all_vegetables')).toBeNull()
  expect(modifierIngredient('carrot_slice')).toBe(modifierIngredient('remove_carrot'))
  expect(modifierIngredient('remove_crushed_peanut')).toBe(modifierIngredient('peanut'))
  expect(selectRemove({ ...draft, removeIds: [] }, { ...remove, optionCode: 'remove_all_vegetables' }, [addon]).addOnQuantities.add).toBe(2)
})
