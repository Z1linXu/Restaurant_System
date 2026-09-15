import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createStoreAddon,
  fetchStoreAddons,
  updateStoreAddon,
  type StoreAddon,
  type StoreAddonCatalog,
} from '../../services/ownerAddonService'
import { AddonCatalogPanel } from './AddonCatalogPanel'

vi.mock('../../services/ownerAddonService', () => ({
  fetchStoreAddons: vi.fn(),
  createStoreAddon: vi.fn(),
  updateStoreAddon: vi.fn(),
}))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)

const teaEgg: StoreAddon = {
  id: 11, store_id: 3, code: 'tea_egg', name_zh: '卤蛋', name_en: 'Tea Egg',
  price: 1.5, active: true, printing_configured: true,
}
const friedEgg: StoreAddon = {
  id: 12, store_id: 3, code: 'fried_egg', name_zh: '煎蛋', name_en: 'Fried Egg',
  price: 2, active: false, printing_configured: false,
}
let view: ReactTestRenderer | undefined

function button(label: string) {
  return view!.root.findAllByType('button').find((node) => node.children.join('') === label)!
}

function text() {
  return JSON.stringify(view!.toJSON())
}

async function mount(onSaved = vi.fn()) {
  await act(async () => { view = create(<AddonCatalogPanel storeId={3} onSaved={onSaved} />) })
}

async function openNew() {
  await act(async () => button('Add Add-on / 添加加料').props.onClick())
}

async function change(label: string, value: string | boolean) {
  await act(async () => view!.root.findByProps({ 'aria-label': label }).props.onChange({
    target: typeof value === 'boolean' ? { checked: value } : { value },
  }))
}

async function submit() {
  await act(async () => view!.root.findByType('form').props.onSubmit({ preventDefault: vi.fn() }))
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(fetchStoreAddons).mockResolvedValue({ addons: [teaEgg, friedEgg], conflicts: [] })
})

afterEach(async () => {
  if (view) await act(async () => view!.unmount())
  view = undefined
})

describe('AddonCatalogPanel', () => {
  it('lists Store availability and Printing coverage independently, keeping fallback Add-ons editable', async () => {
    await mount()
    expect(fetchStoreAddons).toHaveBeenCalledWith(3)
    expect(text()).toContain('Active / 可用')
    expect(text()).toContain('Inactive / 停用')
    expect(text()).toContain('Printing configured / 已配置打印')
    expect(text()).toContain('Using fallback / 使用默认打印显示')
    expect(text()).toContain('$1.50')
    expect(view!.root.findByProps({ 'aria-label': 'Edit Add-on fried_egg' }).props.disabled).toBe(false)
    expect(view!.root.findAllByType('input')).toHaveLength(0)
    expect(view!.root.findAllByType('button')).toHaveLength(3)
  })

  it('creates a Store Add-on and edits it with an immutable code omitted from the update payload', async () => {
    const onSaved = vi.fn()
    const created: StoreAddon = { ...teaEgg, id: 20, code: 'extra_beef', name_zh: '牛肉', name_en: 'Beef', price: 3.25, printing_configured: false }
    vi.mocked(createStoreAddon).mockResolvedValue(created)
    vi.mocked(updateStoreAddon).mockResolvedValue({ ...created, name_zh: '加牛肉', name_en: 'Extra Beef', price: 3.75, active: false })
    await mount(onSaved)
    await openNew()
    expect(view!.root.findByProps({ 'aria-label': 'Add-on code' }).props.readOnly).toBe(false)
    await change('Add-on code', ' extra_beef ')
    await change('Add-on Chinese name', ' 牛肉 ')
    await change('Add-on English name', ' Beef ')
    await change('Add-on price', '3.25')
    await submit()
    expect(createStoreAddon).toHaveBeenCalledWith({
      store_id: 3, code: 'extra_beef', name_zh: '牛肉', name_en: 'Beef', price: 3.25, active: true,
    })
    expect(view!.root.findAllByType('form')).toHaveLength(0)
    expect(text()).toContain('extra_beef')

    await act(async () => view!.root.findByProps({ 'aria-label': 'Edit Add-on extra_beef' }).props.onClick())
    expect(view!.root.findByProps({ 'aria-label': 'Add-on code' }).props.readOnly).toBe(true)
    expect(view!.root.findByProps({ 'aria-label': 'Add-on code' }).props.value).toBe('extra_beef')
    await change('Add-on Chinese name', '加牛肉')
    await change('Add-on English name', 'Extra Beef')
    await change('Add-on price', '3.75')
    await change('Add-on active', false)
    await submit()
    expect(updateStoreAddon).toHaveBeenCalledWith(20, { name_zh: '加牛肉', name_en: 'Extra Beef', price: 3.75, active: false })
    expect(updateStoreAddon).not.toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ code: expect.anything() }))
    expect(text()).toContain('Extra Beef')
    expect(text()).toContain('$3.75')
    expect(onSaved).toHaveBeenCalledTimes(2)
  })

  it('preserves all conflict names and prices read-only, and blocks edits to a catalog code with conflicts', async () => {
    vi.mocked(fetchStoreAddons).mockResolvedValue({
      addons: [teaEgg],
      conflicts: [
        { code: 'tea_egg', option_ids: [1, 2], names_zh: ['卤蛋', '茶叶蛋'], names_en: ['Tea Egg', 'Marinated Egg'], prices: [1.5, 2.25], reason: 'ADDON_VALUES_AMBIGUOUS' },
        { code: null, option_ids: [3], names_zh: ['旧加料'], names_en: ['Legacy Add-on'], prices: [3], reason: 'ADDON_CODE_INVALID' },
      ],
    })
    await mount()
    const conflicts = view!.root.findByProps({ 'aria-label': 'Add-on values awaiting Owner decision' })
    expect(conflicts.findAllByType('input')).toHaveLength(0)
    expect(conflicts.findAllByType('button')).toHaveLength(0)
    for (const value of ['茶叶蛋', 'Marinated Egg', '$1.50', '$2.25', '旧加料', 'Legacy Add-on', '$3.00', 'Missing code / 缺少代码']) {
      expect(text()).toContain(value)
    }
    const edit = view!.root.findByProps({ 'aria-label': 'Edit Add-on tea_egg' })
    expect(edit.props.disabled).toBe(true)
    await act(async () => edit.props.onClick())
    expect(view!.root.findAllByType('form')).toHaveLength(0)
    expect(text()).not.toContain('ADDON_VALUES_AMBIGUOUS')
    await openNew()
    await change('Add-on code', 'tea_egg')
    await change('Add-on Chinese name', '新蛋')
    await submit()
    expect(createStoreAddon).not.toHaveBeenCalled()
    expect(text()).toContain('This code has existing values awaiting an Owner decision.')
  })

  it('keeps the edited values and saved list on failed save, then allows retry', async () => {
    const onSaved = vi.fn()
    vi.mocked(updateStoreAddon).mockRejectedValueOnce(new Error('Unable to save this Add-on'))
      .mockResolvedValue({ ...teaEgg, name_en: 'Renamed Egg', price: 2.5 })
    await mount(onSaved)
    await act(async () => view!.root.findByProps({ 'aria-label': 'Edit Add-on tea_egg' }).props.onClick())
    await change('Add-on English name', 'Renamed Egg')
    await change('Add-on price', '2.50')
    await submit()
    expect(view!.root.findByProps({ role: 'alert' }).findByType('p').children).toEqual(['Unable to save this Add-on'])
    expect(view!.root.findByProps({ 'aria-label': 'Add-on English name' }).props.value).toBe('Renamed Egg')
    expect(view!.root.findByProps({ 'aria-label': 'Add-on price' }).props.value).toBe('2.50')
    expect(text()).toContain('$1.50')
    expect(onSaved).not.toHaveBeenCalled()
    await submit()
    expect(onSaved).toHaveBeenCalledTimes(1)
    expect(view!.root.findAllByType('form')).toHaveLength(0)
  })

  it('rejects missing required values and invalid prices, while accepting a zero price', async () => {
    await mount()
    await openNew()
    await submit()
    expect(text()).toContain('Code and Chinese name are required.')
    await change('Add-on code', 'free_sauce')
    await change('Add-on Chinese name', '酱汁')
    for (const value of ['', '-1', 'Infinity', 'NaN', '1.234', '1e309']) {
      await change('Add-on price', value)
      await submit()
      expect(createStoreAddon).not.toHaveBeenCalled()
      expect(text()).toContain('Enter a non-negative price with at most two decimal places.')
    }
    vi.mocked(createStoreAddon).mockResolvedValue({ ...teaEgg, id: 25, code: 'free_sauce', price: 0 })
    await change('Add-on price', '0')
    await submit()
    expect(createStoreAddon).toHaveBeenCalledWith(expect.objectContaining({ price: 0 }))
  })

  it('shows load failures without a false loading state and permits retry to an empty catalog', async () => {
    vi.mocked(fetchStoreAddons).mockRejectedValueOnce(new Error('Add-on catalog unavailable'))
      .mockResolvedValueOnce({ addons: [], conflicts: [] })
    await mount()
    expect(text()).toContain('Add-on catalog unavailable')
    expect(view!.root.findAllByProps({ role: 'status' })).toHaveLength(0)
    expect(button('Add Add-on / 添加加料').props.disabled).toBe(true)
    await act(async () => button('Retry / 重试').props.onClick())
    expect(text()).toContain('No catalog Add-ons yet.')
    expect(button('Add Add-on / 添加加料').props.disabled).toBe(false)
    expect(view!.root.findAllByProps({ role: 'alert' })).toHaveLength(0)
  })

  it('keeps loading visible until the current Store responds and discards a stale Store response', async () => {
    let resolveOld!: (catalog: StoreAddonCatalog) => void
    vi.mocked(fetchStoreAddons).mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }))
      .mockResolvedValueOnce({ addons: [{ ...friedEgg, store_id: 4 }], conflicts: [] })
    await mount()
    expect(view!.root.findByProps({ role: 'status' })).toBeTruthy()
    expect(button('Add Add-on / 添加加料').props.disabled).toBe(true)
    await act(async () => view!.update(<AddonCatalogPanel storeId={4} />))
    await act(async () => resolveOld({ addons: [teaEgg], conflicts: [] }))
    expect(view!.root.findAllByProps({ 'aria-label': 'Edit Add-on tea_egg' })).toHaveLength(0)
    expect(view!.root.findAllByProps({ 'aria-label': 'Edit Add-on fried_egg' })).toHaveLength(1)
    expect(fetchStoreAddons).toHaveBeenLastCalledWith(4)
  })

  it('does not notify the parent when a save completes after unmount', async () => {
    let resolveSave!: (addon: StoreAddon) => void
    const onSaved = vi.fn()
    vi.mocked(updateStoreAddon).mockReturnValue(new Promise((resolve) => { resolveSave = resolve }))
    await mount(onSaved)
    await act(async () => view!.root.findByProps({ 'aria-label': 'Edit Add-on tea_egg' }).props.onClick())
    await submit()
    expect(button('Saving... / 保存中...').props.disabled).toBe(true)
    await act(async () => view!.unmount())
    view = undefined
    await act(async () => resolveSave(teaEgg))
    expect(onSaved).not.toHaveBeenCalled()
  })
})
