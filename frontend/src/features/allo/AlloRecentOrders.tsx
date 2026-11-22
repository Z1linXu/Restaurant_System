import { useEffect, useState } from 'react'
import { orderStatusDisplayLabel } from '../../utils/displayLabels'
import { AdjustmentHistory, AlloOrderItems, OrderAmounts } from './AlloOrderDetails'
import { money, salesApi, type FinancialDetail, type RecentOrder, type RecentOrders } from './alloSalesService'

function location(order: RecentOrder) {
  return order.table_no || order.pickup_no || (order.order_type === 'dine_in' ? '堂食' : '外带')
}

const button = 'min-h-12 rounded-xl border bg-white px-4 py-3 font-bold disabled:opacity-40'

export function AlloRecentOrders({ store, onBack }: { store: number; onBack: () => void }) {
  const [date, setDate] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<RecentOrders | null>(null)
  const [selected, setSelected] = useState<RecentOrder | null>(null)
  const [detail, setDetail] = useState<FinancialDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [detailError, setDetailError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setListError('')
    const query = new URLSearchParams({ page: String(page) })
    if (date) query.set('date', date)
    salesApi<RecentOrders>(store, '/orders?' + query)
      .then(data => { if (active) setResult(data) })
      .catch(error => { if (active) setListError(String(error)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [store, date, page])

  useEffect(() => {
    setDetail(null)
    setDetailError('')
    if (!selected) return
    let active = true
    salesApi<FinancialDetail>(store, `/orders/${selected.id}`)
      .then(data => { if (active) setDetail(data) })
      .catch(error => { if (active) setDetailError(String(error)) })
    return () => { active = false }
  }, [store, selected])

  function changeDate(value: string) {
    setDate(value)
    setPage(0)
    setSelected(null)
  }

  function changePage(value: number) {
    setPage(value)
    setSelected(null)
  }

  return (
    <section className="space-y-4 p-4" aria-label="最近 30 天订单">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-black">最近 30 天订单</h1>
        <label className="flex items-center gap-2">订单营业日期
          <input type="date" className="min-h-12 rounded-xl border p-3" aria-label="订单营业日期"
            value={date || result?.date || ''} min={result?.earliest_date} max={result?.latest_date}
            onChange={e => changeDate(e.target.value)} />
        </label>
        <button className={button} onClick={onBack}>返回销售汇总</button>
      </header>
      {listError && <p role="alert" className="rounded-xl bg-red-50 p-3">{listError}</p>}
      <div className="grid gap-4 lg:grid-cols-[minmax(16rem,1fr)_minmax(0,2fr)]">
        <div className="space-y-2 rounded-2xl bg-stone-50 p-3">
          {loading ? <p role="status">正在加载订单…</p> : !listError && result && (
            <>
              {result.orders.length === 0 && <p className="p-3">这一天没有已提交订单。</p>}
              {result.orders.map(order => (
                <button key={order.id} onClick={() => setSelected(order)} aria-pressed={selected?.id === order.id}
                  className="w-full rounded-xl border bg-white px-3 py-3 text-left aria-pressed:border-red-800 aria-pressed:bg-red-50">
                  <span className="flex justify-between gap-3 font-bold">
                    <span>{location(order)}</span><span>{money(order.total_amount)}</span>
                  </span>
                  <span className="block text-sm text-stone-700">
                    {order.submitted_time?.slice(11)} · {orderStatusDisplayLabel(order.status)}
                    {order.has_voids && order.status !== 'cancelled' ? ' · 部分已删除' : ''}
                    {Number(order.discount_amount) > 0 ? ' · 有优惠' : ''}
                  </span>
                </button>
              ))}
              {(page > 0 || result.has_more) && (
                <div className="flex justify-between gap-2">
                  <button className={button} disabled={page === 0} onClick={() => changePage(page - 1)}>上一页</button>
                  <button className={button} disabled={!result.has_more} onClick={() => changePage(page + 1)}>下一页</button>
                </div>
              )}
            </>
          )}
        </div>
        {selected && (
          <section className="space-y-3 rounded-2xl bg-white p-4" aria-label="历史订单详情">
            <header>
              <h2 className="text-xl font-bold">{location(selected)}</h2>
              <p className="text-sm text-stone-700">{selected.submitted_time} · {orderStatusDisplayLabel(selected.status)}</p>
            </header>
            {detailError && <p role="alert">{detailError}</p>}
            {!detail && !detailError && <p>正在加载详情…</p>}
            {detail && detail.order.id === selected.id && (
              <>
                <AlloOrderItems detail={detail} />
                <OrderAmounts amounts={detail.amounts} />
                {!!detail.adjustments.length && (
                  <>
                    <h3 className="text-lg font-bold">调整记录</h3>
                    <AdjustmentHistory rows={detail.adjustments} />
                  </>
                )}
              </>
            )}
          </section>
        )}
      </div>
    </section>
  )
}
