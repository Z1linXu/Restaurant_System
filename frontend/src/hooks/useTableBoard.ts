import { useCallback, useEffect, useMemo, useState } from 'react'
import { dineInService } from '../services/dineInService'
import { fetchActiveOrderBoard } from '../services/orderService'
import type { DiningTable, TableSeatCode, TableSlot, TableStatus } from '../types/dinein'

const initialData = dineInService.getInitialData()
const SEAT_CODES: TableSeatCode[] = ['A', 'B']

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

function orderPriority(order: Awaited<ReturnType<typeof fetchActiveOrderBoard>>[number]) {
  const draftPenalty = order.order_status === 'draft' ? 0 : 1000
  const itemWeight = order.total_item_count ?? 0
  const readyWeight =
    order.order_status === 'ready' ? 30 : order.order_status === 'preparing' ? 20 : order.order_status === 'submitted' ? 10 : 0
  return draftPenalty + itemWeight * 10 + readyWeight
}

export function useTableBoard() {
  const [tables, setTables] = useState<DiningTable[]>(createBaseTables)

  const deriveTablesFromActiveOrders = useCallback(
    (activeOrders: Awaited<ReturnType<typeof fetchActiveOrderBoard>>): DiningTable[] =>
      createBaseTables().map<DiningTable>((table) => {
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

  const syncFromBackend = useCallback(async () => {
    const activeOrders = await fetchActiveOrderBoard()
    setTables(deriveTablesFromActiveOrders(activeOrders))
  }, [deriveTablesFromActiveOrders])

  const refreshTableAfterFinish = useCallback(
    async (baseTableLabel: string) => {
      const activeOrders = await fetchActiveOrderBoard()
      const nextTables = deriveTablesFromActiveOrders(activeOrders)
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
    [deriveTablesFromActiveOrders],
  )

  useEffect(() => {
    void syncFromBackend()
  }, [syncFromBackend])

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
    startOrder,
    editOrder,
    endOrder,
    refreshFromBackend: syncFromBackend,
    refreshTableAfterFinish,
  }
}
