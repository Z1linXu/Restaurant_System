import { useEffect, useRef, useState } from 'react'

export type KdsDisplaySizeMode = 'compact' | 'standard' | 'large' | 'xlarge'

interface KdsTopBarProps {
  currentTimeLabel: string
  currentDateLabel: string
  compact?: boolean
  displayMode?: KdsDisplaySizeMode
  onDisplayModeChange?: (mode: KdsDisplaySizeMode) => void
  title?: string
  subtitle?: string
  badgeLabel?: string | null
}

const DISPLAY_MODE_OPTIONS: Array<{ value: KdsDisplaySizeMode; label: string }> = [
  { value: 'compact', label: 'Compact' },
  { value: 'standard', label: 'Standard' },
  { value: 'large', label: 'Large' },
  { value: 'xlarge', label: 'Extra Large' },
]

export function KdsTopBar({
  currentTimeLabel,
  currentDateLabel,
  compact = false,
  displayMode = 'standard',
  onDisplayModeChange,
  title = 'Assembling Station',
  subtitle = 'Prep Line A',
  badgeLabel = null,
}: KdsTopBarProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!menuOpen) {
      return
    }

    const handlePointerDown = (event: MouseEvent) => {
      if (menuRef.current && event.target instanceof Node && !menuRef.current.contains(event.target)) {
        setMenuOpen(false)
      }
    }

    window.addEventListener('mousedown', handlePointerDown)
    return () => window.removeEventListener('mousedown', handlePointerDown)
  }, [menuOpen])

  return (
    <div className={`flex items-center justify-between gap-4 bg-[rgba(255,255,255,0.8)] shadow-[0_14px_28px_rgba(26,28,25,0.04)] ${compact ? 'rounded-[18px] px-4 py-3' : 'rounded-[24px] px-5 py-4'}`}>
      <div className="min-w-0">
        <div className={`${compact ? 'text-[0.95rem]' : 'text-[1.15rem]'} font-bold uppercase tracking-[0.08em] text-[var(--primary)]`}>
          {title}
        </div>
        <div className="flex items-center gap-2">
          <div className={`${compact ? 'text-[0.74rem]' : 'text-[0.88rem]'} font-medium text-[var(--muted)]`}>
            {subtitle}
          </div>
          {badgeLabel ? (
            <span className="rounded-full bg-[rgba(26,28,25,0.06)] px-2 py-0.5 text-[0.68rem] font-bold uppercase tracking-[0.12em] text-[var(--muted)]">
              {badgeLabel}
            </span>
          ) : null}
        </div>
      </div>
      <div className="flex items-center gap-3">
        <div className="text-right">
          <div className={`font-display font-extrabold tracking-[-0.06em] text-[var(--on-surface)] ${compact ? 'text-[1.55rem]' : 'text-[1.9rem]'}`}>
            {currentTimeLabel}
          </div>
          <div className={`${compact ? 'text-[0.68rem]' : 'text-[0.76rem]'} font-semibold uppercase tracking-[0.18em] text-[var(--muted)]`}>
            {currentDateLabel}
          </div>
        </div>

        <div className="relative" ref={menuRef}>
          <button
            type="button"
            className={`rounded-full bg-[rgba(26,28,25,0.06)] font-black tracking-[-0.02em] text-[var(--on-surface)] transition hover:bg-[rgba(26,28,25,0.1)] ${compact ? 'h-10 w-10 text-[0.98rem]' : 'h-11 w-11 text-[1.02rem]'}`}
            onClick={() => setMenuOpen((current) => !current)}
            aria-label="Adjust display size"
            aria-expanded={menuOpen}
          >
            Aa
          </button>

          {menuOpen ? (
            <div className="absolute right-0 z-20 mt-2 w-44 rounded-[18px] bg-[rgba(255,255,255,0.98)] p-2 shadow-[0_18px_36px_rgba(26,28,25,0.12)] ring-1 ring-[rgba(26,28,25,0.06)]">
              {DISPLAY_MODE_OPTIONS.map((option) => {
                const active = option.value === displayMode
                return (
                  <button
                    key={option.value}
                    type="button"
                    className={`flex w-full items-center justify-between rounded-[12px] px-3 py-2 text-left text-[0.88rem] font-semibold transition ${
                      active
                        ? 'bg-[rgba(138,22,22,0.08)] text-[var(--primary)]'
                        : 'text-[var(--on-surface)] hover:bg-[rgba(26,28,25,0.04)]'
                    }`}
                    onClick={() => {
                      onDisplayModeChange?.(option.value)
                      setMenuOpen(false)
                    }}
                  >
                    <span>{option.label}</span>
                    {active ? <span className="text-[0.76rem] font-black uppercase tracking-[0.12em]">On</span> : null}
                  </button>
                )
              })}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}
