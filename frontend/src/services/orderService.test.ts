import { describe, expect, it } from 'vitest'
import type { ItemCustomizationDraft, MenuItem, OrderLineItem } from '../types/ordering'
import { buildFrozenSubmitPayload } from '../hooks/useDraftOrder'
import { mapOptions } from './orderService'
import type { LocalDraftRecord } from '../offline/localDrafts'

function menuItem(overrides: Partial<MenuItem> = {}): MenuItem {
  return {
    id: '71',
    sku: 'traditional_beef_noodle',
    categoryId: '11',
    categoryCode: 'SOUP_NOODLE',
    stationId: '3',
    itemType: 'noodle',
    nameEn: 'Traditional Beef Noodle',
    nameZh: '传统牛肉面',
    descriptionEn: '',
    descriptionZh: '',
    price: 16,
    customization: {
      combo: {
        optionId: '710',
        option: { id: '710', labelEn: 'Combo', labelZh: '套餐', priceDelta: 5, optionCode: 'combo', optionGroup: 'COMBO' },
        upcharge: 5,
        groups: [
          {
            groupCode: 'COMBO_DRINK',
            labelEn: 'Drink',
            labelZh: '饮料',
            selectionRule: 'OPTIONAL_ONE',
            required: false,
            defaultOptionId: '3002',
            options: [
              { id: '3002', labelEn: 'Sprite', labelZh: '雪碧', optionCode: 'combo_sprite', optionGroup: 'COMBO_DRINK' },
            ],
          },
        ],
        eggs: [],
        sides: [],
        sideRemoveOptions: [],
      },
    },
    ...overrides,
  }
}

function draft(overrides: Partial<ItemCustomizationDraft> = {}): ItemCustomizationDraft {
  return {
    comboEnabled: true,
    comboSelections: { COMBO_DRINK: '3001' },
    comboSideRemoveIds: [],
    addOnQuantities: {},
    removeIds: [],
    quantity: 1,
    notes: '',
    ...overrides,
  }
}

describe('order option mapping for dynamic combo groups', () => {
  it('uses the current default when a disabled dynamic component disappears from the menu snapshot', () => {
    const options = mapOptions(draft(), menuItem())

    expect(options).toEqual(expect.arrayContaining([
      expect.objectContaining({ option_id: 710, option_code_snapshot: 'combo', option_group_snapshot: 'COMBO' }),
      expect.objectContaining({ option_id: 3002, option_code_snapshot: 'combo_sprite', option_group_snapshot: 'COMBO_DRINK' }),
    ]))
    expect(options).not.toEqual(expect.arrayContaining([
      expect.objectContaining({ option_id: 3001 }),
    ]))
  })

  it('preserves existing frozen draft option snapshots instead of silently rewriting old lines', () => {
    const frozenLine: OrderLineItem = {
      id: 'line-1',
      menuItemId: '71',
      nameEn: 'Traditional Beef Noodle',
      nameZh: '传统牛肉面',
      quantity: 1,
      unitPrice: 16,
      lineSubtotal: 21,
      categoryCodeSnapshot: 'SOUP_NOODLE',
      stationIdSnapshot: '3',
      skuSnapshot: 'traditional_beef_noodle',
      itemTypeSnapshot: 'noodle',
      optionSnapshots: [
        {
          optionId: '710',
          optionType: 'addon',
          optionCode: 'combo',
          optionGroup: 'COMBO',
          parentOptionId: null,
          nameEn: 'Combo',
          nameZh: '套餐',
          priceDelta: 5,
          quantity: 1,
        },
        {
          optionId: '3001',
          optionType: 'addon',
          optionCode: 'combo_coke',
          optionGroup: 'COMBO_DRINK',
          parentOptionId: null,
          nameEn: 'Coke',
          nameZh: '可乐',
          priceDelta: 0,
          quantity: 1,
        },
      ],
      selection: draft(),
      summaryTags: [{ en: 'Coke', zh: '可乐' }],
      notes: '',
    }
    const record = {
      accountId: 1,
      organizationId: 9,
      storeId: 1,
      key: 'draft',
      localDraftId: 'draft-1',
      clientOrderId: 'client-1',
      contextKey: 'table:T1',
      context: {
        orderType: 'dine_in',
        slotLabel: 'T1',
        tableLabel: 'T1',
        tableNo: 'T1',
        pickupNo: null,
      },
      mode: 'LOCAL_NEW_ORDER',
      serverOrderId: null,
      serverOrderSnapshot: null,
      items: [frozenLine],
      menuRevision: 12,
      createdAt: '2026-08-14T00:00:00.000Z',
      updatedAt: '2026-08-14T00:00:00.000Z',
      submitState: 'LOCAL_DRAFT',
      payloadHash: 'hash',
      lastError: null,
      nextRetryAt: null,
      schemaVersion: 1,
    } satisfies LocalDraftRecord

    const payload = buildFrozenSubmitPayload(record, [frozenLine], [menuItem()])

    expect(payload.items[0].options).toEqual(expect.arrayContaining([
      expect.objectContaining({ option_id: 3001, option_code_snapshot: 'combo_coke', option_group_snapshot: 'COMBO_DRINK' }),
    ]))
  })
})
