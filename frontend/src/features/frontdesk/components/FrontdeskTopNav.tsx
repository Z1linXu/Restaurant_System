import { navigateTo } from '../navigation'

interface FrontdeskTopNavProps {
  activeItem?: 'menu' | 'orders' | 'pickup' | 'stations' | 'dashboard' | null
}

const navItems = [
  { id: 'orders', label: 'Orders', icon: '▤' },
  { id: 'menu', label: 'Menu', icon: '✕' },
  { id: 'pickup', label: 'Pickup', icon: '◉' },
  { id: 'stations', label: 'Stations', icon: '⌂' },
  { id: 'dashboard', label: 'Dashboard', icon: '◫' },
] as const

export function FrontdeskTopNav({ activeItem = null }: FrontdeskTopNavProps) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-[22px] bg-[rgba(255,255,255,0.78)] px-4 py-2.5 shadow-[0_10px_22px_rgba(26,28,25,0.05)] backdrop-blur-sm">
      <div className="flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[rgba(97,0,0,0.12)] text-[1.15rem]">
          👨🏻‍🍳
        </div>
        <div>
          <p className="font-display text-[1.2rem] font-extrabold tracking-[-0.04em] text-[var(--primary)]">Ichiraku POS</p>
          <p className="text-[0.72rem] text-[var(--muted)]">Frontdesk Workstation</p>
        </div>
      </div>

      <nav className="flex items-center gap-2">
        {navItems.map((item) => {
          const active = item.id === activeItem
          return (
            <button
              key={item.id}
              type="button"
              className={`inline-flex min-h-10 items-center gap-2 rounded-[16px] px-3.5 text-[0.88rem] font-semibold transition ${
                active
                  ? 'bg-[var(--surface-container-lowest)] text-[var(--primary)] shadow-[0_10px_22px_rgba(26,28,25,0.05)]'
                  : 'text-[rgba(26,28,25,0.7)] hover:bg-[rgba(26,28,25,0.04)]'
              }`}
              onClick={() => {
                if (item.id === 'orders') {
                  navigateTo('/frontdesk/order')
                  return
                }
                if (item.id === 'pickup') {
                  navigateTo('/pickup')
                  return
                }
                if (item.id === 'menu') {
                  navigateTo('/frontdesk')
                }
              }}
            >
              <span className="text-[1.05rem] leading-none">{item.icon}</span>
              <span>{item.label}</span>
            </button>
          )
        })}
      </nav>
    </div>
  )
}
