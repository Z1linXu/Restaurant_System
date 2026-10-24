import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useDiningTableDisplay } from './useDiningTableDisplay'
import { fetchDiningTables } from '../services/frontdeskConfigService'
import { readDiningTableSnapshot, saveDiningTableSnapshot } from '../offline/workspaceSnapshot'
import type { BackendDiningTableConfig } from '../types/dinein'

vi.mock('../services/frontdeskConfigService', () => ({ fetchDiningTables: vi.fn(), DINING_TABLES_CHANGED_EVENT: 'restaurant-dining-tables-changed' }))
vi.mock('../offline/workspaceSnapshot', () => ({ readDiningTableSnapshot: vi.fn(), saveDiningTableSnapshot: vi.fn() }))
let view: ReactTestRenderer | undefined
let current: ReturnType<typeof useDiningTableDisplay>
function Probe({ storeId = 1, accountId = 7 }: { storeId?: number; accountId?: number }) {
  const value = useDiningTableDisplay(storeId, { accountId, organizationId: 9, storeId })
  useEffect(() => { current = value }, [value])
  return null
}
const table = (store = 1): BackendDiningTableConfig => ({ id: store, store_id: store, table_code: 'T1', table_name: '1', area_name: 'Main', capacity: 4, table_config: 'split_supported', supports_split: true, sort_order: 1, is_active: true, split_label_mode: 'INSIDE_OUTSIDE' })
beforeEach(() => {
  vi.resetAllMocks()
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  vi.stubGlobal('navigator', { onLine: true })
  vi.mocked(readDiningTableSnapshot).mockResolvedValue(null)
  vi.mocked(saveDiningTableSnapshot).mockResolvedValue(undefined)
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.unstubAllGlobals() })

it('loads mode before a server order exists and stores only scoped table configuration', async () => {
  vi.mocked(fetchDiningTables).mockResolvedValue([table(), table(2)])
  await act(async () => { view = create(<Probe />) })
  expect(current.formatLabel('1-A')).toBe('1里')
  expect(current.formatLabel('1-B')).toBe('1外')
  expect(current.baseLabel('1-A', 'old')).toBe('1')
  expect(saveDiningTableSnapshot).toHaveBeenCalledWith({ accountId: 7, organizationId: 9, storeId: 1 }, [table()])
})

it('uses the existing scoped offline snapshot and falls back safely for unknown legacy labels', async () => {
  vi.stubGlobal('navigator', { onLine: false })
  vi.mocked(readDiningTableSnapshot).mockResolvedValue([table()])
  await act(async () => { view = create(<Probe />) })
  expect(current.formatLabel('1-A')).toBe('1里')
  expect(current.formatLabel('old-A')).toBe('old-左')
  expect(fetchDiningTables).not.toHaveBeenCalled()
  expect(saveDiningTableSnapshot).not.toHaveBeenCalled()
})

it('keeps initial-order display in sync with the table board refresh without changing any slot identity', async () => {
  vi.mocked(fetchDiningTables).mockResolvedValue([{ ...table(), split_label_mode: 'LEFT_RIGHT' }])
  await act(async () => { view = create(<Probe />) })
  expect(current.formatLabel('1-A')).toBe('1-左')
  await act(async () => current.rememberTables(1, [table()]))
  expect(current.formatLabel('1-A')).toBe('1里')
  await act(async () => current.rememberTables(2, [table(2)]))
  expect(current.formatLabel('1-A')).toBe('1里')
  expect(saveDiningTableSnapshot).toHaveBeenLastCalledWith({ accountId: 7, organizationId: 9, storeId: 1 }, [table()])
})

it('does not let a delayed initial request overwrite a newer board configuration', async () => {
  let finish!: (tables: BackendDiningTableConfig[]) => void
  vi.mocked(fetchDiningTables).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
  await act(async () => { view = create(<Probe />) })
  await act(async () => current.rememberTables(1, [table()]))
  await act(async () => finish([{ ...table(), split_label_mode: 'LEFT_RIGHT' }]))
  expect(current.formatLabel('1-A')).toBe('1里')
})

it('does not leak a previous Store or account mode while a new context loads or receives a late response', async () => {
  let old!: (tables: BackendDiningTableConfig[]) => void
  let next!: (tables: BackendDiningTableConfig[]) => void
  vi.mocked(fetchDiningTables).mockImplementation((store) => new Promise((resolve) => { if (store === 1) old = resolve; else next = resolve }))
  await act(async () => { view = create(<Probe />) })
  await act(async () => { view!.update(<Probe storeId={2} />) })
  await act(async () => old([table()]))
  expect(current.formatLabel('1-A')).toBe('1-左')
  await act(async () => next([table(2)]))
  expect(current.formatLabel('1-A')).toBe('1里')
  await act(async () => { view!.update(<Probe storeId={2} accountId={8} />) })
  expect(current.formatLabel('1-A')).toBe('1-左')
  expect(readDiningTableSnapshot).toHaveBeenLastCalledWith({ accountId: 8, organizationId: 9, storeId: 2 })
})
