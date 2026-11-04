import { useEffect, useRef, useState } from 'react'
import { useCurrentStore } from '../store/useStoreContext'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { useUberInbox } from './useUberInbox'
import { canDecideUberOrder, decideUberOrder, fetchUberInbox, reprintUberKitchen, type UberInboxPage, type UberItem, type UberOrder } from '../../services/uberEatsService'
import { printResultMessage, reprintErrorMessage } from '../../services/manualReprintService'
import { ApiRequestError } from '../../services/apiClient'
const labels: Record<string, string> = { KITCHEN_SENT_WITH_MAPPING_WARNINGS: '已送入厨房，部分菜品请核对', WAITING_FOR_ACCEPTANCE: '等待 Uber 接单', ACCEPTANCE_REVIEW_REQUIRED: '接单状态待核对，请在 Uber 查看', WAITING_FOR_RELEASE: '等待 Uber 释放到厨房', RELEASED_MAPPING_REQUIRED: 'Uber 已接单：菜单匹配不完整，尚未送入厨房', MIRROR_READY: '正在送入厨房', MIRROR_LOCAL_FAILED: '厨房生产正在恢复', RELEASED_TO_KITCHEN: '已送入厨房', PRINTED: '厨房打印完成', PRINT_PARTIAL: '部分厨房票已打印，请核对', PRINT_FAILED: '厨房打印失败，请核对', CANCELLED_AFTER_RELEASE: 'Uber 已取消：请核对厨房，历史任务与票据保留', RECEIVED: '正在获取订单', PENDING: '等待接单', MAPPING_REQUIRED: '需要匹配菜单', ACCEPTING: '正在确认接单', UBER_ACCEPTED: '正在送入厨房', ACCEPTED: '已接单', DENYING: '正在拒单', DENIED: '已拒单', CANCELLED: '已取消', CANCELLED_REVIEW_REQUIRED: 'Uber 已取消：请立即核对厨房与已打印票据', EDIT_REVIEW_REQUIRED: 'Uber 订单已修改：请人工核对，厨房内容未自动修改', SCHEDULED_REVIEW_REQUIRED: '预约订单：等待正式来单通知', LOCAL_FAILED: 'Uber 已接单，本地生产正在恢复', LOCAL_REVIEW_REQUIRED: '送入厨房失败：请联系管理员', DECISION_REVIEW_REQUIRED: '接单结果不明确，请先在 Uber 核对', EXTERNAL_STATE_REVIEW_REQUIRED: 'Uber 订单状态已变化，请核对' }
function normalizeNote(note: string | null | undefined) {
  return (note ?? '').replace(/\p{White_Space}+/gu, ' ').trim()
}
function Items({ items, orderNote }: { items: UberItem[]; orderNote: string }) {
  const normalizedOrderNote = normalizeNote(orderNote)
  return <ul className="space-y-2">{items.map((item, index) => {
    const ownNote = normalizeNote(item.notes)
    return <li key={`${item.id}-${index}`}>
      <span className="font-bold">{item.removed ? '去除 ' : ''}{item.title} ×{item.quantity}</span>
      {ownNote && ownNote !== normalizedOrderNote && <p className="whitespace-pre-wrap break-words text-amber-900">商品备注：{item.notes}</p>}
      {item.modifiers.length > 0 && <div className="ml-4 border-l-2 pl-3 text-sm"><Items items={item.modifiers} orderNote={orderNote} /></div>}
    </li>
  })}</ul>
}
function OrderItemsAndNotes({ order }: { order: UberOrder }) {
  const orderNote = order.order_note_snapshot ?? order.snapshot?.notes ?? ''
  return <>
    {order.snapshot && <Items items={order.snapshot.items} orderNote={orderNote} />}
    {normalizeNote(orderNote) && <p className="mt-3 whitespace-pre-wrap break-words font-bold text-amber-900">订单备注：{orderNote}</p>}
  </>
}
export default function UberInboxPage() {
  const { storeId } = useCurrentStore()
  const { today, attention, businessDate, timeZone, workspaceKey, loading, error, refresh, loadMore } = useUberInbox(storeId)
  const scope = `${storeId}:${workspaceKey}`
  const historyRequest = useRef<AbortController | null>(null)
  const [historyDate, setHistoryDate] = useState('')
  const [history, setHistory] = useState<{ scope: string; date: string; page: UberInboxPage | null; loading: boolean; error: string | null } | null>(null)
  useEffect(() => () => { historyRequest.current?.abort(); historyRequest.current = null }, [scope])
  const historyView = history?.scope === scope ? history : null
  const loadHistory = async (more = false) => {
    if (!historyDate || historyView?.loading) return
    historyRequest.current?.abort()
    const controller = new AbortController()
    historyRequest.current = controller
    const previous = more && historyView?.date === historyDate ? historyView.page : null
    setHistory({ scope, date: historyDate, page: previous, loading: true, error: null })
    try {
      const result = await fetchUberInbox(storeId, { limit: 100, history_date: historyDate, history_before_id: previous?.next_before_id ?? undefined }, controller.signal)
      if (controller.signal.aborted) return
      const page = result.history
      setHistory({ scope, date: historyDate, page: page && previous ? { ...page, orders: [...new Map([...previous.orders, ...page.orders].map(order => [order.id, order])).values()] } : page, loading: false, error: null })
    } catch (exception) {
      const denied = exception instanceof ApiRequestError && (exception.status === 401 || exception.status === 403)
      if (!controller.signal.aborted) setHistory({ scope, date: historyDate, page: denied ? null : previous, loading: false, error: exception instanceof Error ? exception.message : '无法加载历史订单' })
    }
  }
  const inFlight = useRef(new Set<number>())
  const [busy, setBusy] = useState<number[]>([])
  const [actionError, setActionError] = useState<string | null>(null)
  const [denyOrder, setDenyOrder] = useState<number | null>(null)
  const [reason, setReason] = useState('CAPACITY')
  const [printMessage, setPrintMessage] = useState<string | null>(null)
  const act = async (order: UberOrder, action: 'accept' | 'deny' | 'retry-local') => {
    if (inFlight.current.has(order.id)) return
    inFlight.current.add(order.id); setBusy([...inFlight.current]); setActionError(null)
    try { const result = await decideUberOrder(storeId, order.id, action, reason); if (result.last_error) setActionError(labels[result.status] || result.last_error); setDenyOrder(null); refresh() }
    catch (e) { setActionError(e instanceof Error ? e.message : '操作未确认，请刷新订单状态后重试'); refresh() }
    finally { inFlight.current.delete(order.id); setBusy([...inFlight.current]) }
  }
  const reprint = async (order: UberOrder, module: 'GRAB' | 'HOT_KITCHEN') => {
    if (!order.local_order_id || inFlight.current.has(order.id)) return
    inFlight.current.add(order.id); setBusy([...inFlight.current]); setActionError(null); setPrintMessage(null)
    try { setPrintMessage(printResultMessage(await reprintUberKitchen(order.local_order_id, module))); refresh() }
    catch (error) { setActionError(reprintErrorMessage(error)); refresh() }
    finally { inFlight.current.delete(order.id); setBusy([...inFlight.current]) }
  }
  const renderOrder = (order: UberOrder) => <article key={order.id} className="rounded-2xl bg-white p-5 shadow-sm"><div className="flex justify-between gap-3"><h2 className="text-xl font-black">{order.processing_mode === 'KITCHEN_MIRROR' ? order.customer_header : `Uber Eats #${order.display_id || order.uber_order_id.slice(-5)}`}</h2><span className="text-sm">{order.placed_at ? new Date(`${order.placed_at}Z`).toLocaleString() : order.created_at.replace('T', ' ').slice(0, 19)}</span></div>
      <p className={`my-3 rounded-lg p-3 font-semibold ${order.status.includes('REVIEW') || order.status.includes('MAPPING_REQUIRED') || order.status === 'KITCHEN_SENT_WITH_MAPPING_WARNINGS' || order.status === 'CANCELLED_AFTER_RELEASE' ? 'bg-amber-100' : 'bg-emerald-50'}`}>{labels[order.status] || order.status}</p>
      {order.processing_mode === 'KITCHEN_MIRROR' && <div className="mb-3 text-sm text-stone-600"><p>Uber #{order.display_id || order.uber_order_id.slice(-5)} · 接单与付款由 Uber 处理</p><p>接单观察时间：{order.accepted_observed_at ? new Date(`${order.accepted_observed_at}Z`).toLocaleString() : '等待 Uber 接单'}</p>{order.released_at && <p>厨房释放通知：{new Date(`${order.released_at}Z`).toLocaleString()}</p>}</div>}
      {order.mapped_items && order.mapped_items.length > 0 && <div className="mb-3 rounded-lg bg-stone-50 p-3"><p className="font-semibold">厨房内容</p>{order.mapped_items.map((item, index) => <p key={index}>{item}</p>)}</div>}
      {order.raw_items && order.raw_items.length > 0 && <div className="mb-3 rounded-lg bg-amber-50 p-3"><p className="font-semibold">Raw fallback / Uber 原文</p>{order.raw_items.map((item, index) => <p key={index}>{item}</p>)}</div>}
      <OrderItemsAndNotes order={order} />
      <p className="mt-3 text-sm">菜单匹配：{order.mapping_status === 'MAPPED' ? '已匹配' : order.mapping_status === 'PARTIALLY_MAPPED' ? '部分匹配' : order.mapping_status}</p>
      {order.mapping_errors.length > 0 && <div role="alert" className="mt-2 rounded-lg bg-amber-50 p-3"><p className="font-bold">{order.local_order_id ? '已按已匹配内容及 Uber 原文送入厨房，请核对未匹配菜品。' : 'Uber 菜品无法匹配本地菜单，厨房尚未释放，请联系管理员。'}</p><ul className="list-inside list-disc break-words text-sm">{order.mapping_errors.map((message, i) => <li key={i}>{message}</li>)}</ul></div>}
      {order.local_order_id && <p className="mt-2">本地订单 #{order.local_order_id}</p>}
      {order.processing_mode === 'KITCHEN_MIRROR' && order.local_order_id && <div className="mt-4 space-y-2"><p>GRAB：{order.grab_status}</p>{order.hot_kitchen_status !== 'NOT_REQUIRED' && <p>HOT KITCHEN：{order.hot_kitchen_status}</p>}<div className="flex gap-3"><button disabled={busy.includes(order.id) || order.grab_status === 'WAITING'} className="min-h-14 rounded-xl border px-5 font-bold disabled:opacity-40" onClick={() => void reprint(order, 'GRAB')}>重打 GRAB</button>{order.hot_kitchen_status !== 'NOT_REQUIRED' && order.hot_kitchen_status !== 'WAITING' && <button disabled={busy.includes(order.id)} className="min-h-14 rounded-xl border px-5 font-bold disabled:opacity-40" onClick={() => void reprint(order, 'HOT_KITCHEN')}>重打 HOT KITCHEN</button>}</div></div>}
      {canDecideUberOrder(order) && <div className="mt-4 flex gap-3"><button disabled={busy.includes(order.id) || order.mapping_status !== 'MAPPED'} className="min-h-14 flex-1 rounded-xl bg-emerald-800 px-5 font-bold text-white disabled:opacity-40" onClick={() => void act(order, 'accept')}>{busy.includes(order.id) ? '处理中…' : 'Accept / 接单'}</button><button disabled={busy.includes(order.id)} className="min-h-14 rounded-xl border border-red-800 px-5 font-bold text-red-800 disabled:opacity-40" onClick={() => setDenyOrder(order.id)}>Deny / 拒单</button></div>}
      {denyOrder === order.id && <div className="mt-3 flex flex-wrap gap-3"><label>拒单原因 <select className="min-h-12 border p-2" value={reason} onChange={e => setReason(e.target.value)}><option value="CAPACITY">厨房繁忙</option><option value="ITEM_AVAILABILITY">菜品售罄</option><option value="MISSING_ITEM">菜单匹配不完整</option><option value="STORE_CLOSED">门店已关</option><option value="OTHER">其他</option></select></label><button disabled={busy.includes(order.id)} className="min-h-12 rounded-xl bg-red-800 px-5 text-white" onClick={() => void act(order, 'deny')}>确认拒单</button><button className="min-h-12 px-3" onClick={() => setDenyOrder(null)}>返回</button></div>}
      {order.status === 'LOCAL_REVIEW_REQUIRED' && <button disabled={busy.includes(order.id)} className="mt-3 min-h-12 rounded-xl border px-5" onClick={() => void act(order, 'retry-local')}>重试送入厨房</button>}
    </article>
  const renderOrders = (orders: UberOrder[]) => <div className="grid gap-4 lg:grid-cols-2">{orders.map(renderOrder)}</div>
  const attentionIds = new Set(attention.orders.map(order => order.id))
  const todayOrders = today.orders.filter(order => !attentionIds.has(order.id))
  return <main className="min-h-screen bg-stone-100 p-4 text-stone-900"><FrontdeskTopNav /><div className="mx-auto max-w-6xl py-6">
    <div className="mb-4 flex items-center justify-between gap-3"><h1 className="text-2xl font-black">Uber Eats / 今日订单</h1><button className="min-h-12 rounded-xl bg-white px-5" onClick={refresh}>刷新</button></div>
    <p className="mb-4 text-stone-600">{businessDate && `${businessDate} · ${timeZone ?? ''} · `}今日订单自动更新；往日未完成和需要核对的订单保留在待处理区。</p>
    {(error || actionError) && <p role="alert" className="mb-4 rounded-xl bg-red-100 p-4">{actionError || error}</p>}
    {printMessage && <p role="status" className="mb-4 rounded-xl bg-emerald-50 p-4">{printMessage}</p>}
    {loading && <p role="status">正在加载…</p>}
    {(attention.orders.length > 0 || attention.has_more) && <section aria-label="待处理订单" className="mb-6 rounded-2xl border-2 border-amber-400 p-3">
      <h2 className="mb-3 text-xl font-bold">待处理 / 需要核对</h2>{renderOrders(attention.orders)}
      {attention.has_more && <button className="mt-3 min-h-12 rounded-xl bg-white px-5 font-bold" onClick={() => loadMore('attention')}>更多待处理订单</button>}
    </section>}
    <section aria-label="今日订单"><h2 className="mb-3 text-xl font-bold">今日订单</h2>
      {!loading && !error && todayOrders.length === 0 && <p>今日暂无其他 Uber Eats 订单。</p>}{renderOrders(todayOrders)}
      {today.has_more && <button className="mt-3 min-h-12 rounded-xl bg-white px-5 font-bold" onClick={() => loadMore('today')}>更多今日订单</button>}
    </section>
    <section aria-label="历史订单" className="mt-8 border-t border-stone-300 pt-5"><h2 className="mb-3 text-xl font-bold">历史订单</h2>
      <div className="mb-3 flex flex-wrap items-center gap-3"><label>营业日 <input aria-label="历史营业日" type="date" value={historyDate} onChange={event => setHistoryDate(event.target.value)} className="min-h-12 rounded-xl border bg-white px-3" /></label>
        <button disabled={!historyDate || historyView?.loading} onClick={() => void loadHistory()} className="min-h-12 rounded-xl bg-white px-5 font-bold disabled:opacity-40">查询历史</button></div>
      {historyView?.loading && <p role="status">正在加载历史订单…</p>}{historyView?.error && <p role="alert">{historyView.error}</p>}
      {historyView?.page && <><p className="mb-3 text-stone-600">{historyView.date}</p>{historyView.page.orders.length === 0 && <p>该营业日暂无订单。</p>}{renderOrders(historyView.page.orders)}
        {historyView.page.has_more && <button disabled={historyView.loading} onClick={() => void loadHistory(true)} className="mt-3 min-h-12 rounded-xl bg-white px-5 font-bold">更多历史订单</button>}</>}
    </section>
  </div></main>
}
