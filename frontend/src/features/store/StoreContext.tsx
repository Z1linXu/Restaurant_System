import { activateOfflineStore, getEnvironmentId } from '../../offline/environmentScope'
import { useEffect, useMemo, useState } from 'react'
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
  const [data, setData] = useState<StoreContextResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    let releaseOfflineScope: (() => void) | undefined
    const loadContext = (showLoading: boolean, preferOfflineSnapshot: boolean) => {
      if (showLoading) {
        setLoading(true)
      }
      setError(null)
      fetchStoreContext(storeId, user?.id, { preferOfflineSnapshot })
        .then((response) => {
          if (!active) return
          releaseOfflineScope?.()
          if (user?.id && response.id === storeId && response.organization_id != null) {
            releaseOfflineScope = activateOfflineStore({ environmentId: getEnvironmentId(), accountId: user.id, organizationId: response.organization_id, storeId })
          }
          setData(response)
        })
        .catch((exception) => {
          if (!active) return
          releaseOfflineScope?.()
          setData(null)
          setError(getApiUserMessage(exception, '你没有权限访问这家门店。'))
        })
        .finally(() => {
          if (active) setLoading(false)
        })
    }

    loadContext(true, isOfflineRestricted)
    const handleOnline = () => loadContext(false, false)
    window.addEventListener('online', handleOnline)
    return () => {
      active = false
      releaseOfflineScope?.()
      window.removeEventListener('online', handleOnline)
    }
  }, [isOfflineRestricted, storeId, user?.id])

  const value = useMemo(() => mapStoreContext(storeId, data, loading, error), [data, error, loading, storeId])
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
