import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MenuOptionsPanel } from './MenuOptionsPanel'
import { fetchItemAddons, fetchStoreAddons, updateItemAddon } from '../../services/ownerAddonService'
import { fetchOwnerMenuItemOptions, updateOwnerMenuItemOption, reorderOwnerMenuItemOptions, type MenuItemOptionAdminRecord } from '../../services/ownerMenuOptionService'

vi.mock('../../services/ownerAddonService', () => ({ fetchItemAddons: vi.fn(), fetchStoreAddons: vi.fn(), updateItemAddon: vi.fn() }))
vi.mock('../../services/ownerMenuOptionService', () => ({
  fetchOwnerMenuItemOptions: vi.fn(), updateOwnerMenuItemOption: vi.fn(), reorderOwnerMenuItemOptions: vi.fn(),
  createOwnerMenuItemOption: vi.fn(), updateOwnerMenuItemComboPolicy: vi.fn(), updateOwnerMenuItemSizeConfiguration: vi.fn(),
}))

const addon = { id: 101, store_id: 1, code: 'extra_beef', name_zh: '加牛肉', name_en: 'Extra Beef', price: 3, active: true, printing_configured: false, enabled: false }
function option(id: number, group: string, overrides: Partial<MenuItemOptionAdminRecord> = {}): MenuItemOptionAdminRecord {
  return { id, menu_item_id: 1, option_type: group === 'REMOVE' ? 'remove' : 'addon', option_group: group,
    option_code: `code_${id}`, name_zh: `选项${id}`, name_en: `Option ${id}`, price_delta: 0,
    parent_option_id: null, sort_order: id, is_active: true, ...overrides }
}

describe('MenuOptionsPanel central Add-ons', () => {
  let renderer: ReactTestRenderer
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
    vi.mocked(fetchItemAddons).mockResolvedValue([addon])
    vi.mocked(fetchStoreAddons).mockResolvedValue({ addons: [addon], conflicts: [] })
    vi.mocked(fetchOwnerMenuItemOptions).mockResolvedValue([
      option(1, 'ADD_ON', { store_addon_id: 101, name_zh: '加牛肉', name_en: 'Extra Beef' }),
      option(2, 'REMOVE'), option(3, 'NOODLE_TYPE', { option_type: 'noodle_type' }),
      option(4, 'COMBO_EGG', { option_code: 'combo_fried_egg' }),
    ])
    vi.mocked(updateItemAddon).mockResolvedValue(undefined)
    vi.mocked(updateOwnerMenuItemOption).mockResolvedValue(option(2, 'REMOVE'))
  })
  afterEach(async () => {
    if (renderer) await act(async () => renderer.unmount())
    vi.unstubAllGlobals()
  })
  async function mount() { await act(async () => { renderer = create(<MenuOptionsPanel storeId={1} itemId={1} itemName="Noodles" />) }) }
  const button = (label: string) => renderer.root.findAllByType('button').find((node) => node.children.join('') === label)!

  it('uses eligibility-only checkboxes and preserves catalog active independently', async () => {
    vi.mocked(fetchItemAddons).mockResolvedValue([{ ...addon, active: false, enabled: true }])
    await mount()
    const checkbox = renderer.root.findByProps({ 'aria-label': 'Enable Extra Beef' })
    expect(checkbox.props.checked).toBe(true)
    expect(checkbox.props.disabled).toBe(false)
    vi.mocked(fetchItemAddons).mockResolvedValue([{ ...addon, active: false, enabled: false }])
    await act(async () => checkbox.props.onChange({ target: { checked: false } }))
    expect(updateItemAddon).toHaveBeenCalledWith(1, 101, false)
    expect(renderer.root.findByProps({ 'aria-label': 'Enable Extra Beef' }).props.checked).toBe(false)
    expect(updateOwnerMenuItemOption).not.toHaveBeenCalled()
    expect(reorderOwnerMenuItemOptions).not.toHaveBeenCalled()
    const section = renderer.root.findByProps({ 'aria-label': 'Item Add-ons' })
    expect(section.findAllByType('button')).toHaveLength(0)
  })

  it('keeps unresolved legacy values read-only and keeps Combo egg identity separate', async () => {
    vi.mocked(fetchOwnerMenuItemOptions).mockResolvedValue([
      option(10, 'ADD_ON', { name_zh: '旧牛肉', price_delta: 4 }),
      option(11, 'COMBO_EGG', { option_code: 'combo_fried_egg', name_zh: '套餐煎蛋' }),
    ])
    vi.mocked(fetchStoreAddons).mockResolvedValue({ addons: [addon], conflicts: [{
      code: addon.code, option_ids: [10], names_zh: ['旧牛肉', '加牛肉'], names_en: [], prices: [3, 4], reason: 'VALUES_CONFLICT',
    }] })
    await mount()
    const legacy = renderer.root.findByProps({ 'aria-label': 'Existing Add-ons awaiting agreement' })
    expect(legacy.findAll((node) => node.children.includes('旧牛肉')).length).toBeGreaterThan(0)
    expect(legacy.findAllByType('button')).toHaveLength(0)
    expect(legacy.findAllByType('input')).toHaveLength(0)
    expect(renderer.root.findByProps({ 'aria-label': 'Enable Extra Beef' }).props.disabled).toBe(true)
    expect(legacy.findAll((node) => node.children.includes('套餐煎蛋'))).toHaveLength(0)
  })

  it('keeps an ADD_ON separate from Combo after its catalog names change', async () => {
    const egg = { ...addon, code: 'fried_egg', name_zh: '煎蛋', name_en: 'Fried Egg' }
    const eggOption = option(1, 'ADD_ON', { store_addon_id: egg.id, option_code: egg.code, name_zh: egg.name_zh, name_en: egg.name_en })
    const comboEgg = option(2, 'COMBO_EGG', { option_code: 'combo_fried_egg', name_zh: '套餐煎蛋', name_en: 'Combo' })
    vi.mocked(fetchItemAddons).mockResolvedValue([egg])
    vi.mocked(fetchStoreAddons).mockResolvedValue({ addons: [egg], conflicts: [] })
    vi.mocked(fetchOwnerMenuItemOptions).mockResolvedValue([eggOption, comboEgg])
    await mount()
    expect(button('Combo Disabled')).toBeDefined()

    vi.mocked(fetchItemAddons).mockResolvedValue([{ ...egg, name_zh: '套餐煎蛋', name_en: 'Combo', enabled: true }])
    vi.mocked(fetchOwnerMenuItemOptions).mockResolvedValue([
      { ...eggOption, name_zh: '套餐煎蛋', name_en: 'Combo' }, comboEgg,
    ])
    await act(async () => renderer.root.findByProps({ 'aria-label': 'Enable Fried Egg' }).props.onChange({ target: { checked: true } }))
    expect(renderer.root.findByProps({ 'aria-label': 'Enable Combo' }).props.checked).toBe(true)
    expect(button('Combo Disabled')).toBeDefined()
    expect(button('Combo Allowed')).toBeUndefined()

    await act(async () => button('Edit').props.onClick())
    expect(renderer.root.findByProps({ placeholder: 'Option code' }).props.value).toBe('combo_fried_egg')
    expect(renderer.root.findByProps({ placeholder: '中文名称' }).props.value).toBe('套餐煎蛋')
  })

  it.each([
    ['explicit ADD_ON beats combo code and name', { option_group: 'ADD_ON', option_code: 'combo', name_zh: '套餐', name_en: 'Combo' }, false],
    ['normalized ADD_ON beats names', { option_group: ' add_on ', option_code: 'fried_egg', name_zh: '套餐煎蛋', name_en: ' Combo ' }, false],
    ['explicit COMBO_EGG beats combo code and name', { option_group: 'COMBO_EGG', option_code: 'combo', name_zh: '套餐', name_en: 'Combo' }, false],
    ['explicit COMBO beats other code and names', { option_group: ' combo ', option_code: 'fried_egg', name_zh: '煎蛋', name_en: 'Fried Egg' }, true],
    ['code-only combo works after rename', { option_group: null, option_code: ' Combo ', name_zh: '改名', name_en: 'Renamed' }, true],
    ['stable fried_egg code beats Combo name', { option_group: null, option_code: 'fried_egg', name_zh: '套餐', name_en: 'Combo' }, false],
    ['combo_fried_egg is not a combo upcharge', { option_group: null, option_code: 'combo_fried_egg', name_zh: '套餐', name_en: 'Combo' }, false],
    ['legacy Chinese name works without group or code', { option_group: null, option_code: null, name_zh: '套餐', name_en: '' }, true],
    ['legacy English name works with blank group and code', { option_group: ' ', option_code: ' ', name_zh: '', name_en: ' Combo ' }, true],
    ['legacy name requires addon type', { option_group: null, option_code: null, option_type: 'remove', name_zh: '套餐', name_en: 'Combo' }, false],
  ] as Array<[string, Partial<MenuItemOptionAdminRecord>, boolean]>)('%s', async (_description, overrides, comboAllowed) => {
    vi.mocked(fetchOwnerMenuItemOptions).mockResolvedValue([option(20, 'ADD_ON', overrides)])
    await mount()
    expect(button(comboAllowed ? 'Combo Allowed' : 'Combo Disabled')).toBeDefined()
    expect(button(comboAllowed ? 'Combo Disabled' : 'Combo Allowed')).toBeUndefined()
  })

  it('keeps group-less stable-code Add-ons named Combo visible read-only', async () => {
    vi.mocked(fetchOwnerMenuItemOptions).mockResolvedValue([
      option(20, 'ADD_ON', { option_group: null, option_code: 'fried_egg', name_zh: '套餐煎蛋', name_en: 'Combo' }),
    ])
    await mount()
    expect(button('Combo Disabled')).toBeDefined()
    const legacy = renderer.root.findByProps({ 'aria-label': 'Existing Add-ons awaiting agreement' })
    expect(legacy.findAll((node) => node.children.includes('套餐煎蛋')).length).toBeGreaterThan(0)
    expect(legacy.findAllByType('button')).toHaveLength(0)
    expect(legacy.findAllByType('input')).toHaveLength(0)
  })

  it('retains Remove editing and never offers ADD_ON in the generic form', async () => {
    await mount()
    await act(async () => button('Edit').props.onClick())
    expect(renderer.root.findAllByType('option').some((node) => node.props.value === 'ADD_ON')).toBe(false)
    const zh = renderer.root.findByProps({ placeholder: '中文名称' })
    await act(async () => zh.props.onChange({ target: { value: '不要葱' } }))
    const save = renderer.root.findAllByType('button').find((node) => node.children.join('').includes('Save Option'))!
    await act(async () => save.props.onClick())
    expect(updateOwnerMenuItemOption).toHaveBeenCalledWith(1, 2, expect.objectContaining({ option_group: 'REMOVE', name_zh: '不要葱' }))
  })

  it('retains the checked value and shows failure when eligibility update fails', async () => {
    await mount()
    vi.mocked(updateItemAddon).mockRejectedValueOnce(new Error('ADDON_ELIGIBILITY_CONFLICT'))
    await act(async () => renderer.root.findByProps({ 'aria-label': 'Enable Extra Beef' }).props.onChange({ target: { checked: true } }))
    expect(renderer.root.findByProps({ 'aria-label': 'Enable Extra Beef' }).props.checked).toBe(false)
    expect(renderer.root.findByProps({ role: 'alert' }).children).toContain('ADDON_ELIGIBILITY_CONFLICT')
  })

  it('keeps a non-ADD_ON component group and parent unchanged during generic edits', async () => {
    vi.mocked(fetchOwnerMenuItemOptions).mockResolvedValue([
      option(8, 'COMBO_SIDE_REMOVE', { option_type: 'remove', parent_option_id: 7 }),
    ])
    await mount()
    await act(async () => button('Edit').props.onClick())
    await act(async () => button('Save Option').props.onClick())
    expect(updateOwnerMenuItemOption).toHaveBeenCalledWith(1, 8, expect.objectContaining({ option_group: 'COMBO_SIDE_REMOVE', option_type: 'remove', parent_option_id: 7 }))
  })
})
