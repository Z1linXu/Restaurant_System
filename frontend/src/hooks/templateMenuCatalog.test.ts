import { describe, expect, it } from 'vitest'
import type { BackendMenuCatalog } from '../types/ordering'
import type { TemplateOptionGroup } from '../services/templatePlatformService'
import { mapCatalog } from './useMenuCatalog'
import { buildDefaultDraft } from './useOrderSessions'
import { buildLocalLineItem } from './useDraftOrder'

const option = { id: 11, option_type: 'addon', option_code: 'garlic', option_group: 'CUSTOM', parent_option_id: null, sort_order: 0, name_zh: '蒜蓉', name_en: 'Garlic', price_delta: 1.25, is_active: true }
const group: TemplateOptionGroup = { group_instance_code: 'sauce', name: 'Sauce', kind: 'CUSTOM', selection_mode: 'SINGLE', required: true, is_active: true, sort_order: 0, default_option_ids: [11], options: [option] }
function catalog(groups?: TemplateOptionGroup[] | null): BackendMenuCatalog {
  return { store_id: 2, organization_id: 9, menu_revision: 2, generated_at: '', catalog_version: 'test', combo_metadata_version: 'test', content_hash: 'test', tax_policy: { rate: 0.14975, label: 'QC', version: 'test' }, categories: [{ id: 7, code: 'SYNTHETIC', name_zh: '合成', name_en: 'Synthetic', sort_order: 0, is_active: true, items: [{ id: 20, category_id: 7, station_id: 3, name_zh: '合成菜', name_en: 'Synthetic dish', sku: 'synthetic', item_type: 'menu_item', base_price: 10, is_active: true, is_sold_out: false, sort_order: 0, options: [option], option_groups: groups }] }] }
}

describe('catalog opt-in preserves legacy and uses canonical per-item option leaves', () => {
  it('keeps null and missing metadata in the exact legacy customization path', () => {
    expect(mapCatalog(catalog(null)).items[0]).toEqual(mapCatalog(catalog()).items[0])
    expect(mapCatalog(catalog()).items[0].customization?.addOns?.[0].id).toBe('11')
    expect(mapCatalog(catalog()).items[0].optionGroups).toBeUndefined()
  })
  it('uses explicit metadata without duplicating the old addon choice or its price', () => {
    const mapped = mapCatalog(catalog([group])).items[0]
    expect(mapped.customization?.addOns).toBeUndefined()
    const line = buildLocalLineItem(mapped, buildDefaultDraft(mapped))
    expect(line.lineSubtotal).toBe(11.25)
    expect(line.optionSnapshots).toHaveLength(1)
    expect(line.summaryTags).toEqual([{ en: 'Garlic', zh: '蒜蓉' }])
  })
  it('keeps existing Combo available only while its explicit group is active in new mode', () => {
    const data = catalog([])
    data.categories[0].items[0].options.push({ ...option, id: 19, option_group: 'COMBO', option_code: 'combo', name_zh: '套餐', name_en: 'Combo', price_delta: 3.99 })
    data.combo_configuration = { store_id: 2, menu_revision: 2, groups: [{ component_group: 'COMBO_EGG', group_code: 'COMBO_EGG', name_zh: '鸡蛋', name_en: 'Egg', selection_rule: 'EXACTLY_ONE', required: true, default_component_code: 'combo_fried_egg', components: [{ component_group: 'COMBO_EGG', component_code: 'combo_fried_egg', name_zh: '煎蛋', name_en: 'Fried egg', enabled: true, display_order: 0, is_default: true }] }] }
    expect(mapCatalog(data).items[0].customization?.combo).toBeUndefined()
    data.categories[0].items[0].option_groups = [{ ...group, kind: 'COMBO', options: [] }]
    expect(mapCatalog(data).items[0].customization?.combo?.optionId).toBe('19')
    data.categories[0].items[0].option_groups![0].is_active = false
    expect(mapCatalog(data).items[0].customization?.combo).toBeUndefined()
    data.categories[0].items[0].option_groups = null
    expect(mapCatalog(data).items[0].customization?.combo?.optionId).toBe('19')
  })
})
