import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { KitchenRoutingPanel } from './KitchenRoutingPanel'
import { ItemCustomizationModal } from '../ordering/components/ItemCustomizationModal'
import { fetchMenuCatalog } from '../../services/menuService'
import { fetchKitchenRouting, fetchTemplatePrinters, previewKitchenRouting, saveKitchenRouting, type KitchenRoutingConfig } from '../../services/templatePlatformService'
import type { BackendMenuCatalog } from '../../types/ordering'

vi.mock('../../services/menuService', () => ({ fetchMenuCatalog: vi.fn() }))
vi.mock('../../hooks/useIpadLandscape', () => ({ useIpadLandscape: () => false }))
vi.mock('../../services/templatePlatformService', () => ({ fetchKitchenRouting: vi.fn(), fetchTemplatePrinters: vi.fn(), previewKitchenRouting: vi.fn(), saveKitchenRouting: vi.fn() }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
const route: KitchenRoutingConfig = { enabled: true, groups: ['A', 'B', 'C'].map((name, i) => ({ group_code: name, name, enabled: true, sort_order: i * 10, category_ids: [7], item_ids: [], logical_target: `kitchen-${name}`, printer_id: i === 2 ? null : 4 })), item_overrides: [] }
const catalog: BackendMenuCatalog = { store_id: 2, organization_id: 9, menu_revision: 3, generated_at: '', catalog_version: 'test', combo_metadata_version: 'test', content_hash: 'test', tax_policy: { rate: 0.14975, label: 'QC', version: 'test' }, categories: [{ id: 7, code: 'SYNTHETIC', name_zh: '合成', name_en: 'Synthetic', sort_order: 0, is_active: true, items: [{ id: 20, category_id: 7, station_id: 3, name_zh: '炸春卷', name_en: 'Spring roll', sku: 'synthetic', item_type: 'menu_item', base_price: 4, is_active: true, is_sold_out: false, sort_order: 0, options: [], option_groups: [] }] }] }
let view: ReactTestRenderer | undefined
const text = (node: { children: unknown[] }): string => node.children.map((child) => typeof child === 'string' ? child : child && typeof child === 'object' && 'children' in child ? text(child as { children: unknown[] }) : '').join('')
const button = (label: string) => view!.root.findAllByType('button').find((node) => text(node).includes(label))!
beforeEach(() => {
  vi.clearAllMocks(); vi.stubGlobal('window', { confirm: vi.fn(() => true) })
  vi.stubGlobal('document', { body: { style: { overflow: '', touchAction: '' } } })
  vi.mocked(fetchKitchenRouting).mockResolvedValue(structuredClone(route))
  vi.mocked(fetchTemplatePrinters).mockResolvedValue([{ id: 4, name: 'SYNTHETIC MOCK', is_active: true }])
  vi.mocked(fetchMenuCatalog).mockResolvedValue(structuredClone(catalog))
  vi.mocked(saveKitchenRouting).mockImplementation(async (_store, value) => value)
  vi.mocked(previewKitchenRouting).mockResolvedValue({ tickets: [{ group_code: 'A', group_name: 'A', logical_target: 'kitchen-A', printer_id: 4, rendered_text: 'BACKEND PREVIEW CONTENT', source_keys: ['item-20'], diagnostics: [] }], diagnostics: [{ code: 'PRINTER_MISSING', message: 'C is unbound', source_key: null }], coverage: [{ source_key: 'preview:1', group_codes: ['A'], disposition: 'ROUTED', name: '炸春卷', menu_item_id: 20, parent_source_key: null }], menu_revision: 3, routing_revision: 2 })
})
afterEach(() => { act(() => view?.unmount()); view = undefined; vi.unstubAllGlobals(); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true) })

describe('kitchen routing management reuses backend configuration and pure preview', () => {
  it('shows unbound warnings, allows shared MOCK printer, and saves group order without merging', async () => {
    await act(async () => { view = create(<KitchenRoutingPanel storeId={2} canManage />) })
    expect(text(view!.root)).toContain('This ticket has no printer')
    await act(async () => view!.root.findByProps({ 'aria-label': 'Kitchen printer 3' }).props.onChange({ target: { value: '4' } }))
    await act(async () => view!.root.findByProps({ 'aria-label': 'Move kitchen group 3 up' }).props.onClick())
    await act(async () => button('Save kitchen rules').props.onClick())
    expect(saveKitchenRouting).toHaveBeenCalledWith(2, expect.objectContaining({ groups: [expect.objectContaining({ group_code: 'A', printer_id: 4 }), expect.objectContaining({ group_code: 'C', printer_id: 4, sort_order: 10 }), expect.objectContaining({ group_code: 'B', printer_id: 4, sort_order: 20 })] }))
    expect(previewKitchenRouting).not.toHaveBeenCalled()
  })

  it('previews chosen synthetic lines with existing option IDs, and displays backend-rendered content and diagnostic', async () => {
    await act(async () => { view = create(<KitchenRoutingPanel storeId={2} canManage />) })
    await act(async () => view!.root.findByProps({ 'aria-label': 'Preview item 20' }).props.onChange({ target: { checked: true } }))
    await act(async () => button('Preview saved rules').props.onClick())
    expect(previewKitchenRouting).toHaveBeenCalledWith(2, { items: [{ menu_item_id: 20, quantity: 1, options: [] }] })
    expect(text(view!.root)).toContain('BACKEND PREVIEW CONTENT')
    expect(text(view!.root)).toContain('C is unbound')
    expect(saveKitchenRouting).not.toHaveBeenCalled()
  })

  it('does not invent a successful preview when backend rejects the request', async () => {
    vi.mocked(previewKitchenRouting).mockRejectedValue(new Error('Preview denied by Store scope'))
    await act(async () => { view = create(<KitchenRoutingPanel storeId={2} canManage />) })
    await act(async () => view!.root.findByProps({ 'aria-label': 'Preview item 20' }).props.onChange({ target: { checked: true } }))
    await act(async () => button('Preview saved rules').props.onClick())
    expect(view!.root.findByProps({ role: 'alert' }).children.join()).toContain('Store scope')
    expect(text(view!.root)).not.toContain('BACKEND PREVIEW CONTENT')
  })

  it('passes the existing full Combo child Remove snapshot through preview, including its virtual parent identity', async () => {
    const data = structuredClone(catalog)
    const main = data.categories[0].items[0]
    main.option_groups = [{ group_instance_code: 'combo', name: 'Combo', kind: 'COMBO', selection_mode: 'SINGLE', required: false, is_active: true, sort_order: 0, default_option_ids: [], options: [] }]
    main.options = [{ id: 19, option_type: 'addon', option_code: 'combo', option_group: 'COMBO', parent_option_id: null, sort_order: 0, name_zh: '套餐', name_en: 'Combo', price_delta: 3, is_active: true }]
    data.categories[0].items.push({ ...main, id: 21, name_zh: 'SYNTHETIC 冷菜', name_en: 'SYNTHETIC Cold side', sku: 'synthetic_side', option_groups: [], options: [{ id: 41, option_type: 'remove', option_code: 'remove_synthetic_onion', option_group: 'REMOVE', parent_option_id: null, sort_order: 0, name_zh: 'SYNTHETIC 子项去葱', name_en: 'SYNTHETIC Side no onion', price_delta: 0, is_active: true }] })
    data.combo_configuration = { store_id: 2, menu_revision: 3, groups: [{ component_group: 'COMBO_SIDE', group_code: 'COMBO_SIDE', name_zh: '配菜', name_en: 'Side', selection_rule: 'EXACTLY_ONE', required: true, default_component_code: 'combo_cucumber_salad', components: [{ component_group: 'COMBO_SIDE', component_code: 'combo_cucumber_salad', name_zh: 'SYNTHETIC 冷菜', name_en: 'SYNTHETIC Cold side', enabled: true, display_order: 0, is_default: true, linked_menu_item_id: 21 }] }] }
    vi.mocked(fetchMenuCatalog).mockResolvedValue(data)
    await act(async () => { view = create(<KitchenRoutingPanel storeId={2} canManage />) })
    await act(async () => view!.root.findByProps({ 'aria-label': 'Preview item 20' }).props.onChange({ target: { checked: true } }))
    await act(async () => button('Options / 选项').props.onClick())
    const modal = view!.root.findByType(ItemCustomizationModal)
    await act(async () => modal.props.onChange({ ...modal.props.draft, comboEnabled: true, comboSideId: '-20203', comboSelections: { COMBO_SIDE: '-20203' }, comboSideRemoveIds: ['41'] }))
    await act(async () => button('Preview saved rules').props.onClick())
    const sent = vi.mocked(previewKitchenRouting).mock.calls[0][1].items[0].options
    expect(sent).toEqual(expect.arrayContaining([
      expect.objectContaining({ option_id: -20203, option_code_snapshot: 'combo_cucumber_salad', option_group_snapshot: 'COMBO_SIDE', option_price_snapshot: 0 }),
      expect.objectContaining({ option_id: 41, quantity: 1, option_type_snapshot: 'remove', option_code_snapshot: 'remove_synthetic_onion', option_group_snapshot: 'REMOVE', parent_option_id_snapshot: -20203, option_name_snapshot_zh: 'SYNTHETIC 子项去葱', option_name_snapshot_en: 'SYNTHETIC Side no onion', option_price_snapshot: 0 }),
    ]))
  })
})
