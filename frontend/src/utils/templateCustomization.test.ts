import { describe, expect, it } from 'vitest'
import type { MenuItem } from '../types/ordering'
import type { TemplateOptionGroup } from '../services/templatePlatformService'
import { buildDefaultDraft } from '../hooks/useOrderSessions'
import { buildLocalLineItem } from '../hooks/useDraftOrder'
import { mapFrozenOrderItem } from '../services/orderService'
import { calculateDraftLineSubtotal, mapOptions } from './orderPricing'
import { selectTemplateOption, templateSelectionError } from './templateCustomization'
import { hasRequiredCustomization, isQuickAddItem } from '../features/ordering/orderingCustomizationRules'

export function group(patch: Partial<TemplateOptionGroup> = {}): TemplateOptionGroup {
  return { group_instance_code: 'sauce', name: 'Sauce', kind: 'CUSTOM', selection_mode: 'SINGLE', required: true, is_active: true, sort_order: 0, default_option_ids: [11], options: [
    { id: 11, option_type: 'addon', option_code: 'garlic_sauce', option_group: 'CUSTOM', parent_option_id: null, sort_order: 0, name_zh: '蒜蓉', name_en: 'Garlic', price_delta: 0.35, is_active: true },
    { id: 12, option_type: 'addon', option_code: 'pepper_sauce', option_group: 'CUSTOM', parent_option_id: null, sort_order: 10, name_zh: '黑椒', name_en: 'Pepper', price_delta: 1.2, is_active: true },
  ], ...patch }
}

export function item(groups?: TemplateOptionGroup[]): MenuItem {
  return { id: '20', categoryId: '7', categoryCode: 'SYNTHETIC', stationId: '3', nameEn: 'SYNTHETIC dish', nameZh: '合成菜', descriptionEn: '', descriptionZh: '', price: 10, optionGroups: groups }
}

describe('template selection reuses option snapshots and ordinary pricing', () => {
  it('captures configured defaults and computes integer-cent option quantity x parent quantity', () => {
    const menuItem = item([group({ selection_mode: 'MULTIPLE', kind: 'ADD_ON' })])
    const draft = { ...buildDefaultDraft(menuItem), quantity: 3, groupSelections: { sauce: { '11': 2, '12': 1 } } }
    expect(calculateDraftLineSubtotal(menuItem, draft)).toBe(35.7)
    const line = buildLocalLineItem(menuItem, draft)
    expect(line.optionSnapshots?.map((option) => [option.optionId, option.priceDelta, option.quantity])).toEqual([['11', 0.35, 2], ['12', 1.2, 1]])
    const changedCatalog = { ...menuItem, optionGroups: [group({ options: group().options.map((option) => ({ ...option, price_delta: 99 })) })] }
    expect(mapFrozenOrderItem(line, [changedCatalog]).options.map((option) => option.option_price_snapshot)).toEqual([0.35, 1.2])
    expect(mapFrozenOrderItem(line, [changedCatalog]).unit_price_snapshot).toBe(10)
  })

  it('does not add dynamic options twice when their semantic type is addon', () => {
    const menuItem = item([group()])
    const draft = buildDefaultDraft(menuItem)
    expect(draft.groupSelections).toEqual({ sauce: { '11': 1 } })
    expect(mapOptions(draft, menuItem)).toEqual([expect.objectContaining({ option_id: 11, quantity: 1, option_price_snapshot: 0.35 })])
  })

  it('blocks missing required groups, multiple single choices and invalid option IDs before local quote', () => {
    const menuItem = item([group()])
    const cases: Record<string, Record<string, number>>[] = [{ sauce: {} }, { sauce: { '11': 1, '12': 1 } }, { sauce: { '999': 1 } }]
    for (const selections of cases) {
      const draft = { ...buildDefaultDraft(menuItem), groupSelections: selections }
      expect(templateSelectionError(menuItem, draft)).toBeTruthy()
      expect(() => buildLocalLineItem(menuItem, draft)).toThrow()
    }
  })

  it('does not introduce a business maximum for multi-select groups', () => {
    const options = Array.from({ length: 25 }, (_, i) => ({ ...group().options[0], id: i + 1, option_code: `synthetic-${i}` }))
    const menuItem = item([group({ selection_mode: 'MULTIPLE', options, default_option_ids: [] })])
    const draft = { ...buildDefaultDraft(menuItem), groupSelections: { sauce: Object.fromEntries(options.map((option) => [String(option.id), 1])) } }
    expect(templateSelectionError(menuItem, draft)).toBeNull()
    expect(mapOptions(draft, menuItem)).toHaveLength(25)
  })

  it('preserves stable-code Add-on/Remove conflict handling independent of labels', () => {
    const addon = group({ group_instance_code: 'a', kind: 'ADD_ON', selection_mode: 'MULTIPLE', required: false, options: [{ ...group().options[0], option_code: 'extra_onion', name_en: 'Renamed', id: 11 }] })
    const remove = group({ group_instance_code: 'r', kind: 'REMOVE', selection_mode: 'MULTIPLE', required: false, options: [{ ...group().options[0], option_code: 'remove_onion', id: 12 }] })
    const draft = { ...buildDefaultDraft(item([addon, remove])), groupSelections: { r: { '12': 1 }, a: {} } }
    const selected = selectTemplateOption(draft, addon, 11, 2, [addon, remove])
    expect(selected.groupSelections).toEqual({ r: {}, a: { '11': 2 } })
    expect(selectTemplateOption(selected, remove, 12, 1, [addon, remove]).groupSelections).toEqual({ r: { '12': 1 }, a: {} })
  })
})

describe('explicit group mode Quick Add boundary', () => {
  it('adds an arbitrary category group-free spring roll directly, preserving legacy category behavior', () => {
    expect(isQuickAddItem(item([]))).toBe(true)
    expect(isQuickAddItem(item())).toBe(false)
    expect(isQuickAddItem({ ...item(), categoryCode: 'FRIED' })).toBe(true)
    expect(isQuickAddItem({ ...item(), categoryCode: 'DRINK' })).toBe(true)
  })

  it('never bypasses an interactive or empty required group, even with a configured default', () => {
    expect(isQuickAddItem(item([group()]))).toBe(false)
    const emptyRequired = item([group({ options: [], default_option_ids: [] })])
    expect(isQuickAddItem(emptyRequired)).toBe(false)
    expect(hasRequiredCustomization(emptyRequired)).toBe(true)
    expect(templateSelectionError(emptyRequired, buildDefaultDraft(emptyRequired))).toBeTruthy()
    expect(isQuickAddItem(item([group({ is_active: false })]))).toBe(true)
    expect(isQuickAddItem(item([group({ required: false, options: [], default_option_ids: [] })]))).toBe(true)
  })
})
