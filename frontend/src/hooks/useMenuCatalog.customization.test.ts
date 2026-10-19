import { describe, expect, it } from 'vitest'
import type { BackendMenuCatalog, BackendMenuItem, BackendMenuOption } from '../types/ordering'
import { mapCatalog } from './useMenuCatalog'
import { buildDefaultDraft } from './useOrderSessions'
import { buildFrozenSubmitPayload, buildLocalLineItem, resizeSnapshotOptionQuantities } from './useDraftOrder'
import { createLocalDraftRecord } from '../offline/localDrafts'
import { customizationValidationMessage } from '../utils/itemCustomization'

function option(id: number, code: string, group: string, price: number, type = 'addon'): BackendMenuOption {
  return { id, option_type: type, option_code: code, option_group: group, parent_option_id: null, sort_order: id, name_zh: code, name_en: code, name_fr: `FR ${code}`, price_delta: price, is_active: true }
}

function fixture(): BackendMenuCatalog {
  const noodle: BackendMenuItem = {
    id: 10, category_id: 1, station_id: 7, name_zh: '经典牛肉面', name_en: 'Classic beef noodle soup', name_fr: 'Bœuf classique', sku: 'test_b1', item_type: 'menu_item', base_price: 15.99, is_active: true, is_sold_out: false, sort_order: 10,
    customization_config: { schema_version: 1, option_groups: [{ option_group: 'NOODLE_TYPE', min_select: 1, max_select: 1, distinct: true, default_option_codes: ['thin'] }], addon_max_quantities: { extra_noodles: 1 }, combo: { fixed_price: 19.99, group_codes: ['APPETIZER'] } },
    options: [option(101, 'thin', 'NOODLE_TYPE', 0, 'noodle_type'), option(102, 'thick', 'NOODLE_TYPE', 0, 'noodle_type'), option(103, 'extra_noodles', 'ADD_ON', 0), option(104, 'extra_beef', 'ADD_ON', 7.99), option(105, 'combo', 'COMBO', 4)],
  }
  const rice: BackendMenuItem = {
    ...noodle, id: 20, sku: 'test_c1', name_zh: '烤牛肉饭', base_price: 16.99,
    customization_config: { schema_version: 1, option_groups: [], addon_max_quantities: {}, combo: { fixed_price: 19.99, group_codes: ['SOUP', 'ROLL'] } },
    options: [option(201, 'combo', 'COMBO', 3)],
  }
  const mixed: BackendMenuItem = {
    ...noodle, id: 30, sku: 'test_c5', name_zh: '双拼饭', base_price: 19.99,
    customization_config: { schema_version: 1, option_groups: [{ option_group: 'PROTEIN', min_select: 2, max_select: 2, distinct: true, default_option_codes: [] }], addon_max_quantities: {} },
    options: ['beef', 'chicken', 'pork', 'shrimp'].map((code, index) => option(301 + index, code, 'PROTEIN', 0, 'modifier')),
  }
  const group = (code: string, names: string[], offset: number) => ({
    group_id: offset, group_code: code, component_group: code, name_zh: code, name_en: code, selection_rule: 'EXACTLY_ONE', required: true, enabled: true, display_order: offset, default_component_code: `${code}_0`,
    components: names.map((name, index) => ({ id: offset + index, component_group: code, component_code: `${code}_${index}`, name_zh: name, name_en: name, name_fr: `FR ${name}`, enabled: true, display_order: index, is_default: index === 0, synthetic_option_id: -(1000000000 + offset + index), linked_menu_item_id: offset + index })),
  })
  return {
    store_id: 100, organization_id: 200, menu_revision: 7, generated_at: '2026-10-18T00:00:00Z', catalog_version: 'menu-catalog-v4', combo_metadata_version: 'stable-option-semantics-v1', content_hash: 'test', tax_policy: { rate: 0.14975, label: 'LOCAL DEMO TEST TAX', version: 'test' },
    combo_configuration: { store_id: 100, menu_revision: 7, groups: [group('APPETIZER', ['虾云吞汤（3颗）', '素春卷（2条）', '凉拌黄瓜', '凉拌牛肉', '炸云吞', '炸虾', '鸡翅', '牛肉串', '鸡肉串'], 1000), group('SOUP', ['虾云吞汤（3颗）'], 2000), group('ROLL', ['素春卷（1条）'], 3000)] },
    categories: [{ id: 1, code: 'SOUP_NOODLE', name_zh: '面', name_en: 'Noodles', is_active: true, sort_order: 1, items: [noodle, rice, mixed, { ...noodle, id: 11, sku: 'test_b4', base_price: 20.99, customization_config: { ...noodle.customization_config!, combo: null }, options: noodle.options.filter((choice) => choice.option_group !== 'COMBO') }, { ...rice, id: 21, sku: 'test_c6', base_price: 17.99, options: [option(211, 'combo', 'COMBO', 2)] }, { ...rice, id: 90, sku: 'single_roll_component', sellable: false, options: [], customization_config: null }] }],
  }
}

function record() {
  return createLocalDraftRecord({ environmentId: 'http://synthetic.localhost', accountId: 1, organizationId: 200, storeId: 100 }, { orderType: 'pickup', tableNo: null, pickupNo: 'TEST', slotLabel: 'TEST', tableLabel: 'TEST' }, 7)
}

describe('typed merchant customization through the existing order snapshots', () => {
  it('keeps a Size-free base item and preserves French labels', () => {
    const item = mapCatalog(fixture()).items.find((candidate) => candidate.id === '10')!
    expect(item.customization?.sizes).toBeUndefined()
    expect(item.nameFr).toBe('Bœuf classique')
    expect(buildLocalLineItem(item, buildDefaultDraft(item)).lineSubtotal).toBe(15.99)
    expect(item.customization?.addOns?.find((choice) => choice.optionCode === 'extra_noodles')).toMatchObject({ priceDelta: 0, maxQuantity: 1 })
  })

  it('uses configured noodle defaults and accepts an additional configured type', () => {
    const data = fixture()
    const source = data.categories[0].items[0]
    source.customization_config!.option_groups[0].default_option_codes = ['thick']
    expect(buildDefaultDraft(mapCatalog(data).items[0]).noodleTypeId).toBe('102')
    source.options.push(option(106, 'udon', 'NOODLE_TYPE', 0, 'noodle_type'))
    source.customization_config!.option_groups[0].default_option_codes = ['udon']
    const item = mapCatalog(data).items[0]
    expect(item.customization?.noodleTypes).toHaveLength(3)
    expect(buildDefaultDraft(item).noodleTypeId).toBe('106')
  })

  it.each([1, 3])('freezes fixed noodle pricing and total component/add-on quantities for q=%s', (quantity) => {
    const item = mapCatalog(fixture()).items.find((candidate) => candidate.id === '10')!
    const draft = { ...buildDefaultDraft(item), quantity, comboEnabled: true, addOnQuantities: { '103': 1, '104': 1 } }
    const line = buildLocalLineItem(item, draft)
    const payload = buildFrozenSubmitPayload(record(), [line], [])
    expect(line.lineSubtotal).toBe(27.98 * quantity)
    expect(payload.expected_subtotal_amount).toBe(27.98 * quantity)
    expect(payload.items[0].options.every((choice) => choice.quantity === quantity)).toBe(true)
    expect(payload.items[0].options.find((choice) => choice.option_group_snapshot === 'APPETIZER')?.option_id).toBe(-1000001000)
    const serverAmount = payload.items[0].unit_price_snapshot * quantity + payload.items[0].options.reduce((sum, choice) => sum + choice.option_price_snapshot * choice.quantity, 0)
    expect(Number(serverAmount.toFixed(2))).toBe(payload.expected_subtotal_amount)
  })

  it.each(['20', '21'])('uses one fixed rice total across differing main prices (%s)', (id) => {
    const item = mapCatalog(fixture()).items.find((candidate) => candidate.id === id)!
    const line = buildLocalLineItem(item, { ...buildDefaultDraft(item), comboEnabled: true, quantity: 3 })
    expect(line.lineSubtotal).toBe(59.97)
    expect(item.customization?.combo?.groups.map((group) => group.groupCode)).toEqual(['SOUP', 'ROLL'])
    expect(line.optionSnapshots?.filter((choice) => choice.optionGroup === 'ROLL')).toEqual([expect.objectContaining({ nameZh: '素春卷（1条）', priceDelta: 0, quantity: 3 })])
    expect(line.optionSnapshots?.filter((choice) => choice.optionGroup === 'SOUP')).toEqual([expect.objectContaining({ nameZh: '虾云吞汤（3颗）', priceDelta: 0, quantity: 3 })])
  })

  it('preserves the whole two-roll appetizer in a noodle Combo', () => {
    const item = mapCatalog(fixture()).items[0]
    const choice = item.customization!.combo!.groups[0].options[1]
    const line = buildLocalLineItem(item, { ...buildDefaultDraft(item), comboEnabled: true, quantity: 3, comboSelections: { APPETIZER: choice.id } })
    expect(line.lineSubtotal).toBe(59.97)
    expect(line.optionSnapshots).toContainEqual(expect.objectContaining({ nameZh: '素春卷（2条）', quantity: 3, priceDelta: 0 }))
  })

  it('does not offer a Combo to excluded mains or display component-only products for sale', () => {
    const items = mapCatalog(fixture()).items
    expect(items.find((item) => item.id === '11')?.customization?.combo).toBeUndefined()
    expect(items.find((item) => item.id === '30')?.customization?.combo).toBeUndefined()
    expect(items.some((item) => item.id === '90')).toBe(false)
  })

  it('requires exactly two distinct proteins and freezes both selections per serving', () => {
    const item = mapCatalog(fixture()).items.find((candidate) => candidate.id === '30')!
    for (const selected of [[], ['301'], ['301', '301'], ['301', '302', '303']]) {
      const draft = { ...buildDefaultDraft(item), modifierSelections: { PROTEIN: selected } }
      expect(customizationValidationMessage(item, draft)).toBeTruthy()
      expect(() => buildLocalLineItem(item, draft)).toThrow()
    }
    const line = buildLocalLineItem(item, { ...buildDefaultDraft(item), quantity: 3, modifierSelections: { PROTEIN: ['301', '304'] } })
    expect(line.lineSubtotal).toBe(59.97)
    expect(line.optionSnapshots?.map((choice) => [choice.optionCode, choice.quantity])).toEqual([['beef', 3], ['shrimp', 3]])
  })

  it('rejects a second free add-on and scales only editable generic line snapshots', () => {
    const item = mapCatalog(fixture()).items[0]
    expect(() => buildLocalLineItem(item, { ...buildDefaultDraft(item), addOnQuantities: { '103': 2 } })).toThrow()
    const line = buildLocalLineItem(item, { ...buildDefaultDraft(item), quantity: 3, comboEnabled: true })
    expect(resizeSnapshotOptionQuantities(line, 4)?.every((choice) => choice.quantity === 4)).toBe(true)
    const before = JSON.stringify(line.optionSnapshots)
    expect(resizeSnapshotOptionQuantities({ ...line, optionQuantityMode: undefined }, 4)).toBe(line.optionSnapshots)
    expect(JSON.stringify(line.optionSnapshots)).toBe(before)
  })

  it('keeps existing line French names and options when the current menu changes', () => {
    const item = mapCatalog(fixture()).items[0]
    const line = buildLocalLineItem(item, buildDefaultDraft(item))
    const payload = buildFrozenSubmitPayload(record(), [line], [{ ...item, nameFr: 'Changed', nameZh: 'Changed' }])
    expect(payload.items[0].item_name_snapshot_fr).toBe('Bœuf classique')
    expect(payload.items[0].options[0].option_name_snapshot_fr).toBe('FR thin')
  })
})
