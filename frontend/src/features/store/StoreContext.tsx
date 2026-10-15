import { activateOfflineStore, captureEnvironmentSession, isEnvironmentSessionCurrent, matchesOfflineScope, type OfflineScope } from '../../offline/environmentScope'
import { useEffect, useMemo, useRef, useState } from 'react'
import { fetchStoreContext, type StoreContextResponse } from '../../services/storeWorkspaceService'
import { getApiUserMessage } from '../../services/apiClient'
import { useAuth } from '../auth/useAuth'
import { StoreContext, mapStoreContext } from './StoreContextCore'
import { useCurrentStore } from './useStoreContext'
import {
  evaluateStoreModuleAccess,
  evaluateStoreModuleManagementAccess,
  type StoreModuleAccessResult,
  type StoreModuleKey,
} from './storeModuleAccess'

export function StoreContextProvider({ storeId, children }: { storeId: number; children: React.ReactNode }) {
  const { isOfflineRestricted, user } = useAuth()
  const offlineRestricted = useRef(isOfflineRestricted)
  useEffect(() => { offlineRestricted.current = isOfflineRestricted }, [isOfflineRestricted])
  const [data, setData] = useState<StoreContextResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [resultScopeKey, setResultScopeKey] = useState<string | null>(null)
  let environmentSession: ReturnType<typeof captureEnvironmentSession> | null = null
  try { environmentSession = captureEnvironmentSession() } catch { /* Missing server configuration stays closed. */ }
  const environmentId = environmentSession?.environmentId ?? ''
  const environmentGeneration = environmentSession?.generation ?? -1
  const scopeKey = `${environmentId}:${environmentGeneration}:${user?.id ?? 'anonymous'}:${storeId}`

  useEffect(() => {
    let active = true
    let request: AbortController | null = null
    let registeredScope: OfflineScope | undefined
    let releaseOfflineScope: (() => void) | undefined
    const session = { environmentId, generation: environmentGeneration }
    const releaseScope = () => {
      releaseOfflineScope?.()
      releaseOfflineScope = undefined
      registeredScope = undefined
    }
    const clearContext = (exception: unknown) => {
      releaseScope()
      setData(null)
      setResultScopeKey(scopeKey)
      setError(getApiUserMessage(exception, '你没有权限访问这家门店。'))
      setLoading(false)
    }
    const loadContext = (showLoading: boolean, preferOfflineSnapshot: boolean) => {
      if (!isEnvironmentSessionCurrent(session)) {
        request?.abort()
        clearContext(new Error('AUTH_ENVIRONMENT_CHANGED'))
        return
      }
      if (request) return
      const controller = new AbortController()
      request = controller
      if (showLoading) {
        setLoading(true)
      }
      setError(null)
      fetchStoreContext(storeId, user?.id, { preferOfflineSnapshot, signal: controller.signal })
        .then((response) => {
          if (!active || controller.signal.aborted) return
          if (!isEnvironmentSessionCurrent(session)) throw new Error('AUTH_ENVIRONMENT_CHANGED')
          if (response.id !== storeId) throw new Error('STORE_CONTEXT_SCOPE_MISMATCH')
          const nextScope = user?.id && response.organization_id != null
            ? { environmentId, accountId: user.id, organizationId: response.organization_id, storeId }
            : undefined
          if (nextScope && (!registeredScope || !matchesOfflineScope(nextScope, registeredScope))) {
            releaseScope()
            releaseOfflineScope = activateOfflineStore(nextScope)
            registeredScope = nextScope
          } else if (!nextScope) {
            releaseScope()
          }
          setData(response)
          setResultScopeKey(scopeKey)
        })
        .catch((exception) => {
          if (!active || controller.signal.aborted) return
          clearContext(exception)
        })
        .finally(() => {
          if (request === controller) request = null
          if (active && !controller.signal.aborted && isEnvironmentSessionCurrent(session)) setLoading(false)
        })
    }

    loadContext(true, offlineRestricted.current)
    const refreshForeground = () => {
      if ((typeof document === 'undefined' || document.visibilityState === 'visible')
        && (typeof navigator === 'undefined' || navigator.onLine !== false)) loadContext(false, false)
    }
    const timer = window.setInterval?.(refreshForeground, 60_000)
    window.addEventListener('online', refreshForeground)
    window.addEventListener('focus', refreshForeground)
    if (typeof document !== 'undefined') document.addEventListener('visibilitychange', refreshForeground)
    return () => {
      active = false
      request?.abort()
      releaseScope()
      if (timer != null) window.clearInterval(timer)
      window.removeEventListener('online', refreshForeground)
      window.removeEventListener('focus', refreshForeground)
      if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', refreshForeground)
    }
  }, [environmentGeneration, environmentId, scopeKey, storeId, user?.id])

  const value = useMemo(() => {
    const current = resultScopeKey === scopeKey
    return mapStoreContext(storeId, current ? data : null, !current || loading, current ? error : null)
  }, [data, error, loading, resultScopeKey, scopeKey, storeId])
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

export function RequireStoreAccess({ children }: { children: React.ReactNode }) {
  const store = useCurrentStore()
  if (store.loading) {
    return (
      <div className="min-h-screen bg-[var(--surface)] px-6 py-8 text-[var(--on-surface)]">
        <div className="mx-auto max-w-[760px] rounded-[30px] bg-white px-7 py-8 shadow-[0_22px_54px_rgba(26,28,25,0.1)]">
          <div className="text-[1rem] font-bold text-[var(--muted)]">正在加载门店工作区...</div>
        </div>
      </div>
    )
  }

  if (store.error) {
    return (
      <div className="min-h-screen bg-[var(--surface)] px-6 py-8 text-[var(--on-surface)]">
        <div className="mx-auto max-w-[760px] rounded-[30px] bg-white px-7 py-8 shadow-[0_22px_54px_rgba(26,28,25,0.1)]">
          <div className="text-[1.8rem] font-black tracking-[-0.05em]">无权访问</div>
          <div className="mt-2 text-[0.98rem] font-semibold text-[var(--muted)]">
            {store.error || '你没有权限访问这家门店。'}
          </div>
        </div>
      </div>
    )
  }

  return <>{children}</>
}

export function RequireStoreModule({
  children,
  moduleKey,
}: {
  children: React.ReactNode
  moduleKey: StoreModuleKey
}) {
  const store = useCurrentStore()
  const access = evaluateStoreModuleAccess(store.moduleConfiguration, moduleKey)
  if (!access.allowed) {
    return <StoreModuleUnavailablePage access={access} storeName={store.storeName} />
  }
  return <>{children}</>
}

export function RequireStoreModuleManagement({
  children,
  moduleKey,
}: {
  children: React.ReactNode
  moduleKey: StoreModuleKey
}) {
  const store = useCurrentStore()
  const access = evaluateStoreModuleManagementAccess(store.moduleConfiguration, moduleKey)
  if (!access.allowed) {
    return <StoreModuleUnavailablePage access={access} storeName={store.storeName} />
  }
  return <>{children}</>
}

export function RequireLiveStore({ children }: { children: React.ReactNode }) {
  const store = useCurrentStore()
  if (!store.isLive) {
    return (
      <div className="min-h-screen bg-[var(--surface)] px-6 py-8 text-[var(--on-surface)]">
        <div className="mx-auto max-w-[760px] rounded-[30px] bg-white px-7 py-8 shadow-[0_22px_54px_rgba(26,28,25,0.1)]">
          <div className="text-[1.8rem] font-black tracking-[-0.05em]">Store Not Live</div>
          <div className="mt-2 text-[0.98rem] font-semibold text-[var(--muted)]">
            {store.storeName} is not available for restaurant operations yet.
          </div>
        </div>
      </div>
    )
  }
  return <>{children}</>
}

function StoreModuleUnavailablePage({ access, storeName }: { access: StoreModuleAccessResult; storeName: string }) {
  const needsSetup = access.status === 'MODULE_HARDWARE_CAPABILITY_MISSING'
    || access.status === 'MODULE_ENVIRONMENT_CAPABILITY_MISSING'
  return (
    <div className="min-h-screen bg-[var(--surface)] px-6 py-8 text-[var(--on-surface)]">
      <div className="mx-auto max-w-[760px] rounded-[30px] bg-white px-7 py-8 shadow-[0_22px_54px_rgba(26,28,25,0.1)]">
        <h1 className="text-[1.8rem] font-black">{needsSetup ? '功能暂不可用' : '功能未启用'}</h1>
        <p className="mt-3 text-base leading-7 text-[var(--muted)]">
          {storeName} 当前无法使用 {access.displayName}。{needsSetup ? '请联系门店负责人完成设置。' : '请联系门店负责人。'}
        </p>
      </div>
    </div>
  )
}
