import { useEffect, useMemo, useState } from 'react'
import { Button } from '../../../components/ui/Button'
import { Card } from '../../../components/ui/Card'
import { Input } from '../../../components/ui/Input'
import type {
  BackendOrderResponse,
  BillId,
  ItemAllocation,
  ItemAllocationShared,
  ItemAllocationSharedParticipant,
  SharedMethod,
  SplitBillCount,
} from '../../../types/ordering'

interface OrderDetailPanelProps {
  order: BackendOrderResponse | null
  loading?: boolean
  busy?: boolean
  compact?: boolean
  splitCount: SplitBillCount
  allocations: Record<number, ItemAllocation>
  cashSelected: boolean
  onSplitCountChange: (count: SplitBillCount) => void
  onAllocationChange: (itemId: number, allocation: ItemAllocation) => void
  onCashSelectedChange: (selected: boolean) => void
  onCheckout: () => void
}

interface SharedEditorState {
  itemId: number
  selectedBills: BillId[]
  method: SharedMethod
  manualValues: Partial<Record<BillId, string>>
}

const BILL_LABELS: BillId[] = ['A', 'B', 'C', 'D']
const EMPTY_UNASSIGNED: ItemAllocation = { mode: 'UNASSIGNED' }
const CHECKOUT_TAX_RATE = 0.14975

function getOrderDisplayLabel(order: BackendOrderResponse) {
  return order.table_no ?? order.pickup_no ?? `#${order.id}`
}

function toCents(amount: number) {
  return Math.round(amount * 100)
}

function fromCents(amount: number) {
  return amount / 100
}

function getAvailableBills(splitCount: SplitBillCount) {
  return BILL_LABELS.slice(0, splitCount)
}

function getAllocatedAmountForBill(allocation: ItemAllocation, billId: BillId, fullAmount: number) {
  if (allocation.mode === 'SINGLE') {
    return allocation.billId === billId ? fullAmount : 0
  }
  if (allocation.mode === 'SHARED') {
    return allocation.participants.find((participant) => participant.billId === billId)?.amount ?? 0
  }
  return 0
}

function buildBillTotals(order: BackendOrderResponse, splitCount: SplitBillCount, allocations: Record<number, ItemAllocation>) {
  return getAvailableBills(splitCount).map((billId) => {
    const items = order.items
      .map((item) => {
        const amount = getAllocatedAmountForBill(allocations[item.id] ?? EMPTY_UNASSIGNED, billId, Number(item.line_amount))
        if (amount <= 0) {
          return null
        }
        return { item, amount }
      })
      .filter((entry): entry is { item: BackendOrderResponse['items'][number]; amount: number } => Boolean(entry))

    return {
      label: billId,
      items,
      subtotal: items.reduce((sum, entry) => sum + entry.amount, 0),
      tax: items.reduce((sum, entry) => sum + entry.amount, 0) * CHECKOUT_TAX_RATE,
    }
  })
}

function applyEqualSplit(itemTotal: number, selectedBills: BillId[]): ItemAllocationSharedParticipant[] {
  const totalCents = toCents(itemTotal)
  const base = Math.floor(totalCents / selectedBills.length)
  const remainder = totalCents % selectedBills.length

  return selectedBills.map((billId, index) => ({
    billId,
    amount: fromCents(base + (index < remainder ? 1 : 0)),
  }))
}

function getAllocationSummary(allocation: ItemAllocation) {
  if (allocation.mode === 'UNASSIGNED') {
    return 'Unassigned'
  }
  if (allocation.mode === 'SINGLE') {
    return `Bill ${allocation.billId}`
  }
  if (allocation.method === 'EQUAL') {
    return `Shared: ${allocation.participants.map((participant) => participant.billId).join(' + ')} equally`
  }
  return `Shared: ${allocation.participants
    .map((participant) => `${participant.billId} $${participant.amount.toFixed(2)}`)
    .join(' / ')}`
}

export function OrderDetailPanel({
  order,
  loading = false,
  busy = false,
  compact = false,
  splitCount,
  allocations,
  cashSelected,
  onSplitCountChange,
  onAllocationChange,
  onCashSelectedChange,
  onCheckout,
}: OrderDetailPanelProps) {
  const [sharedEditor, setSharedEditor] = useState<SharedEditorState | null>(null)

  const availableBills = useMemo(() => getAvailableBills(splitCount), [splitCount])
  const billTotals = useMemo(() => (order ? buildBillTotals(order, splitCount, allocations) : []), [allocations, order, splitCount])
  const hasUnassignedItems = useMemo(
    () => splitCount > 1 && !!order?.items.some((item) => (allocations[item.id] ?? EMPTY_UNASSIGNED).mode === 'UNASSIGNED'),
    [allocations, order, splitCount],
  )

  useEffect(() => {
    setSharedEditor(null)
  }, [order?.id, splitCount])

  if (loading) {
    return (
      <Card tone="feature" className={`${compact ? 'rounded-[22px] p-4' : 'rounded-[28px] p-5'}`}>
        <div className="text-[var(--muted)]">Loading order...</div>
      </Card>
    )
  }

  if (!order) {
    return (
      <Card tone="feature" className={`${compact ? 'rounded-[22px] p-4' : 'rounded-[28px] p-5'}`}>
        <div className="text-[var(--muted)]">Select an order to view details and checkout.</div>
      </Card>
    )
  }

  const canCheckout = ['submitted', 'preparing', 'ready'].includes(order.status)
  const checkoutDisabled = !canCheckout || busy || hasUnassignedItems
  const activeSharedItem = sharedEditor ? order.items.find((item) => item.id === sharedEditor.itemId) : null
  const orderSubtotal = Number(order.subtotal_amount)
  const orderTax = orderSubtotal * CHECKOUT_TAX_RATE
  const orderTotal = orderSubtotal + orderTax

  const manualTotalValid = (() => {
    if (!sharedEditor || !activeSharedItem || sharedEditor.method !== 'MANUAL') {
      return true
    }
    if (!sharedEditor.selectedBills.length) {
      return false
    }
    const sum = sharedEditor.selectedBills.reduce((total, billId) => total + Number(sharedEditor.manualValues[billId] ?? 0), 0)
    return Math.abs(sum - Number(activeSharedItem.line_amount)) < 0.001
  })()

  const applySharedAllocation = () => {
    if (!sharedEditor || !activeSharedItem || !sharedEditor.selectedBills.length) {
      return
    }

    const participants =
      sharedEditor.method === 'EQUAL'
        ? applyEqualSplit(Number(activeSharedItem.line_amount), sharedEditor.selectedBills)
        : sharedEditor.selectedBills.map((billId) => ({
            billId,
            amount: Number(sharedEditor.manualValues[billId] ?? 0),
          }))

    const allocation: ItemAllocationShared = {
      mode: 'SHARED',
      method: sharedEditor.method,
      participants,
    }

    onAllocationChange(activeSharedItem.id, allocation)
    setSharedEditor(null)
  }

  return (
    <Card tone="feature" className={`bg-[rgba(255,255,255,0.82)] ${compact ? 'rounded-[22px] p-4' : 'rounded-[28px] p-5'}`}>
      <div className={`flex items-start justify-between gap-4 ${compact ? 'mb-4' : 'mb-5'}`}>
        <div>
          <p className="text-[0.75rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Order detail</p>
          <h2 className={`mt-1 font-display font-extrabold tracking-[-0.06em] text-[var(--on-surface)] ${compact ? 'text-[1.8rem]' : 'text-[2.2rem]'}`}>
            {getOrderDisplayLabel(order)}
          </h2>
          <p className={`${compact ? 'mt-1 text-[0.82rem]' : 'mt-1.5 text-[0.92rem]'} text-[var(--muted)]`}>
            {order.order_no} · {order.order_type === 'pickup' ? 'Takeout / 外带' : 'Dine-in / 堂食'} · {order.status}
          </p>
        </div>
        <div className="text-right">
          <p className="text-[0.75rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Total</p>
          <p className={`mt-1 font-display font-extrabold tracking-[-0.05em] text-[var(--primary)] ${compact ? 'text-[1.8rem]' : 'text-[2.15rem]'}`}>
            ${Number(order.total_amount).toFixed(2)}
          </p>
        </div>
      </div>

      <div className={`grid ${compact ? 'gap-4 xl:grid-cols-[minmax(0,1.45fr)_21rem]' : 'gap-5 xl:grid-cols-[minmax(0,1.5fr)_23rem]'}`}>
        <div className="space-y-3">
          <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] p-3">
            <div className="mb-2 flex items-center justify-between gap-3">
              <p className="text-[0.75rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Items</p>
              <div className="inline-flex rounded-full bg-[rgba(26,28,25,0.05)] p-1">
                {[1, 2, 3, 4].map((count) => (
                  <button
                    key={count}
                    type="button"
                    className={`rounded-full px-3 py-1.5 text-[0.76rem] font-semibold transition ${
                      splitCount === count
                        ? 'bg-[var(--surface-container-lowest)] text-[var(--primary)] shadow-[0_8px_18px_rgba(26,28,25,0.08)]'
                        : 'text-[var(--muted)]'
                    }`}
                    onClick={() => onSplitCountChange(count as SplitBillCount)}
                  >
                    {count} Bill{count > 1 ? 's' : ''}
                  </button>
                ))}
              </div>
            </div>

            {splitCount > 1 ? (
              <div className="mb-3 rounded-[14px] bg-[rgba(97,0,0,0.06)] px-3 py-2 text-[0.8rem] font-medium text-[var(--primary)]">
                {hasUnassignedItems ? 'Please assign all items before checkout.' : 'All items assigned. Ready for checkout.'}
              </div>
            ) : null}

            <div className="space-y-2">
              {order.items.map((item) => {
                const allocation = allocations[item.id] ?? (splitCount === 1 ? { mode: 'SINGLE', billId: 'A' as BillId } : EMPTY_UNASSIGNED)
                const isSharedEditorOpen = sharedEditor?.itemId === item.id

                return (
                  <div
                    key={item.id}
                    className={`grid items-start gap-3 bg-[var(--surface-container-lowest)] ${compact ? 'rounded-[16px] p-3' : 'rounded-[18px] p-3.5'} md:grid-cols-[minmax(0,1fr)_auto_auto]`}
                  >
                    <div className="min-w-0">
                      <p className={`font-semibold text-[var(--on-surface)] ${compact ? 'text-[0.98rem]' : 'text-[1.02rem]'}`}>
                        {item.item_name_snapshot_zh || item.item_name_snapshot_en}
                      </p>
                      <div className={`${compact ? 'mt-1 text-[0.74rem]' : 'mt-1 text-[0.8rem]'} text-[var(--muted)]`}>
                        {item.options.map((option, index) => (
                          <span key={option.id}>
                            {index > 0 ? ' · ' : ''}
                            {option.option_name_snapshot_zh}
                            {(option.quantity ?? 1) > 1 ? ` x${option.quantity}` : ''}
                          </span>
                        ))}
                      </div>
                    </div>
                    <div className={`${compact ? 'text-[0.88rem]' : 'text-[0.92rem]'} font-semibold text-[var(--muted)]`}>
                      ×{item.quantity}
                    </div>
                    <div className="text-right">
                      <p className={`${compact ? 'text-[0.94rem]' : 'text-[1rem]'} font-bold text-[var(--primary)]`}>
                        ${Number(item.line_amount).toFixed(2)}
                      </p>
                    </div>

                    {splitCount > 1 ? (
                      <div className="md:col-span-3">
                        <div className="flex flex-wrap items-center gap-2">
                          {availableBills.map((billId) => (
                            <button
                              key={`${item.id}-${billId}`}
                              type="button"
                              className={`rounded-full px-3 py-1.5 text-[0.74rem] font-semibold transition ${
                                allocation.mode === 'SINGLE' && allocation.billId === billId
                                  ? 'bg-[var(--surface-container-lowest)] text-[var(--primary)] shadow-[0_8px_18px_rgba(26,28,25,0.08)]'
                                  : 'bg-[rgba(26,28,25,0.05)] text-[var(--muted)]'
                              }`}
                              onClick={() => {
                                onAllocationChange(item.id, { mode: 'SINGLE', billId })
                                setSharedEditor((current) => (current?.itemId === item.id ? null : current))
                              }}
                            >
                              Bill {billId}
                            </button>
                          ))}
                          <button
                            type="button"
                            className={`rounded-full px-3 py-1.5 text-[0.74rem] font-semibold transition ${
                              allocation.mode === 'SHARED' || isSharedEditorOpen
                                ? 'bg-[rgba(97,0,0,0.1)] text-[var(--primary)]'
                                : 'bg-[rgba(26,28,25,0.05)] text-[var(--muted)]'
                            }`}
                            onClick={() =>
                              setSharedEditor({
                                itemId: item.id,
                                selectedBills:
                                  allocation.mode === 'SHARED'
                                    ? allocation.participants.map((participant) => participant.billId)
                                    : availableBills.slice(0, Math.min(2, availableBills.length)),
                                method: allocation.mode === 'SHARED' ? allocation.method : 'EQUAL',
                                manualValues:
                                  allocation.mode === 'SHARED' && allocation.method === 'MANUAL'
                                    ? Object.fromEntries(
                                        allocation.participants.map((participant) => [participant.billId, participant.amount.toFixed(2)]),
                                      )
                                    : {},
                              })
                            }
                          >
                            Shared
                          </button>
                          <button
                            type="button"
                            className={`rounded-full px-3 py-1.5 text-[0.74rem] font-semibold transition ${
                              allocation.mode === 'UNASSIGNED'
                                ? 'bg-[rgba(97,0,0,0.1)] text-[var(--primary)]'
                                : 'bg-[rgba(26,28,25,0.05)] text-[var(--muted)]'
                            }`}
                            onClick={() => {
                              onAllocationChange(item.id, { mode: 'UNASSIGNED' })
                              setSharedEditor((current) => (current?.itemId === item.id ? null : current))
                            }}
                          >
                            Unassigned
                          </button>
                          <span className="text-[0.76rem] font-medium text-[var(--muted)]">{getAllocationSummary(allocation)}</span>
                        </div>

                        {isSharedEditorOpen && activeSharedItem ? (
                          <div className="mt-3 rounded-[14px] bg-[rgba(26,28,25,0.04)] p-3">
                            <div className="flex items-center justify-between gap-3">
                              <p className="text-[0.75rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Shared split</p>
                              <p className="text-[0.8rem] font-bold text-[var(--primary)]">${Number(activeSharedItem.line_amount).toFixed(2)}</p>
                            </div>

                            <div className="mt-3">
                              <p className="mb-2 text-[0.74rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Participants</p>
                              <div className="flex flex-wrap gap-2">
                                {availableBills.map((billId) => {
                                  const checked = sharedEditor.selectedBills.includes(billId)
                                  return (
                                    <label
                                      key={`${item.id}-participant-${billId}`}
                                      className={`inline-flex items-center gap-2 rounded-full px-3 py-2 text-[0.76rem] font-semibold transition ${
                                        checked
                                          ? 'bg-[rgba(97,0,0,0.1)] text-[var(--primary)]'
                                          : 'bg-[var(--surface-container-lowest)] text-[var(--muted)]'
                                      }`}
                                    >
                                      <input
                                        type="checkbox"
                                        className="h-3.5 w-3.5 accent-[var(--primary)]"
                                        checked={checked}
                                        onChange={(event) =>
                                          setSharedEditor((current) => {
                                            if (!current || current.itemId !== item.id) {
                                              return current
                                            }
                                            return {
                                              ...current,
                                              selectedBills: event.target.checked
                                                ? [...current.selectedBills, billId]
                                                : current.selectedBills.filter((value) => value !== billId),
                                            }
                                          })
                                        }
                                      />
                                      Bill {billId}
                                    </label>
                                  )
                                })}
                              </div>
                            </div>

                            <div className="mt-3">
                              <p className="mb-2 text-[0.74rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Split method</p>
                              <div className="flex flex-wrap gap-2">
                                {(['EQUAL', 'MANUAL'] as const).map((method) => (
                                  <button
                                    key={`${item.id}-${method}`}
                                    type="button"
                                    className={`rounded-full px-3 py-2 text-[0.76rem] font-semibold transition ${
                                      sharedEditor.method === method
                                        ? 'bg-[rgba(97,0,0,0.1)] text-[var(--primary)]'
                                        : 'bg-[var(--surface-container-lowest)] text-[var(--muted)]'
                                    }`}
                                    onClick={() =>
                                      setSharedEditor((current) =>
                                        current && current.itemId === item.id ? { ...current, method } : current,
                                      )
                                    }
                                  >
                                    {method === 'EQUAL' ? 'Equal Split' : 'Manual Amount'}
                                  </button>
                                ))}
                              </div>
                            </div>

                            {sharedEditor.method === 'MANUAL' ? (
                              <div className="mt-3 grid gap-3 md:grid-cols-2">
                                {sharedEditor.selectedBills.map((billId) => (
                                  <Input
                                    key={`${item.id}-${billId}-amount`}
                                    label={`Bill ${billId}`}
                                    value={sharedEditor.manualValues[billId] ?? ''}
                                    onChange={(event) =>
                                      setSharedEditor((current) =>
                                        current && current.itemId === item.id
                                          ? {
                                              ...current,
                                              manualValues: {
                                                ...current.manualValues,
                                                [billId]: event.target.value,
                                              },
                                            }
                                          : current,
                                      )
                                    }
                                    inputMode="decimal"
                                    placeholder="0.00"
                                  />
                                ))}
                                {!manualTotalValid ? (
                                  <div className="md:col-span-2 rounded-[12px] bg-[rgba(97,0,0,0.06)] px-3 py-2 text-[0.78rem] font-medium text-[var(--primary)]">
                                    Manual amounts must equal ${Number(activeSharedItem.line_amount).toFixed(2)} exactly.
                                  </div>
                                ) : null}
                              </div>
                            ) : null}

                            <div className="mt-3 flex items-center justify-end gap-2">
                              <Button
                                variant="secondary"
                                className="min-h-10 rounded-[14px] px-3 text-[0.8rem]"
                                onClick={() => setSharedEditor(null)}
                              >
                                Cancel
                              </Button>
                              <Button
                                className="min-h-10 rounded-[14px] px-3 text-[0.8rem]"
                                onClick={applySharedAllocation}
                                disabled={
                                  sharedEditor.selectedBills.length === 0 ||
                                  (sharedEditor.method === 'MANUAL' && !manualTotalValid)
                                }
                              >
                                Apply
                              </Button>
                            </div>
                          </div>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                )
              })}
            </div>
          </div>
        </div>

        <div className="space-y-3">
          <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] p-3.5">
            <p className="text-[0.75rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Bill summary</p>
            <div className="mt-3 space-y-2.5">
              {billTotals.map((bill) => (
                <div key={bill.label} className="rounded-[16px] bg-[var(--surface-container-lowest)] p-3">
                  <div className="mb-2 flex items-center justify-between gap-3">
                    <p className="font-semibold text-[var(--on-surface)]">Bill {bill.label}</p>
                    <p className="font-bold text-[var(--primary)]">${(bill.subtotal + bill.tax).toFixed(2)}</p>
                  </div>
                  <div className="space-y-1 text-[0.8rem] text-[var(--muted)]">
                    <div className="flex items-center justify-between">
                      <span>Subtotal</span>
                      <span>${bill.subtotal.toFixed(2)}</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span>Tax (14.975%)</span>
                      <span>${bill.tax.toFixed(2)}</span>
                    </div>
                    <div className="flex items-center justify-between font-semibold text-[var(--on-surface)]">
                      <span>Total</span>
                      <span>${(bill.subtotal + bill.tax).toFixed(2)}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] p-3.5">
            <div className="mb-3 flex items-center justify-between gap-3">
              <p className="text-[0.75rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Payment label</p>
              <button
                type="button"
                className={`rounded-full px-3 py-1.5 text-[0.76rem] font-semibold transition ${
                  cashSelected
                    ? 'bg-[rgba(97,0,0,0.1)] text-[var(--primary)]'
                    : 'bg-[var(--surface-container-lowest)] text-[var(--muted)]'
                }`}
                onClick={() => onCashSelectedChange(!cashSelected)}
              >
                Cash
              </button>
            </div>

            <div className="space-y-2 text-[0.86rem] text-[var(--muted)]">
              <div className="flex items-center justify-between">
                <span>Order subtotal</span>
                <span>${orderSubtotal.toFixed(2)}</span>
              </div>
              <div className="flex items-center justify-between">
                <span>Tax (14.975%)</span>
                <span>${orderTax.toFixed(2)}</span>
              </div>
              <div className="flex items-center justify-between text-[1rem] font-bold text-[var(--on-surface)]">
                <span>Total</span>
                <span className="text-[var(--primary)]">${orderTotal.toFixed(2)}</span>
              </div>
            </div>

            {cashSelected ? (
              <div className="mt-3 rounded-[12px] bg-[rgba(97,0,0,0.06)] px-3 py-2 text-[0.8rem] font-semibold text-[var(--primary)]">
                Payment label: Cash
              </div>
            ) : null}

            <Button
              className={`${compact ? 'mt-3 min-h-11 rounded-[16px] text-[0.9rem]' : 'mt-4 min-h-12 rounded-[18px]'}`}
              onClick={onCheckout}
              disabled={checkoutDisabled}
              fullWidth
            >
              {busy ? 'Checking out...' : canCheckout ? 'Checkout / 结账' : 'Closed / 已完成'}
            </Button>
          </div>
        </div>
      </div>
    </Card>
  )
}
