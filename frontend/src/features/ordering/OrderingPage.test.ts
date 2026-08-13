import { describe, expect, it } from 'vitest'
import type { MenuItem } from '../../types/ordering'
import { hasRequiredCustomization, isQuickAddItem } from './orderingCustomizationRules'

function item(overrides: Partial<MenuItem>): MenuItem {
  return {
    id: '1',
    sku: 'test',
    categoryId: '10',
    categoryCode: 'DRINK',
    stationId: '3',
    itemType: 'DRINK',
    nameEn: 'Test',
    nameZh: '测试',
    descriptionEn: '',
    descriptionZh: '',
    price: 3,
    ...overrides,
  }
}

describe('ordering customization requirements', () => {
  it('does not require an extra size choice for a single enabled size', () => {
    const singleSizeDrink = item({
      customization: {
        sizes: {
          required: true,
          options: [{
            id: 'regular',
            labelEn: 'Regular',
            labelZh: '中碗',
            priceDelta: 0,
            optionType: 'size',
            optionCode: 'size_regular',
            optionGroup: 'SIZE',
          }],
        },
      },
    })

    expect(hasRequiredCustomization(singleSizeDrink)).toBe(false)
    expect(isQuickAddItem(singleSizeDrink)).toBe(true)
  })

  it('requires the modal when multiple sizes are configured', () => {
    const multiSizeDrink = item({
      customization: {
        sizes: {
          required: true,
          options: [
            {
              id: 'regular',
              labelEn: 'Regular',
              labelZh: '中碗',
              priceDelta: 0,
              optionType: 'size',
              optionCode: 'size_regular',
              optionGroup: 'SIZE',
            },
            {
              id: 'large',
              labelEn: 'Large',
              labelZh: '大碗',
              priceDelta: 2,
              optionType: 'size',
              optionCode: 'size_large',
              optionGroup: 'SIZE',
            },
          ],
        },
      },
    })

    expect(hasRequiredCustomization(multiSizeDrink)).toBe(true)
    expect(isQuickAddItem(multiSizeDrink)).toBe(false)
  })
})
