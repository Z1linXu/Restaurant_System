// SockJS first uses HTTP /info; a WebSocket URL rewrite alone cannot route it.
export function sockJsEndpoint() {
  if (typeof window === 'undefined') return '/ws'
  return new URL('/ws', window.__RESTAURANT_API_BASE_URL__ || window.location.href).toString()
}
