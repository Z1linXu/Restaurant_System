import { StrictMode, useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useUberInbox } from './useUberInbox'
import { useAuth, type AuthContextValue } from '../auth/useAuth'
import { ApiRequestError } from '../../services/apiClient'
import { fetchUberInbox, type UberInboxFeed, type UberOrder } from '../../services/uberEatsService'
import { subscribeToFrontdeskOrders } from '../../services/orderService'
vi.mock('../auth/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../../services/uberEatsService', () => ({ fetchUberInbox: vi.fn() }))
vi.mock('../../services/orderService', () => ({ subscribeToFrontdeskOrders: vi.fn(() => vi.fn()) }))

const row = (id = 1) => ({ id, status: 'PENDING' }) as UberOrder
function feed(orders: UberOrder[] = [row()]): UberInboxFeed {
  return { business_date: '2026-11-03', time_zone: 'America/Toronto', today: { orders, has_more: false, next_before_id: null }, attention: { orders: [], has_more: false, next_before_id: null }, history: null }
}
function auth(id = 7, permissions = ['ORDER_VIEW_ACTIVE']): AuthContextValue {
  return { user: { id, username: 'fixture', full_name: 'Fixture', role_code: 'FRONTDESK', store_id: 1, organization_id: 1 }, permissions, features: {}, loading: false, error: null, sessionMode: 'ONLINE', isOfflineRestricted: false, isOwner: false, isManager: false, isFrontdesk: true, signIn: vi.fn(), signOut: vi.fn(), refreshMe: vi.fn() }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

describe('shared authenticated Uber inbox subscription', () => {
  let renderer: ReactTestRenderer | undefined
  const latest: Record<string, ReturnType<typeof useUberInbox>> = {}
  let browser: EventTarget & { setInterval: ReturnType<typeof vi.fn>; clearInterval: ReturnType<typeof vi.fn> }
  let documentTarget: EventTarget & { hidden: boolean }
  function Probe({ store = 1, name = 'page' }: { store?: number; name?: string }) {
    const value = useUberInbox(store)
    useEffect(() => { latest[name] = value }, [value, name])
    return null
  }
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useAuth).mockReturnValue(auth())
    vi.mocked(fetchUberInbox).mockResolvedValue(feed())
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
    browser = Object.assign(new EventTarget(), { setInterval: vi.fn(() => 1), clearInterval: vi.fn() })
    documentTarget = Object.assign(new EventTarget(), { hidden: false })
    vi.stubGlobal('window', browser)
    vi.stubGlobal('document', documentTarget)
  })
  afterEach(async () => {
    if (renderer) await act(async () => renderer!.unmount())
    renderer = undefined
    vi.unstubAllGlobals()
  })
  const mount = async (node = <Probe />) => { await act(async () => { renderer = create(node) }) }
  const hint = async () => { await act(async () => vi.mocked(subscribeToFrontdeskOrders).mock.calls.at(-1)![1]({ event_type: 'uber.inbox.changed' } as never)) }
  it('shares one initial request, 5s timer and realtime client between Page and Badge', async () => {
    await mount(<><Probe /><Probe name="badge" /></>)
    expect(fetchUberInbox).toHaveBeenCalledTimes(1)
    expect(subscribeToFrontdeskOrders).toHaveBeenCalledTimes(1)
    expect(browser.setInterval).toHaveBeenCalledExactlyOnceWith(expect.any(Function), 5000)
    expect(latest.page.orders).toEqual([row()])
    expect(latest.badge.orders).toEqual([row()])
    await hint()
    expect(fetchUberInbox).toHaveBeenCalledTimes(2)
  })
  it('keeps resources while one consumer remains, then cancels them on last unmount', async () => {
    await mount(<><Probe /><Probe name="badge" /></>)
    const unsubscribe = vi.mocked(subscribeToFrontdeskOrders).mock.results[0].value
    await act(async () => renderer!.update(<Probe />))
    expect(unsubscribe).not.toHaveBeenCalled()
    await act(async () => renderer!.unmount())
    expect(unsubscribe).toHaveBeenCalledTimes(1)
    expect(browser.clearInterval).toHaveBeenCalledTimes(1)
    renderer = undefined
  })
  it('reloads after remount without retaining old workspace data', async () => {
    await mount()
    await act(async () => renderer!.unmount())
    await mount()
    expect(fetchUberInbox).toHaveBeenCalledTimes(2)
    expect(latest.page.orders).toEqual([row()])
  })
  it('aborts and isolates Store changes, ignoring a late previous response', async () => {
    const pending = deferred<UberInboxFeed>()
    vi.mocked(fetchUberInbox).mockReturnValueOnce(pending.promise).mockResolvedValueOnce(feed([row(2)]))
    await mount()
    const signal = vi.mocked(fetchUberInbox).mock.calls[0][2]!
    await act(async () => renderer!.update(<Probe store={2} />))
    expect(signal.aborted).toBe(true)
    expect(latest.page.orders).toEqual([row(2)])
    await act(async () => pending.resolve(feed([row(99)])))
    expect(latest.page.orders).toEqual([row(2)])
  })
  it('does not show previous-user data while a new identity request is pending', async () => {
    await mount()
    vi.mocked(useAuth).mockReturnValue(auth(8))
    vi.mocked(fetchUberInbox).mockReturnValueOnce(new Promise(() => {}))
    await act(async () => renderer!.update(<Probe />))
    expect(latest.page.orders).toEqual([])
    expect(latest.page.loading).toBe(true)
    expect(subscribeToFrontdeskOrders).toHaveBeenCalledTimes(2)
  })
  it('isolates permission changes but ignores permission ordering and token refresh', async () => {
    vi.mocked(useAuth).mockReturnValue(auth(7, ['A', 'B']))
    await mount()
    vi.mocked(useAuth).mockReturnValue(auth(7, ['B', 'A']))
    await act(async () => renderer!.update(<Probe />))
    expect(subscribeToFrontdeskOrders).toHaveBeenCalledTimes(1)
    vi.mocked(useAuth).mockReturnValue(auth(7, ['B']))
    await act(async () => renderer!.update(<Probe />))
    expect(subscribeToFrontdeskOrders).toHaveBeenCalledTimes(2)
  })
  it('clears orders and stops polling when the session expires', async () => {
    await mount()
    await act(async () => { browser.dispatchEvent(new Event('restaurant-auth-expired')) })
    expect(latest.page.orders).toEqual([])
    expect(latest.page.error).toContain('登录已过期')
    await hint()
    expect(fetchUberInbox).toHaveBeenCalledTimes(1)
  })
  it('does not start a stream for offline-restricted or unauthenticated sessions', async () => {
    vi.mocked(useAuth).mockReturnValue({ ...auth(), sessionMode: 'OFFLINE_RESTRICTED', isOfflineRestricted: true })
    await mount()
    expect(fetchUberInbox).not.toHaveBeenCalled()
    expect(subscribeToFrontdeskOrders).not.toHaveBeenCalled()
    vi.mocked(useAuth).mockReturnValue({ ...auth(), user: null, sessionMode: 'NONE' })
    await act(async () => renderer!.update(<Probe />))
    expect(fetchUberInbox).not.toHaveBeenCalled()
  })
  it('coalesces hints during one request and performs one trailing refresh', async () => {
    const pending = deferred<UberInboxFeed>()
    vi.mocked(fetchUberInbox).mockReturnValueOnce(pending.promise)
    await mount()
    await hint(); await hint(); await hint()
    expect(fetchUberInbox).toHaveBeenCalledTimes(1)
    await act(async () => pending.resolve(feed()))
    expect(fetchUberInbox).toHaveBeenCalledTimes(2)
  })
  it('refreshes on reconnect/online/visibility and keeps a single client', async () => {
    await mount()
    await act(async () => vi.mocked(subscribeToFrontdeskOrders).mock.calls[0][2]!.onLifecycle!({ storeId: 1, phase: 'CONNECTED', transport: 'sockjs-stomp' }))
    await act(async () => { browser.dispatchEvent(new Event('online')) })
    await act(async () => { documentTarget.dispatchEvent(new Event('visibilitychange')) })
    expect(fetchUberInbox).toHaveBeenCalledTimes(4)
    expect(subscribeToFrontdeskOrders).toHaveBeenCalledTimes(1)
    documentTarget.hidden = true
    await act(async () => browser.setInterval.mock.calls[0][0]())
    expect(fetchUberInbox).toHaveBeenCalledTimes(4)
  })
  it('clears previously visible data after authorization denial', async () => {
    await mount()
    vi.mocked(fetchUberInbox).mockRejectedValueOnce(new ApiRequestError(403, 'Access denied'))
    await hint()
    expect(latest.page.orders).toEqual([])
    expect(latest.page.error).toBe('Access denied')
  })
  it('preserves pagination and refreshes older loaded work instead of hiding it', async () => {
    const first = feed([row(3)])
    first.attention = { orders: [row(2)], has_more: true, next_before_id: 2 }
    const next = feed([row(3)])
    next.attention = { orders: [row(1)], has_more: false, next_before_id: null }
    vi.mocked(fetchUberInbox).mockResolvedValueOnce(first).mockResolvedValueOnce(first).mockResolvedValueOnce(next)
    await mount()
    await act(async () => latest.page.loadMore('attention'))
    expect(latest.page.attention.orders.map(order => order.id)).toEqual([2, 1])
    expect(fetchUberInbox).toHaveBeenLastCalledWith(1, { limit: 100, today_before_id: undefined, attention_before_id: 2 }, expect.any(AbortSignal))
    vi.mocked(fetchUberInbox).mockResolvedValueOnce(feed([row(4)]))
    await hint()
    expect(latest.page.attention.orders).toEqual([])
  })
  it('retains one live timer and subscription under StrictMode', async () => {
    await mount(<StrictMode><Probe /><Probe name="badge" /></StrictMode>)
    const created = vi.mocked(subscribeToFrontdeskOrders).mock.results.map(result => result.value)
    expect(created.filter(unsubscribe => unsubscribe.mock.calls.length === 0)).toHaveLength(1)
    expect(latest.page.orders).toEqual([row()])
  })
})
