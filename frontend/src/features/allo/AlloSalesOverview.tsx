import { useEffect, useRef, useState } from 'react'
import { useCurrentStore } from '../store/useStoreContext'
import { salesApi, money, type Overview } from './alloSalesService'
import { AdjustmentHistory } from './AlloOrderDetails'
import { AlloRecentOrders } from './AlloRecentOrders'
import { followReprint, printResultMessage } from '../../services/manualReprintService'
import type { PrintJobRecord } from '../../services/printingAdminService'

const labels = {
  gross: 'Gross Sales / 折扣前销售',
  discount: 'Discounts / 折扣',
  net: 'Net Sales / 税前净销售额',
  tax: 'Taxes / 税',
  total: 'Total Sales incl. Tax / 含税销售额',
  collected: 'Amount Collected / 账面已收',
} as const
const secondary = 'min-h-12 rounded-xl border bg-white px-4 py-3 font-bold disabled:opacity-50'
const primary = 'min-h-12 rounded-xl bg-red-800 px-4 py-3 font-bold text-white disabled:opacity-50'

export function AlloSalesOverview() {
  const { storeId } = useCurrentStore()
  const [showHistory, setShowHistory] = useState(false)
  return showHistory
    ? <AlloRecentOrders key={storeId} store={storeId} onBack={() => setShowHistory(false)} />
    : <DailyOverview key={storeId} store={storeId} onHistory={() => setShowHistory(true)} />
}

function DailyOverview({ store, onHistory }: { store: number; onHistory: () => void }) {
  const [date, setDate] = useState('')
  const [data, setData] = useState<Overview | null>(null)
  const [snapshot, setSnapshot] = useState<Overview | null>(null)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [busy, setBusy] = useState(false)
  const key = useRef<string | null>(null)
  const inFlight = useRef(false)
  const scope = useRef(0)
  const stop = useRef<(() => void) | null>(null)
  const query = date ? '?date=' + encodeURIComponent(date) : ''

  async function load() {
    const generation = scope.current
    try {
      const overview = await salesApi<Overview>(store, '/overview' + query)
      if (generation === scope.current) setData(overview)
    } catch (failure) {
      if (generation === scope.current) setError(String(failure))
    }
  }

  useEffect(() => {
    scope.current += 1
    void load()
    const timer = window.setInterval(() => void load(), 30000)
    const refresh = () => void load()
    window.addEventListener('allo-sales-changed', refresh)
    window.addEventListener('focus', refresh)
    return () => {
      scope.current += 1
      window.clearInterval(timer)
      window.removeEventListener('allo-sales-changed', refresh)
      window.removeEventListener('focus', refresh)
      stop.current?.()
    }
  }, [store, date])

  async function run(task: (generation: number) => Promise<void>) {
    if (inFlight.current) return
    inFlight.current = true
    setBusy(true)
    setError('')
    const generation = scope.current
    try {
      await task(generation)
    } catch (failure) {
      if (generation === scope.current) setError(String(failure))
    } finally {
      inFlight.current = false
      if (generation === scope.current) setBusy(false)
    }
  }

  function generate() {
    void run(async generation => {
      const report = await salesApi<Overview>(store, '/reports' + query, {})
      if (generation !== scope.current) return
      stop.current?.()
      setSnapshot(report)
      key.current = crypto.randomUUID()
      setStatus('')
    })
  }

  function print() {
    if (!snapshot?.report_id || !key.current) return
    const request = { report_id: snapshot.report_id, request_key: key.current }
    void run(async generation => {
      const job = await salesApi<PrintJobRecord>(store, '/reports/print', request)
      if (generation !== scope.current) return
      setStatus(printResultMessage(job))
      stop.current?.()
      stop.current = followReprint(job, current => {
        if (generation === scope.current) setStatus(printResultMessage(current))
      })
      key.current = null
    })
  }

  function closePreview() {
    setSnapshot(null)
    key.current = null
    setStatus('')
    setError('')
    stop.current?.()
  }

  const report = snapshot ?? data
  return (
    <section className="space-y-5 p-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-black">Sales Overview / 销售汇总</h1>
        <label className="flex items-center gap-2">营业日期
          <input aria-label="营业日期" type="date" value={date || data?.business_date || ''}
            className="min-h-12 rounded-xl border p-3" disabled={busy} onChange={e => {
              closePreview()
              setDate(e.target.value)
              setData(null)
            }} />
        </label>
        <button className={primary} disabled={busy} onClick={generate}>生成销售汇总预览</button>
        <button className={secondary} disabled={busy} onClick={onHistory}>最近 30 天订单</button>
      </header>
      {error && <p role="alert" className="rounded-xl bg-red-50 p-4">{error}</p>}
      {status && <p role="status">{status}</p>}
      {report && (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-3">
            {(Object.keys(labels) as (keyof typeof labels)[]).map(field => (
              <article key={field} className="rounded-2xl bg-white p-5 shadow-sm">
                <p>{labels[field]}</p><strong className="mt-3 block text-3xl">{money(report[field])}</strong>
              </article>
            ))}
          </div>
          <p>{report.collected_note}</p>
          <p>{report.business_date}</p>
          <div className="flex flex-wrap gap-5 rounded-2xl bg-white p-5">
            <p>Dine-in / 堂食：{report.dine_in.count} 单 · {money(report.dine_in.total)}</p>
            <p>Takeout / 外带：{report.takeout.count} 单 · {money(report.takeout.total)}</p>
            <p>有效订单 {report.order_count} · 整单作废 {report.voided_orders} · 作废菜品 {report.voided_quantity} 份
              {report.voided_addon_quantity > 0 && ` · 单独作废加料 ${report.voided_addon_quantity} 份`} · 作废税前金额 {money(report.voided_gross)}</p>
          </div>
          <h2 className="text-xl font-bold">作废与折扣明细</h2>
          {report.adjustments.length > 0 ? <AdjustmentHistory rows={report.adjustments} /> : <p>暂无调整记录。</p>}
          {snapshot && (
            <div className="rounded-2xl bg-white p-5">
              <div className="mb-4 flex flex-wrap gap-3">
                <button disabled={busy || !key.current} className={primary} onClick={print}>打印此报告</button>
                <button disabled={busy} className={secondary} onClick={closePreview}>返回实时汇总</button>
              </div>
              <pre className="overflow-x-auto whitespace-pre-wrap font-mono text-base">{snapshot.rendered_text}</pre>
            </div>
          )}
        </>
      )}
    </section>
  )
}
