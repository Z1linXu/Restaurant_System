import { Card } from '../../../components/ui/Card'
import type { MenuItem } from '../../../types/ordering'

interface MenuItemCardProps {
  item: MenuItem
  onSelect: (item: MenuItem) => void
  onQuickAdd?: (item: MenuItem) => Promise<void> | void
  onDecrement?: (item: MenuItem) => Promise<void> | void
  quickAddState?: 'idle' | 'adding' | 'added'
  orderedQuantity?: number
  canDecrement?: boolean
  compact?: boolean
}

export function MenuItemCard({
  item,
  onSelect,
  onQuickAdd,
  onDecrement,
  quickAddState = 'idle',
  orderedQuantity = 0,
  canDecrement = false,
  compact = false,
}: MenuItemCardProps) {
  const addLabel = quickAddState === 'adding' ? '...' : quickAddState === 'added' ? 'Added' : '+'
  const stepperAddLabel = quickAddState === 'adding' ? '...' : '+'

  return (
    <div
      role="button"
      tabIndex={0}
      className="text-left"
      onClick={() => onSelect(item)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onSelect(item)
        }
      }}
    >
      <Card className={`h-full transition hover:-translate-y-0.5 hover:shadow-[0_18px_40px_rgba(97,0,0,0.08)] ${compact ? 'min-h-[12.75rem] rounded-[22px] p-4' : 'min-h-[16rem] rounded-[28px] p-6'}`}>
        <div className={`flex h-full flex-col ${compact ? 'gap-3' : 'gap-4'}`}>
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-2">
              <h3 className={`${compact ? 'text-[1.45rem]' : 'text-[2rem]'} font-bold leading-tight tracking-[-0.04em] text-[var(--on-surface)]`}>
                {item.nameEn}
              </h3>
              <p className={`${compact ? 'text-[1rem]' : 'text-[1.35rem]'} font-semibold text-[rgba(83,58,50,0.86)]`}>{item.nameZh}</p>
            </div>
            <div className={`text-right ${compact ? 'text-[1.55rem]' : 'text-[2rem]'} font-extrabold tracking-[-0.04em] text-[var(--primary)]`}>
              ${item.price.toFixed(2)}
            </div>
          </div>

          {item.descriptionZh || item.descriptionEn ? (
            <div className={`${compact ? 'space-y-2 text-[0.86rem] leading-6' : 'space-y-3 text-[1.02rem] leading-7'} text-[var(--muted)]`}>
              {item.descriptionZh ? <p>{item.descriptionZh}</p> : null}
              {item.descriptionEn ? <p>{item.descriptionEn}</p> : null}
            </div>
          ) : (
            <div className={`${compact ? 'space-y-1.5 text-[0.86rem] leading-6' : 'space-y-2 text-[1rem] leading-7'} text-[var(--muted)]`}>
              <p>{item.nameZh}</p>
              <p>{item.nameEn}</p>
            </div>
          )}

          <div className="mt-auto flex items-end justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              {item.badge ? (
                <div className={`inline-flex w-fit rounded-full bg-[rgba(144,77,0,0.12)] font-bold uppercase tracking-[0.05em] text-[var(--secondary)] ${compact ? 'px-3 py-1.5 text-[0.72rem]' : 'px-4 py-2 text-sm'}`}>
                  {item.badge.en}
                </div>
              ) : null}
            </div>

            {orderedQuantity > 0 ? (
              <div
                className={`inline-flex items-center rounded-full bg-[rgba(97,0,0,0.08)] font-black text-[var(--primary)] shadow-[0_10px_22px_rgba(97,0,0,0.10)] ${compact ? 'gap-2 px-1.5 py-1' : 'gap-3 px-2 py-1.5'}`}
                onClick={(event) => event.stopPropagation()}
                aria-label={`${item.nameEn} ordered quantity ${orderedQuantity}`}
              >
                <button
                  type="button"
                  onClick={async (event) => {
                    event.stopPropagation()
                    if (!canDecrement) {
                      return
                    }
                    await onDecrement?.(item)
                  }}
                  disabled={!canDecrement}
                  className={`inline-flex items-center justify-center rounded-full bg-white/85 transition disabled:cursor-not-allowed disabled:opacity-35 ${compact ? 'h-8 w-8 text-[1rem]' : 'h-9 w-9 text-[1.1rem]'}`}
                  aria-label={`Remove one ${item.nameEn}`}
                >
                  -
                </button>
                <span className={`min-w-5 text-center tabular-nums ${compact ? 'text-[0.95rem]' : 'text-[1.05rem]'}`}>
                  {orderedQuantity}
                </span>
                <button
                  type="button"
                  onClick={async (event) => {
                    event.stopPropagation()
                    await onQuickAdd?.(item)
                  }}
                  disabled={!onQuickAdd || quickAddState === 'adding'}
                  className={`inline-flex items-center justify-center rounded-full font-bold text-[var(--on-primary)] shadow-[0_10px_22px_rgba(97,0,0,0.18)] transition disabled:cursor-not-allowed disabled:opacity-45 ${
                    quickAddState === 'added'
                      ? 'scale-105 bg-[var(--secondary)]'
                      : 'bg-[var(--primary)]'
                  } ${compact ? 'h-8 w-8 text-[1rem]' : 'h-9 w-9 text-[1.1rem]'}`}
                  aria-label={`Add one ${item.nameEn}`}
                >
                  {stepperAddLabel}
                </button>
              </div>
            ) : onQuickAdd ? (
              <button
                type="button"
                onClick={async (event) => {
                  event.stopPropagation()
                  await onQuickAdd(item)
                }}
                disabled={quickAddState === 'adding'}
                className={`inline-flex items-center justify-center rounded-full font-bold text-[var(--on-primary)] shadow-[0_10px_22px_rgba(97,0,0,0.18)] transition ${
                  quickAddState === 'added'
                    ? 'scale-105 bg-[var(--secondary)]'
                    : 'bg-[var(--primary)]'
                } ${compact ? 'h-10 min-w-10 px-3 text-[1rem]' : 'h-12 min-w-12 px-3 text-[1.1rem]'}`}
                aria-label={`Add ${item.nameEn}`}
              >
                {addLabel}
              </button>
            ) : null}
          </div>
        </div>
      </Card>
    </div>
  )
}
