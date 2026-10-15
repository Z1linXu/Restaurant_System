import { Client, type StompConfig } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getAccessToken } from './apiClient'

export function realtimeEndpoint() {
  if (typeof window === 'undefined') return '/ws'
  // SockJS needs an HTTP endpoint for /info and XHR as well as its WS transport.
  return new URL('/ws', window.__RESTAURANT_API_BASE_URL__ || window.location.href).toString()
}

export function createAuthenticatedRealtimeClient(config: StompConfig = {}) {
  let disposed = false
  let connectingToken: string | null = null
  let reconnectGeneration = 0
  const client = new Client({
    reconnectDelay: 3_000,
    ...config,
    webSocketFactory: config.webSocketFactory ?? (() => new SockJS(realtimeEndpoint())),
  })
  client.beforeConnect = () => {
    const token = getAccessToken()
    connectingToken = token
    if (disposed || !token) {
      client.connectHeaders = {}
      void client.deactivate()
      return
    }
    client.connectHeaders = { Authorization: `Bearer ${token}` }
  }
  const expired = () => {
    reconnectGeneration += 1
    connectingToken = null
    client.connectHeaders = {}
    void client.deactivate()
  }
  const updated = () => {
    if (disposed || getAccessToken() === connectingToken) return
    const generation = ++reconnectGeneration
    void client.deactivate().then(() => {
      if (!disposed && generation === reconnectGeneration && getAccessToken()) client.activate()
    })
  }
  window.addEventListener?.('restaurant-auth-expired', expired)
  window.addEventListener?.('restaurant-auth-updated', updated)
  return {
    client,
    dispose() {
      if (disposed) return
      disposed = true
      reconnectGeneration += 1
      client.connectHeaders = {}
      window.removeEventListener?.('restaurant-auth-expired', expired)
      window.removeEventListener?.('restaurant-auth-updated', updated)
      void client.deactivate()
    },
  }
}
