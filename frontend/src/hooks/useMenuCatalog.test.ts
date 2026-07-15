import { describe, expect, it } from 'vitest'
import type { BackendMenuCatalog } from '../types/ordering'
import { mapCatalog } from './useMenuCatalog'
import { buildDefaultDraft } from './useOrderSessions'

function catalog(): BackendMenuCatalog {
  return {
    store_id: 1,
    organization_id: 9,
    menu_revision: 2,
    generated_at: '2026-07-15T12:00:00',
    catalog_version: 'menu-catalog-v3',
    combo_metadata_version: 'stable-option-semantics-v1',
    content_hash: 'fixture',
    tax_policy: { rate: 0.14975, label: '14.975%', version: 'test' },
    categories: [{
      id: 7,
      code: 'FRIED',
      name_zh: '炸物',
      name_en: 'Fried',
      sort_order: 10,
      is_active: true,
      items: [
        {
          id: 12,
          category_id: 7,
          station_id: 3,
          name_zh: '第二',
          name_en: 'Second',
          sku: 'second',
          item_type: 'menu_item',
          base_price: 2,
          is_active: true,
          is_sold_out: false,
          sort_order: 20,
          options: [],
        },
        {
          id: 11,
          category_id: 7,
          station_id: 3,
          name_zh: '第一',
          name_en: 'First',
          sku: 'first',
          item_type: 'menu_item',
          base_price: 1,
          is_active: true,
          is_sold_out: false,
          sort_order: 10,
          options: [],
        },
      ],
    }],
  }
}

describe('ordering menu item display order', () => {
  it('maps category items by persisted sort order with stable id fallback', () => {
    const mapped = mapCatalog(catalog())

    expect(mapped.items.map((item) => item.id)).toEqual(['11', '12'])
    expect(mapped.items.map((item) => item.sortOrder)).toEqual([10, 20])
  })

  it('uses the first persisted noodle type as the new-item default', () => {
    const data = catalog()
    data.categories[0].items = [{
      ...data.categories[0].items[0],
      id: 31,
      sku: 'cold_noodle_shredded_chicken',
      name_zh: '鸡丝凉面',
      name_en: 'Cold Noodle with Shredded Chicken',
      options: [
        {
          id: 302,
          option_type: 'noodle_type',
          option_code: 'noodle_leek_leaf',
          option_group: 'NOODLE_TYPE',
          parent_option_id: null,
          sort_order: 20,
          name_zh: '韭叶',
          name_en: 'Leek Leaf',
          price_delta: 0,
          is_active: true,
        },
        {
          id: 301,
          option_type: 'noodle_type',
          option_code: 'noodle_thin',
          option_group: 'NOODLE_TYPE',
          parent_option_id: null,
          sort_order: 10,
          name_zh: '细面',
          name_en: 'Thin',
          price_delta: 0,
          is_active: true,
        },
      ],
    }]

    const item = mapCatalog(data).items[0]
    expect(item.customization?.noodleTypes?.map((option) => option.optionCode))
      .toEqual(['noodle_thin', 'noodle_leek_leaf'])
    expect(buildDefaultDraft(item).noodleTypeId).toBe('301')
  })
})
