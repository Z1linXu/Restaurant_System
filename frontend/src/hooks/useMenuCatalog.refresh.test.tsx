import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useMenuCatalog } from './useMenuCatalog'
import { fetchMenuCatalog, fetchMenuRevision } from '../services/menuService'
import { readActiveMenuSnapshot, replaceActiveMenuSnapshot, type ActiveMenuSnapshot } from '../offline/menuCache'
import type { BackendMenuCatalog } from '../types/ordering'

vi.mock('../services/menuService', () => ({ fetchMenuCatalog: vi.fn(), fetchMenuRevision: vi.fn() }))
vi.mock('../services/networkStatus', () => ({ recordAppOperation: vi.fn() }))
vi.mock('../offline/menuCache', () => ({ readActiveMenuSnapshot: vi.fn(), replaceActiveMenuSnapshot: vi.fn() }))

const scope = { accountId: 5, organizationId: 9, storeId: 1 }
function catalog(revision: number, name: string, price: number): BackendMenuCatalog {
  return {
    store_id: 1, organization_id: 9, menu_revision: revision, generated_at: '2026-09-15T17:00:00Z',
    catalog_version: 'menu-catalog-v4', combo_metadata_version: 'stable-option-semantics-v1', content_hash: 'fixture',
    tax_policy: { rate: 0.14975, label: 'Tax', version: 'test' }, categories: [{
      id: 1, code: 'NOODLE', name_zh: '面', name_en: 'Noodles', sort_order: 1, is_active: true, items: [{
        id: 1, category_id: 1, station_id: 2, name_zh: '面', name_en: 'Noodles', sku: 'noodles', item_type: 'menu_item',
        base_price: 10, is_active: true, is_sold_out: false, sort_order: 1, default_combo_egg_component_code: null,
        options: [{ id: 100, option_type: 'addon', option_group: 'ADD_ON', option_code: 'extra_beef',
          name_zh: name, name_en: name, price_delta: price, parent_option_id: null, sort_order: 1, is_active: true }],
      }],
    }],
  }
}
function snapshot(data: BackendMenuCatalog): ActiveMenuSnapshot {
  return {
    head: { ...scope, key: 'head', activeRevision: data.menu_revision, lastUpdatedAt: data.generated_at,
      etag: `rev-${data.menu_revision}`, contentHash: data.content_hash, schemaVersion: 1 },
    snapshot: { ...scope, key: 'snapshot', revision: data.menu_revision, catalog: data,
      downloadedAt: data.generated_at, contentHash: data.content_hash, schemaVersion: 1 },
  }
}

describe('returning to ordering after central Add-on edits', () => {
  let renderer: ReactTestRenderer | undefined
  let latest: ReturnType<typeof useMenuCatalog>
  function Probe() {
    const state = useMenuCatalog(1, scope)
    useEffect(() => { latest = state }, [state])
    return null
  }
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  })
  afterEach(async () => {
    if (renderer) await act(async () => renderer!.unmount())
    vi.unstubAllGlobals()
  })
  it('rechecks revision on remount and replaces the cached names/prices through the existing snapshot path', async () => {
    const old = catalog(3, 'Old beef', 2)
    vi.mocked(readActiveMenuSnapshot).mockResolvedValue(snapshot(old))
    vi.mocked(fetchMenuRevision).mockResolvedValue({ store_id: 1, organization_id: 9, menu_revision: 3,
      menu_updated_at: old.generated_at, catalog_version: old.catalog_version, tax_policy_version: 'test', etag: 'rev-3' })
    await act(async () => { renderer = create(<Probe />) })
    expect(latest.items[0].customization?.addOns?.[0]).toEqual(expect.objectContaining({ labelEn: 'Old beef', priceDelta: 2 }))
    expect(fetchMenuCatalog).not.toHaveBeenCalled()
    await act(async () => renderer!.unmount())
    const updated = catalog(4, 'New beef', 3.5)
    vi.mocked(fetchMenuRevision).mockResolvedValue({ store_id: 1, organization_id: 9, menu_revision: 4,
      menu_updated_at: updated.generated_at, catalog_version: updated.catalog_version, tax_policy_version: 'test', etag: 'rev-4' })
    vi.mocked(fetchMenuCatalog).mockResolvedValue(updated)
    vi.mocked(replaceActiveMenuSnapshot).mockResolvedValue(snapshot(updated))
    await act(async () => { renderer = create(<Probe />) })
    expect(fetchMenuRevision).toHaveBeenCalledTimes(2)
    expect(replaceActiveMenuSnapshot).toHaveBeenCalledWith(scope, updated, 'rev-4')
    expect(latest.items[0].customization?.addOns?.[0]).toEqual(expect.objectContaining({ id: '100', labelEn: 'New beef', priceDelta: 3.5, optionCode: 'extra_beef' }))
    expect(latest.source).toBe('NETWORK')
    expect(old.categories[0].items[0].options[0].price_delta).toBe(2)
  })
})
