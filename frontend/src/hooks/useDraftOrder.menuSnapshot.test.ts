import { describe, expect, it } from 'vitest'
import { buildFrozenSubmitPayload, buildLocalLineItem } from './useDraftOrder'
import { createLocalDraftRecord } from '../offline/localDrafts'
import type { ItemCustomizationDraft, MenuItem } from '../types/ordering'

const menuItem: MenuItem = {
  id: '20',
  sku: 'traditional_beef_noodle',
  categoryId: '2',
  categoryCode: 'SOUP_NOODLE',
  stationId: '3',
  itemType: 'NOODLE',
  nameEn: 'Traditional Beef Noodle',
  nameZh: '传统牛肉面',
  descriptionEn: '',
  descriptionZh: '',
  price: 10,
  customization: {
    sizes: {
      required: true,
      options: [{
        id: '30',
        labelEn: 'Regular',
        labelZh: '中碗',
        priceDelta: 0,
        optionType: 'size',
        optionCode: 'regular',
        optionGroup: 'SIZE',
      }],
    },
    addOns: [{
      id: '40',
      labelEn: 'Broccoli',
      labelZh: '加西兰花',
      priceDelta: 2,
      optionType: 'addon',
      optionCode: 'broccoli',
      optionGroup: 'ADDON',
    }],
  },
}

const draft: ItemCustomizationDraft = {
  sizeId: '30',
  comboEnabled: false,
  comboSideRemoveIds: [],
  addOnQuantities: { '40': 1 },
  removeIds: [],
  quantity: 1,
  notes: '少汤',
}

describe('offline order menu snapshots', () => {
  it('keeps the name, price, routing, and option snapshots captured when the item was added', () => {
    const line = buildLocalLineItem(menuItem, draft)
    const record = createLocalDraftRecord(
      { accountId: 7, organizationId: 9, storeId: 1 },
      {
        orderType: 'dine_in',
        slotLabel: 'T1',
        tableLabel: 'T1',
        tableNo: 'T1',
        pickupNo: null,
      },
      4,
    )
    const changedMenu = { ...menuItem, nameZh: '新牛肉面', price: 12 }

    const payload = buildFrozenSubmitPayload(record, [line], [changedMenu])

    expect(payload.menu_revision).toBe(4)
    expect(payload.items[0]).toMatchObject({
      item_name_snapshot_zh: '传统牛肉面',
      unit_price_snapshot: 10,
      category_code_snapshot: 'SOUP_NOODLE',
      station_id_snapshot: 3,
      item_sku_snapshot: 'traditional_beef_noodle',
    })
    expect(payload.items[0].options).toEqual(expect.arrayContaining([
      expect.objectContaining({ option_id: 40, option_name_snapshot_zh: '加西兰花', option_price_snapshot: 2 }),
    ]))
  })

  it('can submit an already frozen line after the live catalog item disappears', () => {
    const line = buildLocalLineItem(menuItem, draft)
    const record = createLocalDraftRecord(
      { accountId: 7, organizationId: 9, storeId: 1 },
      {
        orderType: 'dine_in',
        slotLabel: 'T1',
        tableLabel: 'T1',
        tableNo: 'T1',
        pickupNo: null,
      },
      4,
    )

    const payload = buildFrozenSubmitPayload(record, [line], [])

    expect(payload.items[0].menu_item_id).toBe(20)
    expect(payload.items[0].options).toHaveLength(2)
  })
})
