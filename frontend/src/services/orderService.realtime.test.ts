import { beforeEach, describe, expect, it, vi } from 'vitest'
import { subscribeToFrontdeskOrders } from './orderService'

interface FakeFrame { body: string }
interface FakeSubscription { unsubscribe: ReturnType<typeof vi.fn> }
interface FakeClientConfig {
  webSocketFactory: () => unknown
  reconnectDelay: number
  connectionTimeout: number
  heartbeatIncoming: number
  heartbeatOutgoing: number
}
interface FakeClient {
  config: FakeClientConfig
  activate: ReturnType<typeof vi.fn>
  deactivate: ReturnType<typeof vi.fn>
  subscribe: ReturnType<typeof vi.fn<(topic: string, callback: (frame: FakeFrame) => void) => FakeSubscription>>
  onConnect?: () => void
  onStompError?: () => void
  onWebSocketError?: () => void
  onWebSocketClose?: (event: { code: number }) => void
  onDisconnect?: () => void
}

const stomp = vi.hoisted(() => {
  const instances: FakeClient[] = []
  const Client = vi.fn(function (this: FakeClient, config: FakeClientConfig) {
    this.config = config
    this.activate = vi.fn()
    this.deactivate = vi.fn().mockResolvedValue(undefined)
    this.subscribe = vi.fn(() => ({ unsubscribe: vi.fn() }))
    instances.push(this)
  })
  return { Client, instances }
})

const sock = vi.hoisted(() => ({ constructor: vi.fn(function SockJs(endpoint: string) { return { endpoint } }) }))

vi.mock('@stomp/stompjs', () => ({ Client: stomp.Client }))
vi.mock('sockjs-client', () => ({ default: sock.constructor }))

describe('frontdesk SockJS/STOMP lifecycle', () => {
  beforeEach(() => {
    stomp.Client.mockClear()
    stomp.instances.length = 0
    sock.constructor.mockClear()
    vi.stubGlobal('window', { location: { href: 'https://staging.example/frontdesk' } })
  })

  it('bootstraps, subscribes only to the Store topic, filters payloads, and disconnects cleanly', async () => {
    const lifecycle = vi.fn()
    const onMessage = vi.fn()
    const cancel = subscribeToFrontdeskOrders(21, onMessage, { onLifecycle: lifecycle })
    const client = stomp.instances[0]
    expect(client).toBeDefined()
    expect(client.activate).toHaveBeenCalledTimes(1)
    expect(client.config.reconnectDelay).toBe(3_000)
    expect(lifecycle.mock.calls.map(([event]) => event.phase)).toEqual(['BOOTSTRAP_STARTED', 'CLIENT_CREATED'])

    client.config.webSocketFactory()
    expect(sock.constructor).toHaveBeenCalledWith('https://staging.example/ws')
    client.onConnect?.()
    expect(client.subscribe).toHaveBeenCalledWith('/topic/stores/21/frontdesk/orders', expect.any(Function))
    expect(lifecycle).toHaveBeenLastCalledWith(expect.objectContaining({ storeId: 21, phase: 'CONNECTED' }))

    const listener = client.subscribe.mock.calls[0][1]
    listener({ body: JSON.stringify({ store_id: 18, event_type: 'order.updated' }) })
    listener({ body: JSON.stringify({ store_id: 21, event_type: 'order.updated' }) })
    listener({ body: 'not-json' })
    expect(onMessage).toHaveBeenCalledTimes(1)
    expect(onMessage).toHaveBeenCalledWith(expect.objectContaining({ store_id: 21 }))

    const subscription = client.subscribe.mock.results[0].value
    cancel()
    cancel()
    expect(subscription.unsubscribe).toHaveBeenCalledTimes(1)
    expect(client.deactivate).toHaveBeenCalledTimes(1)
    expect(lifecycle).toHaveBeenLastCalledWith(expect.objectContaining({ phase: 'CANCELLED' }))
    await Promise.resolve()
  })

  it('reports reconnect/error phases and replaces the old topic subscription after reconnect', () => {
    const lifecycle = vi.fn()
    subscribeToFrontdeskOrders(21, vi.fn(), { onLifecycle: lifecycle })
    const client = stomp.instances[0]
    client.onConnect?.()
    const firstSubscription = client.subscribe.mock.results[0].value
    client.onWebSocketClose?.({ code: 1006 })
    client.onStompError?.()
    client.onWebSocketError?.()
    client.onDisconnect?.()
    client.onConnect?.()

    expect(firstSubscription.unsubscribe).toHaveBeenCalledTimes(1)
    expect(client.subscribe).toHaveBeenCalledTimes(2)
    expect(lifecycle.mock.calls.map(([event]) => event.phase)).toEqual(expect.arrayContaining([
      'CONNECTED', 'SOCKET_CLOSED', 'STOMP_ERROR', 'SOCKET_ERROR', 'DISCONNECTED',
    ]))
    expect(lifecycle).toHaveBeenCalledWith(expect.objectContaining({ phase: 'SOCKET_CLOSED', closeCode: 1006 }))
  })

  it('reports bootstrap failure without leaking credentials or payload content', () => {
    stomp.Client.mockImplementationOnce(() => { throw new Error('secret-token') })
    const lifecycle = vi.fn()
    const cancel = subscribeToFrontdeskOrders(21, vi.fn(), { onLifecycle: lifecycle })

    expect(lifecycle.mock.calls.map(([event]) => event.phase)).toEqual(['BOOTSTRAP_STARTED', 'BOOTSTRAP_ERROR'])
    expect(JSON.stringify(lifecycle.mock.calls)).not.toContain('secret-token')
    expect(() => cancel()).not.toThrow()
  })
})
