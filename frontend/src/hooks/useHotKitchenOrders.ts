import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchKitchenOrderDetail, fetchNoodleDisplayTasks, subscribeToNoodleDisplay } from '../services/kdsService'
import type { NoodleStationOrder } from '../types/kds'

const POLL_INTERVAL_MS = 4000

function filterHotKitchenTasks(tasks: NoodleStationOrder['tasks']) {
  return tasks.filter(
    (task) =>
      (task.station_code === 'WOK' || task.station_code === 'DEEPFRIED')
      && !['ready_for_pickup', 'served', 'done'].includes(task.status),
  )
}

export function useHotKitchenOrders(storeId = 1) {
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
      const tasks = filterHotKitchenTasks(await fetchNoodleDisplayTasks(storeId))
      const orderIds = Array.from(new Set(tasks.map((task) => task.order_id)))

      if (orderIds.length === 0) {
        setOrders([])
        return
      }

      const details = await Promise.all(orderIds.map((orderId) => fetchKitchenOrderDetail(orderId)))
      const detailedOrders = details
        .map((detail) => ({
          detail,
          tasks: tasks.filter((task) => task.order_id === detail.id),
        }))
        .filter((order) => order.tasks.length > 0)
        .sort((left, right) => {
          const leftTime = left.tasks.map((task) => task.created_at).sort()[0] ?? left.detail.created_at ?? ''
          const rightTime = right.tasks.map((task) => task.created_at).sort()[0] ?? right.detail.created_at ?? ''
          return leftTime.localeCompare(rightTime)
        })

      setOrders(detailedOrders)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load hot kitchen orders')
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [storeId])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const unsubscribe = subscribeToNoodleDisplay(storeId, () => {
      void load(true)
    })

    const timer = window.setInterval(() => {
      void load(true)
    }, POLL_INTERVAL_MS)

    return () => {
      unsubscribe()
      window.clearInterval(timer)
    }
  }, [load, storeId])

  const activeTaskCount = useMemo(
    () => orders.reduce((sum, order) => sum + order.tasks.length, 0),
    [orders],
  )

  return {
    orders,
    loading,
    refreshing,
    error,
    activeTaskCount,
    refresh: () => load(true),
  }
}
