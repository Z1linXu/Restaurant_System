import { describe, expect, it } from 'vitest'
import type { MenuItem } from '../../types/ordering'
import { requiresCustomization } from './orderingCustomizationRules'

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

    expect(requiresCustomization(singleSizeDrink)).toBe(false)
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

    expect(requiresCustomization(multiSizeDrink)).toBe(true)
  })

  it('quick-adds plain items and treats empty catalog metadata as no choices', () => {
    expect(requiresCustomization(item({ categoryCode: 'CATEGORY_ADD_ON', sku: 'add_on_zha_jiang' }))).toBe(false)
    expect(requiresCustomization(item({ categoryCode: 'ARBITRARY', customization: {
      noodleTypes: [], spicyLevels: [], addOns: [], removeOptions: [],
    } }))).toBe(false)
  })

  const choice = { id: 'choice', labelEn: 'Default', labelZh: '默认' }

  it.each([
    { spicyLevels: [choice] },
    { noodleTypes: [choice] },
    { soupBases: { required: true, options: [choice] } },
    { sizes: { required: true, options: [{ ...choice, portionPrice: 3 }] } },
    { removeOptions: [choice] },
    { addOns: [choice] },
    { combo: { upcharge: 5, groups: [], eggs: [], sides: [], sideRemoveOptions: [] } },
  ])('shows configured employee choices even with defaults or on former quick-add categories', (customization) => {
    expect(requiresCustomization(item({ customization }))).toBe(true)
  })
})
