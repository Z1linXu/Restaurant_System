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
    const loadContext = (showLoading: boolean, preferOfflineSnapshot: boolean) => {
      if (showLoading) {
        setLoading(true)
      }
      setError(null)
      fetchStoreContext(storeId, user?.id, { preferOfflineSnapshot })
        .then((response) => {
          if (!active) return
          setData(response)
        })
        .catch((exception) => {
          if (!active) return
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

function StoreModuleUnavailablePage({
  access,
  storeName,
}: {
  access: StoreModuleAccessResult
  storeName: string
}) {
  const isEnvironmentGap = access.status === 'MODULE_ENVIRONMENT_CAPABILITY_MISSING'
  const isHardwareGap = access.status === 'MODULE_HARDWARE_CAPABILITY_MISSING'
  const isPrinting = access.moduleKey === 'PRINTING'
  const printingMode = access.module?.legacy_runtime_mode ?? null
  const legacyPrintingFlag = access.module?.legacy_store_flag
  return (
    <div className="min-h-screen bg-[var(--surface)] px-6 py-6 text-[var(--on-surface)]">
      <div className="mx-auto flex min-h-[calc(100vh-3rem)] max-w-[980px] items-center justify-center">
        <div className="w-full rounded-[30px] bg-[rgba(255,255,255,0.88)] px-7 py-8 shadow-[0_18px_42px_rgba(26,28,25,0.07)]">
          <div className="text-[0.78rem] font-black uppercase tracking-[0.2em] text-[var(--primary)]">
            Store module gate
          </div>
          <h1 className="mt-3 font-display text-[2.35rem] font-extrabold tracking-[-0.07em] text-[var(--on-surface)]">
            {isEnvironmentGap ? '运行环境未就绪' : isHardwareGap ? '硬件能力未就绪' : '功能未启用'}
          </h1>
          <p className="mt-3 max-w-[660px] text-[1rem] leading-7 text-[var(--muted)]">
            <span className="font-semibold text-[var(--on-surface)]">{storeName}</span>
            {' '}当前不能打开{' '}
            <span className="font-semibold text-[var(--on-surface)]">{access.displayName}</span>
            {' '}页面。前端已从已认证的 Store Context 读取模块状态，并按 Store-scoped module contract fail closed。
          </p>

          <div className="mt-5 grid gap-3 sm:grid-cols-2">
            <ModuleStatusCard label="Module" value={access.moduleKey} />
            <ModuleStatusCard label="Status" value={access.status} />
            {isPrinting ? <ModuleStatusCard label="Runtime print mode" value={printingMode ?? 'UNKNOWN'} /> : null}
            {isPrinting ? (
              <ModuleStatusCard
                label="Legacy printing flag"
                value={legacyPrintingFlag == null ? 'UNKNOWN' : legacyPrintingFlag ? 'ENABLED' : 'DISABLED'}
              />
            ) : null}
          </div>

          <div className="mt-5 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-3 text-[0.9rem] font-semibold leading-6 text-[var(--muted)]">
            {access.message}
            {access.issues.length ? (
              <ul className="mt-3 space-y-1">
                {access.issues.map((issue) => (
                  <li key={`${issue.code}:${issue.module_key ?? ''}:${issue.target ?? ''}`}>
                    {issue.code}{issue.target ? ` → ${issue.target}` : ''}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  )
}

function ModuleStatusCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-3">
      <div className="text-[0.72rem] font-black uppercase tracking-[0.16em] text-[var(--muted)]">{label}</div>
      <div className="mt-1 break-all text-[0.95rem] font-bold text-[var(--on-surface)]">{value}</div>
    </div>
  )
}
