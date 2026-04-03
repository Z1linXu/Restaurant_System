import type { NoodleStationOrder } from '../../../../types/kds'

interface RamenOrderCardProps {
  order: NoodleStationOrder
  orderIndex: number
}

interface DisplayRow {
  key: string
  primary: string
  secondary: string | null
  quantity: number
  updated: boolean
}

function getOrderLabel(order: NoodleStationOrder) {
  const pickup = order.detail.pickup_no
  if (pickup) {
    return pickup.startsWith('外') ? pickup : `外${pickup}`
  }
  return order.detail.table_no ?? `#${order.detail.id}`
}

function getOrderBadge(order: NoodleStationOrder) {
  if (order.detail.order_type === 'pickup') {
    return '外带'
  }
  if (order.detail.order_type === 'dine_in') {
    return '堂食'
  }
  return order.detail.order_type.toUpperCase()
}

function parseTask(task: NoodleStationOrder['tasks'][number]) {
  const parts = (task.special_instructions_snapshot ?? '')
    .split('|')
    .map((part) => part.trim())
    .filter(Boolean)

  const primary = parts[0] || task.item_name_snapshot_zh
  const secondary = parts.slice(1).join(' · ') || null

  return {
    key: `${task.task_id}`,
    primary,
    secondary,
    quantity: task.quantity,
    updated: Boolean(task.item_modified_after_submit || task.order_modified_after_submit),
  }
}

function buildRows(order: NoodleStationOrder): DisplayRow[] {
  return order.tasks.reduce<DisplayRow[]>((rows, task) => {
    const parsed = parseTask(task)
    const existing = rows.find(
      (row) => row.primary === parsed.primary && row.secondary === parsed.secondary && row.updated === parsed.updated,
    )
    if (existing) {
      existing.quantity += parsed.quantity
      return rows
    }
    rows.push(parsed)
    return rows
  }, [])
}

function getSummaryBadge(order: NoodleStationOrder) {
  const totalBowls = order.tasks.reduce((sum, task) => sum + task.quantity, 0)
  return `${totalBowls}中`
}

function getOrderCreatedAt(order: NoodleStationOrder) {
  const earliestTask = [...order.tasks]
    .map((task) => task.created_at)
    .filter(Boolean)
    .sort()[0]

  const source = earliestTask ?? order.detail.created_at
  if (!source) {
    return null
  }

  return new Date(source).toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
  })
}

export function RamenOrderCard({ order, orderIndex }: RamenOrderCardProps) {
  const rows = buildRows(order)
  const createdAt = getOrderCreatedAt(order)

  return (
    <article className="mb-2.5 break-inside-avoid rounded-[16px] bg-[rgba(255,255,255,0.96)] shadow-[0_12px_24px_rgba(26,28,25,0.08)] ring-1 ring-[rgba(26,28,25,0.05)]">
      <header className="flex items-start justify-between gap-2 border-b border-[rgba(26,28,25,0.06)] px-3 py-2.5">
        <div>
          <div className="flex items-center gap-2">
            <span className="rounded-[8px] bg-[rgba(138,22,22,0.08)] px-2 py-0.5 text-[0.68rem] font-black tracking-[0.14em] text-[var(--primary)]">
              {String(orderIndex).padStart(2, '0')}
            </span>
            <div className="text-[1.5rem] font-extrabold tracking-[-0.06em] text-[var(--on-surface)]">
              {getOrderLabel(order)}
            </div>
          </div>
          <div className="mt-0.5 flex items-center gap-2 text-[0.72rem] font-semibold text-[var(--muted)]">
            <span>{getOrderBadge(order)}</span>
            {createdAt ? <span>· {createdAt}</span> : null}
          </div>
        </div>
        <div className="rounded-[10px] bg-[rgba(26,28,25,0.06)] px-2 py-1 text-[0.74rem] font-bold text-[var(--on-surface)]">
          {getSummaryBadge(order)}
        </div>
      </header>

      <div className="space-y-2 px-3 py-2.5">
        {rows.map((row) => (
          <div key={row.key} className="rounded-[10px] bg-[rgba(26,28,25,0.025)] px-2.5 py-2">
            <div className="flex items-baseline justify-between gap-2">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-1.5">
                  <div className="text-[1.7rem] font-black tracking-[-0.08em] text-[var(--on-surface)]">
                    {row.primary}
                  </div>
                  {row.updated ? (
                    <span className="rounded-[8px] bg-[rgba(138,22,22,0.12)] px-1.5 py-0.5 text-[0.62rem] font-black uppercase tracking-[0.12em] text-[var(--primary)]">
                      Updated
                    </span>
                  ) : null}
                </div>
              </div>
              <div className="shrink-0 text-[1.2rem] font-extrabold tracking-[-0.05em] text-[var(--primary)]">
                ×{row.quantity}
              </div>
            </div>
            {row.secondary ? (
              <div className="mt-0.5 text-[0.72rem] font-semibold uppercase tracking-[0.03em] text-[rgba(87,64,54,0.82)]">
                {row.secondary}
              </div>
            ) : null}
          </div>
        ))}
      </div>
    </article>
  )
}
