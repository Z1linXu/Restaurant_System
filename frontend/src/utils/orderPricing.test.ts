import { describe, expect, it } from 'vitest'
import type { ItemCustomizationDraft, MenuItem } from '../types/ordering'
import { buildDefaultDraft } from '../hooks/useOrderSessions'
import { buildLocalLineItem } from '../hooks/useDraftOrder'
import { calculateDraftLineSubtotal, calculateSnapshotLineSubtotal, mapOptions } from './orderPricing'

const item: MenuItem = {
  id: '40', sku: 'traditional_beef_noodle', categoryId: '1', categoryCode: 'SOUP_NOODLE',
  stationId: '1', itemType: 'NOODLE', nameZh: '牛肉面', nameEn: 'Beef Noodle',
  descriptionEn: '', descriptionZh: '', price: 16.99,
  customization: {
    sizes: { required: true, options: [
      { id: '1', labelZh: '中碗', labelEn: 'Regular', optionType: 'size', optionCode: 'size_regular', optionGroup: 'SIZE', priceDelta: 0 },
      { id: '2', labelZh: '大碗', labelEn: 'Large', optionType: 'size', optionCode: 'size_large', optionGroup: 'SIZE', priceDelta: 2 },
      { id: '3', labelZh: '小碗', labelEn: 'Small', optionType: 'size', optionCode: 'size_small', optionGroup: 'SIZE', priceDelta: -2 },
    ] },
    combo: {
      optionId: '4', upcharge: 5,
      option: { id: '4', labelZh: '套餐', labelEn: 'Combo', optionType: 'addon', optionCode: 'combo', optionGroup: 'COMBO', priceDelta: 5 },
      groups: [
        { groupCode: 'COMBO_EGG', labelZh: '蛋', labelEn: 'Egg', selectionRule: 'REQUIRED_ONE', required: true, defaultOptionId: '5',
          options: [{ id: '5', labelZh: '套餐蛋', labelEn: 'Combo Egg', optionGroup: 'COMBO_EGG', priceDelta: 0 }] },
        { groupCode: 'COMBO_SIDE', labelZh: '小菜', labelEn: 'Side', selectionRule: 'REQUIRED_ONE', required: true, defaultOptionId: '6',
          options: [{ id: '6', labelZh: '套餐小菜', labelEn: 'Combo Side', optionGroup: 'COMBO_SIDE', priceDelta: 0 }] },
      ],
      eggs: [], sides: [], sideRemoveOptions: [
        { id: '7', labelZh: '走花生', labelEn: 'No Peanut', optionType: 'remove', optionGroup: 'COMBO_SIDE_REMOVE', parentOptionId: '6', priceDelta: 0 },
      ],
    },
    addOns: [{ id: '8', labelZh: '额外卤蛋', labelEn: 'Extra Egg', optionType: 'addon', optionCode: 'tea_egg', optionGroup: 'ADD_ON', priceDelta: 1.99 }],
    removeOptions: [{ id: '9', labelZh: '走葱', labelEn: 'No Onion', optionType: 'remove', optionGroup: 'REMOVE', priceDelta: 0 }],
  },
}

function selection(sizeId: string, quantity: number, comboEnabled: boolean, extraEggs: number): ItemCustomizationDraft {
  return { ...buildDefaultDraft(item), sizeId, quantity, comboEnabled, addOnQuantities: { '8': extraEggs },
    removeIds: ['9'], comboSideRemoveIds: comboEnabled ? ['7'] : [] }
}

describe('per-unit frozen order pricing', () => {
  it.each([
    ['1', 1, false, 0, 16.99], ['2', 1, false, 0, 18.99], ['2', 2, false, 0, 37.98],
    ['3', 2, false, 0, 29.98], ['1', 1, true, 0, 21.99], ['2', 1, true, 0, 23.99],
    ['2', 2, true, 0, 47.98], ['2', 2, true, 1, 51.96], ['2', 2, true, 2, 55.94],
  ] as const)('prices size %s quantity %s combo %s extra eggs %s', (size, quantity, combo, eggs, expected) => {
    const draft = selection(size, quantity, combo, eggs)
    expect(calculateDraftLineSubtotal(item, draft)).toBe(expected)
    const frozen = buildLocalLineItem(item, draft)
    expect(frozen.lineSubtotal).toBe(expected)
    expect(frozen.unitPrice).toBe(16.99)
    expect(mapOptions(draft, item).find((option) => option.option_id === Number(size))?.quantity).toBe(1)
    expect(frozen.optionSnapshots?.find((option) => option.optionId === '8')?.quantity).toBe(eggs || undefined)
  })

  it('resizes a legacy draft from its frozen unit and option prices, not its previously wrong line amount', () => {
    const frozen = buildLocalLineItem(item, selection('2', 2, true, 1))
    frozen.lineSubtotal = 42.97
    expect(calculateSnapshotLineSubtotal(frozen.unitPrice, 3, frozen.optionSnapshots!)).toBe(77.94)
    expect(frozen.lineSubtotal).toBe(42.97)
  })

  it('keeps a Regular line, Large line and different Add-on selection independent', () => {
    const lines = [selection('1', 1, false, 0), selection('2', 1, false, 0), selection('2', 1, false, 1)]
      .map((draft) => buildLocalLineItem(item, draft))
    expect(new Set(lines.map((line) => line.id)).size).toBe(3)
    expect(lines.map((line) => line.lineSubtotal)).toEqual([16.99, 18.99, 20.98])
  })
})
