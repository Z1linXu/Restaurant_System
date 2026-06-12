interface DineInTopBarProps {
  onTakeoutClick: () => void
  workstationLabel?: string | null
  compact?: boolean
}

export function DineInTopBar({
  onTakeoutClick,
  workstationLabel = null,
  compact = false,
}: DineInTopBarProps) {
  return (
    <div className={`flex rounded-[28px] bg-[rgba(255,255,255,0.7)] shadow-[0_14px_32px_rgba(26,28,25,0.05)] backdrop-blur-sm ${compact ? 'items-center justify-between gap-4 px-5 py-3' : 'flex-col gap-5 px-7 py-5 xl:flex-row xl:items-center xl:justify-between'}`}>
      <div className={`flex ${compact ? 'items-center gap-6' : 'flex-col gap-5 xl:flex-row xl:items-center xl:gap-12'}`}>
        <div className={`font-display font-extrabold tracking-[-0.05em] text-[var(--primary)] ${compact ? 'text-[2rem]' : 'text-4xl'}`}>
          Ichiraku POS
        </div>
        <div className={`flex items-center ${compact ? 'gap-5' : 'gap-8'}`}>
          <div
            className={`relative font-semibold tracking-[-0.04em] ${compact ? 'pb-2 text-[1.35rem]' : 'pb-3 text-[2rem]'} text-[var(--primary)]`}
          >
            Dine-in /
            <span className={`ml-1 ${compact ? 'text-[1.05rem]' : 'text-[1.7rem]'}`}>堂食</span>
            <span className="absolute inset-x-0 bottom-0 h-[3px] rounded-full bg-[var(--primary)]" />
          </div>

          <button
            type="button"
            onClick={onTakeoutClick}
            className={`inline-flex items-center rounded-[18px] bg-[rgba(97,0,0,0.08)] font-semibold tracking-[-0.03em] text-[var(--primary)] transition hover:bg-[rgba(97,0,0,0.12)] ${compact ? 'min-h-10 gap-2 px-4 text-[0.98rem]' : 'min-h-12 gap-2.5 px-5 text-[1.15rem]'}`}
          >
            <span>Takeout / 外带</span>
          </button>
          {workstationLabel ? (
            <div className={`inline-flex items-center rounded-[18px] bg-[rgba(26,28,25,0.05)] font-semibold uppercase tracking-[0.12em] text-[var(--on-surface)] ${compact ? 'min-h-10 px-4 text-[0.8rem]' : 'min-h-12 px-5 text-[0.9rem]'}`}>
              {workstationLabel}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}
