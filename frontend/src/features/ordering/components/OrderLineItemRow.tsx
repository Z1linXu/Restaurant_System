import { useEffect, useState } from 'react'
import { Button } from '../../../components/ui/Button'
import type { OrderLineItem } from '../../../types/ordering'

interface OrderLineItemRowProps {
  item: OrderLineItem
  onIncrement: () => void
  onDecrement: () => void
  onEdit: () => void
  onRemove: () => void
  onUpdateNote: (notes: string) => void
  compact?: boolean
}

export function OrderLineItemRow({
  item,
  onIncrement,
  onDecrement,
  onEdit,
  onRemove,
  onUpdateNote,
  compact = false,
}: OrderLineItemRowProps) {
  const [noteValue, setNoteValue] = useState(item.notes)

  useEffect(() => {
    setNoteValue(item.notes)
  }, [item.notes])

  useEffect(() => {
    if (noteValue === item.notes) {
      return undefined
    }
    const timeoutId = window.setTimeout(() => {
      onUpdateNote(noteValue)
    }, 450)
    return () => window.clearTimeout(timeoutId)
  }, [item.notes, noteValue, onUpdateNote])

  return (
    <div className={`bg-[var(--surface-container-lowest)] shadow-[0_12px_30px_rgba(26,28,25,0.05)] ${compact ? 'rounded-[20px] p-3.5' : 'rounded-[26px] p-5'}`}>
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <h4 className={`${compact ? 'text-[1.15rem]' : 'text-[1.55rem]'} font-bold tracking-[-0.04em] text-[var(--on-surface)]`}>{item.nameEn}</h4>
          <p className={`${compact ? 'text-[0.86rem]' : 'text-base'} font-medium text-[var(--muted)]`}>{item.nameZh}</p>
        </div>
        <div className="text-right">
          <div className={`${compact ? 'text-[1.3rem]' : 'text-[1.65rem]'} font-extrabold tracking-[-0.04em] text-[var(--primary)]`}>
            ${item.lineSubtotal.toFixed(2)}
          </div>
          <button
            type="button"
            className={`${compact ? 'mt-1 text-[0.74rem]' : 'mt-2 text-sm'} font-semibold text-[var(--muted)] hover:text-[var(--primary)]`}
            onClick={onRemove}
          >
            Remove
          </button>
        </div>
      </div>

      {item.summaryTags.length ? (
        <div className={`${compact ? 'mt-2 flex flex-wrap gap-1.5' : 'mt-3 flex flex-wrap gap-2'}`}>
          {item.summaryTags.map((tag, index) => (
            <span
              key={`${tag.en}-${index}`}
              className={`rounded-full bg-[rgba(97,0,0,0.06)] font-semibold text-[var(--primary)] ${compact ? 'px-2.5 py-1 text-[0.72rem]' : 'px-3 py-1.5 text-sm'}`}
            >
              {tag.en} / {tag.zh}
            </span>
          ))}
        </div>
      ) : null}

      <label className={`block ${compact ? 'mt-2' : 'mt-3'}`}>
        <span className={`font-semibold text-[var(--muted)] ${compact ? 'text-[0.72rem]' : 'text-[0.82rem]'}`}>
          备注 / Special note
        </span>
        <textarea
          value={noteValue}
          onChange={(event) => setNoteValue(event.target.value)}
          placeholder="备注 / Special note"
          rows={compact ? 1 : 2}
          className={`mt-1 w-full resize-none rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-[rgba(255,255,255,0.78)] px-3 py-2 font-medium text-[var(--on-surface)] outline-none focus:border-[rgba(97,0,0,0.38)] ${compact ? 'text-[0.82rem]' : 'text-[0.92rem]'}`}
        />
      </label>

      <div className={`${compact ? 'mt-3' : 'mt-4'} flex items-center justify-between gap-4`}>
        <div className={`inline-flex items-center bg-[var(--surface-container-low)] p-1 ${compact ? 'rounded-[16px]' : 'rounded-[20px]'}`}>
          <button
            type="button"
            className={`inline-flex items-center justify-center text-[var(--on-surface)] ${compact ? 'h-10 w-10 rounded-[12px] text-[1.5rem]' : 'h-12 w-12 rounded-[16px] text-2xl'}`}
            onClick={onDecrement}
          >
            −
          </button>
          <span className={`inline-flex items-center justify-center font-bold ${compact ? 'min-w-10 text-[1rem]' : 'min-w-12 text-lg'}`}>{item.quantity}</span>
          <button
            type="button"
            className={`inline-flex items-center justify-center bg-[var(--primary)] text-[var(--on-primary)] ${compact ? 'h-10 w-10 rounded-[12px] text-[1.5rem]' : 'h-12 w-12 rounded-[16px] text-2xl'}`}
            onClick={onIncrement}
          >
            +
          </button>
        </div>

        <Button variant="secondary" className={compact ? 'min-h-10 rounded-[14px] px-3 text-[0.82rem]' : 'min-h-12 rounded-[18px]'} onClick={onEdit}>
          Edit item
        </Button>
      </div>
    </div>
  )
}
