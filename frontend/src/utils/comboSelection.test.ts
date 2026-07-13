import { describe, expect, it } from 'vitest'
import type { ItemCustomizationDraft, MenuItemCustomizationConfig } from '../types/ordering'
import {
  normalizeComboDraft,
  resolveComboSelection,
  resolveComboUpcharge,
  toggleComboSide,
} from './comboSelection'

const combo: NonNullable<MenuItemCustomizationConfig['combo']> = {
  optionId: 'combo-option',
  upcharge: 5,
  eggs: [
    { id: 'combo-tea-egg', labelEn: 'Combo Tea Egg', labelZh: '套餐卤蛋' },
    { id: 'combo-fried-egg', labelEn: 'Combo Fried Egg', labelZh: '套餐煎蛋' },
  ],
  sides: [
    { id: 'combo-edamame', labelEn: 'Combo Edamame', labelZh: '套餐毛豆' },
  ],
  sideRemoveOptions: [
    {
      id: 'combo-edamame-no-peanut',
      labelEn: 'No Peanut',
      labelZh: '走花生',
      parentOptionId: 'combo-edamame',
    },
  ],
}

function draft(overrides: Partial<ItemCustomizationDraft> = {}): ItemCustomizationDraft {
  return {
    comboEnabled: false,
    comboSideRemoveIds: [],
    addOnQuantities: {},
    removeIds: [],
    quantity: 1,
    notes: '',
    ...overrides,
  }
}

describe('combo selection', () => {
  it('keeps egg-only additions out of combo pricing and combo payload options', () => {
    const selection = draft({
      addOnQuantities: {
        'tea-egg': 3,
        'fried-egg': 1,
      },
    })

    expect(resolveComboSelection(selection, combo)).toEqual({
      enabled: false,
      isNoEgg: false,
      optionIds: [],
    })
    expect(resolveComboUpcharge(selection, combo)).toBe(0)
  })

  it('treats a selected side as a combo with no egg by default', () => {
    const selection = toggleComboSide(draft(), 'combo-edamame')

    expect(selection.comboEnabled).toBe(true)
    expect(resolveComboSelection(selection, combo)).toEqual({
      enabled: true,
      isNoEgg: true,
      optionIds: ['combo-option', 'combo-edamame'],
    })
    expect(resolveComboUpcharge(selection, combo)).toBe(5)
  })

  it('includes one selected combo egg without charging the combo twice', () => {
    const selection = normalizeComboDraft(draft({
      comboEggId: 'combo-fried-egg',
      comboSideId: 'combo-edamame',
      comboSideRemoveIds: ['combo-edamame-no-peanut'],
    }))

    expect(resolveComboSelection(selection, combo)).toEqual({
      enabled: true,
      isNoEgg: false,
      optionIds: ['combo-option', 'combo-fried-egg', 'combo-edamame', 'combo-edamame-no-peanut'],
    })
    expect(resolveComboUpcharge(selection, combo)).toBe(5)
  })

  it('cancels combo state and side-only payload options when the side is removed', () => {
    const selected = toggleComboSide(draft({
      comboEggId: 'combo-tea-egg',
      comboSideId: 'combo-edamame',
      comboSideRemoveIds: ['combo-edamame-no-peanut'],
    }), 'combo-edamame')

    expect(selected.comboEnabled).toBe(false)
    expect(selected.comboEggId).toBeUndefined()
    expect(selected.comboSideRemoveIds).toEqual([])
    expect(resolveComboSelection(selected, combo).enabled).toBe(false)
    expect(resolveComboUpcharge(selected, combo)).toBe(0)
  })

  it('restores a saved combo with its egg and side selections', () => {
    const restored = normalizeComboDraft(draft({
      comboEnabled: true,
      comboEggId: 'combo-tea-egg',
      comboSideId: 'combo-edamame',
    }))

    expect(restored.comboEnabled).toBe(true)
    expect(restored.comboEggId).toBe('combo-tea-egg')
    expect(restored.comboSideId).toBe('combo-edamame')
  })
})
