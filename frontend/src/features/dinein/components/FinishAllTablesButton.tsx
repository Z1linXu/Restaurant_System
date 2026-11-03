import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useAuth } from '../../auth/useAuth'
import { useCurrentStore } from '../../store/useStoreContext'
import { hasUnsubmittedLocalWork, listLocalDraftsForScope, type LocalDraftScope } from '../../../offline/localDrafts'
import { listOrderOutboxForAccount } from '../../../offline/orderOutbox'
import { finishAllTables, previewFinishAllTables, type FinishAllPreview, type FinishAllResult } from '../../../services/finishAllTablesService'

const PROTECTED = new Set(['QUEUED', 'SUBMITTING', 'CONFLICT', 'FAILED_RETRYABLE', 'FAILED_VALIDATION'])

/** Read only: this action never deletes or finalizes drafts/outbox records. */
async function finishAllLocalWarnings(scope: LocalDraftScope) {
  const [drafts, outbox] = await Promise.all([listLocalDraftsForScope(scope), listOrderOutboxForAccount(scope.accountId)])
  const local = drafts.filter(d => d.context.orderType === 'dine_in'
    && !['SUBMITTED', 'COMPLETED', 'CANCELLED', 'CANCELLED_LOCAL'].includes(d.submitState)
    && (hasUnsubmittedLocalWork(d) || PROTECTED.has(d.submitState)))
  const pending = outbox.filter(o => o.storeId === scope.storeId && o.organizationId === scope.organizationId
    && o.frozenPayload.order_type === 'dine_in' && PROTECTED.has(o.state))
  return { drafts: local.length, pending: pending.length,
    blocked: pending.length > 0 || local.some(d => d.mode === 'SERVER_ORDER_UPDATE' || PROTECTED.has(d.submitState)) }
}

export function FinishAllTablesButton({ unsaved = false, onFinished }: {
  unsaved?: boolean
  onFinished: (result: FinishAllResult) => void
}) {
  const { user, permissions } = useAuth()
  const { storeId, organizationId } = useCurrentStore()
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [preview, setPreview] = useState<FinishAllPreview | null>(null)
  const [result, setResult] = useState<FinishAllResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [warnings, setWarnings] = useState({ drafts: 0, pending: 0, blocked: false })
  const busyRef = useRef(false)
  const mounted = useRef(true)
  const key = `finish-all-confirmed:${user?.id}:${organizationId}:${storeId}`
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const scope = () => {
    if (!user?.id || !organizationId) throw new Error('Please sign in again / 请重新登录')
    return { accountId: user.id, organizationId, storeId }
  }
  const openPreview = async () => {
    if (busyRef.current || unsaved) return
    busyRef.current = true; setBusy(true); setOpen(true); setError(null); setResult(null); setPreview(null)
    try {
      let previous: FinishAllPreview | null = null
      const stored = sessionStorage.getItem(key)
      if (stored) {
        try { previous = JSON.parse(stored) as FinishAllPreview } catch { sessionStorage.removeItem(key) }
      }
      const [next, local] = await Promise.all([
        previous?.store_id === storeId ? Promise.resolve(previous) : previewFinishAllTables(storeId),
        finishAllLocalWarnings(scope()),
      ])
      if (!mounted.current) return
      setPreview(next); setWarnings(local)
      if (previous) setError('Previous confirmed request may have completed. Retry checks the same batch; it never includes new orders. / 上次请求结果待确认，重试仅处理原订单集合。')
    } catch (e) { if (mounted.current) setError(e instanceof Error ? e.message : 'Unable to load affected orders / 无法获取订单') }
    finally { busyRef.current = false; if (mounted.current) setBusy(false) }
  }
  const confirm = async () => {
    if (busyRef.current || !preview || preview.order_count === 0 || unsaved || warnings.blocked) return
    busyRef.current = true; setBusy(true); setError(null)
    try {
      const local = await finishAllLocalWarnings(scope())
      if (!mounted.current) return
      setWarnings(local)
      if (local.blocked) throw new Error('Resolve pending local updates/outbox first; all data is retained. / 请先处理待提交订单或本地修改，内容均已保留。')
      // Save before sending: timeout/reload retry must reuse the originally confirmed set.
      sessionStorage.setItem(key, JSON.stringify(preview))
      const response = await finishAllTables(storeId, preview.preview_token)
      if (!mounted.current) return
      setResult(response); sessionStorage.removeItem(key)
    } catch (e) {
      if (mounted.current) {
        const message = e instanceof Error ? e.message : 'Request failed'
        if (message.includes('FINISH_PREVIEW_EXPIRED')) { sessionStorage.removeItem(key); setPreview(null) }
        setError(`${message} · Retry uses the same confirmed orders / 重试仅处理已确认的订单。`)
      }
    }
    finally { busyRef.current = false; if (mounted.current) setBusy(false) }
  }
  if (!permissions.includes('ORDER_COMPLETE')) return null
  return <>
    <div className="flex flex-wrap items-center gap-2">
      <button type="button" disabled={busy || unsaved} onClick={() => void openPreview()}
        className="min-h-12 rounded-[18px] border border-red-800/30 bg-red-50 px-4 py-2 text-sm font-bold text-red-900 disabled:opacity-50">
        {busy ? 'Loading / 处理中…' : 'Finish All Tables / 结束所有桌台'}
      </button>
      {unsaved ? <span className="text-xs text-amber-900">Submit or resolve current changes first / 请先提交或处理当前修改</span> : null}
    </div>
    {open ? createPortal(<div className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/40 p-3">
      <section role="dialog" aria-modal="true" aria-labelledby="finish-all-title" className="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-3xl bg-white p-5 text-[var(--on-surface)] shadow-xl">
        <h2 id="finish-all-title" className="text-xl font-black">Finish All Tables? / 确认结束所有桌台？</h2>
        {!result ? <>
          <p className="mt-3 text-sm">This action will finish all active dine-in orders and release their tables.<br />此操作将结束当前门店所有进行中的堂食订单，并释放对应桌台。</p>
          {preview ? <div className="mt-4 space-y-2">
            <p className="font-bold">{preview.store_name}</p>
            <p>{preview.order_count} orders / 订单 · {preview.table_slot_count} table slots to release / 预计释放桌位</p>
            <ul className="max-h-40 overflow-y-auto rounded-xl bg-stone-50 p-3 text-sm">{preview.orders.map(o => <li key={o.order_id}>{o.table_display_name} · #{o.order_id}</li>)}</ul>
            {preview.order_count === 0 ? <p>No active tables to finish / 没有可结束的进行中桌台</p> : null}
            {preview.blocked_tables.length ? <p className="text-sm text-amber-900">Draft/unsupported orders remain; these tables may stay occupied / 草稿或不可结束订单将保留：{preview.blocked_tables.join('、')}</p> : null}
          </div> : null}
          {warnings.drafts || warnings.pending ? <p className="mt-3 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">This Pad: {warnings.drafts} local drafts, {warnings.pending} pending orders. Nothing will be cleared. / 本机有 {warnings.drafts} 份草稿、{warnings.pending} 个待处理订单，内容不会清除。{warnings.blocked ? ' 请先处理待提交内容。' : ' 未提交草稿不会被结束。'}</p> : null}
          <p className="mt-2 text-xs text-stone-600">Other Pads may have local work that this device cannot see. New orders are excluded. / 其他设备的本地内容在此不可见；确认后的新订单不会被结束。</p>
        </> : <div className="mt-4 space-y-3" role="status">
          <p>Completed / 已完成：{result.completed} · Skipped / 跳过：{result.skipped} · Failed / 失败：{result.failed}</p>
          <p>Released table slots / 已释放桌位：{result.released_slot_count}</p>
          {result.results.filter(r => r.outcome !== 'completed').map(r => <p key={r.order_id} className="text-sm text-amber-900">{r.table_no} #{r.order_id}：{r.reason}</p>)}
          {result.remaining_tables.length ? <p className="text-sm text-amber-900">Still occupied / 尚未释放：{result.remaining_tables.join('、')}</p> : null}
        </div>}
        {error ? <p role="alert" className="mt-3 text-sm text-red-900">{error}</p> : null}
        <div className="mt-5 flex flex-wrap justify-end gap-3">
          {result ? <button type="button" className="min-h-12 rounded-xl bg-[var(--primary)] px-4 font-bold text-white" onClick={() => { setOpen(false); onFinished(result) }}>Back to Tables / 返回桌台</button> : <>
            <button type="button" disabled={busy} className="min-h-12 rounded-xl border px-4 font-bold disabled:opacity-50" onClick={() => { setOpen(false); setPreview(null) }}>Cancel / 取消</button>
            <button type="button" disabled={busy || !preview?.order_count || warnings.blocked || unsaved} onClick={() => void confirm()} className="min-h-12 rounded-xl bg-red-900 px-4 font-bold text-white disabled:opacity-50">{busy ? 'Processing / 处理中…' : 'Confirm Finish All / 确认全部结束'}</button>
          </>}
        </div>
      </section>
    </div>, document.body) : null}
  </>
}
