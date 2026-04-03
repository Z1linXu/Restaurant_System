import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { Card } from '../../../components/ui/Card'
import type { PickupBoardOrder } from '../../../hooks/usePickupBoard'

interface PickupOrderCardProps {
  order: PickupBoardOrder
  busyTaskIds: Set<number>
  onCompleteItem: (taskId: number) => void
  onCompleteAll: (orderId: number) => void
}

function formatReadyAgo(readyAt: string | null) {
  if (!readyAt) {
    return 'READY now'
  }
  const diffMs = Date.now() - new Date(readyAt).getTime()
  const minutes = Math.max(0, Math.floor(diffMs / 60000))
  return `READY ${minutes}m ago`
}

function getOrderLabel(order: PickupBoardOrder) {
  return order.tableNo ?? order.pickupNo ?? `#${order.orderId}`
}

function getCategoryLabel(categoryCode: string | null) {
  switch (categoryCode) {
    case 'SOUP_NOODLE':
    case 'DRY_NOODLE':
    case 'FRIED_NOODLE':
    case 'NOODLES':
      return 'NOODLE'
    case 'SIDE':
    case 'SIDE_DISHES':
      return 'SIDE'
    case 'FRIED':
    case 'FRIED_ITEMS':
      return 'FRIED'
    default:
      return null
  }
}

function getSizeChip(item: PickupBoardOrder['items'][number]) {
  if (item.category_code_snapshot !== 'SOUP_NOODLE' && item.category_code_snapshot !== 'DRY_NOODLE') {
    return null
  }
  if (item.special_instructions_snapshot?.includes('大')) {
    return '大'
  }
  if (item.special_instructions_snapshot?.includes('中')) {
    return '中'
  }
  if (item.size_label?.includes('大碗')) {
    return '大'
  }
  if (item.size_label?.includes('中碗') || item.size_label?.includes('Regular')) {
    return '中'
  }
  return null
}

function shouldShowNoodleDetails(item: PickupBoardOrder['items'][number]) {
  return item.category_code_snapshot === 'SOUP_NOODLE' || item.category_code_snapshot === 'DRY_NOODLE'
}

export function PickupOrderCard({ order, busyTaskIds, onCompleteItem, onCompleteAll }: PickupOrderCardProps) {
  return (
    <Card tone="feature" className="rounded-[24px] bg-[var(--surface-container-lowest)] p-4 shadow-[0_14px_32px_rgba(26,28,25,0.08)]">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <h3 className="font-display text-[2rem] font-extrabold tracking-[-0.06em] text-[var(--on-surface)]">
            {getOrderLabel(order)}
          </h3>
          <p className="mt-1 text-[0.84rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">
            {formatReadyAgo(order.readyAt)}
          </p>
        </div>
        <Badge variant={order.orderType === 'pickup' ? 'warm' : 'accent'}>
          {order.orderType === 'pickup' ? 'Pickup' : 'Dine-in'}
        </Badge>
      </div>

      <div className="space-y-2.5">
        {order.items.map((item) => {
          const categoryLabel = getCategoryLabel(item.category_code_snapshot)
          const sizeChip = getSizeChip(item)
          const busy = busyTaskIds.has(item.task_id)
          return (
            <div
              key={item.task_id}
              className="flex items-center justify-between gap-3 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-3 py-3"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  {item.quantity > 1 ? (
                    <span className="text-[1rem] font-bold text-[var(--primary)]">{item.quantity}x</span>
                  ) : null}
                  {sizeChip ? (
                    <span className="inline-flex min-w-8 items-center justify-center rounded-full bg-[rgba(97,0,0,0.1)] px-2 py-0.5 text-[0.78rem] font-bold text-[var(--primary)]">
                      {sizeChip}
                    </span>
                  ) : null}
                  <p className="truncate text-[1.02rem] font-semibold text-[var(--on-surface)]">
                    {item.item_name_snapshot_zh}
                  </p>
                </div>
                {categoryLabel ? (
                  <div className="mt-1">
                    <Badge variant="neutral" className="px-2 py-0.5 text-[0.68rem]">
                      {categoryLabel}
                    </Badge>
                  </div>
                ) : null}
                {shouldShowNoodleDetails(item) && item.special_instructions_snapshot ? (
                  <p className="mt-1 text-[0.86rem] font-medium leading-5 text-[var(--muted)]">
                    {item.special_instructions_snapshot}
                  </p>
                ) : null}
              </div>

              <Button
                size="md"
                className="min-h-11 rounded-[16px] px-4 text-[0.84rem]"
                disabled={busy}
                onClick={() => onCompleteItem(item.task_id)}
              >
                {busy ? '...' : 'COMPLETE'}
              </Button>
            </div>
          )
        })}
      </div>

      {order.items.length > 1 ? (
        <Button
          variant="secondary"
          fullWidth
          className="mt-3 min-h-11 rounded-[16px] text-[0.84rem]"
          onClick={() => onCompleteAll(order.orderId)}
        >
          ALL COMPLETE
        </Button>
      ) : null}
    </Card>
  )
}
