import { useCallback, useEffect, useState } from 'react'
import { Button } from '../../components/ui/Button'
import { fetchTemplatePrintDiagnostics, templateTicketNeedsAttention, type TemplatePrintTicket } from '../../services/templatePlatformService'
import { ReceiptPreview } from './ReceiptPreview'
import { ApiRequestError } from '../../services/apiClient'

export function TemplatePrintDiagnosticsPanel({ storeId, orderId, batchId, poll = false, hideWhenEmpty = false }: { storeId: number; orderId?: number; batchId?: number | null; poll?: boolean; hideWhenEmpty?: boolean }) {
  const [tickets, setTickets] = useState<TemplatePrintTicket[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refresh, setRefresh] = useState(0)
  const [unsupported, setUnsupported] = useState(false)
  const reload = useCallback(() => setRefresh((value) => value + 1), [])
  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    const controller = new AbortController()
    let checks = 0
    setTickets([]); setLoading(true); setError(null); setUnsupported(false)
    const load = async () => {
      let shouldPoll = true
      try {
        const result = await fetchTemplatePrintDiagnostics(storeId, { orderId, batchId, signal: controller.signal })
        if (!active) return
        const scoped = result.tickets.filter((ticket) => (orderId == null || ticket.order_id === orderId)
          && (batchId == null ? orderId == null || ticket.order_update_batch_id == null : ticket.order_update_batch_id === batchId))
        setTickets(scoped); setError(null)
        if (hideWhenEmpty && !scoped.length) shouldPoll = false
      } catch (cause) {
        if (active && cause instanceof ApiRequestError && (cause.status === 404 || cause.code === 'TEMPLATE_FEATURE_DISABLED')) {
          setUnsupported(true); shouldPoll = false
        } else if (active) {
          setError(cause instanceof Error ? cause.message : 'Unable to read ticket status / 无法读取票据状态')
          if (cause instanceof ApiRequestError && [401, 403, 409].includes(cause.status)) shouldPoll = false
        }
      } finally {
        if (active) {
          setLoading(false)
          if (poll && shouldPoll && checks++ < 20) timer = setTimeout(() => void load(), 3000)
        }
      }
    }
    void load()
    return () => { active = false; controller.abort(); if (timer) clearTimeout(timer) }
  }, [storeId, orderId, batchId, poll, refresh, hideWhenEmpty])

  const warnings = tickets.filter(templateTicketNeedsAttention)
  if (unsupported || (hideWhenEmpty && !tickets.length && !error)) return null
  return <section aria-label="Persistent kitchen ticket diagnostics" className="space-y-3 rounded-2xl border border-[rgba(26,28,25,0.12)] bg-white p-4">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="font-bold">{orderId != null ? `Order #${orderId} · Ticket Status / 票据状态` : 'Kitchen Tickets / 厨房票与持久诊断'}</h2>
      <p className="text-sm text-[var(--muted)]">MOCK · Saved backend records / 后台保存的模拟票据记录</p></div><Button variant="secondary" onClick={reload}>Refresh tickets / 刷新票据</Button></div>
    {warnings.length ? <div role="alert" className="space-y-1 rounded-xl bg-amber-50 p-3 text-amber-950"><p className="font-bold">{orderId != null ? 'Order saved. Some tickets need attention. / 订单已保存，以下票据需要处理。' : 'Tickets need attention / 以下票据需要处理'}</p>
      {warnings.map((ticket) => <p key={ticket.source_key}>{ticket.group_name}: {ticket.reason || ticket.error_code || ticket.state}{ticket.job_id == null ? ' — print job not created / 尚未创建打印任务' : ''}</p>)}</div> : null}
    {loading ? <p>Loading ticket status / 读取票据中…</p> : null}
    {error ? <p role="alert" className="text-amber-950">{orderId != null ? 'Order saved; ticket status is temporarily unavailable. / 订单已保存，暂时无法读取票据状态。 ' : ''}{error}</p> : null}
    {!loading && !tickets.length && !error ? <p className="text-sm text-[var(--muted)]">No saved kitchen ticket intents in this view. / 此范围暂无已保存厨房票意图。</p> : null}
    {tickets.map((ticket) => <details key={ticket.source_key} className="rounded-xl border border-[rgba(26,28,25,0.1)] p-3">
      <summary className="cursor-pointer font-semibold">{ticket.group_name} · {ticket.logical_target} · {ticket.job_id == null ? 'Not created / 未创建' : ticket.state} · Order #{ticket.order_id}{ticket.order_update_batch_id != null ? ` / Update ${ticket.order_update_batch_id}` : ''}</summary>
      <div className="mt-2 space-y-2 text-sm"><p>MOCK · {ticket.reason || ticket.error_code || (ticket.state === 'PRINTED' && ticket.job_id != null ? 'Mock processing completed / 模拟处理完成' : 'See current execution state / 查看当前执行状态')}</p>
        <p className="break-all text-[var(--muted)]">Intent {ticket.intent_id} · Job {ticket.job_id ?? '—'} · {ticket.source_key} · Menu {ticket.menu_revision ?? '—'} / Routing {ticket.routing_revision ?? '—'}</p>
        <ReceiptPreview content={ticket.rendered_text} />
      </div>
    </details>)}
  </section>
}
