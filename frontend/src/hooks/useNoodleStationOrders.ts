import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchKitchenOrderDetail, fetchNoodleDisplayTasks } from '../services/kdsService'
import type { NoodleStationOrder } from '../types/kds'

const POLL_INTERVAL_MS = 4000

export function useNoodleStationOrders(storeId: number) {
  const [orders, setOrders] = useState<NoodleStationOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (isRefresh = false) => {
    if (isRefresh) {
      setRefreshing(true)
    } else {
      setLoading(true)
    }
    setError(null)

    try {
      const tasks = await fetchNoodleDisplayTasks(storeId)
      const orderIds = Array.from(new Set(tasks.map((task) => task.order_id)))
      const details = await Promise.all(orderIds.map((orderId) => fetchKitchenOrderDetail(orderId)))

      const detailedOrders = details
        .map((detail) => ({
          detail,
          tasks: tasks.filter((task) => task.order_id === detail.id),
        }))
        .filter((order) => order.tasks.length > 0)

      setOrders(detailedOrders)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load kitchen orders')
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [storeId])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const timer = window.setInterval(() => {
      void load(true)
    }, POLL_INTERVAL_MS)

    return () => window.clearInterval(timer)
  }, [load])

  const hasOrders = useMemo(() => orders.length > 0, [orders])

  return {
    orders,
    loading,
    refreshing,
    error,
    hasOrders,
    refresh: () => load(true),
  }
}
