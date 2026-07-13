import { describe, expect, it } from 'vitest'
import { deriveConnectionState, normalizeApiEndpoint, type ApiRequestMetric } from './networkStatus'

function metric(overrides: Partial<ApiRequestMetric> = {}): ApiRequestMetric {
  return {
    requestId: 'request-1',
    method: 'GET',
    endpoint: '/api/v1/menu/catalog',
    startedAt: '2026-01-01T00:00:00.000Z',
    completedAt: '2026-01-01T00:00:00.100Z',
    latencyMs: 100,
    status: 200,
    outcome: 'SUCCESS',
    errorCode: null,
    authRefreshOutcome: 'NOT_ATTEMPTED',
    ...overrides,
  }
}

describe('network status classification', () => {
  it('distinguishes browser offline', () => {
    expect(deriveConnectionState({ browserOnline: false, consecutiveFailures: 0, lastErrorCode: null, latestRequest: null }))
      .toBe('BROWSER_OFFLINE')
  })

  it('marks a failed health probe as backend unreachable', () => {
    expect(deriveConnectionState({
      browserOnline: true,
      consecutiveFailures: 1,
      lastErrorCode: 'NETWORK_ERROR',
      latestRequest: metric({ endpoint: '/api/v1/system/health', outcome: 'NETWORK_ERROR', status: 0 }),
    })).toBe('BACKEND_UNREACHABLE')
  })

  it('distinguishes timeout, 5xx, 401, slow, and healthy requests', () => {
    expect(deriveConnectionState({ browserOnline: true, consecutiveFailures: 2, lastErrorCode: 'REQUEST_TIMEOUT', latestRequest: metric({ outcome: 'TIMEOUT', status: 0 }) }))
      .toBe('BACKEND_UNREACHABLE')
    expect(deriveConnectionState({ browserOnline: true, consecutiveFailures: 1, lastErrorCode: 'HTTP_503', latestRequest: metric({ outcome: 'HTTP_ERROR', status: 503 }) }))
      .toBe('ONLINE_DEGRADED')
    expect(deriveConnectionState({ browserOnline: true, consecutiveFailures: 1, lastErrorCode: 'AUTH_REQUIRED', latestRequest: metric({ outcome: 'HTTP_ERROR', status: 401 }) }))
      .toBe('AUTH_REQUIRED')
    expect(deriveConnectionState({ browserOnline: true, consecutiveFailures: 0, lastErrorCode: null, latestRequest: metric({ latencyMs: 4_000 }) }))
      .toBe('ONLINE_DEGRADED')
    expect(deriveConnectionState({ browserOnline: true, consecutiveFailures: 0, lastErrorCode: null, latestRequest: metric() }))
      .toBe('ONLINE_HEALTHY')
  })

  it('normalizes ids and strips query parameters without retaining credentials', () => {
    expect(normalizeApiEndpoint('/api/v1/orders/425?token=secret&notes=private'))
      .toBe('/api/v1/orders/:id')
  })
})
