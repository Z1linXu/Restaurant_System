import { useState } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ItemCustomizationModal } from './ItemCustomizationModal'
import { buildDefaultDraft } from '../../../hooks/useOrderSessions'
import { calculateDraftLineSubtotal } from '../../../utils/orderPricing'
import type { TemplateOptionGroup } from '../../../services/templatePlatformService'
import type { ItemCustomizationDraft, MenuItem } from '../../../types/ordering'

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
beforeEach(() => { vi.stubGlobal('document', { body: { style: { overflow: '', touchAction: '' } } }) })
afterEach(() => { act(() => view?.unmount()); view = undefined; vi.unstubAllGlobals(); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true) })

describe('dynamic groups reuse mature customization controls', () => {
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
