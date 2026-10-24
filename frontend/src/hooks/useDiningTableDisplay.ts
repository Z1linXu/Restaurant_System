import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { BackendDiningTableConfig } from '../types/dinein'
import type { MenuCacheScope } from '../offline/menuCache'
import { canUseOfflineSnapshot } from '../offline/offlineFallbackPolicy'
import { readDiningTableSnapshot, saveDiningTableSnapshot } from '../offline/workspaceSnapshot'
import { fetchDiningTables } from '../services/frontdeskConfigService'
import { findConfiguredTable, formatDiningTableLabel } from '../utils/tableDisplay'

/** Display configuration only; raw route/draft/order table identities never enter this cache. */
export function useDiningTableDisplay(storeId: number, scope: MenuCacheScope | null) {
  const accountId = scope?.accountId ?? null
  const organizationId = scope?.organizationId ?? null
  const key = `${accountId}:${organizationId}:${storeId}`
  const [loaded, setLoaded] = useState<{ key: string; tables: BackendDiningTableConfig[] } | null>(null)
  const suppliedGeneration = useRef(0)
  const rememberTables = useCallback((loadedStoreId: number, tables: BackendDiningTableConfig[]) => {
    if (loadedStoreId !== storeId) return
    suppliedGeneration.current += 1
    const scopedTables = tables.filter((table) => table.store_id === storeId)
    setLoaded({ key, tables: scopedTables })
    if (accountId != null && organizationId != null) {
      void saveDiningTableSnapshot({ accountId, organizationId, storeId }, scopedTables).catch(() => undefined)
    }
  }, [accountId, key, organizationId, storeId])

  useEffect(() => {
    let active = true
    let generation = 0
    const cacheScope = accountId != null && organizationId != null ? { accountId, organizationId, storeId } : null
    const load = async () => {
      const request = ++generation
      const supplied = suppliedGeneration.current
      const cached = cacheScope ? await readDiningTableSnapshot(cacheScope).catch(() => null) : null
      if (!active || request !== generation || supplied !== suppliedGeneration.current) return
      if (cached) setLoaded({ key, tables: cached })
      if (typeof navigator !== 'undefined' && !navigator.onLine) return
      try {
        const tables = (await fetchDiningTables(storeId)).filter((table) => table.store_id === storeId)
        if (!active || request !== generation || supplied !== suppliedGeneration.current) return
        setLoaded({ key, tables })
        if (cacheScope) await saveDiningTableSnapshot(cacheScope, tables).catch(() => undefined)
      } catch (error) {
        if (active && request === generation && supplied === suppliedGeneration.current && !canUseOfflineSnapshot(error)) setLoaded({ key, tables: [] })
      }
    }
    void load()
    window.addEventListener('online', load)
    return () => { active = false; window.removeEventListener('online', load) }
  }, [accountId, organizationId, storeId, key])

  const tables = useMemo(() => loaded?.key === key ? loaded.tables : [], [key, loaded])
  const formatLabel = useCallback((value: string | null | undefined) => formatDiningTableLabel(value, tables), [tables])
  const baseLabel = useCallback((rawSlot: string, fallback: string) => {
    const table = findConfiguredTable(rawSlot, tables)
    return table ? table.table_name || table.table_code : fallback
  }, [tables])
  return { formatLabel, baseLabel, rememberTables }
}
