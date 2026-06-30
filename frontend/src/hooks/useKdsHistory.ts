import { useCallback, useEffect, useState } from 'react'
import { fetchKdsHistory } from '../services/kdsService'
import type { BackendKdsHistoryOrder } from '../types/kds'

export function useKdsHistory(storeId: number, stationCode?: string) {
  const [orders, setOrders] = useState<BackendKdsHistoryOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)

    try {
      const data = await fetchKdsHistory(storeId, stationCode)
      setOrders(data)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load KDS history')
    } finally {
      setLoading(false)
    }
  }, [stationCode, storeId])

  useEffect(() => {
    void load()
  }, [load])

  return {
    orders,
    loading,
    error,
    refresh: load,
  }
}
