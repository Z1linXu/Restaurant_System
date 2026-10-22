import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  fetchAdminMenuItems,
  fetchPlatformOverview,
  type MenuItemAdminRecord,
  type PlatformAdminOverview,
} from '../../services/platformAdminService'
import { AddonCatalogPanel } from './AddonCatalogPanel'
import { MenuManagementPage } from './MenuManagementPage'
import { MenuOptionsPanel } from './MenuOptionsPanel'
import { PrintingRulesMenuPanel } from './PrintingRulesMenuPanel'

const context = vi.hoisted(() => ({ storeId: 1 }))

vi.mock('../store/useStoreContext', () => ({ useCurrentStore: () => ({ storeId: context.storeId }) }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ isFrontdesk: false }) }))
vi.mock('../../services/platformAdminService', () => ({
  fetchAdminMenuItems: vi.fn(),
  fetchPlatformOverview: vi.fn(),
  fetchMenuManagementContext: vi.fn(),
  rebuildAnalyticsForDate: vi.fn(),
  reorderAdminMenuItems: vi.fn(),
  savePlatformEntity: vi.fn(),
}))
vi.mock('./MenuOptionsPanel', () => ({
  MenuOptionsPanel: vi.fn(({ storeId, itemId }: { storeId: number; itemId: number }) => (
    <div data-testid="menu-options" data-store-id={storeId} data-item-id={itemId} />
  )),
}))
vi.mock('./AddonCatalogPanel', () => ({
  AddonCatalogPanel: vi.fn(({ storeId }: { storeId: number }) => <div data-testid="addon-catalog" data-store-id={storeId} />),
}))
vi.mock('./PrintingRulesMenuPanel', () => ({
  PrintingRulesMenuPanel: vi.fn(({ storeId }: { storeId: number }) => <div data-testid="printing-rules-menu" data-store-id={storeId} />),
}))
vi.mock('./RemoveCatalogPanel', () => ({
  RemoveCatalogPanel: vi.fn(({ storeId }: { storeId: number }) => <div data-testid="remove-catalog" data-store-id={storeId} />),
}))
vi.mock('./ComboConfigurationPanel', () => ({ ComboConfigurationPanel: () => null }))
vi.mock('./MenuStructurePanels', () => ({ CategoryManagementPanel: () => null, StationManagementPanel: () => null }))
vi.mock('./PricingRulesPanel', () => ({ PricingRulesPanel: () => null }))
vi.mock('./PrintingDisplayRulesPanel', () => ({ ItemPrintingRuleAliasPanel: () => null }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)

const overview: PlatformAdminOverview = {
  organizations: [], templates: [], roles: [], users: [], dining_tables: [], menu_items: [],
  menu_item_options: [], kds_display_configs: [],
  stores: [{ id: 1, name: 'Store One' }, { id: 2, name: 'Store Two' }],
  menu_categories: [
    { id: 11, store_id: 1, name_zh: '面', name_en: 'Noodles' },
    { id: 22, store_id: 2, name_zh: '面', name_en: 'Noodles' },
  ],
  stations: [
    { id: 11, store_id: 1, name_zh: '厨房一', name_en: 'Kitchen One', code: 'HOT' },
    { id: 22, store_id: 2, name_zh: '厨房二', name_en: 'Kitchen Two', code: 'HOT' },
  ],
}

function item(storeId: number): MenuItemAdminRecord {
  return {
    id: storeId * 100 + 1, store_id: storeId, category_id: storeId * 11, station_id: storeId * 11,
    name_zh: `门店${storeId}菜品`, name_en: `Store ${storeId} dish`, sku: `DISH_${storeId}`,
    item_type: 'NOODLE', base_price: 12, cost_per_item: 3, is_active: true, is_sold_out: false, sort_order: 10,
  }
}

let view: ReactTestRenderer | undefined
const popstateListeners = new Set<() => void>()
const browser = {
  location: { search: '' },
  history: { pushState: vi.fn((_state: unknown, _title: string, path: string) => {
    browser.location.search = new URL(path, 'https://staging.example').search
  }) },
  addEventListener: vi.fn((event: string, callback: () => void) => { if (event === 'popstate') popstateListeners.add(callback) }),
  removeEventListener: vi.fn((event: string, callback: () => void) => { if (event === 'popstate') popstateListeners.delete(callback) }),
}

function button(label: string) {
  return view!.root.findAllByType('button').find((node) => node.children.join('') === label)!
}

async function mount() {
  await act(async () => { view = create(<MenuManagementPage />) })
}

async function switchStore(storeId: number) {
  const storeSelector = view!.root.findAllByType('select').find((select) => (
    select.findAllByType('option').some((option) => option.children.includes('Store One'))
  ))!
  await act(async () => storeSelector.props.onChange({ target: { value: String(storeId) } }))
}

beforeEach(() => {
  vi.clearAllMocks()
  context.storeId = 1
  browser.location.search = ''
  popstateListeners.clear()
  vi.stubGlobal('window', browser)
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.mocked(fetchPlatformOverview).mockResolvedValue(overview)
  vi.mocked(fetchAdminMenuItems).mockImplementation(async (storeId) => [item(storeId)])
})

afterEach(async () => {
  if (view) await act(async () => view!.unmount())
  view = undefined
  vi.unstubAllGlobals()
})

describe('MenuManagementPage Store-scoped panels', () => {
  it('never renders Store One item options with Store Two context while switching Stores', async () => {
    let finishStoreTwoLoad!: (items: MenuItemAdminRecord[]) => void
    const storeTwoItems = new Promise<MenuItemAdminRecord[]>((resolve) => { finishStoreTwoLoad = resolve })
    vi.mocked(fetchAdminMenuItems).mockImplementation(async (storeId) => storeId === 2 ? storeTwoItems : [item(1)])
    await mount()
    await act(async () => button('Options').props.onClick())
    expect(view!.root.findByProps({ 'data-testid': 'menu-options' }).props).toMatchObject({
      'data-store-id': 1, 'data-item-id': 101,
    })

    await switchStore(2)
    expect(fetchAdminMenuItems).toHaveBeenCalledWith(2)
    expect(view!.root.findAllByProps({ 'data-testid': 'menu-options' })).toHaveLength(0)
    expect(vi.mocked(MenuOptionsPanel).mock.calls.map(([props]) => [props.storeId, props.itemId]))
      .not.toContainEqual([2, 101])

    await act(async () => finishStoreTwoLoad([item(2)]))
    expect(view!.root.findAllByProps({ 'data-testid': 'menu-options' })).toHaveLength(0)
    await act(async () => button('Options').props.onClick())
    expect(view!.root.findByProps({ 'data-testid': 'menu-options' }).props).toMatchObject({
      'data-store-id': 2, 'data-item-id': 201,
    })
    expect(vi.mocked(MenuOptionsPanel).mock.calls.map(([props]) => [props.storeId, props.itemId]))
      .not.toContainEqual([2, 101])
  })

  it('filters rows by the selected Store even if the menu response also contains another Store item', async () => {
    vi.mocked(fetchAdminMenuItems).mockResolvedValue([item(1), item(2)])
    await mount()
    expect(view!.root.findAllByType('tbody')[0].findAllByType('tr')).toHaveLength(1)
    await switchStore(2)
    const rows = view!.root.findByType('tbody').findAllByType('tr')
    expect(rows).toHaveLength(1)
    expect(rows[0].findAllByType('div').some((node) => node.children.includes('门店2菜品'))).toBe(true)
    expect(rows[0].findAllByType('div').some((node) => node.children.includes('门店1菜品'))).toBe(false)
    await act(async () => button('Options').props.onClick())
    expect(view!.root.findByProps({ 'data-testid': 'menu-options' }).props).toMatchObject({
      'data-store-id': 2, 'data-item-id': 201,
    })
  })

  it('opens Add-ons through the existing navigation and passes the current numeric Store ID after each switch', async () => {
    await mount()
    expect(view!.root.findAllByProps({ 'data-testid': 'addon-catalog' })).toHaveLength(0)
    await act(async () => button('Add-ons').props.onClick())
    expect(view!.root.findByProps({ 'data-testid': 'addon-catalog' }).props['data-store-id']).toBe(1)
    await switchStore(2)
    expect(view!.root.findByProps({ 'data-testid': 'addon-catalog' }).props['data-store-id']).toBe(2)
    await act(async () => button('Menu Items').props.onClick())
    expect(view!.root.findAllByProps({ 'data-testid': 'addon-catalog' })).toHaveLength(0)
    await act(async () => button('Add-ons').props.onClick())
    expect(view!.root.findByProps({ 'data-testid': 'addon-catalog' }).props['data-store-id']).toBe(2)
    expect(vi.mocked(AddonCatalogPanel).mock.calls.map(([props]) => props.storeId)).toContain(2)
  })

  it('clears open item options when the shared Store context changes', async () => {
    await mount()
    await act(async () => button('Options').props.onClick())
    context.storeId = 2
    await act(async () => view!.update(<MenuManagementPage />))
    expect(view!.root.findAllByProps({ 'data-testid': 'menu-options' })).toHaveLength(0)
    expect(vi.mocked(MenuOptionsPanel).mock.calls.map(([props]) => [props.storeId, props.itemId]))
      .not.toContainEqual([2, 101])
    await act(async () => button('Add-ons').props.onClick())
    expect(view!.root.findByProps({ 'data-testid': 'addon-catalog' }).props['data-store-id']).toBe(2)
  })
})

describe('Menu Management seven-section navigation', () => {
  it('keeps Printing Display Rules immediately after Pricing Rules and preserves the selected query across refresh', async () => {
    await mount()
    const expected = ['Menu Items', 'Add-ons', 'Categories', 'Stations', 'Combo Configuration', 'Pricing Rules', 'Printing Display Rules']
    const nav = view!.root.findAllByType('div').find((node) => {
      const children = node.findAllByType('button')
      return children.length === 7 && children.map((child) => child.children.join('')).join('|') === expected.join('|')
    })!
    expect(nav).toBeDefined()
    await act(async () => button('Printing Display Rules').props.onClick())
    expect(browser.history.pushState).toHaveBeenLastCalledWith({}, '', '/stores/1/admin/menu/items?section=printing-display-rules')
    expect(view!.root.findByProps({ 'data-testid': 'printing-rules-menu' }).props['data-store-id']).toBe(1)
    await act(async () => view!.unmount())
    await mount()
    expect(view!.root.findByProps({ 'data-testid': 'printing-rules-menu' }).props['data-store-id']).toBe(1)
    expect(popstateListeners.size).toBe(1)
  })

  it('restores sections on browser history navigation and removes the history listener on unmount', async () => {
    browser.location.search = '?section=printing-display-rules'
    await mount()
    expect(view!.root.findAllByProps({ 'data-testid': 'printing-rules-menu' })).toHaveLength(1)
    await act(async () => {
      browser.location.search = '?section=addons'
      popstateListeners.forEach((listener) => listener())
    })
    expect(view!.root.findAllByProps({ 'data-testid': 'printing-rules-menu' })).toHaveLength(0)
    expect(view!.root.findAllByProps({ 'data-testid': 'addon-catalog' })).toHaveLength(1)
    await act(async () => view!.unmount())
    view = undefined
    expect(popstateListeners.size).toBe(0)
  })

  it('passes Store Two to the existing printing wrapper after a Store switch', async () => {
    browser.location.search = '?section=printing-display-rules'
    await mount()
    await switchStore(2)
    expect(view!.root.findByProps({ 'data-testid': 'printing-rules-menu' }).props['data-store-id']).toBe(2)
    expect(vi.mocked(PrintingRulesMenuPanel).mock.calls.at(-1)![0].storeId).toBe(2)
  })

  it('shows Add-ons and Removes as sub-sections and resets their Store scope on switch', async () => {
    await mount()
    await act(async () => button('Add-ons').props.onClick())
    const sections = () => view!.root.findByProps({ 'aria-label': 'Modifier catalog sections' })
    expect(sections().findAllByType('button').map((node) => node.children.join(''))).toEqual(['Add-ons', 'Removes'])
    await act(async () => sections().findAllByType('button')[1].props.onClick())
    expect(view!.root.findByProps({ 'data-testid': 'remove-catalog' }).props['data-store-id']).toBe(1)
    expect(view!.root.findAllByProps({ 'data-testid': 'addon-catalog' })).toHaveLength(0)
    await switchStore(2)
    expect(view!.root.findAllByProps({ 'data-testid': 'remove-catalog' })).toHaveLength(0)
    expect(view!.root.findByProps({ 'data-testid': 'addon-catalog' }).props['data-store-id']).toBe(2)
    await act(async () => sections().findAllByType('button')[1].props.onClick())
    expect(view!.root.findByProps({ 'data-testid': 'remove-catalog' }).props['data-store-id']).toBe(2)
  })
})
