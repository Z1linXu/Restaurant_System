import { useRef, useState } from 'react'
import { useCurrentStore } from '../store/useStoreContext'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { useUberInbox } from './useUberInbox'
import { canDecideUberOrder, decideUberOrder, reprintUberKitchen, type UberItem, type UberOrder } from '../../services/uberEatsService'
import { printResultMessage, reprintErrorMessage } from '../../services/manualReprintService'
const labels: Record<string, string> = { WAITING_FOR_RELEASE: '等待 Uber 释放到厨房', RELEASED_MAPPING_REQUIRED: '已释放：菜单匹配不完整，尚未送入厨房', MIRROR_READY: '正在送入厨房', MIRROR_LOCAL_FAILED: '厨房生产正在恢复', RELEASED_TO_KITCHEN: '已送入厨房', PRINTED: '厨房打印完成', PRINT_PARTIAL: '部分厨房票已打印，请核对', PRINT_FAILED: '厨房打印失败，请核对', CANCELLED_AFTER_RELEASE: 'Uber 已取消：请核对厨房，历史任务与票据保留', RECEIVED: '正在获取订单', PENDING: '等待接单', MAPPING_REQUIRED: '需要匹配菜单', ACCEPTING: '正在确认接单', UBER_ACCEPTED: '正在送入厨房', ACCEPTED: '已接单', DENYING: '正在拒单', DENIED: '已拒单', CANCELLED: '已取消', CANCELLED_REVIEW_REQUIRED: 'Uber 已取消：请立即核对厨房与已打印票据', EDIT_REVIEW_REQUIRED: 'Uber 订单已修改：请人工核对，厨房内容未自动修改', SCHEDULED_REVIEW_REQUIRED: '预约订单：等待正式来单通知', LOCAL_FAILED: 'Uber 已接单，本地生产正在恢复', LOCAL_REVIEW_REQUIRED: '送入厨房失败：请联系管理员', DECISION_REVIEW_REQUIRED: '接单结果不明确，请先在 Uber 核对', EXTERNAL_STATE_REVIEW_REQUIRED: 'Uber 订单状态已变化，请核对' }
function Items({ items }: { items: UberItem[] }) { return <ul className="space-y-2">{items.map((item, index) => <li key={`${item.id}-${index}`}><span className="font-bold">{item.removed ? '去除 ' : ''}{item.title} ×{item.quantity}</span>{item.notes && <p className="text-amber-900">备注：{item.notes}</p>}{item.modifiers.length > 0 && <div className="ml-4 border-l-2 pl-3 text-sm"><Items items={item.modifiers} /></div>}</li>)}</ul> }
export default function UberInboxPage() {
  const { storeId } = useCurrentStore()
  const { orders, loading, error, refresh } = useUberInbox(storeId)
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
  return <main className="min-h-screen bg-stone-100 p-4 text-stone-900"><FrontdeskTopNav /><div className="mx-auto max-w-6xl py-6"><div className="mb-4 flex items-center justify-between"><h1 className="text-2xl font-black">Uber Eats / 今日订单</h1><button className="min-h-12 rounded-xl bg-white px-5" onClick={refresh}>刷新</button></div>
    <p className="mb-4 text-stone-600">查看 Uber 订单与厨房打印记录。未完成或需要核对的往日订单也会保留。</p>
    {(error || actionError) && <p role="alert" className="mb-4 rounded-xl bg-red-100 p-4">{actionError || error}</p>}
    {printMessage && <p role="status" className="mb-4 rounded-xl bg-emerald-50 p-4">{printMessage}</p>}
    {loading && <p role="status">正在加载…</p>}{!loading && !error && orders.length === 0 && <p>暂无 Uber Eats 订单。</p>}
    <div className="grid gap-4 lg:grid-cols-2">{orders.map(order => <article key={order.id} className="rounded-2xl bg-white p-5 shadow-sm"><div className="flex justify-between gap-3"><h2 className="text-xl font-black">{order.processing_mode === 'KITCHEN_MIRROR' ? order.customer_header : `Uber Eats #${order.display_id || order.uber_order_id.slice(-5)}`}</h2><span className="text-sm">{order.placed_at ? new Date(`${order.placed_at}Z`).toLocaleString() : order.created_at.replace('T', ' ').slice(0, 19)}</span></div>
      <p className={`my-3 rounded-lg p-3 font-semibold ${order.status.includes('REVIEW') || order.status.includes('MAPPING_REQUIRED') || order.status === 'CANCELLED_AFTER_RELEASE' ? 'bg-amber-100' : 'bg-emerald-50'}`}>{labels[order.status] || order.status}</p>
      {order.processing_mode === 'KITCHEN_MIRROR' && <div className="mb-3 text-sm text-stone-600"><p>Uber #{order.display_id || order.uber_order_id.slice(-5)} · 接单与付款由 Uber 处理</p><p>厨房释放：{order.released_at ? new Date(`${order.released_at}Z`).toLocaleString() : '等待 Uber 通知'}</p></div>}
      {order.mapped_items && order.mapped_items.length > 0 && <div className="mb-3 rounded-lg bg-stone-50 p-3"><p className="font-semibold">厨房内容</p>{order.mapped_items.map((item, index) => <p key={index}>{item}</p>)}</div>}
      {order.snapshot && <><Items items={order.snapshot.items} />{order.snapshot.notes && <p className="mt-3 font-bold text-amber-900">订单备注：{order.snapshot.notes}</p>}</>}
      <p className="mt-3 text-sm">菜单匹配：{order.mapping_status === 'MAPPED' ? '已匹配' : order.mapping_status}</p>
      {order.mapping_errors.length > 0 && <div role="alert" className="mt-2 rounded-lg bg-amber-50 p-3"><p className="font-bold">Uber 菜品无法匹配本地菜单，厨房尚未释放，请联系管理员。</p><ul className="list-inside list-disc break-words text-sm">{order.mapping_errors.map((message, i) => <li key={i}>{message}</li>)}</ul></div>}
      {order.local_order_id && <p className="mt-2">本地订单 #{order.local_order_id}</p>}
      {order.processing_mode === 'KITCHEN_MIRROR' && order.local_order_id && <div className="mt-4 space-y-2"><p>GRAB：{order.grab_status}</p>{order.hot_kitchen_status !== 'NOT_REQUIRED' && <p>HOT KITCHEN：{order.hot_kitchen_status}</p>}<div className="flex gap-3"><button disabled={busy.includes(order.id) || order.grab_status === 'WAITING'} className="min-h-14 rounded-xl border px-5 font-bold disabled:opacity-40" onClick={() => void reprint(order, 'GRAB')}>重打 GRAB</button>{order.hot_kitchen_status !== 'NOT_REQUIRED' && order.hot_kitchen_status !== 'WAITING' && <button disabled={busy.includes(order.id)} className="min-h-14 rounded-xl border px-5 font-bold disabled:opacity-40" onClick={() => void reprint(order, 'HOT_KITCHEN')}>重打 HOT KITCHEN</button>}</div></div>}
      {canDecideUberOrder(order) && <div className="mt-4 flex gap-3"><button disabled={busy.includes(order.id) || order.mapping_status !== 'MAPPED'} className="min-h-14 flex-1 rounded-xl bg-emerald-800 px-5 font-bold text-white disabled:opacity-40" onClick={() => void act(order, 'accept')}>{busy.includes(order.id) ? '处理中…' : 'Accept / 接单'}</button><button disabled={busy.includes(order.id)} className="min-h-14 rounded-xl border border-red-800 px-5 font-bold text-red-800 disabled:opacity-40" onClick={() => setDenyOrder(order.id)}>Deny / 拒单</button></div>}
      {denyOrder === order.id && <div className="mt-3 flex flex-wrap gap-3"><label>拒单原因 <select className="min-h-12 border p-2" value={reason} onChange={e => setReason(e.target.value)}><option value="CAPACITY">厨房繁忙</option><option value="ITEM_AVAILABILITY">菜品售罄</option><option value="MISSING_ITEM">菜单匹配不完整</option><option value="STORE_CLOSED">门店已关</option><option value="OTHER">其他</option></select></label><button disabled={busy.includes(order.id)} className="min-h-12 rounded-xl bg-red-800 px-5 text-white" onClick={() => void act(order, 'deny')}>确认拒单</button><button className="min-h-12 px-3" onClick={() => setDenyOrder(null)}>返回</button></div>}
      {order.status === 'LOCAL_REVIEW_REQUIRED' && <button disabled={busy.includes(order.id)} className="mt-3 min-h-12 rounded-xl border px-5" onClick={() => void act(order, 'retry-local')}>重试送入厨房</button>}
    </article>)}</div></div></main>
}
