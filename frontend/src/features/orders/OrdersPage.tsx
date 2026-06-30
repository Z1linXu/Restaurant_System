import { useEffect, useState } from 'react'
import { Card } from '../../components/ui/Card'
import { useIpadLandscape } from '../../hooks/useIpadLandscape'
import {
  fetchOrderDetail,
  fetchOrderPrintOptions,
  fetchTodayOrderHistory,
  reprintOrderReceipt,
} from '../../services/orderService'
import type { BackendFrontdeskOrderBoardItem, BackendOrderResponse, OrderPrintOption } from '../../types/ordering'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { OrderHistoryDetail } from './components/OrderHistoryDetail'
import { OrderMiniCard } from './components/OrderMiniCard'
import { useCurrentStore } from '../store/StoreContext'

export function OrdersPage() {
  const { storeId } = useCurrentStore()
  const isIpadLandscape = useIpadLandscape()
  const [orders, setOrders] = useState<BackendFrontdeskOrderBoardItem[]>([])
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null)
  const [selectedOrder, setSelectedOrder] = useState<BackendOrderResponse | null>(null)
  const [printOptions, setPrintOptions] = useState<OrderPrintOption[]>([])
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [printBusy, setPrintBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadToday = async () => {
    setLoading(true)
    setError(null)
    try {
      const nextOrders = await fetchTodayOrderHistory(storeId, 100)
      setOrders(nextOrders)
      setSelectedOrderId((current) => current && nextOrders.some((order) => order.order_id === current)
        ? current
        : nextOrders[0]?.order_id ?? null)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load today orders')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadToday()
  }, [storeId])

  useEffect(() => {
    if (!selectedOrderId) {
      setSelectedOrder(null)
      setPrintOptions([])
      return
    }
    let active = true
    setDetailLoading(true)
    Promise.allSettled([fetchOrderDetail(selectedOrderId), fetchOrderPrintOptions(selectedOrderId)])
      .then(([detailResult, optionsResult]) => {
        if (!active) return
        if (detailResult.status === 'fulfilled') {
          setSelectedOrder(detailResult.value)
        } else {
          setSelectedOrder(null)
          setError(detailResult.reason instanceof Error ? detailResult.reason.message : 'Failed to load order detail')
        }
        setPrintOptions(optionsResult.status === 'fulfilled' ? optionsResult.value : [])
      })
      .finally(() => active && setDetailLoading(false))
    return () => { active = false }
  }, [selectedOrderId])

  const handleReprint = async (option: OrderPrintOption) => {
    if (!selectedOrder || !option.available) return
    try {
      setPrintBusy(option.module_code)
      const result = await reprintOrderReceipt(selectedOrder.id, option.module_code)
      window.alert(result.status === 'PRINTED' ? `${option.label} sent.` : `${option.label} failed: ${result.error_message ?? 'Unknown error'}`)
    } catch (printError) {
      window.alert(printError instanceof Error ? printError.message : 'Reprint failed')
    } finally {
      setPrintBusy(null)
    }
  }

  return (
    <div className={`min-h-screen bg-[var(--surface)] ${isIpadLandscape ? 'px-3 py-3' : 'px-5 py-4 md:px-7'}`}>
      <div className="mx-auto max-w-[1720px] space-y-4">
        <FrontdeskTopNav activeItem="orders" />
        <header className="flex items-end justify-between rounded-[24px] bg-white/75 px-5 py-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Today / 今日</p>
            <h1 className="mt-1 text-3xl font-extrabold">Order History / 订单记录</h1>
            <p className="mt-1 text-sm text-[var(--muted)]">Read-only. Payment and checkout actions are not available here.</p>
          </div>
          <button type="button" onClick={() => void loadToday()} className="rounded-full bg-stone-100 px-4 py-2 font-bold">Refresh</button>
        </header>
        {error ? <div className="rounded-[18px] bg-red-50 px-4 py-3 font-semibold text-red-700">{error}</div> : null}
        <div className={`grid gap-4 ${isIpadLandscape ? 'grid-cols-[18rem_minmax(0,1fr)]' : 'xl:grid-cols-[20rem_minmax(0,1fr)]'}`}>
          <Card tone="well" className="rounded-[24px] p-3.5">
            <div className="mb-3 flex justify-between text-sm font-bold text-[var(--muted)]"><span>Today orders</span><span>{orders.length}</span></div>
            <div className="max-h-[calc(100vh-15rem)] space-y-2 overflow-y-auto">
              {loading ? <p className="p-4 text-center text-[var(--muted)]">Loading...</p> : orders.length ? orders.map((order) => (
                <OrderMiniCard key={order.order_id} order={order} selected={selectedOrderId === order.order_id} onClick={() => setSelectedOrderId(order.order_id)} compact={isIpadLandscape} />
              )) : <p className="p-4 text-center text-[var(--muted)]">No orders today.</p>}
            </div>
          </Card>
          <OrderHistoryDetail order={selectedOrder} loading={detailLoading} printOptions={printOptions} printBusy={printBusy} onReprint={(option) => void handleReprint(option)} />
        </div>
      </div>
    </div>
  )
}
