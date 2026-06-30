import type { BackendOrderResponse, OrderPrintOption } from '../../../types/ordering'

interface OrderHistoryDetailProps {
  order: BackendOrderResponse | null
  loading: boolean
  printOptions: OrderPrintOption[]
  printBusy: string | null
  onReprint: (option: OrderPrintOption) => void
}

export function OrderHistoryDetail({ order, loading, printOptions, printBusy, onReprint }: OrderHistoryDetailProps) {
  if (loading) {
    return <div className="rounded-[28px] bg-white p-8 text-center text-[var(--muted)]">Loading order detail...</div>
  }
  if (!order) {
    return <div className="rounded-[28px] bg-white p-8 text-center text-[var(--muted)]">Select an order to view details.</div>
  }

  const location = order.table_no || order.pickup_no || 'Walk-in'
  return (
    <section className="rounded-[28px] bg-white p-5 shadow-[0_16px_36px_rgba(26,28,25,0.06)]">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-stone-100 pb-4">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Order detail / 订单详情</p>
          <h2 className="mt-1 text-3xl font-extrabold">{location}</h2>
        </div>
        <span className="rounded-full bg-stone-100 px-4 py-2 font-bold capitalize">{order.status}</span>
      </div>

      <div className="mt-4 space-y-3">
        {order.items.map((item) => (
          <div key={item.id} className="rounded-[18px] bg-stone-50 px-4 py-3">
            <div className="flex justify-between gap-4">
              <div>
                <p className="text-lg font-bold">{item.item_name_snapshot_zh || item.item_name_snapshot_en}</p>
                {item.options.length ? (
                  <p className="mt-1 text-sm text-[var(--muted)]">
                    {item.options.map((option) => option.option_name_snapshot_zh || option.option_name_snapshot_en).join(' · ')}
                  </p>
                ) : null}
                {item.notes ? <p className="mt-1 text-sm font-semibold">备注：{item.notes}</p> : null}
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
        <span>Total</span>
        <span>${Number(order.total_amount).toFixed(2)}</span>
      </div>

      <div className="mt-5">
        <p className="mb-2 text-sm font-bold text-[var(--muted)]">Print / 重打完整订单</p>
        <div className="grid gap-2 sm:grid-cols-2">
          {printOptions.map((option) => (
            <button
              key={option.module_code}
              type="button"
              disabled={!option.available || printBusy != null}
              onClick={() => onReprint(option)}
              className="min-h-12 rounded-[16px] bg-[var(--primary)] px-4 font-bold text-white disabled:bg-stone-200 disabled:text-stone-500"
            >
              {printBusy === option.module_code ? 'Printing...' : option.label}
              {!option.available ? <span className="block text-xs">{option.unavailable_reason}</span> : null}
            </button>
          ))}
        </div>
      </div>
    </section>
  )
}
