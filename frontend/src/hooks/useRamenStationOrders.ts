import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchKitchenOrderDetail, fetchNoodleDisplayTasks, subscribeToNoodleDisplay } from '../services/kdsService'
import type { BackendKdsTaskDisplay, NoodleStationOrder } from '../types/kds'

const POLL_INTERVAL_MS = 4000

function filterRamenTasks(tasks: BackendKdsTaskDisplay[]) {
  return tasks.filter((task) => task.station_code === 'NOODLE' || task.station_code === 'WOK')
}

export function useRamenStationOrders(storeId = 1) {
  const [orders, setOrders] = useState<NoodleStationOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError(null)

    try {
      const allTasks = await fetchNoodleDisplayTasks(storeId)
      const ramenTasks = filterRamenTasks(allTasks)
      const orderIds = Array.from(new Set(ramenTasks.map((task) => task.order_id)))

      if (orderIds.length === 0) {
        setOrders([])
        setLoading(false)
        return
      }

      const details = await Promise.all(orderIds.map((orderId) => fetchKitchenOrderDetail(orderId)))
      const detailedOrders = details
        .map((detail) => ({
          detail,
          tasks: ramenTasks.filter((task) => task.order_id === detail.id),
        }))
        .filter((order) => order.tasks.length > 0)
        .sort((left, right) => {
          const leftTime = left.tasks.map((task) => task.created_at).sort()[0] ?? ''
          const rightTime = right.tasks.map((task) => task.created_at).sort()[0] ?? ''
          return leftTime.localeCompare(rightTime)
        })

      setOrders(detailedOrders)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load ramen station orders')
    } finally {
      setLoading(false)
    }
  }, [storeId])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const unsubscribe = subscribeToNoodleDisplay(storeId, () => {
      void load()
    })

    const timer = window.setInterval(() => {
      void load()
    }, POLL_INTERVAL_MS)

    return () => {
      unsubscribe()
      window.clearInterval(timer)
    }
  }, [load, storeId])

  const hasOrders = useMemo(() => orders.length > 0, [orders])

  return {
    orders,
    loading,
    error,
    hasOrders,
  }
}
