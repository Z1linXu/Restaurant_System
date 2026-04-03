import { Card } from '../../../components/ui/Card'
import type { BackendFrontdeskOrderBoardItem } from '../../../types/ordering'

interface OrderMiniCardProps {
  order: BackendFrontdeskOrderBoardItem
  selected: boolean
  onClick: () => void
  compact?: boolean
}

function displayLabel(order: BackendFrontdeskOrderBoardItem) {
  return order.table_no ?? order.pickup_no ?? `#${order.order_id}`
}

export function OrderMiniCard({ order, selected, onClick, compact = false }: OrderMiniCardProps) {
  const isActive = ['submitted', 'preparing', 'ready'].includes(order.order_status)

  return (
    <button type="button" className="w-full text-left" onClick={onClick}>
      <Card
        tone="feature"
        className={`transition ${
          selected ? 'bg-[rgba(97,0,0,0.08)] ring-[rgba(97,0,0,0.14)]' : 'bg-[var(--surface-container-lowest)]'
        } ${compact ? 'rounded-[18px] p-3 shadow-[0_8px_22px_rgba(26,28,25,0.05)]' : 'rounded-[22px] p-4'}`}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className={`font-display font-extrabold tracking-[-0.06em] text-[var(--on-surface)] ${compact ? 'text-[1.45rem]' : 'text-[1.75rem]'}`}>
              {displayLabel(order)}
            </div>
            <div className={`${compact ? 'mt-0.5 text-[0.72rem]' : 'mt-1 text-[0.8rem]'} font-semibold uppercase tracking-[0.12em] text-[var(--muted)]`}>
              {order.order_status}
            </div>
          </div>
          <span
            className={`rounded-full font-bold ${
              isActive
                ? 'bg-[rgba(97,0,0,0.08)] text-[var(--primary)]'
                : 'bg-[rgba(26,28,25,0.06)] text-[var(--muted)]'
            } ${compact ? 'px-2 py-1 text-[0.7rem]' : 'px-2.5 py-1 text-[0.78rem]'}`}
          >
            {order.order_type === 'pickup' ? 'TAKEOUT' : 'DINE-IN'}
          </span>
        </div>
      </Card>
    </button>
  )
}
