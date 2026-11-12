import { withMenuCode } from '../../../utils/menuCode'
import { useLocale } from '../../../i18n/useLocale'
import type { BackendOrderResponse, OrderPrintOption } from '../../../types/ordering'
import { orderLocationDisplay } from '../../../utils/tableDisplay'
import type { PrintJobRecord } from '../../../services/printingAdminService'
import {
  orderStatusDisplayLabel,
  printJobDisplayLabel,
  printJobOperatorDisplayMessage,
  printOptionDisplayLabel,
  printStatusDisplayLabel,
} from '../../../utils/displayLabels'

interface OrderHistoryDetailProps {
  order: BackendOrderResponse | null
  loading: boolean
  printOptions: OrderPrintOption[]
  printJobs: PrintJobRecord[]
  printBusy: string | null
  printStatusMessage: { kind: 'success' | 'error'; message: string } | null
  onReprint: (option: OrderPrintOption) => void
}

export function OrderHistoryDetail({
  order,
  loading,
  printOptions,
  printJobs,
  printBusy,
  printStatusMessage,
  onReprint,
}: OrderHistoryDetailProps) {
  const { t, label } = useLocale()
  if (loading) {
    return <div className="rounded-[28px] bg-white p-8 text-center text-[var(--muted)]">{t('common.loading')}</div>
  }
  if (!order) {
    return <div className="rounded-[28px] bg-white p-8 text-center text-[var(--muted)]">{t('orders.select')}</div>
  }

  const location = orderLocationDisplay(order, '现场')
  const attentionJobs = printJobs.filter((job) => job.status === 'FAILED' || job.status === 'CANCELLED')
  return (
    <section className="rounded-[28px] bg-white p-5 shadow-[0_16px_36px_rgba(26,28,25,0.06)]">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-stone-100 pb-4">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{t('orders.details')}</p>
          <h2 className="mt-1 text-3xl font-extrabold">{location}</h2>
        </div>
        <span className="rounded-full bg-stone-100 px-4 py-2 font-bold">{orderStatusDisplayLabel(order.status)}</span>
      </div>

      <div className="mt-4 space-y-3">
        {order.items.map((item) => (
          <div key={item.id} className="rounded-[18px] bg-stone-50 px-4 py-3">
            <div className="flex justify-between gap-4">
              <div>
                <p className="text-lg font-bold">{withMenuCode(item.item_sku_snapshot, label({ en: item.item_name_snapshot_en, zh: item.item_name_snapshot_zh, fr: item.item_name_snapshot_fr }))}</p>
                {item.options.length ? (
                  <p className="mt-1 text-sm text-[var(--muted)]">
                    {item.options.map((option) => label({ en: option.option_name_snapshot_en, zh: option.option_name_snapshot_zh, fr: option.option_name_snapshot_fr })).join(' · ')}
                  </p>
                ) : null}
                {item.notes ? <p className="mt-1 text-sm font-semibold">{t('order.note')}: {item.notes}</p> : null}
              </div>
              <div className="text-right">
                <p className="font-bold">x{item.quantity}</p>
                <p className="text-sm text-[var(--muted)]">${Number(item.line_amount).toFixed(2)}</p>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-5 flex items-center justify-between border-t border-stone-100 pt-4 text-xl font-extrabold">
        <span>{t('order.total')}</span>
        <span>${Number(order.total_amount).toFixed(2)}</span>
      </div>

      <div className="mt-5">
        <p className="mb-2 text-sm font-bold text-[var(--muted)]">{t('orders.reprintFull')}</p>
        {printStatusMessage ? (
          <div className={`mb-3 rounded-[16px] px-4 py-3 text-sm font-bold ${
            printStatusMessage.kind === 'success'
              ? 'bg-[rgba(18,141,77,0.1)] text-[rgb(25,112,69)]'
              : 'bg-[rgba(151,34,34,0.12)] text-[rgb(116,22,22)]'
          }`}>
            {printStatusMessage.message}
          </div>
        ) : null}
        {attentionJobs.length ? (
          <div className="mb-3 rounded-[18px] border border-[rgba(151,34,34,0.24)] bg-[rgba(151,34,34,0.1)] px-4 py-3 text-sm text-[rgb(116,22,22)]">
            <div className="font-black">{t('orders.printAttention')}</div>
            <div className="mt-1 font-semibold">
              {t('orders.printAttentionHint')}
            </div>
          </div>
        ) : null}
        <div className="grid gap-2 sm:grid-cols-2">
          {printOptions.map((option) => (
            <button
              key={option.module_code}
              type="button"
              disabled={!option.available || printBusy != null}
              onClick={() => onReprint(option)}
              className="min-h-12 rounded-[16px] bg-[var(--primary)] px-4 font-bold text-white disabled:bg-stone-200 disabled:text-stone-500"
            >
              {printBusy === option.module_code ? t('printing.printing') : printOptionDisplayLabel(option.module_code, option.label)}
              {!option.available ? <span className="block text-xs">{option.unavailable_reason}</span> : null}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-5 border-t border-stone-100 pt-4">
        <p className="mb-2 text-sm font-bold text-[var(--muted)]">{t('orders.printHistory')}</p>
        {printJobs.length ? (
          <div className="space-y-2">
            {printJobs.map((job) => (
              <div
                key={job.id}
                className={`rounded-[16px] px-4 py-3 ${
                  job.status === 'FAILED' || job.status === 'CANCELLED'
                    ? 'bg-[rgba(151,34,34,0.08)]'
                    : 'bg-stone-50'
                }`}
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div className="text-sm font-black text-[var(--on-surface)]">
                      {printJobDisplayLabel(job)}
                    </div>
                    <div className="mt-1 text-xs font-semibold text-[var(--muted)]">
                      {job.printer_name ?? job.printer_endpoint ?? '无打印机'} · {job.error_code ?? '无错误码'}
                    </div>
                  </div>
                  <div className="text-right">
                    <span className={`rounded-full px-2.5 py-1 text-xs font-black ${printJobStatusTone(job.status)}`}>
                      {printStatusDisplayLabel(job.status)}
                    </span>
                    <div className="mt-1 text-xs text-[var(--muted)]">
                      {formatDateTime(job.printed_at ?? job.failed_at ?? job.created_at)}
                    </div>
                  </div>
                </div>
                {job.operator_message || job.error_message ? (
                  <div className="mt-2 text-sm font-semibold text-[rgb(116,22,22)]">
                    {printJobOperatorDisplayMessage(job)}
                  </div>
                ) : null}
                {job.operator_message && job.error_message && job.operator_message !== job.error_message ? (
                  <div className="mt-1 text-xs text-[var(--muted)]">
                    {t('orders.techInfo')}: {job.error_message}
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        ) : (
          <div className="rounded-[16px] bg-stone-50 px-4 py-3 text-sm font-semibold text-[var(--muted)]">
            {t('orders.noPrintHistory')}
          </div>
        )}
      </div>
    </section>
  )
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString([], {
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function printJobStatusTone(status: PrintJobRecord['status']) {
  if (status === 'PRINTED') {
    return 'bg-[rgba(18,141,77,0.12)] text-[rgb(25,112,69)]'
  }
  if (status === 'FAILED') {
    return 'bg-[rgba(151,34,34,0.14)] text-[rgb(116,22,22)]'
  }
  if (status === 'CANCELLED') {
    return 'bg-[rgba(118,77,21,0.14)] text-[rgb(118,77,21)]'
  }
  return 'bg-stone-200 text-stone-700'
}
