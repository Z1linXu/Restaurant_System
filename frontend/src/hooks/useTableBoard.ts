import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fetchDiningTables } from '../services/frontdeskConfigService'
import {
  fetchActiveOrderBoardForStore,
  subscribeToFrontdeskOrders,
  type FrontdeskRealtimeLifecycleEvent,
} from '../services/orderService'
import type { BackendDiningTableConfig, DiningTable, TableSeatCode, TableSlot, TableStatus } from '../types/dinein'

const SEAT_CODES: TableSeatCode[] = ['A', 'B']
const FRONTDESK_FALLBACK_POLL_INTERVAL_MS = 30_000
const FRONTDESK_CONNECTED_POLL_INTERVAL_MS = 120_000
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

export function mapBackendDiningTable(table: BackendDiningTableConfig): DiningTable {
  return {
    id: table.id,
    label: table.table_name || table.table_code,
    seats: table.capacity,
    zone: table.area_name,
    tableConfig: table.table_config,
    occupancyMode: 'empty',
  }
}

export function buildTableSlots(tables: DiningTable[]) {
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

export function visibleTableSlotsForStore(tables: DiningTable[], dataStoreId: number | null, currentStoreId: number) {
  return dataStoreId === currentStoreId ? buildTableSlots(tables) : []
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
  onOrderTerminal?: (orderId: number, status: string) => void | Promise<void>
}

export function useTableBoard(options: UseTableBoardOptions) {
  const enabled = options.enabled ?? true
  const storeId = options.storeId
  const [tables, setTables] = useState<DiningTable[]>([])
  const [tablesStoreId, setTablesStoreId] = useState<number | null>(null)
  const [syncError, setSyncError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(enabled)
  const [hasLoaded, setHasLoaded] = useState(false)
  const [isOnline, setIsOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine))
  const [isRealtimeConnected, setIsRealtimeConnected] = useState(false)
  const [realtimeStatus, setRealtimeStatus] = useState<FrontdeskRealtimeLifecycleEvent['phase'] | null>(null)
  const generationRef = useRef(0)
  const syncStateRef = useRef({ generation: 0, inFlight: false, pending: false })
  const wsRefreshTimeoutRef = useRef<number | null>(null)
  const pollTimeoutRef = useRef<number | null>(null)
  const enabledRef = useRef(enabled)
  const realtimeConnectedRef = useRef(false)
  const onOrderTerminalRef = useRef(options.onOrderTerminal)

  useEffect(() => {
    onOrderTerminalRef.current = options.onOrderTerminal
  }, [options.onOrderTerminal])

  useEffect(() => {
    enabledRef.current = enabled
    generationRef.current += 1
    syncStateRef.current = {
      generation: generationRef.current,
      inFlight: false,
      pending: false,
    }
    realtimeConnectedRef.current = false
    setIsRealtimeConnected(false)
    setRealtimeStatus(null)
    setTables([])
    setTablesStoreId(null)
    setSyncError(null)
    setHasLoaded(false)
    setIsLoading(enabled)

    if (wsRefreshTimeoutRef.current !== null) {
      window.clearTimeout(wsRefreshTimeoutRef.current)
      wsRefreshTimeoutRef.current = null
    }
    if (pollTimeoutRef.current !== null) {
      window.clearTimeout(pollTimeoutRef.current)
      pollTimeoutRef.current = null
    }
  }, [enabled, storeId])

  const hydrateBaseTables = useCallback(async () => {
    const diningTables = await fetchDiningTables(storeId)
    return diningTables.map(mapBackendDiningTable)
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
    const generation = generationRef.current
    const syncState = syncStateRef.current
    if (!enabledRef.current || syncState.generation !== generation) {
      return
    }

    if (!options?.force && document.visibilityState !== 'visible') {
      return
    }

    if (syncState.inFlight) {
      syncState.pending = true
      return
    }

    syncState.inFlight = true
    setIsLoading(true)
    try {
      do {
        syncState.pending = false
        const [baseTables, activeOrders] = await Promise.all([hydrateBaseTables(), fetchActiveOrderBoardForStore(storeId)])
        if (generationRef.current !== generation || !enabledRef.current || syncStateRef.current !== syncState) {
          return
        }
        setSyncError(null)
        setHasLoaded(true)
        setTables(deriveTablesFromActiveOrders(baseTables, activeOrders))
        setTablesStoreId(storeId)
      } while (generationRef.current === generation
        && enabledRef.current
        && syncState.pending
        && (options?.force || document.visibilityState === 'visible'))
    } catch (error) {
      if (generationRef.current === generation && enabledRef.current && syncStateRef.current === syncState) {
        setHasLoaded(true)
        setTablesStoreId(storeId)
        setSyncError(error instanceof Error ? error.message : 'Unable to sync table board')
      }
    } finally {
      if (syncStateRef.current === syncState) {
        syncState.inFlight = false
        setIsLoading(false)
      }
    }
  }, [deriveTablesFromActiveOrders, hydrateBaseTables, storeId])

  const refreshTableAfterFinish = useCallback(
    async (baseTableLabel: string) => {
      const generation = generationRef.current
      if (!enabledRef.current) return
      setIsLoading(true)
      try {
        const [baseTables, activeOrders] = await Promise.all([hydrateBaseTables(), fetchActiveOrderBoardForStore(storeId)])
        if (generationRef.current !== generation || !enabledRef.current) return
        const nextTables = deriveTablesFromActiveOrders(baseTables, activeOrders)
        const hasRemainingSeatOrders = activeOrders.some((order) => {
          const tableNo = order.table_no ?? ''
          return tableNo === baseTableLabel || tableNo.startsWith(`${baseTableLabel}-`)
        })

        setSyncError(null)
        setHasLoaded(true)
        setTablesStoreId(storeId)
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
      } catch (error) {
        if (generationRef.current === generation && enabledRef.current) {
          setHasLoaded(true)
          setTablesStoreId(storeId)
          setSyncError(error instanceof Error ? error.message : 'Unable to sync table board')
        }
        throw error
      } finally {
        if (generationRef.current === generation) {
          setIsLoading(false)
        }
      }
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

    const generation = generationRef.current
    const isCurrent = () => generationRef.current === generation && enabledRef.current

    const scheduleWebSocketRefresh = (force = false) => {
      if (!isCurrent()) {
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
        if (!isCurrent()) {
          return
        }
        void syncFromBackend(force ? { force: true } : undefined)
      }, force ? 100 : FRONTDESK_WS_DEBOUNCE_MS)
    }

    const schedulePoll = () => {
      if (!isCurrent() || document.visibilityState !== 'visible') {
        return
      }
      if (pollTimeoutRef.current !== null) {
        window.clearTimeout(pollTimeoutRef.current)
      }
      const delay = realtimeConnectedRef.current
        ? FRONTDESK_CONNECTED_POLL_INTERVAL_MS
        : FRONTDESK_FALLBACK_POLL_INTERVAL_MS
      pollTimeoutRef.current = window.setTimeout(() => {
        pollTimeoutRef.current = null
        if (!isCurrent() || document.visibilityState !== 'visible') {
          return
        }
        void syncFromBackend()
        schedulePoll()
      }, delay)
    }

    const handleRealtimeLifecycle = (event: FrontdeskRealtimeLifecycleEvent) => {
      if (!isCurrent()) return
      setRealtimeStatus(event.phase)
      const connected = event.phase === 'CONNECTED'
      if (connected !== realtimeConnectedRef.current) {
        realtimeConnectedRef.current = connected
        setIsRealtimeConnected(connected)
        schedulePoll()
      }
    }

    const unsubscribe = subscribeToFrontdeskOrders(storeId, (message) => {
      if (!isCurrent() || message.store_id !== storeId) {
        return
      }
      const eventType = (message.event_type ?? '').toLowerCase()
      const terminalStatus = message.order_status?.toLowerCase()
        ?? (eventType === 'order.completed' ? 'completed' : eventType === 'order.cancelled' ? 'cancelled' : null)
      const isFinishEvent = eventType === 'order.completed'
        || eventType === 'order.cancelled'
        || terminalStatus === 'completed'
        || terminalStatus === 'cancelled'
      if (isFinishEvent && message.order_id != null && terminalStatus) {
        void onOrderTerminalRef.current?.(message.order_id, terminalStatus)
      }
      scheduleWebSocketRefresh(isFinishEvent)
    }, { onLifecycle: handleRealtimeLifecycle })
    schedulePoll()

    const handleVisibilityChange = () => {
      if (!isCurrent()) {
        return
      }
      if (document.visibilityState !== 'visible') {
        if (wsRefreshTimeoutRef.current !== null) {
          window.clearTimeout(wsRefreshTimeoutRef.current)
          wsRefreshTimeoutRef.current = null
        }
        if (pollTimeoutRef.current !== null) {
          window.clearTimeout(pollTimeoutRef.current)
          pollTimeoutRef.current = null
        }
        return
      }

      void syncFromBackend({ force: true })
      schedulePoll()
    }

    const handleOnline = () => {
      setIsOnline(true)
      if (isCurrent() && document.visibilityState === 'visible') {
        void syncFromBackend({ force: true })
        schedulePoll()
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
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
      if (wsRefreshTimeoutRef.current !== null) {
        window.clearTimeout(wsRefreshTimeoutRef.current)
        wsRefreshTimeoutRef.current = null
      }
      if (pollTimeoutRef.current !== null) {
        window.clearTimeout(pollTimeoutRef.current)
        pollTimeoutRef.current = null
      }
    }
  }, [enabled, storeId, syncFromBackend])

  const tableSlots = useMemo(
    () => visibleTableSlotsForStore(tables, tablesStoreId, storeId),
    [storeId, tables, tablesStoreId],
  )

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
    void _tableLabel
    void _target
    setTables((currentTables) => currentTables)
  }

  return {
    tableSlots,
    statusCounts,
    syncError,
    isLoading: isLoading || tablesStoreId !== storeId,
    isEmpty: tablesStoreId === storeId && hasLoaded && tableSlots.length === 0,
    isOnline,
    isRealtimeConnected,
    realtimeStatus,
    startOrder,
    editOrder,
    endOrder,
    refreshFromBackend: syncFromBackend,
    refreshTableAfterFinish,
  }
}
