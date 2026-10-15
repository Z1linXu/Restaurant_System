import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAuthenticatedRealtimeClient, realtimeEndpoint } from './realtimeClient'
import { fetchHotKitchenDisplayTasks, subscribeToHotKitchen } from './kdsService'
import { subscribeToServingShelf } from './pickupService'
import { apiRequest } from './apiClient'

interface FakeClient {
  beforeConnect?: () => void
  connectHeaders: Record<string, string>
  onConnect?: () => void
  activate: ReturnType<typeof vi.fn>
  deactivate: ReturnType<typeof vi.fn>
  subscribe: ReturnType<typeof vi.fn>
}
const mocks = vi.hoisted(() => ({ token: 'token-a' as string | null, clients: [] as FakeClient[] }))
vi.mock('./apiClient', () => ({ getAccessToken: () => mocks.token, apiRequest: vi.fn() }))
vi.mock('@stomp/stompjs', () => ({ Client: vi.fn(function (this: FakeClient) {
  this.connectHeaders = {}
  this.activate = vi.fn()
  this.deactivate = vi.fn().mockResolvedValue(undefined)
  this.subscribe = vi.fn(() => ({ unsubscribe: vi.fn() }))
  mocks.clients.push(this)
}) }))
vi.mock('sockjs-client', () => ({ default: vi.fn(function () { return {} }) }))

describe('authenticated realtime lifecycle', () => {
  beforeEach(() => {
    mocks.token = 'token-a'
    mocks.clients.length = 0
    const events = new EventTarget()
    vi.stubGlobal('window', {
      location: { href: 'https://restaurant-pad.local/frontdesk' },
      __RESTAURANT_API_BASE_URL__: 'https://merchant-a.invalid',
      addEventListener: events.addEventListener.bind(events),
      removeEventListener: events.removeEventListener.bind(events),
      dispatchEvent: events.dispatchEvent.bind(events),
    })
  })
  it('uses the configured HTTP API origin for every SockJS transport', () => {
    expect(realtimeEndpoint()).toBe('https://merchant-a.invalid/ws')
  })
  it('gets the current token on each CONNECT and never puts it in the URL', () => {
    const { client, dispose } = createAuthenticatedRealtimeClient()
    const fake = client as unknown as FakeClient
    fake.beforeConnect?.()
    expect(fake.connectHeaders).toEqual({ Authorization: 'Bearer token-a' })
    mocks.token = 'token-b'
    fake.beforeConnect?.()
    expect(fake.connectHeaders).toEqual({ Authorization: 'Bearer token-b' })
    expect(realtimeEndpoint()).not.toContain('token')
    dispose()
  })
  it('stops without credentials, on logout, and after disposal', async () => {
    const { client, dispose } = createAuthenticatedRealtimeClient()
    const fake = client as unknown as FakeClient
    mocks.token = null
    fake.beforeConnect?.()
    expect(fake.deactivate).toHaveBeenCalledTimes(1)
    expect(fake.connectHeaders).toEqual({})
    mocks.token = 'token-a'
    fake.beforeConnect?.()
    window.dispatchEvent(new Event('restaurant-auth-expired'))
    expect(fake.connectHeaders).toEqual({})
    expect(fake.deactivate).toHaveBeenCalledTimes(2)
    dispose()
    dispose()
    mocks.token = 'token-b'
    window.dispatchEvent(new Event('restaurant-auth-updated'))
    await Promise.resolve()
    expect(fake.activate).not.toHaveBeenCalled()
    expect(fake.deactivate).toHaveBeenCalledTimes(3)
  })
  it('closes the previous identity before connecting with refreshed credentials', async () => {
    const { client, dispose } = createAuthenticatedRealtimeClient()
    const fake = client as unknown as FakeClient
    fake.beforeConnect?.()
    mocks.token = 'token-b'
    window.dispatchEvent(new Event('restaurant-auth-updated'))
    expect(fake.deactivate).toHaveBeenCalledTimes(1)
    expect(fake.activate).not.toHaveBeenCalled()
    await Promise.resolve()
    expect(fake.activate).toHaveBeenCalledTimes(1)
    fake.beforeConnect?.()
    expect(fake.connectHeaders.Authorization).toBe('Bearer token-b')
    dispose()
  })
  it('hot kitchen subscribes to its capability-specific topic', () => {
    const cancel = subscribeToHotKitchen(10, vi.fn())
    const fake = mocks.clients[0]
    fake.onConnect?.()
    expect(fake.subscribe).toHaveBeenCalledWith('/topic/stores/10/kds/hot-kitchen', expect.any(Function))
    cancel()
  })
  it('pickup drops foreign or malformed payloads and unsubscribes on cancellation', () => {
    const listener = vi.fn()
    const cancel = subscribeToServingShelf(10, listener)
    const fake = mocks.clients[0]
    fake.onConnect?.()
    const callback = fake.subscribe.mock.calls[0][1]
    callback({ body: JSON.stringify({ store_id: 20 }) })
    callback({ body: 'bad-json' })
    callback({ body: JSON.stringify({ store_id: 10 }) })
    expect(listener).toHaveBeenCalledTimes(1)
    const subscription = fake.subscribe.mock.results[0].value
    cancel()
    expect(subscription.unsubscribe).toHaveBeenCalledTimes(1)
  })
  it('hot kitchen uses its existing capability-specific HTTP endpoint', async () => {
    await fetchHotKitchenDisplayTasks(10)
    expect(apiRequest).toHaveBeenCalledWith('/api/v1/kds/hot-kitchen?store_id=10')
  })

})
