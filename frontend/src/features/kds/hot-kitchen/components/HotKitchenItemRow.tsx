import { Button } from '../../../../components/ui/Button'
import type { BackendKdsTaskDisplay } from '../../../../types/kds'
import type { KdsDisplaySizeMode } from '../../noodle/components/KdsTopBar'

interface HotKitchenItemRowProps {
  task: BackendKdsTaskDisplay
  compact?: boolean
  displayMode?: KdsDisplaySizeMode
  saving?: boolean
  onComplete: () => void
}

export function HotKitchenItemRow({
  task,
  compact = false,
  displayMode = 'standard',
  saving = false,
  onComplete,
}: HotKitchenItemRowProps) {
  const size =
    displayMode === 'compact'
      ? {
          shell: 'rounded-[14px] px-3 py-2.5',
          title: 'text-[1rem]',
          qty: 'text-[1.08rem]',
          modifier: 'text-[0.82rem]',
          action: 'min-h-8 rounded-[12px] px-3 py-2 text-[0.66rem]',
        }
      : displayMode === 'large'
        ? {
          shell: 'rounded-[18px] px-4 py-3.5',
          title: 'text-[1.42rem]',
          qty: 'text-[1.65rem]',
          modifier: 'text-[1.08rem]',
          action: 'min-h-12 rounded-[18px] px-4.5 py-3 text-[0.9rem]',
        }
        : displayMode === 'xlarge'
          ? {
              shell: 'rounded-[20px] px-4.5 py-4',
              title: 'text-[1.7rem]',
              qty: 'text-[1.95rem]',
              modifier: 'text-[1.18rem]',
              action: 'min-h-14 rounded-[20px] px-5 py-3.5 text-[1rem]',
            }
          : {
              shell: compact ? 'rounded-[16px] px-3.5 py-3' : 'rounded-[18px] px-4 py-3.5',
              title: compact ? 'text-[1.06rem]' : 'text-[1.16rem]',
              qty: compact ? 'text-[1.16rem]' : 'text-[1.3rem]',
              modifier: compact ? 'text-[0.88rem]' : 'text-[0.94rem]',
              action: compact ? 'min-h-9 rounded-[14px] px-3 py-2 text-[0.7rem]' : 'min-h-10 rounded-[16px] px-4 py-2.5 text-[0.78rem]',
            }

  const modifiers = (task.special_instructions_snapshot ?? '')
    .split('|')
    .map((part) => part.trim())
    .filter(Boolean)
  const primaryPart = modifiers[0] ?? ''
  const primarySuffix = primaryPart.startsWith(task.item_name_snapshot_zh)
    ? primaryPart.slice(task.item_name_snapshot_zh.length).trim()
    : ''
  const inlineModifiers = [
    ...(primarySuffix ? [primarySuffix] : []),
    ...modifiers.slice(1),
  ].filter(Boolean)
  const modifierText = inlineModifiers.join(' ')
  const isUpdated = Boolean(task.item_modified_after_submit || task.order_modified_after_submit)

  return (
    <div className={`flex items-start justify-between gap-3 bg-[rgba(255,255,255,0.82)] ${size.shell}`}>
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className={`font-display font-bold tracking-[-0.03em] text-[var(--on-surface)] ${size.title}`}>
              {task.item_name_snapshot_zh}
              {modifierText ? (
                <span className={`ml-2 font-semibold tracking-[-0.02em] text-[rgba(87,64,54,0.9)] ${size.modifier}`}>
                  {modifierText}
                </span>
              ) : null}
              {isUpdated ? (
                <span className="ml-2 align-middle text-[0.68em] font-black uppercase tracking-[0.1em] text-[var(--primary)]">
                  UPDATED
                </span>
              ) : null}
            </div>
          </div>
          <div className={`shrink-0 font-extrabold tracking-[-0.04em] text-[var(--primary)] ${size.qty}`}>
            ×{task.quantity}
          </div>
        </div>
      </div>

      <Button
        variant="primary"
        className={size.action}
        onClick={onComplete}
        disabled={saving}
      >
        {saving ? '...' : 'Complete'}
      </Button>
    </div>
  )
}
