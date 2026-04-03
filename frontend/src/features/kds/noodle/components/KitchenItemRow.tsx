import type { BackendKdsTaskDisplay } from '../../../../types/kds'
import type { KdsDisplaySizeMode } from './KdsTopBar'

function statusPillClasses(tone: 'updated' | 'new' | 'cancelled') {
  if (tone === 'updated') {
    return 'bg-[rgba(138,22,22,0.09)] text-[var(--primary)]'
  }
  if (tone === 'new') {
    return 'bg-[rgba(144,77,0,0.12)] text-[var(--secondary)]'
  }
  return 'bg-[rgba(26,28,25,0.08)] text-[var(--muted)]'
}

function splitInstructionParts(task: BackendKdsTaskDisplay) {
  const parts = (task.special_instructions_snapshot ?? '')
    .split('|')
    .map((part) => part.trim())
    .filter(Boolean)

  return {
    primaryLine: parts[0] ? [parts[0]] : [],
    secondaryLine: parts.slice(1),
  }
}

interface KitchenItemRowProps {
  task: BackendKdsTaskDisplay
  selected: boolean
  completed: boolean
  onToggle?: () => void
  compact?: boolean
  sectionLabel?: string | null
  primaryOverride?: string | null
  secondaryOverride?: string | null
  quantityOverride?: number | null
  selectable?: boolean
  displayMode?: KdsDisplaySizeMode
}

export function KitchenItemRow({
  task,
  selected,
  completed,
  onToggle,
  compact = false,
  sectionLabel = null,
  primaryOverride,
  secondaryOverride,
  quantityOverride,
  selectable = true,
  displayMode = 'standard',
}: KitchenItemRowProps) {
  const { primaryLine, secondaryLine } = splitInstructionParts(task)
  const isCancelled = false
  const changeTag = task.item_modified_after_submit ? 'UPDATED' : null
  const inferredPrimary = primaryLine[0]
  const inferredSecondary = secondaryLine.join(' · ')
  const primaryLooksLikeModifier =
    !!inferredPrimary && (inferredPrimary.startsWith('走') || inferredPrimary.startsWith('+') || inferredPrimary.startsWith('少') || inferredPrimary.startsWith('（'))
  const primaryText = primaryOverride ?? (primaryLooksLikeModifier ? task.item_name_snapshot_zh : inferredPrimary ?? task.item_name_snapshot_zh)
  const secondaryText = secondaryOverride ?? (primaryLooksLikeModifier ? [inferredPrimary, inferredSecondary].filter(Boolean).join(' · ') : inferredSecondary)
  const displayQuantity = quantityOverride ?? task.quantity

  const rowScale =
    displayMode === 'compact'
      ? {
          shell: 'rounded-[14px] px-3 py-2.5',
          label: 'px-2 py-0.5 text-[0.58rem]',
          primary: 'text-[0.92rem]',
          secondary: 'mt-0.5 text-[0.78rem] leading-5',
          checkbox: 'h-7 w-7',
        }
      : displayMode === 'large'
        ? {
            shell: 'rounded-[18px] px-4 py-3.5',
            label: 'px-2.5 py-0.5 text-[0.72rem]',
            primary: 'text-[1.16rem]',
            secondary: 'mt-1 text-[1.02rem] leading-7',
            checkbox: 'h-10 w-10',
          }
        : displayMode === 'xlarge'
          ? {
              shell: 'rounded-[20px] px-4.5 py-4',
              label: 'px-2.5 py-0.5 text-[0.76rem]',
              primary: 'text-[1.26rem]',
              secondary: 'mt-1 text-[1.08rem] leading-7',
              checkbox: 'h-11 w-11',
            }
          : {
              shell: compact ? 'rounded-[16px] px-3.5 py-3' : 'rounded-[20px] px-4 py-4',
              label: compact ? 'px-2 py-0.5 text-[0.62rem]' : 'px-2.5 py-0.5 text-[0.68rem]',
              primary: compact ? 'text-[1rem]' : 'text-[1.08rem]',
              secondary: compact ? 'mt-0.5 text-[0.84rem] leading-6' : 'mt-1 text-[0.98rem] leading-7',
              checkbox: compact ? 'h-8 w-8' : 'h-9 w-9',
            }

  return (
    <div
      className={`transition ${
        completed
          ? 'bg-[rgba(26,28,25,0.06)] text-[rgba(26,28,25,0.45)]'
          : selected
            ? 'bg-[rgba(97,0,0,0.08)]'
            : 'bg-[rgba(255,255,255,0.82)]'
      } ${rowScale.shell}`}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            {sectionLabel ? (
              <span className={`${rowScale.label} rounded-full bg-[rgba(26,28,25,0.06)] font-bold uppercase tracking-[0.12em] text-[var(--muted)]`}>
                {sectionLabel}
              </span>
            ) : null}
            <div
              className={`font-display font-bold tracking-[-0.03em] ${
                isCancelled ? 'line-through opacity-55' : 'text-[var(--on-surface)]'
              } ${rowScale.primary}`}
            >
              {primaryText} ×{displayQuantity}
            </div>
            {changeTag ? (
              <span className={`rounded-full px-2.5 py-1 text-[0.7rem] font-bold tracking-[0.12em] ${statusPillClasses('updated')}`}>
                {changeTag}
              </span>
            ) : null}
            {isCancelled ? (
              <span className={`rounded-full px-2.5 py-1 text-[0.7rem] font-bold tracking-[0.12em] ${statusPillClasses('cancelled')}`}>
                CANCELLED
              </span>
            ) : null}
          </div>
          {secondaryText ? (
            <div className={`${rowScale.secondary} font-medium text-[var(--muted)]`}>
              {secondaryText.split(' · ').map((label, index) => {
                const isRemove = /不要|no /i.test(label) || label.startsWith('走') || label.startsWith('少')
                return (
                  <span key={`${task.task_id}-${label}`}>
                    {index > 0 ? <span className="text-[rgba(26,28,25,0.32)]"> · </span> : null}
                    <span className={isRemove ? 'font-semibold text-[var(--primary)]' : 'text-[var(--on-surface)]'}>
                      {label}
                    </span>
                  </span>
                )
              })}
            </div>
          ) : null}
        </div>

        {selectable ? (
          <button
            type="button"
            onClick={onToggle}
            className={`mt-1 flex shrink-0 items-center justify-center rounded-full border-2 transition ${
              completed || selected
                ? 'border-[var(--primary)] bg-[var(--primary)] text-[var(--on-primary)]'
                : 'border-[rgba(26,28,25,0.14)] bg-transparent text-transparent'
            } ${rowScale.checkbox}`}
            aria-label={selected ? 'Unselect item' : 'Select item'}
          >
            ✓
          </button>
        ) : null}
      </div>
    </div>
  )
}
