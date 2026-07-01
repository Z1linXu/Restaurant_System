import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { dineInService } from '../services/dineInService'
import { fetchDiningTables } from '../services/frontdeskConfigService'
import { fetchActiveOrderBoardForStore, subscribeToFrontdeskOrders } from '../services/orderService'
import type { BackendDiningTableConfig, DiningTable, TableSeatCode, TableSlot, TableStatus } from '../types/dinein'

const initialData = dineInService.getInitialData()
const SEAT_CODES: TableSeatCode[] = ['A', 'B']
const FRONTDESK_POLL_INTERVAL_MS = 30_000
const FRONTDESK_WS_DEBOUNCE_MS = 500

function stripOccupancy(table: DiningTable): DiningTable {
  return {
    ...table,
    occupancyMode: 'empty',
    fullOrder: undefined,
    splitOrders: undefined,
    alertMessage: undefined,
  }
}

function createBaseTables() {
  return initialData.tables.map(stripOccupancy)
}

function mapBackendDiningTable(table: BackendDiningTableConfig): DiningTable {
  return {
    id: table.id,
    label: table.table_name || table.table_code,
    seats: table.capacity,
    zone: table.area_name,
    tableConfig: table.table_config,
    occupancyMode: 'empty',
  }
}

function buildSlots(tables: DiningTable[]) {
  return tables.flatMap<TableSlot>((table) => {
    if (table.occupancyMode === 'empty') {
      return [
        {
          id: `${table.label}-full`,
          label: table.label,
          baseTableLabel: table.label,
          zone: table.zone,
          status: 'available',
          action: table.tableConfig === 'split_supported' ? 'entry' : 'start',
          mode: 'full',
        },
      ]
    }

    if (table.occupancyMode === 'full') {
      return [
        {
          id: `${table.label}-full`,
          label: table.label,
          baseTableLabel: table.label,
          zone: table.zone,
          status: table.alertMessage ? 'alert' : 'occupied',
          action: 'edit',
          mode: 'full',
          orderId: table.fullOrder?.orderId,
          orderDbId: table.fullOrder?.orderDbId,
          orderStatus: table.fullOrder?.orderStatus,
          backendTableNo: table.fullOrder?.backendTableNo,
          alertMessage: table.alertMessage,
        },
      ]
    }

    return SEAT_CODES.map((seatCode) => {
      const order = table.splitOrders?.[seatCode]

      return {
        id: `${table.label}-${seatCode}`,
        label: `${table.label}-${seatCode}`,
        baseTableLabel: table.label,
        zone: table.zone,
        status: order && table.alertMessage ? 'alert' : order ? 'occupied' : 'available',
        action: order ? 'edit' : 'start',
        mode: 'split',
        orderId: order?.orderId,
        orderDbId: order?.orderDbId,
        orderStatus: order?.orderStatus,
        backendTableNo: order?.backendTableNo,
        seatCode,
        alertMessage: order ? table.alertMessage : undefined,
      }
    })
  })
}

function orderPriority(order: Awaited<ReturnType<typeof fetchActiveOrderBoardForStore>>[number]) {
  const draftPenalty = order.order_status === 'draft' ? 0 : 1000
  const itemWeight = order.total_item_count ?? 0
  const readyWeight =
    order.order_status === 'ready' ? 30 : order.order_status === 'preparing' ? 20 : order.order_status === 'submitted' ? 10 : 0
  return draftPenalty + itemWeight * 10 + readyWeight
}

interface UseTableBoardOptions {
  enabled?: boolean
  storeId: number
}

export function useTableBoard(options: UseTableBoardOptions) {
  const enabled = options.enabled ?? true
  const storeId = options.storeId
  const [tables, setTables] = useState<DiningTable[]>(createBaseTables)
  const [syncError, setSyncError] = useState<string | null>(null)
  const [isOnline, setIsOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine))
  const syncInFlightRef = useRef(false)
  const syncPendingRef = useRef(false)
  const wsRefreshTimeoutRef = useRef<number | null>(null)
  const enabledRef = useRef(enabled)

  useEffect(() => {
    enabledRef.current = enabled
    if (!enabled) {
      syncPendingRef.current = false
      if (wsRefreshTimeoutRef.current !== null) {
        window.clearTimeout(wsRefreshTimeoutRef.current)
        wsRefreshTimeoutRef.current = null
      }
    }
  }, [enabled])

  const hydrateBaseTables = useCallback(async () => {
    try {
      const diningTables = await fetchDiningTables(storeId)
      if (!diningTables.length) {
        return createBaseTables()
      }
      return diningTables.map(mapBackendDiningTable)
    } catch (_error) {
      return createBaseTables()
    }
  }, [storeId])

  const deriveTablesFromActiveOrders = useCallback(
    (baseTables: DiningTable[], activeOrders: Awaited<ReturnType<typeof fetchActiveOrderBoardForStore>>): DiningTable[] =>
      baseTables.map<DiningTable>((table) => {
        const matchingOrders = activeOrders
          .filter((order) => {
            const tableNo = order.table_no ?? ''
            return tableNo === table.label || tableNo.startsWith(`${table.label}-`)
          })
          .sort((left, right) => orderPriority(right) - orderPriority(left))

        if (matchingOrders.length === 0) {
          return table
        }

        const fullOrder = matchingOrders.find((order) => (order.table_no ?? '') === table.label)
        const fallbackSingleOnlyOrder = table.tableConfig === 'single_only' ? matchingOrders[0] : null
        const selectedFullOrder = fullOrder ?? fallbackSingleOnlyOrder

        if (selectedFullOrder) {
          return {
            ...table,
            occupancyMode: 'full',
            fullOrder: {
              orderId: selectedFullOrder.order_no,
              orderDbId: selectedFullOrder.order_id,
              orderStatus: selectedFullOrder.order_status,
              backendTableNo: selectedFullOrder.table_no ?? table.label,
            },
          }
        }

        const splitOrders: Partial<Record<TableSeatCode, { orderId: string; orderDbId?: number; orderStatus?: string; backendTableNo?: string }>> = {}
        matchingOrders.forEach((order) => {
          const tableNo = order.table_no ?? ''
          const seatCode = tableNo.split('-')[1]
          if ((seatCode === 'A' || seatCode === 'B') && !splitOrders[seatCode]) {
            splitOrders[seatCode] = {
              orderId: order.order_no,
              orderDbId: order.order_id,
              orderStatus: order.order_status,
              backendTableNo: tableNo,
            }
          }
        })

        if (Object.keys(splitOrders).length === 0) {
          return table
        }

        return {
          ...table,
          occupancyMode: 'split',
          splitOrders,
        }
      }),
    [],
  )

  const syncFromBackend = useCallback(async (options?: { force?: boolean }) => {
    if (!enabledRef.current) {
      syncPendingRef.current = false
      return
    }

    if (!options?.force && document.visibilityState !== 'visible') {
      return
    }

    if (syncInFlightRef.current) {
      syncPendingRef.current = true
      return
    }

    syncInFlightRef.current = true
    try {
      do {
        syncPendingRef.current = false
        const [baseTables, activeOrders] = await Promise.all([hydrateBaseTables(), fetchActiveOrderBoardForStore(storeId)])
        if (!enabledRef.current) {
          syncPendingRef.current = false
          return
        }
        setSyncError(null)
        setTables(deriveTablesFromActiveOrders(baseTables, activeOrders))
      } while (enabledRef.current && syncPendingRef.current && (options?.force || document.visibilityState === 'visible'))
    } catch (error) {
      setSyncError(error instanceof Error ? error.message : 'Unable to sync table board')
    } finally {
      syncInFlightRef.current = false
    }
  }, [deriveTablesFromActiveOrders, hydrateBaseTables, storeId])

  const refreshTableAfterFinish = useCallback(
    async (baseTableLabel: string) => {
      const [baseTables, activeOrders] = await Promise.all([hydrateBaseTables(), fetchActiveOrderBoardForStore(storeId)])
      const nextTables = deriveTablesFromActiveOrders(baseTables, activeOrders)
      const hasRemainingSeatOrders = activeOrders.some((order) => {
        const tableNo = order.table_no ?? ''
        return tableNo === baseTableLabel || tableNo.startsWith(`${baseTableLabel}-`)
      })

      setTables(
        nextTables.map((table) => {
          if (table.label !== baseTableLabel) {
            return table
          }

          if (!hasRemainingSeatOrders) {
            return stripOccupancy(table)
          }

          return table
        }),
      )
    },
    [deriveTablesFromActiveOrders, hydrateBaseTables, storeId],
  )

  useEffect(() => {
    if (!enabled) {
      return
    }

    void syncFromBackend({ force: true })
  }, [enabled, syncFromBackend])

  useEffect(() => {
    if (!enabled) {
      return () => undefined
    }

    const scheduleWebSocketRefresh = () => {
      if (!enabledRef.current) {
        return
      }
      if (document.visibilityState !== 'visible') {
        return
      }

      if (wsRefreshTimeoutRef.current !== null) {
        window.clearTimeout(wsRefreshTimeoutRef.current)
      }

      wsRefreshTimeoutRef.current = window.setTimeout(() => {
        wsRefreshTimeoutRef.current = null
        if (!enabledRef.current) {
          return
        }
        void syncFromBackend()
      }, FRONTDESK_WS_DEBOUNCE_MS)
    }

    const unsubscribe = subscribeToFrontdeskOrders(storeId, () => {
      scheduleWebSocketRefresh()
    })

    const poller = window.setInterval(() => {
      if (!enabledRef.current) {
        return
      }
      if (document.visibilityState !== 'visible') {
        return
      }
      void syncFromBackend()
    }, FRONTDESK_POLL_INTERVAL_MS)

    const handleVisibilityChange = () => {
      if (!enabledRef.current) {
        return
      }
      if (document.visibilityState !== 'visible') {
        if (wsRefreshTimeoutRef.current !== null) {
          window.clearTimeout(wsRefreshTimeoutRef.current)
          wsRefreshTimeoutRef.current = null
        }
        return
      }

      void syncFromBackend({ force: true })
    }

    const handleOnline = () => {
      setIsOnline(true)
      if (enabledRef.current && document.visibilityState === 'visible') {
        void syncFromBackend({ force: true })
      }
    }

    const handleOffline = () => {
      setIsOnline(false)
      setSyncError('当前设备离线，请检查网络后重试 / Device is offline. Please check the network and try again.')
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)

    return () => {
      unsubscribe()
      window.clearInterval(poller)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
      if (wsRefreshTimeoutRef.current !== null) {
        window.clearTimeout(wsRefreshTimeoutRef.current)
        wsRefreshTimeoutRef.current = null
      }
    }
  }, [enabled, storeId, syncFromBackend])

  const tableSlots = useMemo(() => buildSlots(tables), [tables])

  const statusCounts = useMemo(
    () =>
      tableSlots.reduce<Record<TableStatus, number>>(
        (accumulator, slot) => {
          accumulator[slot.status] += 1
          return accumulator
        },
        { available: 0, occupied: 0, alert: 0 },
      ),
    [tableSlots],
  )

  const startOrder = (slotId: string, selection?: 'left' | 'right' | 'full') => {
    const slot = tableSlots.find((item) => item.id === slotId)
    if (!slot) {
      return null
    }

    return {
      slotId: slot.id,
      slotLabel:
        slot.action === 'entry'
          ? selection === 'left'
            ? `${slot.baseTableLabel}-A`
            : selection === 'right'
              ? `${slot.baseTableLabel}-B`
              : slot.baseTableLabel
          : slot.backendTableNo ?? slot.label,
      mode: slot.mode,
      selection: selection ?? null,
      orderId: null,
    }
  }

  const editOrder = (slotId: string) => {
    const slot = tableSlots.find((item) => item.id === slotId && item.action === 'edit')
    if (!slot) {
      return null
    }

    return {
      slotId: slot.id,
      slotLabel: slot.backendTableNo ?? slot.label,
      orderId: slot.orderId ?? 'UNKNOWN',
      orderDbId: slot.orderDbId,
      mode: slot.mode,
    }
  }

  const endOrder = (_tableLabel: string, _target: 'full' | TableSeatCode) => {
    setTables((currentTables) => currentTables)
  }

  return {
    tableSlots,
    statusCounts,
    syncError,
    isOnline,
    startOrder,
    editOrder,
    endOrder,
    refreshFromBackend: syncFromBackend,
    refreshTableAfterFinish,
  }
}
