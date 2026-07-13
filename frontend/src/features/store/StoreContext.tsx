import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { fetchStoreContext, type StoreContextResponse } from '../../services/storeWorkspaceService'
import { getApiUserMessage } from '../../services/apiClient'
import { useAuth } from '../auth/useAuth'

interface StoreContextValue {
  storeId: number
  storeName: string
  storeCode: string | null
  organizationId: number | null
  organizationName: string | null
  roleCode: string | null
  loading: boolean
  error: string | null
}

const StoreContext = createContext<StoreContextValue | null>(null)

function mapStoreContext(storeId: number, data: StoreContextResponse | null, loading: boolean, error: string | null): StoreContextValue {
  return {
    storeId,
    storeName: data?.name ?? `门店 ${storeId}`,
    storeCode: data?.code ?? null,
    organizationId: data?.organization_id ?? null,
    organizationName: data?.organization_name ?? null,
    roleCode: data?.role_code ?? null,
    loading,
    error,
  }
}

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

export function useCurrentStore() {
  const value = useContext(StoreContext)
  if (!value) {
    throw new Error('useCurrentStore must be used inside StoreContextProvider')
  }
  return value
}

export function useOptionalCurrentStore() {
  return useContext(StoreContext)
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
