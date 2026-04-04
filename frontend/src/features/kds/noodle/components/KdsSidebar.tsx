interface KdsSidebarProps {
  activeItem: 'orders' | 'history'
  onNavigate: (target: 'orders' | 'history') => void
  compact?: boolean
}

const navItems: Array<{ key: 'orders' | 'history'; label: string; icon: string }> = [
  { key: 'orders', label: 'Orders', icon: '▦' },
  { key: 'history', label: 'History', icon: '🕘' },
]

export function KdsSidebar({ activeItem, onNavigate, compact = false }: KdsSidebarProps) {
  return (
    <aside className={`flex min-h-screen flex-col items-center bg-[rgba(255,255,255,0.72)] shadow-[12px_0_30px_rgba(26,28,25,0.04)] ${compact ? 'w-[4.75rem] py-4' : 'w-[5.75rem] py-6'}`}>
      <div className={`flex items-center justify-center bg-[var(--surface-container-lowest)] shadow-[0_12px_28px_rgba(26,28,25,0.08)] ${compact ? 'mb-7 h-12 w-12 rounded-[18px] text-[1.35rem]' : 'mb-10 h-14 w-14 rounded-[22px] text-[1.65rem]'}`}>
        <span aria-hidden>🍜</span>
      </div>

      <div className={`flex flex-1 flex-col items-center ${compact ? 'gap-3.5' : 'gap-5'}`}>
        {navItems.map((item) => {
          const active = item.key === activeItem
          return (
            <button
              key={item.key}
              type="button"
              onClick={() => onNavigate(item.key)}
              className={`flex items-center justify-center transition ${
                active
                  ? 'bg-[var(--surface-container-lowest)] text-[var(--primary)] shadow-[0_12px_28px_rgba(26,28,25,0.08)]'
                  : 'bg-transparent text-[var(--muted)]'
              } ${compact ? 'h-11 w-11 rounded-[16px] text-[0.95rem]' : 'h-14 w-14 rounded-[22px] text-lg'}`}
              aria-label={item.label}
            >
              {item.icon}
            </button>
          )
        })}
      </div>

      <button
        type="button"
        className={`mt-8 flex items-center justify-center text-[var(--muted)] ${compact ? 'h-11 w-11 rounded-[16px] text-[1.5rem]' : 'h-14 w-14 rounded-[22px] text-2xl'}`}
        aria-label="Power"
      >
        ⏻
      </button>
    </aside>
  )
}
