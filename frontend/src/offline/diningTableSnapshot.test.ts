import { beforeEach, expect, it, vi } from 'vitest'
import { openOfflineDatabase } from './offlineDatabase'
import { readDiningTableSnapshot, saveDiningTableSnapshot, WORKSPACE_SNAPSHOT_MAX_AGE_MS } from './workspaceSnapshot'
import type { BackendDiningTableConfig } from '../types/dinein'

vi.mock('./offlineDatabase', () => ({ OFFLINE_STORES: { workspaceSnapshots: 'workspaceSnapshots' }, openOfflineDatabase: vi.fn(), requestResult: async (value: unknown) => value, transactionComplete: async () => undefined }))
const records = new Map<string, Record<string, unknown>>()
const scope = { accountId: 7, organizationId: 9, storeId: 2 }
const table: BackendDiningTableConfig = { id: 20, store_id: 2, table_code: 'T1', table_name: '1', area_name: 'Main', table_config: 'split_supported', capacity: 4, supports_split: true, split_label_mode: 'INSIDE_OUTSIDE', sort_order: 1, is_active: true }
beforeEach(() => {
  records.clear()
  vi.mocked(openOfflineDatabase).mockResolvedValue({ transaction: () => ({ objectStore: () => ({ get: (key: string) => records.get(key), put: (record: Record<string, unknown>) => records.set(String(record.key), record) }) }) } as unknown as IDBDatabase)
})
it('reuses bounded workspace storage while isolating account, Organization and Store', async () => {
  await saveDiningTableSnapshot(scope, [table, { ...table, store_id: 1 }])
  expect(await readDiningTableSnapshot(scope)).toEqual([table])
  expect(await readDiningTableSnapshot({ ...scope, accountId: 8 })).toBeNull()
  expect(await readDiningTableSnapshot({ ...scope, organizationId: 10 })).toBeNull()
  expect(await readDiningTableSnapshot({ ...scope, storeId: 1 })).toBeNull()
  const saved = [...records.values()][0]
  saved.validatedAt = new Date(Date.now() - WORKSPACE_SNAPSHOT_MAX_AGE_MS - 1).toISOString()
  expect(await readDiningTableSnapshot(scope)).toBeNull()
})
it('does not restore explicitly inactive rows from older display snapshots', async () => {
  await saveDiningTableSnapshot(scope, [table, { ...table, id: 21, is_active: false }])
  expect(await readDiningTableSnapshot(scope)).toEqual([table])
  const saved = [...records.values()][0]
  saved.tables = [{ ...table, is_active: false }]
  expect(await readDiningTableSnapshot(scope)).toEqual([])
})
