import { useEffect, useMemo, useState } from 'react'
import { Card } from '../../components/ui/Card'
import { useIpadLandscape } from '../../hooks/useIpadLandscape'
import {
  completeOrder,
  fetchFrontdeskOrderBoard,
  fetchFrontdeskOrderHistory,
  fetchOrderDetail,
} from '../../services/orderService'
import type {
  BackendFrontdeskOrderBoardItem,
  BackendOrderResponse,
  BillId,
  ItemAllocation,
  SplitBillCount,
} from '../../types/ordering'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { OrderDetailPanel } from './components/OrderDetailPanel'
import { OrderMiniCard } from './components/OrderMiniCard'

const DEFAULT_SINGLE_BILL: BillId = 'A'

function buildInitialAllocations(order: BackendOrderResponse, splitCount: SplitBillCount) {
  const nextAllocations: Record<number, ItemAllocation> = {}
  order.items.forEach((item) => {
    nextAllocations[item.id] =
      splitCount === 1
        ? { mode: 'SINGLE', billId: DEFAULT_SINGLE_BILL }
        : { mode: 'UNASSIGNED' }
  })
  return nextAllocations
}

export function OrdersPage() {
  const isIpadLandscape = useIpadLandscape()
  const [activeOrders, setActiveOrders] = useState<BackendFrontdeskOrderBoardItem[]>([])
  const [historyOrders, setHistoryOrders] = useState<BackendFrontdeskOrderBoardItem[]>([])
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null)
  const [selectedOrder, setSelectedOrder] = useState<BackendOrderResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [checkoutBusy, setCheckoutBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [splitCount, setSplitCount] = useState<SplitBillCount>(1)
  const [allocations, setAllocations] = useState<Record<number, ItemAllocation>>({})
  const [cashSelected, setCashSelected] = useState(false)

  const preferredOrderIdFromRoute = useMemo(() => {
    const params = new URLSearchParams(window.location.search)
    const rawOrderId = params.get('orderId')
    if (!rawOrderId) {
      return null
    }
    const parsed = Number(rawOrderId)
    return Number.isFinite(parsed) ? parsed : null
  }, [])

  const loadBoard = async (preferredOrderId?: number | null) => {
    setLoading(true)
    setError(null)
    try {
      const [nextActiveOrders, nextHistoryOrders] = await Promise.all([
        fetchFrontdeskOrderBoard({
          statuses: ['submitted', 'preparing', 'ready'],
        }),
        fetchFrontdeskOrderHistory({
          statuses: ['completed', 'cancelled'],
          limit: 20,
        }),
      ])
      setActiveOrders(nextActiveOrders)
      setHistoryOrders(nextHistoryOrders)

      const combined = [...nextActiveOrders, ...nextHistoryOrders]
      const nextSelectedOrderId =
        preferredOrderId && combined.some((order) => order.order_id === preferredOrderId)
          ? preferredOrderId
          : combined[0]?.order_id ?? null
      setSelectedOrderId(nextSelectedOrderId)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load orders')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadBoard(preferredOrderIdFromRoute)
  }, [preferredOrderIdFromRoute])

  useEffect(() => {
    if (!selectedOrderId) {
      setSelectedOrder(null)
      return
    }

    let active = true
    setDetailLoading(true)
    setError(null)

    void fetchOrderDetail(selectedOrderId)
      .then((detail) => {
        if (!active) {
          return
        }
        setSelectedOrder(detail)
        setSplitCount(1)
        setAllocations(buildInitialAllocations(detail, 1))
        setCashSelected(false)
      })
      .catch((loadError) => {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : 'Failed to load order detail')
        }
      })
      .finally(() => {
        if (active) {
          setDetailLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [selectedOrderId])

  const groupedOrders = useMemo(
    () => [
      { key: 'active', label: 'Active Orders', orders: activeOrders },
      { key: 'history', label: 'Recent History', orders: historyOrders },
    ],
    [activeOrders, historyOrders],
  )

  const handleSplitCountChange = (count: SplitBillCount) => {
    setSplitCount(count)
    if (selectedOrder) {
      setAllocations(buildInitialAllocations(selectedOrder, count))
    }
  }

  const handleCheckout = async () => {
    if (!selectedOrder) {
      return
    }

    const confirmed = window.confirm(
      'Checkout this order?\nThis will complete the order and move it to history.',
    )
    if (!confirmed) {
      return
    }

    try {
      setCheckoutBusy(true)
      const completed = await completeOrder(selectedOrder.id)
      setSelectedOrder(completed)
      await loadBoard(completed.id)
    } catch (checkoutError) {
      window.alert(checkoutError instanceof Error ? checkoutError.message : 'Failed to checkout order')
    } finally {
      setCheckoutBusy(false)
    }
  }

  return (
    <div className={`min-h-screen bg-[var(--surface)] ${isIpadLandscape ? 'px-3 py-3' : 'px-5 py-4 md:px-7 xl:px-8'}`}>
      <div className={`mx-auto ${isIpadLandscape ? 'max-w-none space-y-3' : 'max-w-[1720px] space-y-6'}`}>
        <FrontdeskTopNav activeItem="orders" />

        <div className={`rounded-[24px] bg-[rgba(255,255,255,0.74)] shadow-[0_14px_32px_rgba(26,28,25,0.05)] ${isIpadLandscape ? 'px-4 py-3' : 'px-6 py-5'}`}>
          <div className="flex items-end justify-between gap-4">
            <div>
              <p className="text-[0.76rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Orders / 订单</p>
              <h1 className={`mt-1 font-display font-extrabold tracking-[-0.05em] text-[var(--on-surface)] ${isIpadLandscape ? 'text-[2rem]' : 'text-[2.5rem]'}`}>
                Checkout & Order Lookup
              </h1>
            </div>
            <button
              type="button"
              className="rounded-full bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.8rem] font-semibold text-[var(--primary)] transition hover:bg-[rgba(97,0,0,0.12)]"
              onClick={() => void loadBoard(selectedOrderId)}
            >
              Refresh
            </button>
          </div>
        </div>

        {error ? (
          <div className="rounded-[20px] bg-[rgba(97,0,0,0.08)] px-4 py-3 font-medium text-[var(--primary)]">{error}</div>
        ) : null}

        <div className={`grid ${isIpadLandscape ? 'grid-cols-[18rem_minmax(0,1fr)] gap-3' : 'gap-5 xl:grid-cols-[20rem_minmax(0,1fr)]'}`}>
          <Card tone="well" className={`${isIpadLandscape ? 'rounded-[24px] p-3.5' : 'rounded-[30px] p-4'}`}>
            <div className="space-y-4">
              {groupedOrders.map((group) => (
                <div key={group.key}>
                  <div className="mb-2 flex items-center justify-between">
                    <p className="text-[0.76rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{group.label}</p>
                    <span className="text-[0.78rem] font-semibold text-[var(--muted)]">{group.orders.length}</span>
                  </div>
                  <div className="space-y-2">
                    {loading ? (
                      <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-3 py-4 text-[0.88rem] text-[var(--muted)]">Loading...</div>
                    ) : group.orders.length ? (
                      group.orders.map((order) => (
                        <OrderMiniCard
                          key={order.order_id}
                          order={order}
                          selected={selectedOrderId === order.order_id}
                          onClick={() => setSelectedOrderId(order.order_id)}
                          compact={isIpadLandscape}
                        />
                      ))
                    ) : (
                      <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-3 py-4 text-[0.88rem] text-[var(--muted)]">No orders.</div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </Card>

          <OrderDetailPanel
            order={selectedOrder}
            loading={detailLoading}
            busy={checkoutBusy}
            compact={isIpadLandscape}
            splitCount={splitCount}
            allocations={allocations}
            cashSelected={cashSelected}
            onSplitCountChange={handleSplitCountChange}
            onAllocationChange={(itemId, allocation) =>
              setAllocations((current) => ({ ...current, [itemId]: allocation }))
            }
            onCashSelectedChange={setCashSelected}
            onCheckout={() => void handleCheckout()}
          />
        </div>
      </div>
    </div>
  )
}
