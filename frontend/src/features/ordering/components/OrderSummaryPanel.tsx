import { Button } from '../../../components/ui/Button'
import type { OrderSession } from '../../../types/ordering'
import { OrderLineItemRow } from './OrderLineItemRow'

interface OrderSummaryPanelProps {
  session: OrderSession
  subtotal: number
  tax: number
  total: number
  busy?: boolean
  onIncrementItem: (itemId: string) => void
  onDecrementItem: (itemId: string) => void
  onEditItem: (itemId: string) => void
  onRemoveItem: (itemId: string) => void
  onUpdateItemNote: (itemId: string, notes: string) => void
  onSaveDraft: () => void
  onCancelOrder: () => void
  onSubmitOrder: () => void
  compact?: boolean
}

export function OrderSummaryPanel({
  session,
  subtotal,
  tax,
  total,
  busy = false,
  onIncrementItem,
  onDecrementItem,
  onEditItem,
  onRemoveItem,
  onUpdateItemNote,
  onSaveDraft,
  onCancelOrder,
  onSubmitOrder,
  compact = false,
}: OrderSummaryPanelProps) {
  const statusLabel =
    session.status === 'draft'
      ? 'Draft / 草稿'
      : session.status === 'preparing'
        ? 'Preparing / 制作中'
        : 'Submitted / 已提交'
  const isDraft = session.status === 'draft'
  const canConfirmUpdate = !isDraft && session.isModifiedAfterSubmit
  const primaryActionLabel = isDraft
    ? 'Submit Order / 提交订单'
    : canConfirmUpdate
      ? 'Update Order / 更新订单'
      : 'Order In Progress / 订单进行中'
  const primaryActionDisabled = busy || (!isDraft && !canConfirmUpdate)

  return (
    <div className={`flex h-full flex-col bg-[rgba(255,255,255,0.82)] shadow-[0_18px_42px_rgba(26,28,25,0.06)] ${compact ? 'gap-3 rounded-[24px] p-3.5' : 'gap-5 rounded-[32px] p-5'}`}>
      <div className={`bg-[rgba(26,28,25,0.04)] ${compact ? 'rounded-[18px] px-4 py-3' : 'rounded-[24px] px-5 py-4'}`}>
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">Current order</p>
        <div className="mt-2 flex items-end justify-between gap-4">
          <div>
            <p className={`${compact ? 'text-[1.35rem]' : 'text-[1.7rem]'} font-bold tracking-[-0.04em] text-[var(--on-surface)]`}>{session.slotLabel}</p>
            <p className={`${compact ? 'text-[0.78rem]' : 'text-sm'} font-medium text-[var(--muted)]`}>{session.orderId}</p>
          </div>
          <span className={`rounded-full bg-[rgba(97,0,0,0.08)] font-bold text-[var(--primary)] ${compact ? 'px-2.5 py-1 text-[0.72rem]' : 'px-3 py-1.5 text-sm'}`}>
            {statusLabel}
          </span>
        </div>
      </div>

      <div className={`min-h-0 flex-1 overflow-y-auto pr-1 ${compact ? 'space-y-2.5' : 'space-y-4'}`}>
        {session.items.length ? (
          session.items.map((item) => (
            <OrderLineItemRow
              key={item.id}
              item={item}
              compact={compact}
              onIncrement={() => onIncrementItem(item.id)}
              onDecrement={() => onDecrementItem(item.id)}
              onEdit={() => onEditItem(item.id)}
              onRemove={() => onRemoveItem(item.id)}
              onUpdateNote={(notes) => onUpdateItemNote(item.id, notes)}
            />
          ))
        ) : (
          <div className={`rounded-[18px] bg-[rgba(26,28,25,0.04)] text-center text-[var(--muted)] ${compact ? 'px-4 py-6 text-[0.88rem]' : 'px-5 py-8 text-[1.02rem]'}`}>
            Select items from the menu to build this order.
          </div>
        )}
      </div>

      <div className={`bg-[rgba(26,28,25,0.04)] ${compact ? 'space-y-2 rounded-[18px] px-4 py-3.5' : 'space-y-3 rounded-[24px] px-5 py-5'}`}>
        <div className={`flex items-center justify-between text-[rgba(83,58,50,0.82)] ${compact ? 'text-[0.92rem]' : 'text-lg'}`}>
          <span>Subtotal / 小计</span>
          <span>${subtotal.toFixed(2)}</span>
        </div>
        <div className={`flex items-center justify-between text-[rgba(83,58,50,0.82)] ${compact ? 'text-[0.92rem]' : 'text-lg'}`}>
          <span>Tax (8.5%) / 税</span>
          <span>${tax.toFixed(2)}</span>
        </div>
        <div className={`flex items-center justify-between font-extrabold tracking-[-0.04em] text-[var(--on-surface)] ${compact ? 'text-[1.45rem]' : 'text-[2rem]'}`}>
          <span>Total / 合计</span>
          <span className="text-[var(--primary)]">${total.toFixed(2)}</span>
        </div>
      </div>

      <div className={`grid grid-cols-2 ${compact ? 'gap-2' : 'gap-3'}`}>
        <Button variant="secondary" className={compact ? 'min-h-11 rounded-[16px] text-[0.84rem]' : 'min-h-14 rounded-[20px]'} onClick={onSaveDraft}>
          {busy ? 'Saving...' : 'Save Draft'}
        </Button>
        <Button variant="secondary" className={compact ? 'min-h-11 rounded-[16px] text-[0.84rem]' : 'min-h-14 rounded-[20px]'} onClick={onCancelOrder}>
          Cancel Order
        </Button>
      </div>

      <Button
        className={compact ? 'min-h-12 rounded-[18px] text-[0.95rem]' : 'min-h-16 rounded-[24px] text-lg'}
        onClick={onSubmitOrder}
        disabled={primaryActionDisabled}
      >
        {primaryActionLabel}
      </Button>
    </div>
  )
}
