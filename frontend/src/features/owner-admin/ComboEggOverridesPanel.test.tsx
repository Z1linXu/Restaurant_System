import { useState } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  fetchOwnerMenuItemOptions,
  fetchStoreComboConfiguration,
  updateOwnerMenuItemComboEggDefault,
  type MenuItemOptionAdminRecord,
  type StoreComboConfigurationRecord,
} from '../../services/ownerMenuOptionService'
import { fetchAdminMenuItems, type MenuItemAdminRecord } from '../../services/platformAdminService'
import { ComboEggOverridesPanel } from './ComboEggOverridesPanel'

vi.mock('../../services/ownerMenuOptionService', () => ({
  fetchOwnerMenuItemOptions: vi.fn(),
  fetchStoreComboConfiguration: vi.fn(),
  updateOwnerMenuItemComboEggDefault: vi.fn(),
}))
vi.mock('../../services/platformAdminService', () => ({ fetchAdminMenuItems: vi.fn() }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)

function item(id: number, patch: Partial<MenuItemAdminRecord> = {}): MenuItemAdminRecord {
  return {
    id, store_id: 1, category_id: 1, station_id: 1, name_zh: `菜品${id}`, name_en: `Item ${id}`,
    sku: `ITEM_${id}`, item_type: 'NOODLE', base_price: 12, cost_per_item: 3,
    is_active: true, is_sold_out: false, sort_order: id, ...patch,
  }
}

function option(patch: Partial<MenuItemOptionAdminRecord> = {}): MenuItemOptionAdminRecord {
  return {
    id: 1, menu_item_id: 1, option_type: 'addon', option_code: 'combo', option_group: 'COMBO',
    parent_option_id: null, sort_order: 1, name_zh: '套餐', name_en: 'Combo', price_delta: 5, is_active: true,
    ...patch,
  }
}

const initial: StoreComboConfigurationRecord = {
  store_id: 1, menu_revision: 4,
  groups: [{
    component_group: 'COMBO_EGG', group_code: 'COMBO_EGG', name_zh: '蛋', name_en: 'Egg',
    enabled: true, default_component_code: 'combo_tea_egg', components: [
      { component_group: 'COMBO_EGG', component_code: 'combo_tea_egg', name_zh: '卤蛋', name_en: 'Tea Egg', enabled: true, display_order: 10, is_default: true },
      { component_group: 'COMBO_EGG', component_code: 'combo_fried_egg', name_zh: '煎蛋', name_en: 'Fried Egg', enabled: true, display_order: 20, is_default: false },
      { component_group: 'COMBO_EGG', component_code: 'combo_disabled', name_zh: '停用蛋', name_en: 'Disabled Egg', enabled: false, display_order: 30, is_default: false },
    ],
  }],
  item_overrides: [],
}

const exception = { item_id: 8, name_zh: '已有菜品', name_en: 'Existing item', default_combo_egg_component_code: 'combo_fried_egg' }
let view: ReactTestRenderer | undefined

async function mount(configuration = initial, onChanged = vi.fn()) {
  await act(async () => { view = create(<ComboEggOverridesPanel storeId={1} configuration={configuration} onChanged={onChanged} />) })
  return view!
}

function button(text: string) {
  return view!.root.findAllByType('button').find((node) => node.children.join('') === text)!
}

async function openAdd() {
  await act(async () => button('Add Exception / 添加例外').props.onClick())
}

async function select(label: string, value: string) {
  await act(async () => view!.root.findByProps({ 'aria-label': label }).props.onChange({ target: { value } }))
}

async function submit() {
  await act(async () => view!.root.findByType('form').props.onSubmit({ preventDefault: vi.fn() }))
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(fetchAdminMenuItems).mockResolvedValue([item(1)])
  vi.mocked(fetchOwnerMenuItemOptions).mockResolvedValue([option()])
  vi.mocked(updateOwnerMenuItemComboEggDefault).mockResolvedValue(undefined)
  vi.mocked(fetchStoreComboConfiguration).mockResolvedValue(initial)
})

afterEach(async () => {
  if (view) await act(async () => view!.unmount())
  view = undefined
})

describe('ComboEggOverridesPanel', () => {
  it('offers only active same-store Combo items and enabled egg components, without item-name guessing', async () => {
    vi.mocked(fetchAdminMenuItems).mockResolvedValue([
      item(1, { name_en: 'Renamed dish' }),
      item(2, { name_en: 'Chow mein' }),
      item(3), item(4, { is_active: false }), item(5, { store_id: 2 }), item(6), item(8),
    ])
    vi.mocked(fetchOwnerMenuItemOptions).mockImplementation(async (id) => {
      if (id === 2) return [option({ option_code: 'fried_egg', option_group: 'ADD_ON' })]
      if (id === 3) return [option({ is_active: false })]
      if (id === 6) return [option({ option_code: 'combo', option_group: null })]
      return [option()]
    })
    await mount({ ...initial, item_overrides: [exception] })
    await openAdd()
    const itemOptions = view!.root.findByProps({ 'aria-label': 'Override item' }).findAllByType('option')
    expect(itemOptions.map((node) => node.props.value)).toEqual(['', 1])
    expect(fetchOwnerMenuItemOptions).not.toHaveBeenCalledWith(4)
    expect(fetchOwnerMenuItemOptions).not.toHaveBeenCalledWith(5)
    const eggs = view!.root.findByProps({ 'aria-label': 'Item default egg' }).findAllByType('option')
    expect(eggs.map((node) => node.props.value)).toEqual(['combo_tea_egg', 'combo_fried_egg'])
  })

  it('adds, edits and removes only exceptions, refreshing the saved configuration and sending null on removal', async () => {
    function Host() {
      const [configuration, setConfiguration] = useState(initial)
      return <ComboEggOverridesPanel storeId={1} configuration={configuration} onChanged={setConfiguration} />
    }
    await act(async () => { view = create(<Host />) })
    await openAdd()
    await select('Override item', '1')
    await select('Item default egg', 'combo_fried_egg')
    const added = { item_id: 1, name_zh: '菜品1', name_en: 'Item 1', default_combo_egg_component_code: 'combo_fried_egg' }
    vi.mocked(fetchStoreComboConfiguration).mockResolvedValue({ ...initial, item_overrides: [added] })
    await submit()
    expect(updateOwnerMenuItemComboEggDefault).toHaveBeenLastCalledWith(1, 'combo_fried_egg')
    expect(view!.root.findAllByType('li')).toHaveLength(1)

    await act(async () => view!.root.findByProps({ 'aria-label': 'Edit egg default for Item 1' }).props.onClick())
    expect(view!.root.findByProps({ 'aria-label': 'Override item' }).props.disabled).toBe(true)
    await select('Item default egg', 'combo_tea_egg')
    vi.mocked(fetchStoreComboConfiguration).mockResolvedValue({ ...initial, item_overrides: [{ ...added, default_combo_egg_component_code: 'combo_tea_egg' }] })
    await submit()
    expect(updateOwnerMenuItemComboEggDefault).toHaveBeenLastCalledWith(1, 'combo_tea_egg')

    vi.mocked(fetchStoreComboConfiguration).mockResolvedValue(initial)
    await act(async () => view!.root.findByProps({ 'aria-label': 'Remove egg default for Item 1' }).props.onClick())
    expect(updateOwnerMenuItemComboEggDefault).toHaveBeenLastCalledWith(1, null)
    expect(fetchStoreComboConfiguration).toHaveBeenCalledTimes(3)
    expect(view!.root.findAllByType('li')).toHaveLength(0)
  })

  it('keeps existing exceptions visible and removable when candidates cannot be loaded', async () => {
    vi.mocked(fetchAdminMenuItems).mockRejectedValue(new Error('Menu unavailable'))
    await mount({ ...initial, item_overrides: [exception] })
    expect(view!.root.findByProps({ role: 'alert' }).findByType('p').children).toEqual(['Menu unavailable'])
    expect(view!.root.findAllByType('li')).toHaveLength(1)
    expect(button('Add Exception / 添加例外').props.disabled).toBe(true)
    await act(async () => view!.root.findByProps({ 'aria-label': 'Remove egg default for Existing item' }).props.onClick())
    expect(updateOwnerMenuItemComboEggDefault).toHaveBeenCalledWith(8, null)
  })

  it('preserves the saved exception and current edit after a failed save', async () => {
    const changed = vi.fn()
    vi.mocked(updateOwnerMenuItemComboEggDefault).mockRejectedValue(new Error('Egg no longer available'))
    await mount({ ...initial, item_overrides: [exception] }, changed)
    await act(async () => view!.root.findByProps({ 'aria-label': 'Edit egg default for Existing item' }).props.onClick())
    await select('Item default egg', 'combo_tea_egg')
    await submit()
    expect(view!.root.findByProps({ role: 'alert' }).findByType('p').children).toEqual(['Egg no longer available'])
    expect(view!.root.findAllByType('li')).toHaveLength(1)
    expect(view!.root.findByProps({ 'aria-label': 'Item default egg' }).props.value).toBe('combo_tea_egg')
    expect(changed).not.toHaveBeenCalled()
    expect(fetchStoreComboConfiguration).not.toHaveBeenCalled()
  })

  it('preserves an unavailable saved default for display and requires a valid replacement before saving', async () => {
    await mount({ ...initial, item_overrides: [{ ...exception, default_combo_egg_component_code: 'combo_disabled' }] })
    await act(async () => view!.root.findByProps({ 'aria-label': 'Edit egg default for Existing item' }).props.onClick())
    expect(view!.root.findByProps({ 'aria-label': 'Item default egg' }).props.value).toBe('combo_disabled')
    expect(button('Save Exception / 保存例外').props.disabled).toBe(true)
    await submit()
    expect(updateOwnerMenuItemComboEggDefault).not.toHaveBeenCalled()
    await select('Item default egg', 'combo_fried_egg')
    expect(button('Save Exception / 保存例外').props.disabled).toBe(false)
  })

  it('does not refresh or notify the new page when a save completes after unmount', async () => {
    let resolveSave!: () => void
    vi.mocked(updateOwnerMenuItemComboEggDefault).mockReturnValue(new Promise((resolve) => { resolveSave = resolve }))
    const changed = vi.fn()
    await mount({ ...initial, item_overrides: [exception] }, changed)
    await act(async () => view!.root.findByProps({ 'aria-label': 'Remove egg default for Existing item' }).props.onClick())
    await act(async () => view!.unmount())
    view = undefined
    await act(async () => resolveSave())
    expect(fetchStoreComboConfiguration).not.toHaveBeenCalled()
    expect(changed).not.toHaveBeenCalled()
  })
})
