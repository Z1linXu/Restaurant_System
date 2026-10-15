import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { StoreContextProvider, RequireStoreAccess, RequireStoreModule } from './StoreContext'
import { useCurrentStore } from './useStoreContext'
import { useStorePresentation } from './useStorePresentation'
import { useMenuCatalog } from '../../hooks/useMenuCatalog'
import { activateOfflineAccount, canProcessOfflineRecord } from '../../offline/environmentScope'
import { apiRequest } from '../../services/apiClient'
import { saveStoreContextSnapshot } from '../../offline/workspaceSnapshot'
import { replaceActiveMenuSnapshot } from '../../offline/menuCache'
import { fetchMenuCatalog, fetchMenuRevision } from '../../services/menuService'
import type { StoreContextResponse } from '../../services/storeWorkspaceService'
import type { BackendMenuCatalog } from '../../types/ordering'
import type { ActiveMenuSnapshot } from '../../offline/menuCache'

const state = vi.hoisted(() => ({ accountId: 1, revision: 1, enabled: true, brand: 'Initial Brand', offlineRestricted: false, stop: vi.fn(), read: vi.fn() }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: state.accountId }, isOfflineRestricted: state.offlineRestricted }) }))
vi.mock('../../services/apiClient', async original => ({ ...await original<typeof import('../../services/apiClient')>(), apiRequest: vi.fn() }))
vi.mock('../../offline/workspaceSnapshot', async original => ({ ...await original<typeof import('../../offline/workspaceSnapshot')>(), saveStoreContextSnapshot: vi.fn(async () => undefined) }))
vi.mock('../../services/menuService', () => ({ fetchMenuCatalog: vi.fn(), fetchMenuRevision: vi.fn() }))
vi.mock('../../offline/menuCache', () => ({ readActiveMenuSnapshot: vi.fn(async () => null), replaceActiveMenuSnapshot: vi.fn() }))
vi.mock('../../services/networkStatus', () => ({ recordAppOperation: vi.fn() }))

let view: ReactTestRenderer | undefined
let visibility = 'visible'
let online = true
const scope = { environmentId: 'https://a.synthetic.invalid', accountId: 1, organizationId: 9, storeId: 1 }
const summary = () => ({ schema_version: 1 as const, config_revision: state.revision, brand_name: state.brand, locale: 'en-CA', currency: 'CAD' as const, timezone: 'America/Toronto', tax_policy_reference: 'tax', profile_code: 'NEUTRAL', compatibility_mode: 'NEUTRAL' as const })
function context(id = 1): StoreContextResponse {
  return { id, name: `Store ${id}`, organization_id: 9, safe_summary: summary(), module_configuration: { store_id: id, modules: [{ module_key: 'PRINTING', enabled: state.enabled, persisted: true }], validation_issues: [] } } as unknown as StoreContextResponse
}
function catalog(): BackendMenuCatalog {
  return { store_id: 1, organization_id: 9, menu_revision: state.revision, generated_at: '2026-10-15T12:00:00Z', content_hash: `hash-${state.revision}`, catalog_version: 'v4', combo_metadata_version: 'v1', tax_policy: { rate: 0.1, label: 'Tax', version: 'v1' }, safe_summary: summary(), categories: [] }
}
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done }); return { promise, resolve } }
function Printing() { useEffect(() => { state.read(); return state.stop }, []); return <b>Printing active</b> }
function Presentation() { const presentation = useStorePresentation(); return <span>{presentation.brandName}</span> }
function Menu() {
  const store = useCurrentStore()
  const menu = useMenuCatalog(store.storeId, { accountId: state.accountId, organizationId: store.organizationId })
  return <span>{`menu:${menu.catalog?.menuRevision};config:${menu.catalog?.safeSummary?.config_revision}`}</span>
}
function page(storeId = 1, withMenu = false) {
  return <StoreContextProvider storeId={storeId}><RequireStoreAccess><Presentation />{withMenu ? <Menu /> : null}<RequireStoreModule moduleKey="PRINTING"><Printing /></RequireStoreModule></RequireStoreAccess></StoreContextProvider>
}
const text = () => JSON.stringify(view!.toJSON())
const focus = () => window.dispatchEvent(new Event('focus'))
beforeEach(() => {
  vi.useFakeTimers(); vi.clearAllMocks()
  state.accountId = 1; state.revision = 1; state.enabled = true; state.brand = 'Initial Brand'; state.offlineRestricted = false
  visibility = 'visible'; online = true
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', Object.assign(new EventTarget(), { location: { origin: scope.environmentId }, setInterval, clearInterval, setTimeout, clearTimeout }))
  const document = new EventTarget()
  Object.defineProperty(document, 'visibilityState', { get: () => visibility })
  vi.stubGlobal('document', document)
  vi.stubGlobal('navigator', { get onLine() { return online } })
  activateOfflineAccount(1)
  vi.mocked(apiRequest).mockImplementation(async path => context(Number(path.match(/stores\/(\d+)/)?.[1] ?? 1)) as never)
  vi.mocked(fetchMenuCatalog).mockImplementation(async () => catalog())
  vi.mocked(fetchMenuRevision).mockImplementation(async () => ({ store_id: 1, organization_id: 9, menu_revision: state.revision, menu_updated_at: '', catalog_version: 'v4', tax_policy_version: 'v1', etag: `etag-${state.revision}` }))
  vi.mocked(replaceActiveMenuSnapshot).mockImplementation(async (_scope, data) => ({ head: { lastUpdatedAt: data.generated_at }, snapshot: { catalog: data } }) as ActiveMenuSnapshot)
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.useRealTimers(); vi.unstubAllGlobals() })

it('refreshes actual Provider brand/module and menu/config revisions on focus and within 60 seconds, without re-registering the same scope', async () => {
  const registration = vi.fn(); window.addEventListener('restaurant-offline-scope-updated', registration)
  await act(async () => { view = create(page(1, true)) })
  expect(text()).toContain('Initial Brand'); expect(text()).toContain('menu:1;config:1')
  expect(state.read).toHaveBeenCalledTimes(1); expect(registration).toHaveBeenCalledTimes(1)
  state.brand = 'Updated Brand'; state.revision = 2; state.enabled = false
  await act(async () => { focus() })
  expect(text()).toContain('Updated Brand'); expect(text()).toContain('menu:2;config:2')
  expect(state.stop).toHaveBeenCalledTimes(1)
  expect(saveStoreContextSnapshot).toHaveBeenLastCalledWith(1, expect.objectContaining({ safe_summary: expect.objectContaining({ config_revision: 2 }) }))
  expect(replaceActiveMenuSnapshot).toHaveBeenLastCalledWith(scope, expect.objectContaining({ menu_revision: 2, safe_summary: expect.objectContaining({ config_revision: 2 }) }), 'etag-2')
  state.brand = 'Third Brand'; state.revision = 3
  await act(async () => { await vi.advanceTimersByTimeAsync(60_000) })
  expect(text()).toContain('Third Brand'); expect(text()).toContain('menu:3;config:3')
  expect(registration).toHaveBeenCalledTimes(1)
  expect(canProcessOfflineRecord(scope)).toBe(true)
  expect(vi.getTimerCount()).toBe(2)
  await act(async () => view!.unmount()); view = undefined
  expect(vi.getTimerCount()).toBe(0)
  const calls = vi.mocked(apiRequest).mock.calls.length
  await act(async () => { focus(); document.dispatchEvent(new Event('visibilitychange')); await vi.advanceTimersByTimeAsync(120_000) })
  expect(apiRequest).toHaveBeenCalledTimes(calls)
})

it('deduplicates overlapping foreground events, skips hidden/offline refreshes and keeps registration during a pending refresh', async () => {
  const registration = vi.fn(); window.addEventListener('restaurant-offline-scope-updated', registration)
  await act(async () => { view = create(page()) })
  visibility = 'hidden'
  await act(async () => { focus(); await vi.advanceTimersByTimeAsync(120_000) })
  visibility = 'visible'; online = false
  await act(async () => { focus(); document.dispatchEvent(new Event('visibilitychange')); await vi.advanceTimersByTimeAsync(60_000) })
  expect(apiRequest).toHaveBeenCalledTimes(1)
  online = true
  const pending = deferred<StoreContextResponse>(); vi.mocked(apiRequest).mockReturnValueOnce(pending.promise)
  await act(async () => { focus(); focus(); window.dispatchEvent(new Event('online')); document.dispatchEvent(new Event('visibilitychange')); await vi.advanceTimersByTimeAsync(60_000) })
  expect(apiRequest).toHaveBeenCalledTimes(2)
  expect(canProcessOfflineRecord(scope)).toBe(true); expect(registration).toHaveBeenCalledTimes(1)
  await act(async () => pending.resolve(context()))
  expect(registration).toHaveBeenCalledTimes(1)
})

it('aborts an old Store request and rejects its late response before any cache write', async () => {
  const old = deferred<StoreContextResponse>(); const current = deferred<StoreContextResponse>()
  vi.mocked(apiRequest).mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
  await act(async () => { view = create(page(1)) })
  const oldSignal = vi.mocked(apiRequest).mock.calls[0][1]!.signal!
  await act(async () => view!.update(page(2)))
  expect(oldSignal.aborted).toBe(true)
  state.brand = 'Store Two'; await act(async () => current.resolve(context(2)))
  state.brand = 'Late foreign Store'; await act(async () => old.resolve(context(1)))
  expect(text()).toContain('Store Two'); expect(text()).not.toContain('Late foreign Store')
  expect(saveStoreContextSnapshot).toHaveBeenCalledTimes(1)
  expect(saveStoreContextSnapshot).toHaveBeenCalledWith(1, expect.objectContaining({ id: 2 }))
})

it('immediately drops old branding on an account/environment switch and ignores the old pending refresh', async () => {
  await act(async () => { view = create(page()) })
  const old = deferred<StoreContextResponse>(); const current = deferred<StoreContextResponse>()
  vi.mocked(apiRequest).mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
  await act(async () => { focus() })
  const oldSignal = vi.mocked(apiRequest).mock.calls[1][1]!.signal!
  state.accountId = 2; window.__RESTAURANT_API_BASE_URL__ = 'https://b.synthetic.invalid'; activateOfflineAccount(2)
  await act(async () => view!.update(page()))
  expect(oldSignal.aborted).toBe(true); expect(text()).not.toContain('Initial Brand')
  state.brand = 'Account B'; await act(async () => current.resolve(context()))
  state.brand = 'Late A'; await act(async () => old.resolve(context()))
  expect(text()).toContain('Account B'); expect(text()).not.toContain('Late A')
  expect(saveStoreContextSnapshot).toHaveBeenCalledTimes(2)
  expect(saveStoreContextSnapshot).toHaveBeenLastCalledWith(2, expect.objectContaining({ safe_summary: expect.objectContaining({ brand_name: 'Account B' }) }))
})

it('does not re-register the same scope just because restricted mode changes', async () => {
  const registration = vi.fn(); window.addEventListener('restaurant-offline-scope-updated', registration)
  await act(async () => { view = create(page()) })
  state.offlineRestricted = true
  await act(async () => view!.update(page()))
  expect(registration).toHaveBeenCalledTimes(1)
  expect(apiRequest).toHaveBeenCalledTimes(1)
})
