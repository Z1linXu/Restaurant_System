// Storage isolation only. The backend remains the authorization boundary.
export interface OfflineScope {
  environmentId: string
  accountId: number
  organizationId: number
  storeId: number
}

export function canonicalApiOrigin(value: string) {
  const url = new URL(value)
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password
    || url.search || url.hash || (url.pathname !== '/' && url.pathname !== '')) {
    throw new Error('INVALID_API_ENVIRONMENT')
  }
  return url.origin
}

export function getEnvironmentId() {
  const configured = window.__RESTAURANT_API_BASE_URL__
  if (window.__RESTAURANT_APP_CONFIG__?.mode === 'BUNDLED_ASSETS' && !configured) {
    throw new Error('API_ENVIRONMENT_REQUIRED')
  }
  const origin = canonicalApiOrigin(configured || window.location.origin)
  if (new URL(origin).hostname === 'restaurant-pad.local') throw new Error('API_ENVIRONMENT_REQUIRED')
  return origin
}

export function offlineScopeKey(scope: OfflineScope) {
  if (canonicalApiOrigin(scope.environmentId) !== scope.environmentId) throw new Error('INVALID_API_ENVIRONMENT')
  return `environment:${encodeURIComponent(scope.environmentId)}|account:${scope.accountId}|organization:${scope.organizationId}|store:${scope.storeId}`
}

export function matchesOfflineScope(record: OfflineScope, scope: OfflineScope) {
  return record.environmentId === scope.environmentId && record.accountId === scope.accountId
    && record.organizationId === scope.organizationId && record.storeId === scope.storeId
}

let sessionGeneration = 0
let activeScope: OfflineScope | null = null
let activeAccount: { environmentId: string; accountId: number } | null = null
export function captureEnvironmentSession() {
  return { environmentId: getEnvironmentId(), generation: sessionGeneration }
}
export function isEnvironmentSessionCurrent(session: ReturnType<typeof captureEnvironmentSession>) {
  return session.generation === sessionGeneration && session.environmentId === getEnvironmentId()
}
export function assertEnvironmentSession(session: ReturnType<typeof captureEnvironmentSession>) {
  if (!isEnvironmentSessionCurrent(session)) throw new Error('AUTH_ENVIRONMENT_CHANGED')
}
export function activateOfflineAccount(accountId: number) {
  const environmentId = getEnvironmentId()
  if (activeAccount?.accountId !== accountId || activeAccount.environmentId !== environmentId) {
    sessionGeneration += 1
    activeScope = null
  }
  activeAccount = { environmentId, accountId }
}
export function invalidateOfflineSession() {
  sessionGeneration += 1
  activeAccount = null
  activeScope = null
}
export function activateOfflineStore(scope: OfflineScope) {
  if (scope.environmentId !== getEnvironmentId() || activeAccount?.accountId !== scope.accountId
    || activeAccount.environmentId !== scope.environmentId) throw new Error('OFFLINE_SCOPE_INACTIVE')
  const registered = { ...scope }
  activeScope = registered
  window.dispatchEvent(new Event('restaurant-offline-scope-updated'))
  return () => {
    if (activeScope === registered) {
      activeScope = null
      window.dispatchEvent(new Event('restaurant-offline-scope-updated'))
    }
  }
}
export function canProcessOfflineRecord(record: OfflineScope) {
  return record.environmentId === getEnvironmentId() && activeAccount?.environmentId === record.environmentId
    && activeAccount.accountId === record.accountId && activeScope != null && matchesOfflineScope(record, activeScope)
}
