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

const layout = vi.hoisted(() => ({ compact: false }))
vi.mock('../../../hooks/useIpadLandscape', () => ({ useIpadLandscape: () => layout.compact }))
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
beforeEach(async () => { layout.compact = false; await appI18n.changeLanguage('en'); vi.stubGlobal('document', { body: { style: { overflow: '', touchAction: '' } } }) })
afterEach(() => { act(() => view?.unmount()); view = undefined; vi.unstubAllGlobals(); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true) })

describe('dynamic groups reuse mature customization controls', () => {
  const noodle: TemplateOptionGroup = { ...sauce, group_instance_code: 'noodle', name: 'Noodle choice', name_translations: { en: 'Noodle choice', 'zh-CN': '面型', 'fr-CA': 'Choix de nouilles' }, sort_order: 0, default_option_ids: [21], options: [
    { ...sauce.options[0], id: 21, name_en: 'Thin noodles', name_zh: '细面', name_fr: 'Nouilles fines', price_delta: 0, is_default: true },
    { ...sauce.options[0], id: 22, name_en: 'Thick noodles', name_zh: '粗面', name_fr: 'Nouilles épaisses', price_delta: 0 },
    { ...sauce.options[0], id: 23, name_en: 'Rice vermicelli', name_zh: '米粉', name_fr: 'Vermicelles de riz', price_delta: 0 },
  ] }
  const spicy: TemplateOptionGroup = { ...sauce, group_instance_code: 'spicy', kind: 'SPICY_LEVEL', name: 'Spicy Level', name_translations: { en: 'Spicy Level', 'zh-CN': '辣度', 'fr-CA': 'Niveau de piquant' }, sort_order: 20, default_option_ids: [31], options: [
    { ...sauce.options[0], id: 31, name_en: 'Non-Spicy', name_zh: '不辣', name_fr: 'Non épicé', price_delta: 0, is_default: true },
    { ...sauce.options[0], id: 32, name_en: 'Mild', name_zh: '少辣', name_fr: 'Peu épicé', price_delta: 0 },
    { ...sauce.options[0], id: 33, name_en: 'Regular', name_zh: '正常辣', name_fr: 'Épicé', price_delta: 0 },
    { ...sauce.options[0], id: 34, name_en: 'Extra', name_zh: '加辣', name_fr: 'Très épicé', price_delta: 0 },
  ] }
  const addon: TemplateOptionGroup = { ...sauce, group_instance_code: 'addon', kind: 'ADD_ON', name: 'Add-ons', name_translations: { en: 'Add-ons', 'zh-CN': '加料' }, selection_mode: 'MULTIPLE', required: false, sort_order: 10 }
  const remove: TemplateOptionGroup = { ...sauce, group_instance_code: 'remove', kind: 'REMOVE', name: 'Remove', name_translations: { en: 'Remove', 'zh-CN': '免除' }, selection_mode: 'MULTIPLE', required: false, sort_order: 30,
    options: [{ ...sauce.options[0], id: 41, name_en: 'No garnish', name_zh: '免配菜', price_delta: 0 }] }

  it.each([false, true])('keeps configured defaults, single-choice replacement and add-on quantity two in compact=%s', async (compact) => {
    layout.compact = compact
    const item: MenuItem = { ...dish, categoryCode: 'NOODLE', optionGroups: [noodle, addon, spicy, remove] }
    const originalConfiguration = JSON.stringify(item.optionGroups)
    await act(async () => { view = create(<Harness item={item} />) })
    expect(lastDraft.groupSelections?.noodle).toEqual({ '21': 1 })
    expect(lastDraft.groupSelections?.spicy).toEqual({ '31': 1 })
    expect(button('Add to Order').props.disabled).toBe(false)
    expect(view!.root.findAll((node) => Boolean(node.props['data-option-group'])).map((node) => node.props['data-option-group'])).toEqual(['noodle', 'spicy', 'addon', 'remove'])
    expect(view!.root.findAllByType('h3').map(text)).toContain('Spicy Level / 辣度 Required')
    expect(text(button('Thin noodles'))).toBe('Thin noodles细面')
    await act(async () => button('Mild').props.onClick())
    await act(async () => button('Extra').props.onClick())
    expect(lastDraft.groupSelections?.spicy).toEqual({ '34': 1 })
    const addonSection = () => view!.root.findAllByType('section').find((node) => node.props['data-option-group'] === 'addon')!
    const garlicIncrement = () => addonSection().findAllByType('button').filter((node) => text(node) === '+')[1]
    await act(async () => garlicIncrement().props.onClick())
    await act(async () => garlicIncrement().props.onClick())
    expect(lastDraft.groupSelections?.addon).toEqual({ '11': 2 })
    expect(calculateDraftLineSubtotal(item, lastDraft)).toBe(12)
    expect(JSON.stringify(item.optionGroups)).toBe(originalConfiguration)
  })

  it('shows bilingual choices through all locales without adding a missing Remove group or losing draft state', async () => {
    const item: MenuItem = { ...dish, categoryCode: 'NOODLE', optionGroups: [noodle, addon, spicy] }
    await act(async () => { view = create(<><LanguageSelector /><Harness item={item} /></>) })
    await act(async () => button('Thick noodles').props.onClick())
    await act(async () => button('Regular').props.onClick())
    await act(async () => view!.root.findByType('textarea').props.onChange({ target: { value: 'Keep this note' } }))
    const selected = JSON.stringify(lastDraft)
    for (const locale of ['zh-CN', 'en', 'fr-CA']) {
      await act(async () => view!.root.findByType('select').props.onChange({ target: { value: locale } }))
      expect(text(button(locale === 'fr-CA' ? 'Nouilles fines' : 'Thin noodles'))).toBe(locale === 'fr-CA' ? 'Nouilles fines细面' : 'Thin noodles细面')
      expect(text(button(locale === 'fr-CA' ? 'Épicé' : 'Regular'))).toBe(locale === 'fr-CA' ? 'Épicé正常辣' : 'Regular正常辣')
      expect(view!.root.findAll((node) => node.props?.['data-option-group'] === 'remove')).toHaveLength(0)
      expect(JSON.stringify(lastDraft)).toBe(selected)
      expect(calculateDraftLineSubtotal(item, lastDraft)).toBe(10)
    }
    expect(view!.root.findAllByType('section').find((node) => node.props['data-option-group'] === 'noodle')!.findAllByType('button')).toHaveLength(3)
    expect(JSON.stringify(view!.toJSON())).not.toContain('Capillary')
    expect(JSON.stringify(view!.toJSON())).not.toContain('No Cilantro')
  })

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
