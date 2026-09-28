import { useEffect, useRef, useState } from 'react'
import { followReprint, printResultMessage, reprintErrorMessage } from '../../services/manualReprintService'
import { Card } from '../../components/ui/Card'
import { useIpadLandscape } from '../../hooks/useIpadLandscape'
import {
  fetchOrderDetail,
  fetchOrderPrintJobs,
  fetchOrderPrintOptions,
  fetchTodayOrderHistory,
  reprintOrderReceipt,
} from '../../services/orderService'
import type { BackendFrontdeskOrderBoardItem, BackendOrderResponse, OrderPrintOption } from '../../types/ordering'
import type { PrintJobRecord } from '../../services/printingAdminService'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { OrderHistoryDetail } from './components/OrderHistoryDetail'
import { OrderMiniCard } from './components/OrderMiniCard'
import { useCurrentStore } from '../store/useStoreContext'

export function OrdersPage() {
  const reprintFollow = useRef<(() => void) | null>(null)
  const { storeId } = useCurrentStore()
  const reprintScope = useRef(0)
  const isIpadLandscape = useIpadLandscape()
  const [orders, setOrders] = useState<BackendFrontdeskOrderBoardItem[]>([])
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null)
  useEffect(() => () => { reprintScope.current += 1; reprintFollow.current?.() }, [storeId, selectedOrderId])
  const [selectedOrder, setSelectedOrder] = useState<BackendOrderResponse | null>(null)
  const [printOptions, setPrintOptions] = useState<OrderPrintOption[]>([])
  const [printJobs, setPrintJobs] = useState<PrintJobRecord[]>([])
  const [printStatusMessage, setPrintStatusMessage] = useState<{ kind: 'success' | 'error'; message: string } | null>(null)
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
      setError(loadError instanceof Error ? loadError.message : '今日订单加载失败')
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
      setPrintJobs([])
      setPrintStatusMessage(null)
      return
    }
    let active = true
    setDetailLoading(true)
    setPrintStatusMessage(null)
    Promise.allSettled([
      fetchOrderDetail(selectedOrderId),
      fetchOrderPrintOptions(selectedOrderId),
      fetchOrderPrintJobs(selectedOrderId),
    ])
      .then(([detailResult, optionsResult, printJobsResult]) => {
        if (!active) return
        if (detailResult.status === 'fulfilled') {
          setSelectedOrder(detailResult.value)
        } else {
          setSelectedOrder(null)
          setError(detailResult.reason instanceof Error ? detailResult.reason.message : '订单详情加载失败')
        }
        setPrintOptions(optionsResult.status === 'fulfilled' ? optionsResult.value : [])
        setPrintJobs(printJobsResult.status === 'fulfilled' ? printJobsResult.value : [])
      })
      .finally(() => active && setDetailLoading(false))
    return () => { active = false }
  }, [selectedOrderId])

  const handleReprint = async (option: OrderPrintOption) => {
    if (!selectedOrder || !option.available) return
    const scope = reprintScope.current
    try {
      setPrintBusy(option.module_code)
      setPrintStatusMessage(null)
      const result = await reprintOrderReceipt(selectedOrder.id, option.module_code)
      if (scope !== reprintScope.current) return
      reprintFollow.current?.()
      reprintFollow.current = followReprint(result, job => {
        setPrintStatusMessage({ kind: job.status === 'FAILED' ? 'error' : 'success', message: printResultMessage(job) })
        setPrintJobs(current => [job, ...current.filter(existing => existing.id !== job.id)])
      })
    } catch (printError) {
      if (scope !== reprintScope.current) return
      setPrintStatusMessage({
        kind: 'error',
        message: reprintErrorMessage(printError),
      })
    } finally {
      if (scope === reprintScope.current) setPrintBusy(null)
    }
  }

  return (
    <div className={`min-h-screen bg-[var(--surface)] ${isIpadLandscape ? 'px-3 py-3' : 'px-5 py-4 md:px-7'}`}>
      <div className="mx-auto max-w-[1720px] space-y-4">
        <FrontdeskTopNav activeItem="orders" />
        <header className="flex items-end justify-between rounded-[24px] bg-white/75 px-5 py-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">今日</p>
            <h1 className="mt-1 text-3xl font-extrabold">订单记录</h1>
            <p className="mt-1 text-sm text-[var(--muted)]">只读查看订单和打印记录；这里不做结账、拆单或收款操作。</p>
          </div>
          <button type="button" onClick={() => void loadToday()} className="rounded-full bg-stone-100 px-4 py-2 font-bold">刷新</button>
        </header>
        {error ? <div className="rounded-[18px] bg-red-50 px-4 py-3 font-semibold text-red-700">{error}</div> : null}
        <div className={`grid gap-4 ${isIpadLandscape ? 'grid-cols-[18rem_minmax(0,1fr)]' : 'xl:grid-cols-[20rem_minmax(0,1fr)]'}`}>
          <Card tone="well" className="rounded-[24px] p-3.5">
            <div className="mb-3 flex justify-between text-sm font-bold text-[var(--muted)]"><span>今日订单</span><span>{orders.length}</span></div>
            <div className="max-h-[calc(100vh-15rem)] space-y-2 overflow-y-auto">
              {loading ? <p className="p-4 text-center text-[var(--muted)]">加载中...</p> : orders.length ? orders.map((order) => (
                <OrderMiniCard key={order.order_id} order={order} selected={selectedOrderId === order.order_id} onClick={() => setSelectedOrderId(order.order_id)} compact={isIpadLandscape} />
              )) : <p className="p-4 text-center text-[var(--muted)]">今天暂无订单。</p>}
            </div>
          </Card>
          <OrderHistoryDetail
            order={selectedOrder}
            loading={detailLoading}
            printOptions={printOptions}
            printJobs={printJobs}
            printBusy={printBusy}
            printStatusMessage={printStatusMessage}
            onReprint={(option) => void handleReprint(option)}
          />
        </div>
      </div>
    </div>
  )
}
