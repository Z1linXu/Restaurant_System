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
    catalog_version: 'menu-catalog-v2',
    combo_metadata_version: 'stable-option-semantics-v1',
    content_hash: '',
    tax_policy: {
      rate: 0.14975,
      label: '14.975%',
      version: 'ca-qc-tax-2026-01',
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
    expect(catalog().content_hash).toBe('fnv1a32:6b4cec40')
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
})
