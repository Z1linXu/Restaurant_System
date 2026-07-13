import { describe, expect, it } from 'vitest'
import type { MenuItem } from '../types/ordering'
import { buildDefaultDraft } from '../hooks/useOrderSessions'
import { resolveNoodleTypeId } from './noodleTypeDefaults'

function menuItem(sku: string): MenuItem {
  return {
    id: '10',
    sku,
    categoryId: 'dry-noodle',
    categoryCode: 'DRY_NOODLE',
    nameEn: 'Cold Noodle with Shredded Chicken',
    nameZh: '鸡丝凉面',
    descriptionEn: '',
    descriptionZh: '',
    price: 13.8,
    customization: {
      noodleTypes: [
        {
          id: '21',
          labelEn: 'Leek Leaf',
          labelZh: '韭叶',
          optionCode: 'noodle_leek_leaf',
        },
        {
          id: '22',
          labelEn: 'Thin',
          labelZh: '细',
          optionCode: 'noodle_thin',
        },
      ],
    },
  }
}

describe('noodle type defaults', () => {
  it('defaults a new shredded chicken cold noodle to the stable thin option', () => {
    expect(buildDefaultDraft(menuItem('cold_noodle_shredded_chicken')).noodleTypeId).toBe('22')
  })

  it('preserves a saved or manually selected leek-leaf option', () => {
    expect(resolveNoodleTypeId(menuItem('cold_noodle_shredded_chicken'), '21')).toBe('21')
  })

  it('keeps the existing first-option default for other noodle items', () => {
    expect(buildDefaultDraft(menuItem('zha_jiang_noodle')).noodleTypeId).toBe('21')
  })
})
