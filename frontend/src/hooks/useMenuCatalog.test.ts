import { describe, expect, it } from 'vitest'
import type { BackendMenuCatalog } from '../types/ordering'
import { createLocalDraftRecord } from '../offline/localDrafts'
import { buildFrozenSubmitPayload, buildLocalLineItem } from './useDraftOrder'
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

  it('uses the Chinatown Small size as the default when persisted first', () => {
    const data = catalog()
    data.categories[0].items = [{
      ...data.categories[0].items[0],
      id: 41,
      sku: 'traditional_beef_noodle',
      name_zh: '兰州牛肉面',
      name_en: 'Traditional LanZhou Hand-pull Beef Noodle',
      base_price: 14.99,
      options: [
        {
          id: 403,
          option_type: 'size',
          option_code: 'size_large',
          option_group: 'SIZE',
          parent_option_id: null,
          sort_order: 3,
          name_zh: '大碗',
          name_en: 'Large',
          price_delta: 4,
          is_active: true,
        },
        {
          id: 401,
          option_type: 'size',
          option_code: 'size_small',
          option_group: 'SIZE',
          parent_option_id: null,
          sort_order: 1,
          name_zh: '小碗',
          name_en: 'Small',
          price_delta: 0,
          is_active: true,
        },
        {
          id: 402,
          option_type: 'size',
          option_code: 'size_medium',
          option_group: 'SIZE',
          parent_option_id: null,
          sort_order: 2,
          name_zh: '中碗',
          name_en: 'Medium',
          price_delta: 2,
          is_active: true,
        },
      ],
    }]

    const item = mapCatalog(data).items[0]

    expect(item.customization?.sizes?.options.map((option) => option.optionCode))
      .toEqual(['size_small', 'size_medium', 'size_large'])
    expect(buildDefaultDraft(item).sizeId).toBe('401')
  })

  it('uses active dynamic sizes only and keeps the first active size as default', () => {
    const data = catalog()
    data.categories[0].items = [{
      ...data.categories[0].items[0],
      id: 51,
      sku: 'dynamic_size_test_noodle',
      name_zh: '动态规格面',
      name_en: 'Dynamic Size Noodle',
      base_price: 10,
      options: [
        {
          id: 501,
          option_type: 'size',
          option_code: 'size_small',
          option_group: 'SIZE',
          parent_option_id: null,
          sort_order: 10,
          name_zh: '小碗',
          name_en: 'Small',
          price_delta: -1,
          is_active: true,
        },
        {
          id: 502,
          option_type: 'size',
          option_code: 'size_regular',
          option_group: 'SIZE',
          parent_option_id: null,
          sort_order: 20,
          name_zh: '中碗',
          name_en: 'Regular',
          price_delta: 0,
          is_active: true,
        },
        {
          id: 503,
          option_type: 'size',
          option_code: 'size_large',
          option_group: 'SIZE',
          parent_option_id: null,
          sort_order: 30,
          name_zh: '大碗',
          name_en: 'Large',
          price_delta: 3,
          is_active: false,
        },
      ],
    }]

    const item = mapCatalog(data).items[0]

    expect(item.customization?.sizes?.options.map((option) => option.optionCode))
      .toEqual(['size_small', 'size_regular'])
    expect(buildDefaultDraft(item).sizeId).toBe('501')
  })

  it('auto-selects a single configured size and snapshots its identity', () => {
    const data = catalog()
    data.categories[0].items = [{
      ...data.categories[0].items[0],
      id: 52,
      sku: 'single_size_test_noodle',
      name_zh: '单规格面',
      name_en: 'Single Size Noodle',
      base_price: 12,
      options: [
        {
          id: 521,
          option_type: 'size',
          option_code: 'size_regular',
          option_group: 'SIZE',
          parent_option_id: null,
          sort_order: 10,
          name_zh: '中碗',
          name_en: 'Regular',
          price_delta: 0,
          is_active: true,
        },
      ],
    }]

    const item = mapCatalog(data).items[0]
    const line = buildLocalLineItem(item, buildDefaultDraft(item))

    expect(buildDefaultDraft(item).sizeId).toBe('521')
    expect(line.optionSnapshots).toEqual(expect.arrayContaining([
      expect.objectContaining({
        optionId: '521',
        optionCode: 'size_regular',
        optionGroup: 'SIZE',
        nameZh: '中碗',
      }),
    ]))
  })

  it('keeps the mapped Chinatown Small default through the frozen submit payload', () => {
    const data = catalog()
    data.categories[0].items = [{
      ...data.categories[0].items[0],
      id: 41,
      sku: 'traditional_beef_noodle',
      name_zh: '兰州牛肉面',
      name_en: 'Traditional LanZhou Hand-pull Beef Noodle',
      base_price: 14.99,
      options: [
        {
          id: 402,
          option_type: 'size',
          option_code: 'size_medium',
          option_group: 'SIZE',
          parent_option_id: null,
          sort_order: 2,
          name_zh: '中碗',
          name_en: 'Medium',
          price_delta: 2,
          is_active: true,
        },
        {
          id: 401,
          option_type: 'size',
          option_code: 'size_small',
          option_group: 'SIZE',
          parent_option_id: null,
          sort_order: 1,
          name_zh: '小碗',
          name_en: 'Small',
          price_delta: 0,
          is_active: true,
        },
      ],
    }]

    const item = mapCatalog(data).items[0]
    const line = buildLocalLineItem(item, buildDefaultDraft(item))
    const record = createLocalDraftRecord(
      { accountId: 7, organizationId: 9, storeId: 1 },
      {
        orderType: 'dine_in',
        slotLabel: 'T1',
        tableLabel: 'T1',
        tableNo: 'T1',
        pickupNo: null,
      },
      data.menu_revision,
    )
    const payload = buildFrozenSubmitPayload(record, [line], [item])

    expect(line.lineSubtotal).toBe(14.99)
    expect(payload.expected_subtotal_amount).toBe(14.99)
    expect(payload.items[0].unit_price_snapshot).toBe(14.99)
    expect(payload.items[0].options).toEqual(expect.arrayContaining([
      expect.objectContaining({
        option_id: 401,
        option_code_snapshot: 'size_small',
        option_name_snapshot_zh: '小碗',
        option_price_snapshot: 0,
      }),
    ]))
  })

  it('maps Store-filtered combo components and defaults to the first enabled egg and side', () => {
    const data = catalog()
    data.pricing_policy = {
      store_id: 1,
      policy_revision: 3,
      size_small_delta: -2,
      size_regular_delta: 0,
      size_large_delta: 2,
      combo_delta: 5,
    }
    data.combo_configuration = {
      store_id: 1,
      menu_revision: data.menu_revision,
      groups: [
        {
          component_group: 'COMBO_EGG',
          name_zh: '蛋类',
          name_en: 'Egg',
          default_component_code: 'combo_tea_egg',
          components: [
            {
              component_group: 'COMBO_EGG',
              component_code: 'combo_tea_egg',
              name_zh: '卤蛋',
              name_en: 'Tea Egg',
              enabled: true,
              display_order: 10,
              is_default: true,
            },
            {
              component_group: 'COMBO_EGG',
              component_code: 'combo_fried_egg',
              name_zh: '煎蛋',
              name_en: 'Fried Egg',
              enabled: false,
              display_order: 20,
              is_default: false,
            },
          ],
        },
        {
          component_group: 'COMBO_SIDE',
          name_zh: '小菜',
          name_en: 'Side',
          default_component_code: 'combo_edamame',
          components: [
            {
              component_group: 'COMBO_SIDE',
              component_code: 'combo_edamame',
              name_zh: '毛豆',
              name_en: 'Edamame',
              enabled: true,
              display_order: 10,
              is_default: true,
            },
          ],
        },
      ],
    }
    data.categories[0].code = 'SOUP_NOODLE'
    data.categories[0].items = [{
      ...data.categories[0].items[0],
      id: 61,
      sku: 'traditional_beef_noodle',
      name_zh: '传统牛肉面',
      name_en: 'Traditional Beef Noodle',
      options: [
        {
          id: 610,
          option_type: 'addon',
          option_code: 'combo',
          option_group: 'COMBO',
          parent_option_id: null,
          sort_order: 100,
          name_zh: '套餐',
          name_en: 'Combo',
          price_delta: 5,
          is_active: true,
        },
        {
          id: 611,
          option_type: 'addon',
          option_code: 'combo_tea_egg',
          option_group: 'COMBO_EGG',
          parent_option_id: null,
          sort_order: 110,
          name_zh: '套餐卤蛋',
          name_en: 'Combo Tea Egg',
          price_delta: 0,
          is_active: true,
        },
        {
          id: 613,
          option_type: 'addon',
          option_code: 'combo_edamame',
          option_group: 'COMBO_SIDE',
          parent_option_id: null,
          sort_order: 130,
          name_zh: '套餐毛豆',
          name_en: 'Combo Edamame',
          price_delta: 0,
          is_active: true,
        },
      ],
    }]

    const item = mapCatalog(data).items[0]
    const draft = buildDefaultDraft(item)
    const line = buildLocalLineItem(item, { ...draft, comboEnabled: true })

    expect(item.customization?.combo?.eggs.map((option) => option.optionCode)).toEqual(['combo_tea_egg'])
    expect(item.customization?.combo?.sides.map((option) => option.optionCode)).toEqual(['combo_edamame'])
    expect(draft.comboEggId).toBe('-20101')
    expect(draft.comboSideId).toBe('-20201')
    expect(line.lineSubtotal).toBe(7)
    expect(line.optionSnapshots).toEqual(expect.arrayContaining([
      expect.objectContaining({ optionCode: 'combo', optionGroup: 'COMBO', priceDelta: 5 }),
      expect.objectContaining({ optionCode: 'combo_tea_egg', optionGroup: 'COMBO_EGG' }),
      expect.objectContaining({ optionCode: 'combo_edamame', optionGroup: 'COMBO_SIDE' }),
    ]))
  })

  it('maps dynamic combo groups into order snapshots without owner-entered option ids', () => {
    const data = catalog()
    data.pricing_policy = {
      store_id: 1,
      policy_revision: 3,
      size_small_delta: -2,
      size_regular_delta: 0,
      size_large_delta: 2,
      combo_delta: 5,
    }
    data.combo_configuration = {
      store_id: 1,
      menu_revision: data.menu_revision,
      groups: [
        {
          component_group: 'COMBO_EGG',
          group_code: 'COMBO_EGG',
          name_zh: '蛋类',
          name_en: 'Egg',
          selection_rule: 'EXACTLY_ONE',
          required: true,
          enabled: true,
          display_order: 10,
          default_component_code: 'combo_tea_egg',
          components: [{
            component_group: 'COMBO_EGG',
            component_code: 'combo_tea_egg',
            name_zh: '卤蛋',
            name_en: 'Tea Egg',
            enabled: true,
            display_order: 10,
            is_default: true,
          }],
        },
        {
          component_group: 'COMBO_SIDE',
          group_code: 'COMBO_SIDE',
          name_zh: '小菜',
          name_en: 'Side',
          selection_rule: 'EXACTLY_ONE',
          required: true,
          enabled: true,
          display_order: 20,
          default_component_code: 'combo_edamame',
          components: [{
            component_group: 'COMBO_SIDE',
            component_code: 'combo_edamame',
            name_zh: '毛豆',
            name_en: 'Edamame',
            enabled: true,
            display_order: 10,
            is_default: true,
          }],
        },
        {
          component_group: 'COMBO_DRINK',
          group_code: 'COMBO_DRINK',
          name_zh: '饮料',
          name_en: 'Drink',
          selection_rule: 'OPTIONAL_ONE',
          required: false,
          enabled: true,
          display_order: 30,
          default_component_code: 'combo_coke',
          components: [
            {
              component_group: 'COMBO_DRINK',
              component_code: 'combo_coke',
              name_zh: '可乐',
              name_en: 'Coke',
              enabled: true,
              display_order: 10,
              is_default: true,
            },
            {
              component_group: 'COMBO_DRINK',
              component_code: 'combo_sprite',
              name_zh: '雪碧',
              name_en: 'Sprite',
              enabled: true,
              display_order: 20,
              is_default: false,
            },
          ],
        },
      ],
    }
    data.categories[0].code = 'SOUP_NOODLE'
    data.categories[0].items = [{
      ...data.categories[0].items[0],
      id: 71,
      sku: 'traditional_beef_noodle',
      options: [{
        id: 710,
        option_type: 'addon',
        option_code: 'combo',
        option_group: 'COMBO',
        parent_option_id: null,
        sort_order: 100,
        name_zh: '套餐',
        name_en: 'Combo',
        price_delta: 5,
        is_active: true,
      }],
    }]

    const item = mapCatalog(data).items[0]
    const draft = buildDefaultDraft(item)
    const line = buildLocalLineItem(item, { ...draft, comboEnabled: true })

    expect(item.customization?.combo?.groups.map((group) => group.groupCode)).toEqual(['COMBO_EGG', 'COMBO_SIDE', 'COMBO_DRINK'])
    expect(draft.comboSelections.COMBO_DRINK).toBeDefined()
    expect(line.optionSnapshots).toEqual(expect.arrayContaining([
      expect.objectContaining({ optionCode: 'combo_coke', optionGroup: 'COMBO_DRINK', nameZh: '可乐' }),
    ]))
  })
})
