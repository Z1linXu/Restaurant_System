import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { navigateTo } from '../frontdesk/navigation'
import { isFeatureEnabled, type FeaturePackage } from '../feature-flags/featureConfig'
import { StoreSwitcher } from '../store/StoreSwitcher'
import { buildStorePath, stripStorePrefix } from '../store/storeRoutes'
import { useOptionalCurrentStore } from '../store/StoreContext'

interface OwnerAdminShellProps {
  title: string
  description?: string
  children: React.ReactNode
}

interface NavItem {
  label: string
  path: string
  match: (pathname: string) => boolean
  feature: FeaturePackage | null
  frontdeskVisible?: boolean
}

const navItems: NavItem[] = [
  { label: 'Owner Home / Stores', path: '/owner/dashboard', match: (path) => path.startsWith('/owner'), feature: 'ADMIN' },
  { label: 'Home / Dashboard', path: '/admin/dashboard', match: (path) => path === '/admin' || path.startsWith('/admin/dashboard'), feature: 'ADMIN' },
  { label: 'Menu Management', path: '/admin/menu/items', match: (path) => path.startsWith('/admin/menu/items'), feature: 'ADMIN', frontdeskVisible: true },
  { label: 'Dining Tables', path: '/admin/settings/tables', match: (path) => path.startsWith('/admin/settings/tables'), feature: 'ADMIN' },
  { label: 'Printing Settings', path: '/admin/settings/printing', match: (path) => path.startsWith('/admin/settings/printing'), feature: 'PRINTING', frontdeskVisible: true },
  { label: 'Staff Management', path: '/admin/staff', match: (path) => path.startsWith('/admin/staff'), feature: 'ADMIN' },
  { label: 'Audit Logs', path: '/admin/audit-logs', match: (path) => path.startsWith('/admin/audit-logs') || path.startsWith('/admin/audit'), feature: 'ADMIN' },
  { label: 'Reports', path: '/admin/reports/sales', match: (path) => path.startsWith('/admin/reports'), feature: 'ANALYTICS' },
]

export function OwnerAdminShell({ title, description, children }: OwnerAdminShellProps) {
  const { user, isOwner, isManager, isFrontdesk, signOut } = useAuth()
  const currentStore = useOptionalCurrentStore()
  const [pathname, setPathname] = useState(window.location.pathname)

  useEffect(() => {
    const handlePopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const visibleItems = useMemo(
    () => navItems.filter((item) => {
      if (item.feature != null && !isFeatureEnabled(item.feature)) {
        return false
      }
      if (isOwner || isManager) {
        return true
      }
      return isFrontdesk && item.frontdeskVisible === true
    }),
    [isFrontdesk, isManager, isOwner],
  )

  const homePath = isFrontdesk ? '/frontdesk' : '/admin/dashboard'
  const shellTitle = isFrontdesk ? 'Store Tools' : 'Owner Console'
  const frontdeskPath = currentStore ? buildStorePath(currentStore.storeId, '/frontdesk') : '/frontdesk'
  const backToFrontdeskButton = (
    <button
      type="button"
      onClick={() => navigateTo(frontdeskPath)}
      className="min-h-11 rounded-[16px] bg-[var(--primary)] px-4 text-[0.92rem] font-black text-white shadow-[0_12px_24px_rgba(97,0,0,0.16)]"
    >
      返回前台
    </button>
  )

  if (isFrontdesk) {
    return (
      <div className="min-h-screen bg-[linear-gradient(180deg,#f6f3ec_0%,#efe9dd_100%)] px-3 py-3 text-[var(--on-surface)] sm:px-4">
        <main className="mx-auto max-w-none space-y-3">
          <div className="sticky top-0 z-40 rounded-[24px] border border-[rgba(97,0,0,0.08)] bg-[rgba(255,255,255,0.92)] px-4 py-3 shadow-[0_14px_30px_rgba(26,28,25,0.08)] backdrop-blur">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-[180px]">
                <div className="text-[1.45rem] font-black tracking-[-0.05em] text-[var(--primary)]">Store Tools</div>
                <div className="mt-0.5 text-[0.82rem] font-semibold text-[var(--muted)]">
                  {user?.full_name || user?.username || 'Frontdesk'}
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {visibleItems.map((item) => {
                  const normalizedPath = stripStorePrefix(pathname).path
                  const active = item.match(normalizedPath)
                  const targetPath = currentStore ? buildStorePath(currentStore.storeId, item.path) : item.path
                  return (
                    <button
                      key={item.path}
                      type="button"
                      onClick={() => navigateTo(targetPath)}
                      className={`min-h-11 rounded-[16px] px-4 text-[0.9rem] font-bold transition ${
                        active
                          ? 'bg-[rgba(97,0,0,0.12)] text-[var(--primary)]'
                          : 'bg-[rgba(26,28,25,0.05)] text-[rgba(26,28,25,0.76)] hover:bg-[rgba(97,0,0,0.08)]'
                      }`}
                    >
                      {item.label}
                    </button>
                  )
                })}
                {backToFrontdeskButton}
                <StoreSwitcher compact />
                <button
                  type="button"
                  onClick={() => void signOut().finally(() => navigateTo('/login'))}
                  className="min-h-11 rounded-[16px] bg-[rgba(26,28,25,0.06)] px-4 text-[0.9rem] font-bold text-[var(--muted)]"
                >
                  退出登录
                </button>
              </div>
            </div>
          </div>

          <div className="rounded-[26px] bg-[rgba(255,255,255,0.84)] px-4 py-4 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div className="text-[1.5rem] font-black tracking-[-0.05em]">{title}</div>
                {description ? <div className="mt-1 text-[0.9rem] text-[var(--muted)]">{description}</div> : null}
              </div>
            </div>
          </div>
          <div className="min-w-0">{children}</div>
        </main>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-[linear-gradient(180deg,#f6f3ec_0%,#efe9dd_100%)] text-[var(--on-surface)]">
      <div className="grid min-h-screen xl:grid-cols-[260px_minmax(0,1fr)]">
        <aside className="border-r border-[rgba(97,0,0,0.08)] bg-[rgba(255,255,255,0.82)] px-4 py-5 backdrop-blur-sm">
          <button
            type="button"
            onClick={() => navigateTo(currentStore ? buildStorePath(currentStore.storeId, homePath) : homePath)}
            className="w-full rounded-[24px] bg-[rgba(97,0,0,0.04)] px-4 py-4 text-left"
          >
            <div className="text-[1.55rem] font-black tracking-[-0.05em] text-[var(--primary)]">{shellTitle}</div>
            <div className="mt-1 text-[0.84rem] leading-5 text-[var(--muted)]">
              {user?.full_name || user?.username || 'Restaurant Admin'}
            </div>
          </button>

          <nav className="mt-5 space-y-2">
            {visibleItems.map((item) => {
              const normalizedPath = stripStorePrefix(pathname).path
              const active = item.match(normalizedPath)
              const targetPath = item.path.startsWith('/owner')
                ? item.path
                : currentStore
                  ? buildStorePath(currentStore.storeId, item.path)
                  : item.path
              return (
                <button
                  key={item.path}
                  type="button"
                  onClick={() => navigateTo(targetPath)}
                  className={`w-full rounded-[18px] px-4 py-3 text-left transition ${
                    active
                      ? 'bg-[var(--primary)] text-white shadow-[0_18px_34px_rgba(97,0,0,0.18)]'
                      : 'bg-transparent text-[rgba(26,28,25,0.78)] hover:bg-[rgba(97,0,0,0.06)]'
                  }`}
                >
                  <div className="text-[0.98rem] font-semibold">{item.label}</div>
                </button>
              )
            })}
          </nav>

          <button
            type="button"
            onClick={() => void signOut().finally(() => navigateTo('/login'))}
            className="mt-5 w-full rounded-[18px] bg-[rgba(26,28,25,0.06)] px-4 py-3 text-left text-[0.95rem] font-bold text-[var(--muted)]"
          >
            退出登录 / Sign out
          </button>
        </aside>

        <main className="px-5 py-5 xl:px-6">
          <div className="mx-auto max-w-[1600px] space-y-5">
            <div className="rounded-[28px] bg-[rgba(255,255,255,0.84)] px-5 py-4 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="text-[1.7rem] font-black tracking-[-0.05em]">{title}</div>
                  {description ? <div className="mt-1 text-[0.92rem] text-[var(--muted)]">{description}</div> : null}
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {backToFrontdeskButton}
                  <StoreSwitcher />
                </div>
              </div>
            </div>
            {children}
          </div>
        </main>
      </div>
    </div>
  )
}
