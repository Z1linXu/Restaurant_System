import { useEffect, useMemo, useState } from 'react'
import { navigateTo } from '../frontdesk/navigation'
import { fetchWorkspaces, type WorkspaceStore } from '../../services/storeWorkspaceService'
import { replaceStoreId } from './storeRoutes'
import { useOptionalCurrentStore } from './StoreContext'
import { useAuth } from '../auth/useAuth'

export function StoreSwitcher({ compact = false }: { compact?: boolean }) {
  const currentStore = useOptionalCurrentStore()
  const { isOwner, isManager } = useAuth()
  const [stores, setStores] = useState<WorkspaceStore[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    fetchWorkspaces()
      .then((response) => {
        if (!active) return
        setStores(response.stores ?? [])
        setError(null)
      })
      .catch((exception) => {
        if (!active) return
        setError(exception instanceof Error ? exception.message : 'Unable to load stores')
      })
    return () => {
      active = false
    }
  }, [])

  const selectedStoreId = currentStore?.storeId ?? stores[0]?.id ?? ''
  const label = useMemo(() => {
    const matched = stores.find((store) => store.id === selectedStoreId)
    return currentStore?.storeName ?? matched?.name ?? 'Store'
  }, [currentStore?.storeName, selectedStoreId, stores])

  if (error) {
    return <div className="text-[0.78rem] font-bold text-red-700">Store unavailable</div>
  }

  if (stores.length <= 1) {
    return (
      <div className={`rounded-[16px] bg-[rgba(26,28,25,0.05)] font-bold text-[var(--muted)] ${compact ? 'px-3 py-2 text-[0.76rem]' : 'px-4 py-2 text-[0.86rem]'}`}>
        {label}
      </div>
    )
  }

  return (
    <div className="inline-flex flex-wrap items-center gap-2">
      <label className="inline-flex items-center gap-2 rounded-[16px] bg-[rgba(26,28,25,0.05)] px-3 py-2">
        <span className="text-[0.72rem] font-black uppercase tracking-[0.12em] text-[var(--muted)]">Store</span>
        <select
          value={selectedStoreId}
          onChange={(event) => {
            const nextStoreId = Number(event.target.value)
            if (!nextStoreId) return
            navigateTo(replaceStoreId(window.location.pathname, nextStoreId) + window.location.search)
          }}
          className="bg-transparent text-[0.86rem] font-extrabold outline-none"
        >
          {stores.map((store) => (
            <option key={store.id} value={store.id}>
              {store.name}
            </option>
          ))}
        </select>
      </label>
      {(isOwner || isManager) ? (
        <button
          type="button"
          onClick={() => navigateTo('/owner/dashboard')}
          className="rounded-[16px] bg-[rgba(97,0,0,0.07)] px-3 py-2 text-[0.76rem] font-black text-[var(--primary)]"
        >
          Owner Home
        </button>
      ) : null}
    </div>
  )
}
