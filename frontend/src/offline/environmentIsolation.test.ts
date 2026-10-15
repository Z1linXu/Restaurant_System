import { saveAuthTokens } from '../services/apiClient'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { IDBFactory } from 'fake-indexeddb'
import { closeOfflineDatabase, OFFLINE_DATABASE_NAME, OFFLINE_STORES, openOfflineDatabase, requestResult, transactionComplete } from './offlineDatabase'
import { createLocalDraftRecord, cleanupExpiredLocalDrafts, listLocalDraftsForScope, saveLocalDraft } from './localDrafts'
import { createOrderOutboxRecord, listOrderOutboxForAccount, saveOrderOutboxRecord } from './orderOutbox'
import { hasLegacyOfflineOrders } from './legacyOfflineRecords'
import { calculateMenuContentHash, readActiveMenuSnapshot, replaceActiveMenuSnapshot } from './menuCache'
import { clearRestrictedAuthSnapshot, readRestrictedAuthSnapshot, readRestrictedWorkspaceSnapshot, saveRestrictedAuthSnapshot, saveWorkspaceSnapshot } from './workspaceSnapshot'
import { activateOfflineAccount, canonicalApiOrigin, getEnvironmentId, invalidateOfflineSession } from './environmentScope'
import type { BackendMenuCatalog } from '../types/ordering'
import type { LoginResponse } from '../services/authService'

const scope = { environmentId: 'https://a.synthetic.invalid', accountId: 7, organizationId: 9, storeId: 3 }
const context = { orderType: 'dine_in' as const, slotLabel: 'T1', tableLabel: 'T1', tableNo: 'T1', pickupNo: null }
function draft() { return createLocalDraftRecord(scope, context, 12, new Date('2000-01-01')) }
function payload(clientId: string) {
  return { organization_id: 9, store_id: 3, client_order_id: clientId, idempotency_key: clientId, server_order_id: null,
    order_type: 'dine_in', table_no: 'T1', pickup_no: null, menu_revision: 12, expected_subtotal_amount: 0, items: [] } as const
}
function useEnvironment(environmentId: string) { window.__RESTAURANT_API_BASE_URL__ = environmentId; activateOfflineAccount(7) }
beforeEach(async () => {
  await closeOfflineDatabase()
  vi.stubGlobal('indexedDB', new IDBFactory())
  const tokens = new Map<string, string>()
  const localStorage = { getItem: (key: string) => tokens.get(key) ?? null, setItem: (key: string, value: string) => tokens.set(key, value), removeItem: (key: string) => tokens.delete(key) }
  vi.stubGlobal('window', Object.assign(new EventTarget(), { localStorage, location: { origin: 'https://restaurant-pad.local' }, __RESTAURANT_API_BASE_URL__: scope.environmentId }))
  invalidateOfflineSession(); activateOfflineAccount(7)
  saveAuthTokens('synthetic-access-A', 'synthetic-refresh-A')
})
afterEach(async () => { await closeOfflineDatabase(); vi.unstubAllGlobals() })

async function allRows(name: string) {
  const db = await openOfflineDatabase(); const tx = db.transaction(name, 'readonly'); const done = transactionComplete(tx)
  const result = await requestResult(tx.objectStore(name).getAll()); await done; return result
}

describe('actual IndexedDB upgrade and environment isolation', () => {
  it('preserves every unknown-origin v4 row unchanged, never auto adopts/deletes/replays, and blocks old DB4 openers', async () => {
    const legacyDraft = draft(); const { environmentId: _environment, ...withoutEnvironment } = legacyDraft
    expect(_environment).toBe(scope.environmentId)
    const oldDraft = { ...withoutEnvironment, key: 'account:7|organization:9|store:3|table:T1', schemaVersion: 1, items: [{ quantity: 1 }], submitState: 'LOCAL_DRAFT' }
    const outbox = createOrderOutboxRecord(legacyDraft, { ...payload(legacyDraft.clientOrderId), items: [] })
    const { environmentId: _origin, ...oldOutbox } = outbox
    expect(_origin).toBe(scope.environmentId)
    const oldRows = [oldDraft, ...['QUEUED', 'SUBMITTING', 'FAILED_RETRYABLE', 'CONFLICT'].map((state, index) => ({
      ...oldOutbox, key: `account:7|organization:9|store:3|client:legacy-${index}`, clientOrderId: `legacy-${index}`, state, schemaVersion: 1,
    }))]
    const old = await new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open(OFFLINE_DATABASE_NAME, 4)
      request.onupgradeneeded = () => {
        for (const name of Object.values(OFFLINE_STORES)) {
          const store = request.result.createObjectStore(name, { keyPath: 'key' })
          if (name === OFFLINE_STORES.orderOutbox) store.createIndex('byAccountId', 'accountId')
        }
      }
      request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error)
    })
    const tx = old.transaction([OFFLINE_STORES.localDrafts, OFFLINE_STORES.orderOutbox, OFFLINE_STORES.workspaceSnapshots], 'readwrite')
    const saved = transactionComplete(tx); tx.objectStore(OFFLINE_STORES.localDrafts).put(oldRows[0])
    oldRows.slice(1).forEach((row) => tx.objectStore(OFFLINE_STORES.orderOutbox).put(row))
    tx.objectStore(OFFLINE_STORES.workspaceSnapshots).put({ key: 'auth:current', user: { id: 7 }, schemaVersion: 1 })
    await saved; old.close()
    expect((await openOfflineDatabase()).version).toBe(5)
    await cleanupExpiredLocalDrafts(Date.now()); await clearRestrictedAuthSnapshot()
    expect(await listLocalDraftsForScope(scope)).toEqual([])
    expect(await listOrderOutboxForAccount(7)).toEqual([])
    expect(await readRestrictedAuthSnapshot()).toBeNull()
    expect(await hasLegacyOfflineOrders()).toBe(true)
    expect(JSON.stringify(await allRows(OFFLINE_STORES.localDrafts))).toBe(JSON.stringify([oldRows[0]]))
    expect(JSON.stringify(await allRows(OFFLINE_STORES.orderOutbox))).toBe(JSON.stringify(oldRows.slice(1)))
    expect(await allRows(OFFLINE_STORES.workspaceSnapshots)).toEqual([{ key: 'auth:current', user: { id: 7 }, schemaVersion: 1 }])
    await expect(new Promise((resolve, reject) => { const request = indexedDB.open(OFFLINE_DATABASE_NAME, 4); request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error) })).rejects.toMatchObject({ name: 'VersionError' })
  })

  it('isolates two API origins with identical IDs on one bundled origin and resumes the exact original queue on return', async () => {
    const original = draft(); const queued = createOrderOutboxRecord(original, { ...payload(original.clientOrderId), items: [] })
    await saveLocalDraft({ ...original, submitState: 'QUEUED' }); await saveOrderOutboxRecord(queued)
    const catalog: BackendMenuCatalog = { store_id: 3, organization_id: 9, menu_revision: 12, generated_at: new Date().toISOString(),
      catalog_version: 'menu-catalog-v4', combo_metadata_version: 'test', content_hash: '', categories: [], tax_policy: { rate: 0, label: 'Tax', version: 'test' } }
    catalog.content_hash = calculateMenuContentHash(catalog); await replaceActiveMenuSnapshot(scope, catalog, 'etag')
    const auth = { user: { id: 7, username: 'synthetic', organization_id: 9, store_id: 3 }, permissions: [], features: {} } as unknown as LoginResponse
    await saveRestrictedAuthSnapshot(auth)
    await saveWorkspaceSnapshot(7, { default_store_id: 3, organizations: [], stores: [{ id: 3, organization_id: 9 } as never] })
    useEnvironment('https://b.synthetic.invalid')
    const other = { ...scope, environmentId: getEnvironmentId() }
    expect(await listLocalDraftsForScope(other)).toEqual([]); expect(await listOrderOutboxForAccount(7)).toEqual([])
    expect(await readActiveMenuSnapshot(other)).toBeNull(); expect(await readRestrictedAuthSnapshot()).toBeNull()
    expect(await readRestrictedWorkspaceSnapshot(7)).toBeNull()
    await saveLocalDraft({ ...createLocalDraftRecord(other, context, 12), clientOrderId: original.clientOrderId })
    expect((await listLocalDraftsForScope(other))).toHaveLength(1)
    useEnvironment(scope.environmentId)
    expect((await listLocalDraftsForScope(scope))[0].clientOrderId).toBe(original.clientOrderId)
    expect((await listOrderOutboxForAccount(7))[0].frozenPayload).toEqual(queued.frozenPayload)
    expect((await listOrderOutboxForAccount(7))[0].payloadHash).toBe(queued.payloadHash)
    expect(await readActiveMenuSnapshot(scope)).not.toBeNull(); expect((await readRestrictedAuthSnapshot())?.accountId).toBe(7)
    expect(await listLocalDraftsForScope({ ...scope, organizationId: 10 })).toEqual([])
    expect(await listLocalDraftsForScope({ ...scope, accountId: 8 })).toEqual([])
  })
  it('canonicalizes default ports and rejects missing bundled targets and ambiguous URL components', () => {
    expect(canonicalApiOrigin('https://A.synthetic.invalid:443/')).toBe(scope.environmentId)
    for (const value of ['file:///tmp/app', 'https://user:secret@a.invalid', 'https://a.invalid/api', 'https://a.invalid?x=1']) expect(() => canonicalApiOrigin(value)).toThrow()
    window.__RESTAURANT_API_BASE_URL__ = ''; expect(() => getEnvironmentId()).toThrow('API_ENVIRONMENT_REQUIRED')
  })
})
