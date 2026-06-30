import { navigateTo } from '../../frontdesk/navigation'
import { isFeatureEnabled, type FeaturePackage } from '../../feature-flags/featureConfig'
import { useAuth } from '../../auth/useAuth'
import { StoreSwitcher } from '../../store/StoreSwitcher'
import { buildStorePath } from '../../store/storeRoutes'
import { useOptionalCurrentStore } from '../../store/StoreContext'

interface DineInSidebarProps {
  activeItem?: 'menu'
  collapsed?: boolean
  onToggleCollapse?: () => void
  compact?: boolean
}

const navItems = [
  { id: 'orders', label: 'Orders', icon: '▤', feature: 'CORE_POS' },
  { id: 'menu', label: 'Menu', icon: '✕', feature: 'CORE_POS' },
  { id: 'dashboard', label: 'Dashboard', icon: '◫', feature: 'ADMIN' },
] as const satisfies Array<{
  id: 'orders' | 'menu' | 'dashboard'
  label: string
  icon: string
  feature: FeaturePackage
}>

export function DineInSidebar({
  activeItem = 'menu',
  collapsed = false,
  onToggleCollapse,
  compact = false,
}: DineInSidebarProps) {
  const { isFrontdesk } = useAuth()
  const currentStore = useOptionalCurrentStore()
  const path = (target: string) => currentStore ? buildStorePath(currentStore.storeId, target) : target
  return (
    <aside
      className={`flex h-full min-h-screen w-full flex-col bg-[rgba(244,244,239,0.72)] backdrop-blur-sm transition-[padding] duration-200 ${
        compact ? 'py-3' : 'py-5'
      } ${
        collapsed ? (compact ? 'px-2.5' : 'px-3') : compact ? 'px-3.5' : 'px-4 xl:px-5'
      }`}
    >
      <div className={`${compact ? 'space-y-5' : 'space-y-8'}`}>
        <div className={`flex ${collapsed ? 'flex-col items-center gap-3' : 'items-start justify-between gap-3'}`}>
          {!collapsed ? (
            <div className="flex items-center gap-3">
              <div className={`flex items-center justify-center rounded-full bg-[rgba(97,0,0,0.12)] text-2xl ${compact ? 'h-10 w-10 text-[1.35rem]' : 'h-12 w-12'}`}>
                👨🏻‍🍳
              </div>
              <div>
                <p className={`font-display font-bold tracking-[-0.04em] text-[var(--on-surface)] ${compact ? 'text-[1.15rem]' : 'text-[1.5rem]'}`}>
                  {currentStore?.storeName ?? 'Frontdesk'}
                </p>
                <p className={`${compact ? 'text-[0.78rem]' : 'text-sm'} text-[var(--muted)]`}>Shift: Morning</p>
              </div>
            </div>
          ) : (
            <div className={`flex items-center justify-center rounded-full bg-[rgba(97,0,0,0.12)] text-2xl ${compact ? 'h-10 w-10 text-[1.35rem]' : 'h-12 w-12'}`}>
              👨🏻‍🍳
            </div>
          )}

          <button
            type="button"
            onClick={onToggleCollapse}
            className={`flex items-center justify-center rounded-2xl bg-[rgba(255,255,255,0.7)] text-xl text-[var(--on-surface)] shadow-[0_10px_22px_rgba(26,28,25,0.05)] transition hover:bg-white ${compact ? 'h-9 w-9 text-base' : 'h-11 w-11'}`}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? '»' : '«'}
          </button>
        </div>

        <nav className={`${compact ? 'space-y-2' : 'space-y-2.5'}`}>
          {navItems.filter((item) => isFeatureEnabled(item.feature) && !(isFrontdesk && item.id === 'dashboard')).map((item) => {
            const active = item.id === activeItem
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => {
                  if (item.id === 'orders') {
                    navigateTo(path('/frontdesk/order'))
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
                className={`flex w-full items-center rounded-[22px] text-left transition ${
                  collapsed ? (compact ? 'justify-center px-0 py-3' : 'justify-center px-0 py-3.5') : compact ? 'gap-3 px-3 py-3' : 'gap-4 px-4 py-3.5'
                } ${
                  active
                    ? 'bg-[var(--surface-container-lowest)] text-[var(--primary)] shadow-[0_12px_28px_rgba(26,28,25,0.06)]'
                    : 'text-[rgba(26,28,25,0.72)] hover:bg-[rgba(255,255,255,0.45)]'
                }`}
                title={collapsed ? item.label : undefined}
              >
                <span className={`${compact ? 'text-[1.45rem]' : 'text-[1.8rem]'} leading-none`}>{item.icon}</span>
                {!collapsed ? <span className={`${compact ? 'text-[0.92rem]' : 'text-[1rem]'} font-medium`}>{item.label}</span> : null}
              </button>
            )
          })}
        </nav>
        {!collapsed ? <StoreSwitcher compact /> : null}
      </div>
    </aside>
  )
}
