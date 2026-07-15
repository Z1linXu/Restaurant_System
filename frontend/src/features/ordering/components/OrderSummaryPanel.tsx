import { Button } from '../../../components/ui/Button'
import type { OrderSession } from '../../../types/ordering'
import { TAX_RATE_LABEL } from '../../../utils/tax'
import { formatSplitSlotLabel } from '../../../utils/tableDisplay'
import { orderStatusDisplayLabel } from '../../../utils/displayLabels'
import { OrderLineItemRow } from './OrderLineItemRow'
import type { LocalDraftSubmitState } from '../../../offline/localDrafts'
import { OfflineOrderStatusPanel } from '../../offline/OfflineOrderStatusPanel'

interface OrderSummaryPanelProps {
  session: OrderSession
  subtotal: number
  tax: number
  total: number
  busy?: boolean
  localSubmitState: LocalDraftSubmitState
  showSubmissionStatus?: boolean
  orderLocked?: boolean
  lastBackendSuccessAt: string | null
  submissionErrorCode?: string | null
  nextRetryAt?: string | null
  onIncrementItem: (itemId: string) => void
  onDecrementItem: (itemId: string) => void
  onEditItem: (itemId: string) => void
  onRemoveItem: (itemId: string) => void
  onUpdateItemNote: (itemId: string, notes: string) => void
  onSaveDraft: () => void
  onCancelOrder: () => void
  onSubmitOrder: () => void
  onRetryQueuedOrder?: () => void
  onReturnQueuedOrderToDraft?: () => void
  compact?: boolean
}

export function OrderSummaryPanel({
  session,
  subtotal,
  tax,
  total,
  busy = false,
  localSubmitState,
  showSubmissionStatus = false,
  orderLocked = false,
  lastBackendSuccessAt,
  submissionErrorCode,
  nextRetryAt,
  onIncrementItem,
  onDecrementItem,
  onEditItem,
  onRemoveItem,
  onUpdateItemNote,
  onSaveDraft,
  onCancelOrder,
  onSubmitOrder,
  onRetryQueuedOrder,
  onReturnQueuedOrderToDraft,
  compact = false,
}: OrderSummaryPanelProps) {
  const displaySlotLabel = formatSplitSlotLabel(session.slotLabel)
  const statusLabel = orderStatusDisplayLabel(session.status)
  const isDraft = session.status === 'draft'
  const canConfirmUpdate = !isDraft && session.isModifiedAfterSubmit
  const primaryActionLabel = isDraft
    ? localSubmitState === 'QUEUED'
      ? '等待网络提交'
      : localSubmitState === 'SUBMITTING'
        ? '正在提交订单...'
        : localSubmitState === 'FAILED_RETRYABLE'
          ? '等待自动重试'
          : localSubmitState === 'CONFLICT' || localSubmitState === 'FAILED_VALIDATION'
            ? '请检查订单'
            : localSubmitState === 'SUBMITTED'
              ? '已进入服务器和厨房'
              : '提交订单'
    : canConfirmUpdate
      ? '更新订单'
      : '订单进行中'
  const primaryActionDisabled = busy || orderLocked || (!isDraft && !canConfirmUpdate)
  const itemCount = session.items.reduce((sum, item) => sum + item.quantity, 0)

  return (
    <div className={`order-summary-panel flex flex-col overflow-hidden bg-[rgba(255,255,255,0.82)] shadow-[0_18px_42px_rgba(26,28,25,0.06)] ${compact ? 'gap-3 rounded-[24px] p-3.5' : 'gap-5 rounded-[32px] p-5'}`}>
      <div className={`shrink-0 bg-[rgba(26,28,25,0.04)] ${compact ? 'rounded-[18px] px-4 py-3' : 'rounded-[24px] px-5 py-4'}`}>
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">当前订单</p>
        <div className="mt-2 flex items-end justify-between gap-4">
          <div>
            <p className={`${compact ? 'text-[1.35rem]' : 'text-[1.7rem]'} font-bold tracking-[-0.04em] text-[var(--on-surface)]`}>{displaySlotLabel}</p>
            <p className={`${compact ? 'text-[0.78rem]' : 'text-sm'} font-medium text-[var(--muted)]`}>{session.orderId}</p>
          </div>
          <span className={`rounded-full bg-[rgba(97,0,0,0.08)] font-bold text-[var(--primary)] ${compact ? 'px-2.5 py-1 text-[0.72rem]' : 'px-3 py-1.5 text-sm'}`}>
            {statusLabel}
          </span>
        </div>
      </div>

      {isDraft || showSubmissionStatus ? (
        <OfflineOrderStatusPanel
          state={localSubmitState}
          lastBackendSuccessAt={lastBackendSuccessAt}
          lastErrorCode={submissionErrorCode}
          nextRetryAt={nextRetryAt}
          compact={compact}
          onRetry={onRetryQueuedOrder}
          onReturnToDraft={onReturnQueuedOrderToDraft}
          onCancelLocal={onCancelOrder}
        />
      ) : null}

      <div className={`order-summary-list-safe flex-1 overflow-y-auto overscroll-contain pr-1 ${compact ? 'min-h-[220px] max-h-[18rem] space-y-2.5 pb-3' : 'min-h-[280px] max-h-[30rem] space-y-4 pb-4'}`}>
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
              disabled={orderLocked}
            />
          ))
        ) : (
          <div className={`rounded-[18px] bg-[rgba(26,28,25,0.04)] text-center text-[var(--muted)] ${compact ? 'px-4 py-6 text-[0.88rem]' : 'px-5 py-8 text-[1.02rem]'}`}>
            还没有点菜，请从菜单选择菜品。
          </div>
        )}
      </div>

      <div className={`order-summary-footer-safe shrink-0 border-t border-[rgba(26,28,25,0.06)] bg-[rgba(255,255,255,0.92)] ${compact ? 'space-y-2.5 pt-3' : 'space-y-3.5 pt-4'}`}>
        <div className={`rounded-[18px] bg-[rgba(26,28,25,0.04)] ${compact ? 'space-y-1.5 px-4 py-3' : 'space-y-2.5 px-5 py-4'}`}>
          <div className={`flex items-center justify-between font-bold text-[rgba(83,58,50,0.82)] ${compact ? 'text-[0.85rem]' : 'text-base'}`}>
            <span>数量：{itemCount}</span>
            <span>小计 ${subtotal.toFixed(2)}</span>
          </div>
          <div className={`flex items-center justify-between text-[rgba(83,58,50,0.82)] ${compact ? 'text-[0.86rem]' : 'text-base'}`}>
            <span>税 ({TAX_RATE_LABEL})</span>
            <span>${tax.toFixed(2)}</span>
          </div>
          <div className={`flex items-center justify-between font-extrabold tracking-[-0.04em] text-[var(--on-surface)] ${compact ? 'text-[1.25rem]' : 'text-[1.75rem]'}`}>
            <span>合计</span>
            <span className="text-[var(--primary)]">${total.toFixed(2)}</span>
          </div>
        </div>

        <div className={`grid grid-cols-2 ${compact ? 'gap-2' : 'gap-3'}`}>
          <Button variant="secondary" className={compact ? 'min-h-11 rounded-[16px] text-[0.84rem]' : 'min-h-14 rounded-[20px]'} onClick={onSaveDraft} disabled={orderLocked}>
            {busy ? '保存中...' : '保存到本机'}
          </Button>
          <Button variant="secondary" className={compact ? 'min-h-11 rounded-[16px] text-[0.84rem]' : 'min-h-14 rounded-[20px]'} onClick={onCancelOrder} disabled={orderLocked}>
            取消订单
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
    </div>
  )
}
