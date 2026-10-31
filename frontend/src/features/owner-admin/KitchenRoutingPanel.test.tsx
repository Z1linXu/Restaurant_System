import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { KitchenRoutingPanel } from './KitchenRoutingPanel'
import { fetchMenuCatalog } from '../../services/menuService'
import { fetchKitchenRouting, fetchTemplatePrinters, previewKitchenRouting, saveKitchenRouting, type KitchenRoutingConfig } from '../../services/templatePlatformService'
import type { BackendMenuCatalog } from '../../types/ordering'

vi.mock('../../services/menuService', () => ({ fetchMenuCatalog: vi.fn() }))
vi.mock('../../services/templatePlatformService', () => ({ fetchKitchenRouting: vi.fn(), fetchTemplatePrinters: vi.fn(), previewKitchenRouting: vi.fn(), saveKitchenRouting: vi.fn() }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
const route: KitchenRoutingConfig = { enabled: true, groups: ['A', 'B', 'C'].map((name, i) => ({ group_code: name, name, enabled: true, sort_order: i * 10, category_ids: [7], item_ids: [], logical_target: `kitchen-${name}`, printer_id: i === 2 ? null : 4 })), item_overrides: [] }
const catalog: BackendMenuCatalog = { store_id: 2, organization_id: 9, menu_revision: 3, generated_at: '', catalog_version: 'test', combo_metadata_version: 'test', content_hash: 'test', tax_policy: { rate: 0.14975, label: 'QC', version: 'test' }, categories: [{ id: 7, code: 'SYNTHETIC', name_zh: '合成', name_en: 'Synthetic', sort_order: 0, is_active: true, items: [{ id: 20, category_id: 7, station_id: 3, name_zh: '炸春卷', name_en: 'Spring roll', sku: 'synthetic', item_type: 'menu_item', base_price: 4, is_active: true, is_sold_out: false, sort_order: 0, options: [], option_groups: [] }] }] }
let view: ReactTestRenderer | undefined
const text = (node: { children: unknown[] }): string => node.children.map((child) => typeof child === 'string' ? child : child && typeof child === 'object' && 'children' in child ? text(child as { children: unknown[] }) : '').join('')
const button = (label: string) => view!.root.findAllByType('button').find((node) => text(node).includes(label))!
beforeEach(() => {
  vi.clearAllMocks(); vi.stubGlobal('window', { confirm: vi.fn(() => true) })
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
})
