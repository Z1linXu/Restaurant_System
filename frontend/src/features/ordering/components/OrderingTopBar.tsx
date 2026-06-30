import { Input } from '../../../components/ui/Input'
import { formatSplitSlotLabel } from '../../../utils/tableDisplay'

interface OrderingTopBarProps {
  tableLabel: string
  slotLabel: string
  orderType?: 'dine_in' | 'pickup'
  pickupLabel?: string | null
  workstationLabel?: string | null
  onEditPickupLabel?: () => void
  onBack: () => void
  searchValue: string
  onSearchChange: (value: string) => void
  compact?: boolean
}

export function OrderingTopBar({
  tableLabel,
  slotLabel,
  orderType = 'dine_in',
  pickupLabel = null,
  workstationLabel = null,
  onEditPickupLabel,
  onBack,
  searchValue,
  onSearchChange,
  compact = false,
}: OrderingTopBarProps) {
  const displayTableLabel = formatSplitSlotLabel(tableLabel)
  const displaySlotLabel = formatSplitSlotLabel(slotLabel)

  return (
    <div className={`flex rounded-[30px] bg-[rgba(255,255,255,0.74)] shadow-[0_14px_32px_rgba(26,28,25,0.05)] ${compact ? 'items-center justify-between gap-4 px-5 py-3.5' : 'flex-col gap-4 px-6 py-5 xl:flex-row xl:items-center xl:justify-between'}`}>
      <div className={`flex items-center ${compact ? 'gap-3' : 'gap-4'}`}>
        <button
          type="button"
          onClick={onBack}
          className={`inline-flex items-center justify-center rounded-[18px] bg-[rgba(26,28,25,0.04)] font-semibold text-[var(--on-surface)] ${compact ? 'min-h-11 px-4 text-[0.95rem]' : 'min-h-14 px-5 text-base'}`}
        >
          Back
        </button>

        <div className={`rounded-[22px] bg-[rgba(26,28,25,0.04)] ${compact ? 'px-4 py-2.5' : 'px-5 py-4'}`}>
          <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
            {orderType === 'pickup' ? 'Takeout / 外带' : 'Dine-in / 堂食'}
          </p>
          <p className={`mt-1 font-display font-extrabold tracking-[-0.04em] text-[var(--on-surface)] ${compact ? 'text-[1.45rem]' : 'text-[2rem]'}`}>
            {displayTableLabel} / {displaySlotLabel}
          </p>
          {workstationLabel ? (
            <div className={`mt-2 inline-flex items-center rounded-full bg-[rgba(97,0,0,0.08)] px-2.5 py-1 text-[var(--primary)] ${compact ? 'text-[0.72rem]' : 'text-[0.8rem]'} font-semibold uppercase tracking-[0.14em]`}>
              {workstationLabel}
            </div>
          ) : null}
          {orderType === 'pickup' ? (
            <div className={`mt-1 flex items-center gap-2 ${compact ? 'text-[0.8rem]' : 'text-[0.95rem]'}`}>
              <p className="font-medium text-[var(--muted)]">
                {pickupLabel}
              </p>
              {onEditPickupLabel ? (
                <button
                  type="button"
                  onClick={onEditPickupLabel}
                  className="rounded-full bg-[rgba(97,0,0,0.08)] px-2.5 py-1 text-[0.76rem] font-semibold text-[var(--primary)] transition hover:bg-[rgba(97,0,0,0.12)]"
                >
                  Edit info
                </button>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>

      <div className={`w-full ${compact ? 'max-w-[18rem]' : 'max-w-[23rem]'}`}>
        <Input
          label="Search menu / 搜索菜单"
          placeholder="Search menu... / 搜索菜单..."
          value={searchValue}
          onChange={(event) => onSearchChange(event.target.value)}
        />
      </div>
    </div>
  )
}
