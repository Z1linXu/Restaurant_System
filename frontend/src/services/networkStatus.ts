export type ConnectionState =
  | 'ONLINE_HEALTHY'
  | 'ONLINE_DEGRADED'
  | 'BACKEND_UNREACHABLE'
  | 'BROWSER_OFFLINE'
  | 'AUTH_REQUIRED'

export type AuthRefreshOutcome = 'NOT_ATTEMPTED' | 'SUCCEEDED' | 'FAILED'

export interface ApiRequestMetric {
  requestId: string
  method: string
  endpoint: string
  startedAt: string
  completedAt: string
  latencyMs: number
  status: number
  outcome: 'SUCCESS' | 'HTTP_ERROR' | 'TIMEOUT' | 'NETWORK_ERROR'
  errorCode: string | null
  authRefreshOutcome: AuthRefreshOutcome
}

export interface AppOperationMetric {
  operation: 'MENU_LOAD' | 'ORDER_SUBMIT'
  stage: 'STARTED' | 'SUCCEEDED' | 'FAILED'
  storeId: number
  startedAt: string
  completedAt: string | null
  latencyMs: number | null
  errorCode: string | null
}

export interface ConnectionSnapshot {
  state: ConnectionState
  browserOnline: boolean
  lastSuccessAt: string | null
  lastProbeAt: string | null
  consecutiveFailures: number
  consecutiveTimeouts: number
  lastErrorCode: string | null
  latestRequest: ApiRequestMetric | null
  latestOperation: AppOperationMetric | null
}

const MAX_REQUEST_METRICS = 100
const HEALTHY_LATENCY_MS = 3_000
const listeners = new Set<() => void>()
const requestMetrics: ApiRequestMetric[] = []

function currentBrowserOnline() {
  return typeof navigator === 'undefined' ? true : navigator.onLine
}

let snapshot: ConnectionSnapshot = {
  state: currentBrowserOnline() ? 'ONLINE_DEGRADED' : 'BROWSER_OFFLINE',
  browserOnline: currentBrowserOnline(),
  lastSuccessAt: null,
  lastProbeAt: null,
  consecutiveFailures: 0,
  consecutiveTimeouts: 0,
  lastErrorCode: null,
  latestRequest: null,
  latestOperation: null,
}

export const networkDiagnosticsDisplayEnabled =
  typeof import.meta === 'undefined' || import.meta.env.VITE_NETWORK_DIAGNOSTICS_ENABLED !== 'false'

export function normalizeApiEndpoint(input: string) {
  try {
    const base = typeof window === 'undefined' ? 'https://restaurant-pad.local' : window.location.origin
    return new URL(input, base).pathname.replace(/\/(\d+)(?=\/|$)/g, '/:id')
  } catch {
    return input.split('?')[0].replace(/\/(\d+)(?=\/|$)/g, '/:id')
  }
}

export function deriveConnectionState(value: Pick<
  ConnectionSnapshot,
  'browserOnline' | 'consecutiveFailures' | 'lastErrorCode' | 'latestRequest'
>): ConnectionState {
  if (!value.browserOnline) return 'BROWSER_OFFLINE'
  if (value.lastErrorCode === 'AUTH_REQUIRED') return 'AUTH_REQUIRED'

  const request = value.latestRequest
  if (request?.outcome === 'NETWORK_ERROR' || request?.outcome === 'TIMEOUT') {
    const isHealthProbe = request.endpoint === '/api/v1/system/health'
    return isHealthProbe || value.consecutiveFailures >= 2 ? 'BACKEND_UNREACHABLE' : 'ONLINE_DEGRADED'
  }
  if (request?.outcome === 'HTTP_ERROR' || value.consecutiveFailures > 0 || (request?.latencyMs ?? 0) > HEALTHY_LATENCY_MS) {
    return 'ONLINE_DEGRADED'
  }
  return request ? 'ONLINE_HEALTHY' : 'ONLINE_DEGRADED'
}

function publish(next: ConnectionSnapshot) {
  snapshot = next
  listeners.forEach((listener) => listener())
}

export function setBrowserOnlineStatus(browserOnline: boolean) {
  publish({
    ...snapshot,
    browserOnline,
    state: browserOnline ? deriveConnectionState({ ...snapshot, browserOnline }) : 'BROWSER_OFFLINE',
  })
}

export function recordApiRequestMetric(metric: ApiRequestMetric) {
  requestMetrics.push(metric)
  if (requestMetrics.length > MAX_REQUEST_METRICS) requestMetrics.shift()

  const succeeded = metric.outcome === 'SUCCESS'
  const authRequired = metric.status === 401 && metric.authRefreshOutcome !== 'SUCCEEDED'
  const consecutiveFailures = succeeded ? 0 : snapshot.consecutiveFailures + 1
  const consecutiveTimeouts = metric.outcome === 'TIMEOUT' ? snapshot.consecutiveTimeouts + 1 : 0
  const next: ConnectionSnapshot = {
    ...snapshot,
    browserOnline: currentBrowserOnline(),
    lastSuccessAt: succeeded ? metric.completedAt : snapshot.lastSuccessAt,
    lastProbeAt: metric.endpoint === '/api/v1/system/health' ? metric.completedAt : snapshot.lastProbeAt,
    consecutiveFailures,
    consecutiveTimeouts,
    lastErrorCode: authRequired ? 'AUTH_REQUIRED' : metric.errorCode,
    latestRequest: metric,
  }
  next.state = deriveConnectionState(next)
  publish(next)
}

export function recordAppOperation(metric: AppOperationMetric) {
  if (networkDiagnosticsDisplayEnabled) {
    console.info('[app-operation]', {
      operation: metric.operation,
      stage: metric.stage,
      storeId: metric.storeId,
      latencyMs: metric.latencyMs,
      errorCode: metric.errorCode,
    })
  }
  publish({ ...snapshot, latestOperation: metric })
}

export function getConnectionSnapshot() {
  return snapshot
}

export function subscribeConnectionStatus(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function getRecentApiRequestMetrics() {
  return requestMetrics.slice()
}
