import { useState } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ItemCustomizationModal } from './ItemCustomizationModal'
import { buildDefaultDraft } from '../../../hooks/useOrderSessions'
import { calculateDraftLineSubtotal } from '../../../utils/orderPricing'
import type { TemplateOptionGroup } from '../../../services/templatePlatformService'
import type { ItemCustomizationDraft, MenuItem } from '../../../types/ordering'
import { appI18n } from '../../../i18n/engine'
import { LanguageSelector } from '../../../components/LanguageSelector'

vi.mock('../../../hooks/useIpadLandscape', () => ({ useIpadLandscape: () => false }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
const sauce: TemplateOptionGroup = { group_instance_code: 'sauce', name: 'Sauce', kind: 'CUSTOM', selection_mode: 'SINGLE', required: true, is_active: true, sort_order: 10, default_option_ids: [], options: [
  { id: 11, option_type: 'addon', option_group: 'CUSTOM', option_code: 'garlic', parent_option_id: null, sort_order: 20, name_zh: '蒜蓉', name_en: 'Garlic', price_delta: 1, is_active: true },
  { id: 12, option_type: 'addon', option_group: 'CUSTOM', option_code: 'pepper', parent_option_id: null, sort_order: 10, name_zh: '黑椒', name_en: 'Pepper', price_delta: 2, is_active: true },
] }
const dish: MenuItem = { id: '20', categoryId: '7', nameZh: '合成菜', nameEn: 'Synthetic dish', descriptionZh: '', descriptionEn: '', price: 10, optionGroups: [sauce] }
let view: ReactTestRenderer | undefined
let lastDraft: ItemCustomizationDraft
const text = (node: { children: unknown[] }): string => node.children.map((child) => typeof child === 'string' ? child : child && typeof child === 'object' && 'children' in child ? text(child as { children: unknown[] }) : '').join('')
const button = (value: string) => view!.root.findAllByType('button').find((node) => text(node).includes(value))!
function Harness({ item }: { item: MenuItem }) {
  const [draft, setDraft] = useState(buildDefaultDraft(item))
  lastDraft = draft
  return <ItemCustomizationModal item={item} draft={draft} mode="add" subtotal={calculateDraftLineSubtotal(item, draft)} onChange={setDraft} onClose={() => undefined} onSubmit={() => undefined} />
}
beforeEach(async () => { await appI18n.changeLanguage('en'); vi.stubGlobal('document', { body: { style: { overflow: '', touchAction: '' } } }) })
afterEach(() => { act(() => view?.unmount()); view = undefined; vi.unstubAllGlobals(); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true) })

describe('dynamic groups reuse mature customization controls', () => {
  it('enforces the confirmed four-choose-two group, and lets a selected choice be removed at the limit', async () => {
    const group: TemplateOptionGroup = { ...sauce, name: 'Two sides', selection_mode: 'MULTIPLE', min_select: 2, max_select: 2,
      options: [1, 2, 3, 4].map((id) => ({ ...sauce.options[0], id, name_en: `Choice ${id}`, name_zh: `合成选项${id}`, price_delta: id })) }
    const item = { ...dish, optionGroups: [group] }
    await act(async () => { view = create(<Harness item={item} />) })
    expect(button('Add to Order').props.disabled).toBe(true)
    await act(async () => button('Choice 1').props.onClick())
    expect(button('Add to Order').props.disabled).toBe(true)
    await act(async () => button('Choice 2').props.onClick())
    expect(button('Add to Order').props.disabled).toBe(false)
    expect(button('Choice 3').props.disabled).toBe(true)
    expect(button('Choice 1').props.disabled).toBe(false)
    await act(async () => button('Choice 1').props.onClick())
    expect(button('Add to Order').props.disabled).toBe(true)
    expect(button('Choice 3').props.disabled).toBe(false)
    await act(async () => button('Choice 3').props.onClick())
    expect(lastDraft.groupSelections).toEqual({ sauce: { '1': 0, '2': 1, '3': 1 } })
    expect(calculateDraftLineSubtotal(item, lastDraft)).toBe(15)
  })

  it('keeps paid add-on quantities while min/max counts distinct choices', async () => {
    const group: TemplateOptionGroup = { ...sauce, kind: 'ADD_ON', selection_mode: 'MULTIPLE', min_select: 1, max_select: 1 }
    const item = { ...dish, optionGroups: [group] }
    await act(async () => { view = create(<Harness item={item} />) })
    expect(button('Add to Order').props.disabled).toBe(true)
    const increments = () => view!.root.findAllByType('button').filter((node) => text(node) === '+')
    // Display sorting puts Pepper first and Garlic second. Two Garlic portions are one distinct choice.
    await act(async () => increments()[1].props.onClick())
    await act(async () => increments()[1].props.onClick())
    expect(lastDraft.groupSelections).toEqual({ sauce: { '11': 2 } })
    expect(calculateDraftLineSubtotal(item, lastDraft)).toBe(12)
    expect(button('Add to Order').props.disabled).toBe(false)
    expect(increments()[0].props.disabled).toBe(true)
    expect(increments()[1].props.disabled).toBe(false)
  })

  it('switches the actual customization UI to French without losing its selected option or changing the amount', async () => {
    const item = { ...dish, nameFr: 'Plat synthétique', optionGroups: [{ ...sauce, name_translations: { 'fr-CA': 'Sauce' }, options: sauce.options.map((option) => ({ ...option, name_fr: option.id === 11 ? 'Ail' : 'Poivre' })) }] }
    await act(async () => { view = create(<><LanguageSelector /><Harness item={item} /></>) })
    await act(async () => button('Garlic').props.onClick())
    const before = JSON.stringify(lastDraft)
    const amount = calculateDraftLineSubtotal(item, lastDraft)
    await act(async () => view!.root.findByType('select').props.onChange({ target: { value: 'fr-CA' } }))
    expect(JSON.stringify(view!.toJSON())).toContain('Plat synthétique')
    expect(button('Ail')).toBeDefined()
    expect(button('Ajouter à la commande').props.disabled).toBe(false)
    expect(JSON.stringify(lastDraft)).toBe(before)
    expect(calculateDraftLineSubtotal(item, lastDraft)).toBe(amount)
  })

  it('sorts choices, requires selection, and replaces a single selection with the correct ordinary price', async () => {
    await act(async () => { view = create(<Harness item={dish} />) })
    expect(button('Add to Order').props.disabled).toBe(true)
    const choices = view!.root.findAllByType('button').map(text).filter((label) => label.includes('Pepper') || label.includes('Garlic'))
    expect(choices[0]).toContain('Pepper')
    await act(async () => button('Garlic').props.onClick())
    expect(lastDraft.groupSelections).toEqual({ sauce: { '11': 1 } })
    expect(button('Add to Order').props.disabled).toBe(false)
    await act(async () => button('Pepper').props.onClick())
    expect(lastDraft.groupSelections).toEqual({ sauce: { '12': 1 } })
    expect(calculateDraftLineSubtotal(dish, lastDraft)).toBe(12)
  })

  it('keeps a required empty group blocked instead of silently bypassing it', async () => {
    await act(async () => { view = create(<Harness item={{ ...dish, optionGroups: [{ ...sauce, options: [] }] }} />) })
    expect(button('Add to Order').props.disabled).toBe(true)
    expect(JSON.stringify(view!.toJSON())).toContain('No available options')
  })

  it('places the existing Combo section in configured group order, without another Combo system', async () => {
    const item: MenuItem = { ...dish, optionGroups: [{ ...sauce, kind: 'COMBO', group_instance_code: 'combo', name: 'Combo', options: [], required: false, sort_order: 20 }, sauce], customization: { combo: { optionId: '19', upcharge: 3.99, groups: [], eggs: [], sides: [], sideRemoveOptions: [] } } }
    await act(async () => { view = create(<Harness item={item} />) })
    expect(view!.root.findAll((node) => Boolean(node.props['data-option-group'])).map((node) => node.props['data-option-group'])).toEqual(['sauce', 'combo'])
    expect(view!.root.findAllByType('section').filter((node) => text(node).includes('Combo'))).not.toHaveLength(0)
  })
})
