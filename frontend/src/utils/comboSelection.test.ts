import { describe, expect, it } from 'vitest'
import type { ComboChoiceGroup, ItemCustomizationDraft } from '../types/ordering'
import {
  defaultComboSelections,
  resolveComboDraftSelections,
  resolveComboGroupOptionId,
} from './comboSelection'

function group(overrides: Partial<ComboChoiceGroup>): ComboChoiceGroup {
  return {
    groupCode: 'COMBO_DRINK',
    labelEn: 'Drink',
    labelZh: '饮料',
    selectionRule: 'OPTIONAL_ONE',
    required: false,
    defaultOptionId: 'coke',
    options: [
      { id: 'coke', labelEn: 'Coke', labelZh: '可乐', optionCode: 'combo_coke', optionGroup: 'COMBO_DRINK' },
      { id: 'sprite', labelEn: 'Sprite', labelZh: '雪碧', optionCode: 'combo_sprite', optionGroup: 'COMBO_DRINK' },
    ],
    ...overrides,
  }
}

function draft(overrides: Partial<ItemCustomizationDraft> = {}): ItemCustomizationDraft {
  return {
    comboEnabled: true,
    comboSelections: {},
    comboSideRemoveIds: [],
    addOnQuantities: {},
    removeIds: [],
    quantity: 1,
    notes: '',
    ...overrides,
  }
}

describe('combo selection resolution', () => {
  it('selects any dynamic configured combo group generically', () => {
    const selected = resolveComboGroupOptionId(
      draft({ comboSelections: { COMBO_DRINK: 'sprite' } }),
      group({ groupCode: 'COMBO_DRINK' }),
    )

    expect(selected).toBe('sprite')
  })

  it('falls back to the current default when a stale cached option was disabled or removed', () => {
    const selected = resolveComboGroupOptionId(
      draft({ comboSelections: { COMBO_DRINK: 'old-coke' } }),
      group({
        defaultOptionId: 'sprite',
        options: [
          { id: 'sprite', labelEn: 'Sprite', labelZh: '雪碧', optionCode: 'combo_sprite', optionGroup: 'COMBO_DRINK' },
        ],
      }),
    )

    expect(selected).toBe('sprite')
  })

  it('does not invent an optional selection when no current default exists', () => {
    const selected = resolveComboGroupOptionId(
      draft({ comboSelections: { COMBO_DRINK: 'old-coke' } }),
      group({ required: false, defaultOptionId: undefined, options: [] }),
    )

    expect(selected).toBeUndefined()
  })

  it('keeps legacy egg and side transport ids only when the current group still contains them', () => {
    const eggGroup = group({
      groupCode: 'COMBO_EGG',
      required: true,
      defaultOptionId: 'tea',
      options: [
        { id: 'tea', labelEn: 'Tea Egg', labelZh: '卤蛋', optionCode: 'combo_tea_egg', optionGroup: 'COMBO_EGG' },
        { id: 'fried', labelEn: 'Fried Egg', labelZh: '煎蛋', optionCode: 'combo_fried_egg', optionGroup: 'COMBO_EGG' },
      ],
    })
    const resolved = resolveComboDraftSelections(
      draft({ comboEggId: 'fried', comboSelections: { COMBO_EGG: 'missing' } }),
      [eggGroup],
    )

    expect(resolved.comboSelections.COMBO_EGG).toBe('fried')
    expect(resolved.comboEggId).toBe('fried')
  })

  it('builds deterministic defaults for required and defaulted groups', () => {
    expect(defaultComboSelections([
      group({ groupCode: 'COMBO_DRINK', required: false, defaultOptionId: 'sprite' }),
      group({ groupCode: 'COMBO_SIDE', required: true, defaultOptionId: undefined }),
      group({ groupCode: 'COMBO_OPTIONAL', required: false, defaultOptionId: undefined }),
    ])).toEqual({
      COMBO_DRINK: 'sprite',
      COMBO_SIDE: 'coke',
    })
  })
})
