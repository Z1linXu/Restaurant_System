import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TemplateOptionGroupsPanel } from './TemplateOptionGroupsPanel'
import { copyTemplateGroup } from '../../utils/templateOptionGroups'
import { fetchItemOptionGroups, fetchOptionLibrary, saveItemOptionGroups, saveOptionLibrary } from '../../services/templatePlatformService'
import type { TemplateOptionGroup } from '../../services/templatePlatformService'
import { fetchStoreAddons } from '../../services/ownerAddonService'

vi.mock('../../services/templatePlatformService', () => ({ fetchItemOptionGroups: vi.fn(), fetchOptionLibrary: vi.fn(), saveItemOptionGroups: vi.fn(), saveOptionLibrary: vi.fn() }))
vi.mock('../../services/ownerAddonService', () => ({ fetchStoreAddons: vi.fn() }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
const source: TemplateOptionGroup = { group_instance_code: 'spicy', name: 'Spicy', kind: 'SPICY_LEVEL', selection_mode: 'SINGLE', required: true, is_active: true, sort_order: 0, default_option_ids: [5], options: [{ id: 5, option_type: 'spicy_level', option_code: 'mild', option_group: 'SPICY_LEVEL', parent_option_id: null, sort_order: 0, name_zh: '微辣', name_en: 'Mild', price_delta: 0, is_active: true }] }
let view: ReactTestRenderer | undefined
const text = (node: { children: unknown[] }): string => node.children.map((child) => typeof child === 'string' ? child : child && typeof child === 'object' && 'children' in child ? text(child as { children: unknown[] }) : '').join('')
const button = (label: string) => view!.root.findAllByType('button').find((node) => text(node).includes(label))!

beforeEach(() => {
  vi.clearAllMocks(); vi.stubGlobal('window', { confirm: vi.fn(() => true) })
  vi.mocked(fetchOptionLibrary).mockResolvedValue({ groups: [structuredClone(source)] })
  vi.mocked(fetchStoreAddons).mockResolvedValue({ addons: [{ id: 99, store_id: 2, code: 'extra_beef', name_zh: '加牛肉', name_en: 'Extra Beef', price: 3.25, active: true, printing_configured: false }], conflicts: [] })
  vi.mocked(fetchItemOptionGroups).mockResolvedValue({ groups: [] })
  vi.mocked(saveOptionLibrary).mockImplementation(async (_store, payload) => payload)
  vi.mocked(saveItemOptionGroups).mockImplementation(async (_store, _item, payload) => ({ ...payload, groups: payload.groups.map((group) => ({ ...group, options: group.options.map((option, i) => ({ ...option, id: option.id ?? 100 + i })) })) }))
})
afterEach(() => { act(() => view?.unmount()); view = undefined; vi.unstubAllGlobals(); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true) })

describe('independent option group copies', () => {
  it('clears library option IDs and keeps a default marker without mutating the source', () => {
    const a = copyTemplateGroup(source), b = copyTemplateGroup(source)
    a.options[0].price_delta = 4
    expect(b.options[0].price_delta).toBe(0)
    expect(source.options[0].price_delta).toBe(0)
    expect(a.options[0].id).toBeUndefined()
    expect(a.options[0].is_default).toBe(true)
    expect(a.default_option_ids).toEqual([])
    expect(a.group_instance_code).not.toBe(b.group_instance_code)
  })

  it('copies a library group to an item, edits its price, and saves to the scoped item API', async () => {
    await act(async () => { view = create(<TemplateOptionGroupsPanel storeId={2} itemId={20} />) })
    await act(async () => view!.root.findByProps({ 'aria-label': 'Library group' }).props.onChange({ target: { value: 'spicy' } }))
    await act(async () => button('Add independent copy').props.onClick())
    await act(async () => view!.root.findByProps({ 'aria-label': 'Option price 1-1' }).props.onChange({ target: { value: '1.75' } }))
    await act(async () => button('Save option groups').props.onClick())
    expect(saveItemOptionGroups).toHaveBeenCalledWith(2, 20, { groups: [expect.objectContaining({ source_library_code: 'spicy', options: [expect.objectContaining({ id: undefined, price_delta: 1.75, is_default: true })] })] })
    expect(saveOptionLibrary).not.toHaveBeenCalled()
    expect(view!.root.findAllByProps({ role: 'status' }).map(text).join()).toContain('Saved')
  })

  it('sorts groups and options and can persist explicit no-group configuration', async () => {
    vi.mocked(fetchItemOptionGroups).mockResolvedValue({ groups: [structuredClone(source), { ...structuredClone(source), group_instance_code: 'size', name: 'Size' }] })
    await act(async () => { view = create(<TemplateOptionGroupsPanel storeId={2} itemId={20} />) })
    await act(async () => view!.root.findByProps({ 'aria-label': 'Move group 2 up' }).props.onClick())
    await act(async () => button('Save option groups').props.onClick())
    expect(vi.mocked(saveItemOptionGroups).mock.calls[0][2].groups.map((group) => [group.name, group.sort_order])).toEqual([['Size', 0], ['Spicy', 10]])
    await act(async () => button('Remove group').props.onClick())
    await act(async () => button('Remove group').props.onClick())
    await act(async () => button('Save option groups').props.onClick())
    expect(saveItemOptionGroups).toHaveBeenLastCalledWith(2, 20, { groups: [] })
  })

  it('does not claim save success after backend rejection', async () => {
    vi.mocked(saveOptionLibrary).mockRejectedValue(new Error('Store scope denied'))
    await act(async () => { view = create(<TemplateOptionGroupsPanel storeId={2} />) })
    await act(async () => button('Save option groups').props.onClick())
    expect(view!.root.findByProps({ role: 'alert' }).children.join()).toContain('Store scope denied')
    expect(view!.root.findAllByProps({ role: 'status' })).toHaveLength(0)
  })

  it('persists option order and transfers a single default without changing option identity', async () => {
    const second = { ...source.options[0], id: 6, name_en: 'Hot', name_zh: '辣', option_code: 'hot', sort_order: 10 }
    vi.mocked(fetchItemOptionGroups).mockResolvedValue({ groups: [{ ...structuredClone(source), options: [source.options[0], second] }] })
    await act(async () => { view = create(<TemplateOptionGroupsPanel storeId={2} itemId={20} />) })
    await act(async () => view!.root.findByProps({ 'aria-label': 'Move option 1-2 up' }).props.onClick())
    await act(async () => view!.root.findByProps({ 'aria-label': 'Default option 1-1' }).props.onChange({ target: { checked: true } }))
    await act(async () => button('Save option groups').props.onClick())
    const saved = vi.mocked(saveItemOptionGroups).mock.calls[0][2].groups[0]
    expect(saved.options.map((option) => [option.id, option.sort_order, option.is_default])).toEqual([[6, 0, true], [5, 10, false]])
    expect(saved.default_option_ids).toEqual([6])
  })

  it('creates a freely named custom group with its own option/default and enabled state', async () => {
    await act(async () => { view = create(<TemplateOptionGroupsPanel storeId={2} itemId={20} />) })
    await act(async () => button('New group').props.onClick())
    await act(async () => view!.root.findByProps({ 'aria-label': 'Group name 1' }).props.onChange({ target: { value: 'Temperature / 温度' } }))
    await act(async () => button('Add option').props.onClick())
    await act(async () => view!.root.findByProps({ 'aria-label': 'Option Chinese name 1-1' }).props.onChange({ target: { value: '常温' } }))
    await act(async () => view!.root.findByProps({ 'aria-label': 'Option English name 1-1' }).props.onChange({ target: { value: 'Room temperature' } }))
    await act(async () => view!.root.findByProps({ 'aria-label': 'Default option 1-1' }).props.onChange({ target: { checked: true } }))
    await act(async () => view!.root.findByProps({ 'aria-label': 'Enabled group 1' }).props.onChange({ target: { checked: false } }))
    await act(async () => button('Save option groups').props.onClick())
    expect(saveItemOptionGroups).toHaveBeenCalledWith(2, 20, { groups: [expect.objectContaining({ name: 'Temperature / 温度', kind: 'CUSTOM', is_active: false, options: [expect.objectContaining({ name_zh: '常温', name_en: 'Room temperature', is_default: true })] })] })
    expect(vi.mocked(saveItemOptionGroups).mock.calls[0][2].groups[0].options[0].id).toBeUndefined()
  })

  it('never clears item configuration when its initial scoped read failed', async () => {
    vi.mocked(fetchItemOptionGroups).mockRejectedValue(new Error('Unable to load canonical groups'))
    await act(async () => { view = create(<TemplateOptionGroupsPanel storeId={2} itemId={20} />) })
    expect(button('Save option groups').props.disabled).toBe(true)
    await act(async () => button('Save option groups').props.onClick())
    expect(saveItemOptionGroups).not.toHaveBeenCalled()
  })

  it('selects shared Add-ons from the catalog and keeps their bilingual names and price read-only', async () => {
    vi.mocked(fetchItemOptionGroups).mockResolvedValue({ groups: [{ ...structuredClone(source), kind: 'ADD_ON', group_instance_code: 'paid_addons', selection_mode: 'MULTIPLE', required: false, default_option_ids: [], options: [] }] })
    await act(async () => { view = create(<TemplateOptionGroupsPanel storeId={2} itemId={20} />) })
    await act(async () => view!.root.findByProps({ 'aria-label': 'Add-on catalog 1' }).props.onChange({ target: { value: '99' } }))
    expect(view!.root.findByProps({ 'aria-label': 'Option price 1-1' }).props).toMatchObject({ value: 3.25, readOnly: true })
    expect(view!.root.findByProps({ 'aria-label': 'Option Chinese name 1-1' }).props).toMatchObject({ value: '加牛肉', readOnly: true })
    expect(view!.root.findByProps({ 'aria-label': 'Option English name 1-1' }).props.readOnly).toBe(true)
    expect(view!.root.findByProps({ href: '/stores/2/admin/menu/items?section=addons' })).toBeTruthy()
    await act(async () => view!.root.findByProps({ 'aria-label': 'Option French name 1-1' }).props.onChange({ target: { value: 'Bœuf supplémentaire' } }))
    await act(async () => button('Save option groups').props.onClick())
    expect(saveItemOptionGroups).toHaveBeenCalledWith(2, 20, { groups: [expect.objectContaining({ options: [expect.objectContaining({ store_addon_id: 99, addon_eligible: true, option_code: 'extra_beef', price_delta: 3.25, name_fr: 'Bœuf supplémentaire' })] })] })
  })

  it('preserves item eligibility while a catalog entry is unavailable', async () => {
    const linked = { ...structuredClone(source.options[0]), store_addon_id: 99, addon_eligible: true, is_active: false, is_default: false }
    vi.mocked(fetchItemOptionGroups).mockResolvedValue({ groups: [{ ...structuredClone(source), kind: 'ADD_ON', required: false, default_option_ids: [], options: [linked] }] })
    await act(async () => { view = create(<TemplateOptionGroupsPanel storeId={2} itemId={20} />) })
    expect(view!.root.findByProps({ 'aria-label': 'Enabled option 1-1' }).props.checked).toBe(true)
    await act(async () => button('Save option groups').props.onClick())
    expect(vi.mocked(saveItemOptionGroups).mock.calls[0][2].groups[0].options[0]).toMatchObject({ addon_eligible: true, is_active: false })
  })
})
