import { describe, expect, it } from 'vitest'
import type { BackendMenuCatalog } from '../types/ordering'
import {
  calculateMenuContentHash,
  menuScopeKey,
  menuSnapshotKey,
  validateMenuCatalog,
  type MenuCacheScope,
} from './menuCache'

const scope: MenuCacheScope = {
  accountId: 5,
  organizationId: 9,
  storeId: 1,
}

function catalog(): BackendMenuCatalog {
  const value: BackendMenuCatalog = {
    store_id: 1,
    organization_id: 9,
    menu_revision: 7,
    generated_at: '2026-07-13T10:00:00',
    catalog_version: 'menu-catalog-v3',
    combo_metadata_version: 'stable-option-semantics-v1',
    content_hash: '',
    tax_policy: {
      rate: 0.14975,
      label: '14.975%',
      version: 'ca-qc-tax-2026-01',
    },
    pricing_policy: {
      store_id: 1,
      policy_revision: 1,
      size_small_delta: -2,
      size_regular_delta: 0,
      size_large_delta: 2,
      combo_delta: 5,
    },
    combo_configuration: {
      store_id: 1,
      menu_revision: 7,
      groups: [
        {
          group_id: 101,
          group_code: 'COMBO_EGG',
          component_group: 'COMBO_EGG',
          name_zh: '蛋类',
          name_en: 'Egg',
          selection_rule: 'EXACTLY_ONE',
          required: true,
          enabled: true,
          display_order: 10,
          default_component_code: 'combo_tea_egg',
          components: [
            {
              id: 1001,
              group_id: 101,
              component_group: 'COMBO_EGG',
              component_code: 'combo_tea_egg',
              name_zh: '卤蛋',
              name_en: 'Tea Egg',
              enabled: true,
              display_order: 10,
              is_default: true,
              linked_menu_item_id: null,
              linked_menu_item_sku: null,
              linked_menu_item_name_zh: null,
              linked_menu_item_name_en: null,
              business_behavior: 'NO_KITCHEN_TASK',
            },
            {
              id: 1002,
              group_id: 101,
              component_group: 'COMBO_EGG',
              component_code: 'combo_fried_egg',
              name_zh: '煎蛋',
              name_en: 'Fried Egg',
              enabled: true,
              display_order: 20,
              is_default: false,
              linked_menu_item_id: null,
              linked_menu_item_sku: null,
              linked_menu_item_name_zh: null,
              linked_menu_item_name_en: null,
              business_behavior: 'NO_KITCHEN_TASK',
            },
          ],
        },
        {
          group_id: 102,
          group_code: 'COMBO_SIDE',
          component_group: 'COMBO_SIDE',
          name_zh: '小菜',
          name_en: 'Side',
          selection_rule: 'EXACTLY_ONE',
          required: true,
          enabled: true,
          display_order: 20,
          default_component_code: 'combo_edamame',
          components: [
            {
              id: 2001,
              group_id: 102,
              component_group: 'COMBO_SIDE',
              component_code: 'combo_edamame',
              name_zh: '毛豆',
              name_en: 'Edamame',
              enabled: true,
              display_order: 10,
              is_default: true,
              linked_menu_item_id: 501,
              linked_menu_item_sku: 'edamame',
              linked_menu_item_name_zh: '毛豆',
              linked_menu_item_name_en: 'Edamame',
              business_behavior: 'LINKED_MENU_ITEM',
            },
            {
              id: 2002,
              group_id: 102,
              component_group: 'COMBO_SIDE',
              component_code: 'combo_shredded_potato',
              name_zh: '土豆丝',
              name_en: 'Shredded Potato',
              enabled: true,
              display_order: 20,
              is_default: false,
              linked_menu_item_id: 502,
              linked_menu_item_sku: 'shredded_potato',
              linked_menu_item_name_zh: '土豆丝',
              linked_menu_item_name_en: 'Shredded Potato',
              business_behavior: 'LEGACY_COMBO_SIDE_TASK',
            },
            {
              id: 2003,
              group_id: 102,
              component_group: 'COMBO_SIDE',
              component_code: 'combo_cucumber_salad',
              name_zh: '拌黄瓜',
              name_en: 'Cucumber Salad',
              enabled: true,
              display_order: 30,
              is_default: false,
              linked_menu_item_id: 503,
              linked_menu_item_sku: 'cucumber_salad',
              linked_menu_item_name_zh: '拌黄瓜',
              linked_menu_item_name_en: 'Cucumber Salad',
              business_behavior: 'LEGACY_COMBO_SIDE_TASK',
            },
          ],
        },
      ],
    },
    categories: [{
      id: 11,
      code: 'SOUP_NOODLE',
      name_zh: '汤面',
      name_en: 'Soup Noodle',
      sort_order: 1,
      is_active: true,
      items: [{
        id: 21,
        category_id: 11,
        station_id: 3,
        name_zh: '传统牛肉面',
        name_en: 'Traditional Beef Noodle',
        sku: 'traditional_beef_noodle',
        item_type: 'noodle',
        base_price: 16,
        is_active: true,
        is_sold_out: false,
        sort_order: 10,
        options: [{
          id: 31,
          option_type: 'spicy_level',
          option_code: 'medium_spicy',
          option_group: 'SPICY_LEVEL',
          parent_option_id: null,
          sort_order: 2,
          name_zh: '中辣',
          name_en: 'Medium',
          price_delta: 0,
          is_active: true,
          side_item_remove_options: [],
        }],
      }],
    }],
  }
  value.content_hash = calculateMenuContentHash(value)
  return value
}

describe('versioned menu cache identity and integrity', () => {
  it('uses account, organization, store, and revision in cache keys', () => {
    expect(menuScopeKey(scope)).toBe('account:5|organization:9|store:1')
    expect(menuSnapshotKey(scope, 7)).toBe('account:5|organization:9|store:1|revision:7')
    expect(menuScopeKey({ ...scope, accountId: 6 })).not.toBe(menuScopeKey(scope))
    expect(menuScopeKey({ ...scope, storeId: 2 })).not.toBe(menuScopeKey(scope))
  })

  it('matches the backend deterministic hash fixture', () => {
    expect(catalog().content_hash).toBe('fnv1a32:07ab0e4f')
  })

  it('rejects scope, revision, and content corruption', () => {
    const valid = catalog()
    expect(() => validateMenuCatalog(valid, scope, 7)).not.toThrow()
    expect(() => validateMenuCatalog(valid, { ...scope, storeId: 2 }, 7)).toThrow('MENU_CACHE_SCOPE_MISMATCH')
    expect(() => validateMenuCatalog(valid, scope, 8)).toThrow('MENU_CACHE_REVISION_MISMATCH')

    valid.categories[0].items[0].base_price = 18
    expect(() => validateMenuCatalog(valid, scope, 7)).toThrow('MENU_CACHE_HASH_MISMATCH')
  })

  it('does not include generated_at in the content hash', () => {
    const first = catalog()
    const second = catalog()
    second.generated_at = '2030-01-01T00:00:00'
    expect(calculateMenuContentHash(second)).toBe(first.content_hash)
  })

  it('includes menu item display order in the content hash', () => {
    const first = catalog()
    const reordered = catalog()
    reordered.categories[0].items[0].sort_order = 20
    expect(calculateMenuContentHash(reordered)).not.toBe(first.content_hash)
  })

  it('includes Store combo configuration in the content hash', () => {
    const first = catalog()
    const changed = catalog()
    changed.combo_configuration!.groups[0].components[1].enabled = false
    expect(calculateMenuContentHash(changed)).not.toBe(first.content_hash)
  })

  it('includes A5.5 dynamic combo group metadata in the content hash', () => {
    const first = catalog()

    const renamedGroup = catalog()
    renamedGroup.combo_configuration!.groups[0].group_code = 'COMBO_EGG_RENAMED'
    expect(calculateMenuContentHash(renamedGroup)).not.toBe(first.content_hash)

    const ruleChanged = catalog()
    ruleChanged.combo_configuration!.groups[0].selection_rule = 'OPTIONAL_ONE'
    ruleChanged.combo_configuration!.groups[0].required = false
    expect(calculateMenuContentHash(ruleChanged)).not.toBe(first.content_hash)

    const reorderedGroup = catalog()
    reorderedGroup.combo_configuration!.groups[1].display_order = 5
    expect(calculateMenuContentHash(reorderedGroup)).not.toBe(first.content_hash)
  })

  it('includes A5.5 dynamic combo component metadata in the content hash', () => {
    const first = catalog()

    const linkedItemChanged = catalog()
    linkedItemChanged.combo_configuration!.groups[1].components[0].linked_menu_item_id = 999
    expect(calculateMenuContentHash(linkedItemChanged)).not.toBe(first.content_hash)

    const behaviorChanged = catalog()
    behaviorChanged.combo_configuration!.groups[1].components[1].business_behavior = 'NO_KITCHEN_TASK'
    expect(calculateMenuContentHash(behaviorChanged)).not.toBe(first.content_hash)

    const defaultChanged = catalog()
    defaultChanged.combo_configuration!.groups[1].default_component_code = 'combo_cucumber_salad'
    expect(calculateMenuContentHash(defaultChanged)).not.toBe(first.content_hash)
  })

  it('hashes dynamic default combo groups deterministically across repeated catalog reads', () => {
    const first = catalog()
    first.combo_configuration!.groups.push({
      group_id: 103,
      group_code: 'COMBO_DRINK',
      component_group: 'COMBO_DRINK',
      name_zh: '饮料',
      name_en: 'Drink',
      selection_rule: 'OPTIONAL_ONE',
      required: false,
      enabled: true,
      display_order: 30,
      default_component_code: 'combo_coke',
      components: [
        {
          id: 3001,
          group_id: 103,
          component_group: 'COMBO_DRINK',
          component_code: 'combo_coke',
          name_zh: '可乐',
          name_en: 'Coke',
          enabled: true,
          display_order: 10,
          is_default: true,
          linked_menu_item_id: null,
          linked_menu_item_sku: null,
          linked_menu_item_name_zh: null,
          linked_menu_item_name_en: null,
          business_behavior: 'NO_KITCHEN_TASK',
        },
      ],
    })

    const second = structuredClone(first)
    expect(calculateMenuContentHash(second)).toBe(calculateMenuContentHash(first))

    second.combo_configuration!.groups[2].components[0].enabled = false
    expect(calculateMenuContentHash(second)).not.toBe(calculateMenuContentHash(first))
  })

  it('keeps validating legacy v2 snapshots before the next network refresh', () => {
    const legacy = catalog()
    legacy.catalog_version = 'menu-catalog-v2'
    expect(calculateMenuContentHash(legacy)).toBe('fnv1a32:b8046cdf')
  })
})
