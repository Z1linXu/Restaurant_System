import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { IDBFactory } from 'fake-indexeddb'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'
import { closeOfflineDatabase, OFFLINE_STORES, openOfflineDatabase, requestResult } from '../../offline/offlineDatabase'
import { readRestrictedAuthSnapshot, saveRestrictedAuthSnapshot } from '../../offline/workspaceSnapshot'
import { activateOfflineAccount, invalidateOfflineSession } from '../../offline/environmentScope'
import { getAccessToken, saveAuthTokens } from '../../services/apiClient'
import type { LoginResponse } from '../../services/authService'

const identity = (id: number) => ({ user: { id, username: `synthetic-user-${id}`, full_name: `User ${id}`, role_code: 'FRONTDESK', store_id: 3, organization_id: id + 10 }, permissions: ['ORDER_CREATE'], features: {} }) as LoginResponse
let view: ReactTestRenderer | undefined
let latest: ReturnType<typeof useAuth>
const fetchMock = vi.fn()
function Probe() {
  const auth = useAuth()
  useEffect(() => { latest = auth }, [auth])
  return <div>{auth.user?.username ?? 'none'}</div>
}
beforeEach(async () => {
  await closeOfflineDatabase(); vi.stubGlobal('indexedDB', new IDBFactory())
  const tokens = new Map<string, string>()
  const localStorage = { getItem: (key: string) => tokens.get(key) ?? null, setItem: (key: string, value: string) => tokens.set(key, value), removeItem: (key: string) => tokens.delete(key) }
  vi.stubGlobal('window', Object.assign(new EventTarget(), { localStorage, location: { origin: 'https://synthetic.invalid' }, setTimeout, clearTimeout }))
  vi.stubGlobal('document', Object.assign(new EventTarget(), { visibilityState: 'visible' }))
  vi.stubGlobal('navigator', { onLine: false }); vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  fetchMock.mockReset(); fetchMock.mockRejectedValue(new TypeError('synthetic offline'))
  invalidateOfflineSession(); activateOfflineAccount(7); saveAuthTokens('synthetic-access-A', 'synthetic-refresh-A')
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; await closeOfflineDatabase(); vi.unstubAllGlobals() })

async function waitForAuthReady() {
  for (let attempt = 0; latest.loading && attempt < 50; attempt += 1) {
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 10)) })
  }
  expect(latest.loading).toBe(false)
}
async function mountOffline() {
  invalidateOfflineSession()
  await act(async () => { view = create(<AuthProvider><Probe /></AuthProvider>) })
  await waitForAuthReady()
}

describe('actual restricted offline auth restoration', () => {
  it('does not restore A when B tokens persist but the B snapshot write fails', async () => {
    await saveRestrictedAuthSnapshot(identity(7))
    saveAuthTokens('synthetic-access-B', 'synthetic-refresh-B')
    const db = await openOfflineDatabase(); const transaction = vi.spyOn(db, 'transaction').mockImplementationOnce(() => { throw new Error('synthetic snapshot write failed') })
    await expect(saveRestrictedAuthSnapshot(identity(8))).rejects.toThrow('synthetic snapshot write failed'); transaction.mockRestore()
    await mountOffline()
    expect(latest.user).toBeNull(); expect(latest.permissions).toEqual([]); expect(latest.sessionMode).toBe('NONE')
    expect(JSON.stringify(view!.toJSON())).not.toContain('synthetic-user-7')
    expect(await readRestrictedAuthSnapshot()).toBeNull(); expect(getAccessToken()).toBe('synthetic-access-B')
    const rows = await requestResult<Array<{ accountId: number; credentialFingerprint: string }>>(db.transaction(OFFLINE_STORES.workspaceSnapshots).objectStore(OFFLINE_STORES.workspaceSnapshots).getAll())
    expect(rows).toHaveLength(1); expect(rows[0].accountId).toBe(7); expect(rows[0].credentialFingerprint).toMatch(/^[a-f0-9]{64}$/)
    expect(JSON.stringify(rows)).not.toContain('synthetic-access-A')
  })
  it('restores the same account after a verified refresh writes its new credential association', async () => {
    await saveRestrictedAuthSnapshot(identity(7))
    saveAuthTokens('synthetic-access-A-refreshed', 'synthetic-refresh-A-refreshed')
    expect(await readRestrictedAuthSnapshot()).toBeNull()
    await saveRestrictedAuthSnapshot(identity(7)); await mountOffline()
    expect(latest.user?.id).toBe(7); expect(latest.sessionMode).toBe('OFFLINE_RESTRICTED'); expect(fetchMock).not.toHaveBeenCalled()
  })
  it('keeps a late snapshot write from binding a previous identity to newly persisted credentials', async () => {
    await saveRestrictedAuthSnapshot(identity(7))
    const lateWrite = saveRestrictedAuthSnapshot(identity(7))
    const rejected = expect(lateWrite).rejects.toThrow('AUTH_CREDENTIAL_CHANGED')
    saveAuthTokens('synthetic-access-B', 'synthetic-refresh-B')
    await rejected; expect(await readRestrictedAuthSnapshot()).toBeNull()
    await mountOffline(); expect(latest.user).toBeNull()
  })
  it('survives the actual online refresh and auth/me chain before a same-account offline restart', async () => {
    await saveRestrictedAuthSnapshot(identity(7))
    vi.stubGlobal('navigator', { onLine: true })
    let firstMe = true
    fetchMock.mockImplementation(async (input: string) => {
      if (input.endsWith('/api/v1/auth/refresh')) return new Response(JSON.stringify({ success: true, data: { access_token: 'synthetic-access-A-fresh', refresh_token: 'synthetic-refresh-A-fresh' } }))
      if (firstMe) { firstMe = false; return new Response(JSON.stringify({ success: false }), { status: 401 }) }
      return new Response(JSON.stringify({ success: true, data: identity(7) }))
    })
    await act(async () => { view = create(<AuthProvider><Probe /></AuthProvider>) }); await waitForAuthReady()
    expect(latest.user?.id).toBe(7); expect(getAccessToken()).toBe('synthetic-access-A-fresh')
    await vi.waitFor(async () => expect((await readRestrictedAuthSnapshot())?.accountId).toBe(7))
    await act(async () => view!.unmount()); view = undefined
    vi.stubGlobal('navigator', { onLine: false }); fetchMock.mockClear()
    await mountOffline(); expect(latest.user?.id).toBe(7); expect(latest.sessionMode).toBe('OFFLINE_RESTRICTED')
    expect(fetchMock).not.toHaveBeenCalled()
  })
  it.each([200, 403])('does not revive or clear identity from old online A HTTP %s after B credentials persist', async (status) => {
    vi.stubGlobal('navigator', { onLine: true })
    let finish!: (value: Response) => void; fetchMock.mockReturnValue(new Promise((resolve) => { finish = resolve }))
    await act(async () => { view = create(<AuthProvider><Probe /></AuthProvider>) })
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    await act(async () => {
      saveAuthTokens('synthetic-access-B', 'synthetic-refresh-B')
      finish(new Response(JSON.stringify({ success: status === 200, data: identity(7) }), { status }))
    })
    await waitForAuthReady()
    expect(latest.user).toBeNull(); expect(getAccessToken()).toBe('synthetic-access-B'); expect(await readRestrictedAuthSnapshot()).toBeNull()
  })
  it('handles missing bundled API configuration without a rejected initialization or loading loop', async () => {
    window.__RESTAURANT_APP_CONFIG__ = { mode: 'BUNDLED_ASSETS' } as RestaurantBundledAppConfig
    window.__RESTAURANT_API_BASE_URL__ = ''
    await mountOffline(); expect(latest.user).toBeNull(); expect(latest.error).toContain('Configure a valid server address')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
