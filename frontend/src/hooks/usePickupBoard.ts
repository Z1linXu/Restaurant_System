import { useEffect, useMemo, useState } from 'react'
import { fetchServingShelf, markShelfItemServed, subscribeToServingShelf } from '../services/pickupService'
import type { BackendServingShelfItem } from '../types/kds'

const PICKUP_REFRESH_INTERVAL_MS = 4000

export interface PickupBoardOrder {
  orderId: number
  orderNo: string
  orderType: string
  tableNo: string | null
  pickupNo: string | null
  readyAt: string | null
  items: BackendServingShelfItem[]
}

function sortItems(items: BackendServingShelfItem[]) {
  return [...items].sort((a, b) => {
    const aTime = a.ready_for_pickup_at ?? a.created_at
    const bTime = b.ready_for_pickup_at ?? b.created_at
    return aTime.localeCompare(bTime)
  })
}

function buildOrderMap(items: BackendServingShelfItem[]) {
  const nextMap = new Map<number, PickupBoardOrder>()
  sortItems(items).forEach((item) => {
    const existing = nextMap.get(item.order_id)
    const readyAt = item.ready_for_pickup_at ?? item.created_at
    if (!existing) {
      nextMap.set(item.order_id, {
        orderId: item.order_id,
        orderNo: item.order_no,
        orderType: item.order_type,
        tableNo: item.table_no,
        pickupNo: item.pickup_no,
        readyAt,
        items: [item],
      })
      return
    }
    existing.items.push(item)
    if ((existing.readyAt ?? readyAt) > readyAt) {
      existing.readyAt = readyAt
    }
  })

  return nextMap
}

export function usePickupBoard(storeId: number) {
  const [ordersMap, setOrdersMap] = useState<Map<number, PickupBoardOrder>>(new Map())
  const [busyTaskIds, setBusyTaskIds] = useState<Set<number>>(new Set())
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setError(null)

    const refreshShelf = async () => {
      try {
        const items = await fetchServingShelf(storeId)
        const relevantItems = items.filter((item) => item.order_type !== 'delivery')
        setOrdersMap(buildOrderMap(relevantItems))
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : 'Failed to load pickup board')
      }
    }

    void refreshShelf()

    const intervalId = window.setInterval(() => {
      void refreshShelf()
    }, PICKUP_REFRESH_INTERVAL_MS)

    const unsubscribe = subscribeToServingShelf(storeId, (message) => {
      if (!message.order_id) {
        return
      }
      if (!['kitchen_task.ready_for_pickup', 'kitchen_task.served'].includes(message.event_type)) {
        return
      }

      void refreshShelf()
    })

    return () => {
      window.clearInterval(intervalId)
      unsubscribe()
    }
  }, [storeId])

  const orders = useMemo(
    () =>
      [...ordersMap.values()].sort((a, b) => {
        const aTime = a.readyAt ?? ''
        const bTime = b.readyAt ?? ''
        return aTime.localeCompare(bTime)
      }),
    [ordersMap],
  )

  const completeItem = async (taskId: number) => {
    setBusyTaskIds((current) => new Set(current).add(taskId))
    setError(null)
    const targetOrder = [...ordersMap.values()].find((order) => order.items.some((item) => item.task_id === taskId))

    setOrdersMap((current) => {
      const next = new Map(current)
      if (!targetOrder) {
        return next
      }
      const order = next.get(targetOrder.orderId)
      if (!order) {
        return next
      }
      const remainingItems = order.items.filter((item) => item.task_id !== taskId)
      if (!remainingItems.length) {
        next.delete(order.orderId)
        return next
      }
      next.set(order.orderId, {
        ...order,
        items: remainingItems,
        readyAt: remainingItems.map((item) => item.ready_for_pickup_at ?? item.created_at).sort()[0] ?? order.readyAt,
      })
      return next
    })

    try {
      await markShelfItemServed(taskId)
    } catch (completeError) {
      setError(completeError instanceof Error ? completeError.message : 'Failed to complete item')
      if (targetOrder) {
        setOrdersMap((current) => {
          const next = new Map(current)
          next.set(targetOrder.orderId, targetOrder)
          return next
        })
      }
    } finally {
      setBusyTaskIds((current) => {
        const next = new Set(current)
        next.delete(taskId)
        return next
      })
    }
  }

  const completeAll = async (orderId: number) => {
    const order = ordersMap.get(orderId)
    if (!order) {
      return
    }
    for (const item of order.items) {
      // sequential to keep UI/state simple for MVP
      // eslint-disable-next-line no-await-in-loop
      await completeItem(item.task_id)
    }
  }

  return {
    orders,
    busyTaskIds,
    error,
    completeItem,
    completeAll,
  }
}
