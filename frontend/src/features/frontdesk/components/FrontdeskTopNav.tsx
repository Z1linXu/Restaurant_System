import { navigateTo } from '../navigation'
import { isFeatureEnabled, type FeaturePackage } from '../../feature-flags/featureConfig'
import { StoreSwitcher } from '../../store/StoreSwitcher'
import { buildStorePath } from '../../store/storeRoutes'
import { useOptionalCurrentStore } from '../../store/StoreContext'

interface FrontdeskTopNavProps {
  activeItem?: 'menu' | 'orders' | 'pickup' | 'stations' | 'dashboard' | null
}

const navItems = [
  { id: 'orders', label: 'Orders', icon: '▤', feature: 'CORE_POS' },
  { id: 'menu', label: 'Menu', icon: '✕', feature: 'CORE_POS' },
  { id: 'pickup', label: 'Pickup', icon: '◉', feature: 'KDS' },
  { id: 'dashboard', label: 'Dashboard', icon: '◫', feature: 'ADMIN' },
] as const satisfies Array<{
  id: 'menu' | 'orders' | 'pickup' | 'dashboard'
  label: string
  icon: string
  feature: FeaturePackage
}>

export function FrontdeskTopNav({ activeItem = null }: FrontdeskTopNavProps) {
  const currentStore = useOptionalCurrentStore()
  const path = (target: string) => currentStore ? buildStorePath(currentStore.storeId, target) : target
  return (
    <div className="flex items-center justify-between gap-3 rounded-[22px] bg-[rgba(255,255,255,0.78)] px-4 py-2.5 shadow-[0_10px_22px_rgba(26,28,25,0.05)] backdrop-blur-sm">
      <div className="flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[rgba(97,0,0,0.12)] text-[1.15rem]">
          👨🏻‍🍳
        </div>
        <div>
          <p className="font-display text-[1.2rem] font-extrabold tracking-[-0.04em] text-[var(--primary)]">蘭</p>
          <p className="text-[0.72rem] text-[var(--muted)]">{currentStore?.storeName ?? 'Frontdesk Workstation'}</p>
        </div>
      </div>

      <nav className="flex items-center gap-2">
        <StoreSwitcher compact />
        {navItems.filter((item) => isFeatureEnabled(item.feature)).map((item) => {
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
                  navigateTo(path('/frontdesk/order'))
                  return
                }
                if (item.id === 'pickup') {
                  navigateTo(path('/pickup'))
                  return
                }
                if (item.id === 'menu') {
                  navigateTo(path('/frontdesk'))
                  return
                }
                if (item.id === 'dashboard') {
                  navigateTo(path('/admin/dashboard'))
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
