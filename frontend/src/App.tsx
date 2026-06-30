import { Suspense, lazy, useEffect, useState } from 'react'
import { RequireAuth, type AppRole } from './features/auth/RequireAuth'
import { FeatureDisabledPage } from './features/feature-flags/FeatureDisabledPage'
import { getRequiredFeatureForPath, isFeatureEnabled } from './features/feature-flags/featureConfig'
import { OwnerAdminShell } from './features/owner-admin/OwnerAdminShell'
import { DevRoleSwitcher } from './features/dev/DevRoleSwitcher'
import { StoreContextProvider, RequireStoreAccess } from './features/store/StoreContext'
import { chooseDefaultStore, defaultWorkspacePathForRole, mapLegacyPathToStorePath, stripStorePrefix } from './features/store/storeRoutes'
import { fetchWorkspaces } from './services/storeWorkspaceService'
import { navigateTo } from './features/frontdesk/navigation'
import { useAuth } from './features/auth/useAuth'

const DineIn = lazy(() => import('./pages/DineIn'))
const AdminAuditLogs = lazy(() => import('./pages/AdminAuditLogs'))
const AdminDashboard = lazy(() => import('./pages/AdminDashboard'))
const AdminDiningTables = lazy(() => import('./pages/AdminDiningTables'))
const AdminMenuItems = lazy(() => import('./pages/AdminMenuItems'))
const AdminPrintingSettings = lazy(() => import('./pages/AdminPrintingSettings'))
const AdminPlatform = lazy(() => import('./pages/AdminPlatform'))
const AdminReportsItems = lazy(() => import('./pages/AdminReportsItems'))
const AdminReportsProfit = lazy(() => import('./pages/AdminReportsProfit'))
const AdminReportsSales = lazy(() => import('./pages/AdminReportsSales'))
const AdminReportsStores = lazy(() => import('./pages/AdminReportsStores'))
const AdminStaff = lazy(() => import('./pages/AdminStaff'))
const Home = lazy(() => import('./pages/Home'))
const KdsHistory = lazy(() => import('./pages/KdsHistory'))
const KdsHotKitchen = lazy(() => import('./pages/KdsHotKitchen'))
const KdsNoodle = lazy(() => import('./pages/KdsNoodle'))
const KdsRamen = lazy(() => import('./pages/KdsRamen'))
const Login = lazy(() => import('./pages/Login'))
const Orders = lazy(() => import('./pages/Orders'))
const OwnerDashboard = lazy(() => import('./pages/OwnerDashboard'))
const PickupBoard = lazy(() => import('./pages/PickupBoard'))

const ADMIN_ROLES: AppRole[] = ['OWNER', 'ADMIN', 'MANAGER']
const OWNER_ROLES: AppRole[] = ['OWNER', 'ADMIN']
const FRONTDESK_ROLES: AppRole[] = ['OWNER', 'ADMIN', 'MANAGER', 'FRONTDESK']
const HOT_KITCHEN_ROLES: AppRole[] = ['OWNER', 'ADMIN', 'MANAGER', 'HOT_KITCHEN']
const NOODLE_ROLES: AppRole[] = ['OWNER', 'ADMIN', 'MANAGER', 'NOODLE_VIEW']
const PASS_ROLES: AppRole[] = ['OWNER', 'ADMIN', 'MANAGER', 'PASS']
const ALL_ROLES: AppRole[] = ['OWNER', 'ADMIN', 'MANAGER', 'FRONTDESK', 'HOT_KITCHEN', 'NOODLE_VIEW', 'PASS']

function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-[var(--surface)] px-6 py-6 text-[var(--on-surface)]">
          <div className="mx-auto max-w-[1200px] rounded-[24px] bg-[rgba(255,255,255,0.8)] px-6 py-5 shadow-[0_14px_32px_rgba(26,28,25,0.05)]">
            <div className="text-[0.9rem] font-medium text-[var(--muted)]">Loading page...</div>
          </div>
        </div>
      }
    >
      {children}
      <DevRoleSwitcher />
    </Suspense>
  )
}

function guard(children: React.ReactNode, allowedRoles: AppRole[]) {
  return <RequireAuth allowedRoles={allowedRoles}>{children}</RequireAuth>
}

function ownerAdminPage(children: React.ReactNode, title: string, description?: string) {
  return <OwnerAdminShell title={title} description={description}>{children}</OwnerAdminShell>
}

function storePage(storeId: number, children: React.ReactNode, allowedRoles: AppRole[]) {
  return guard(
    <StoreContextProvider storeId={storeId}>
      <RequireStoreAccess>{children}</RequireStoreAccess>
    </StoreContextProvider>,
    allowedRoles,
  )
}

function LegacyStoreRedirect({ pathname }: { pathname: string }) {
  const { user } = useAuth()
  const [message, setMessage] = useState('Opening your store workspace...')

  useEffect(() => {
    let active = true
    fetchWorkspaces()
      .then((workspaces) => {
        if (!active) return
        const store = chooseDefaultStore(workspaces)
        if (!store) {
          setMessage('No store workspace is assigned to this account.')
          return
        }
        const target =
          pathname === '/' || pathname === ''
            ? defaultWorkspacePathForRole(user?.role_code, workspaces)
            : mapLegacyPathToStorePath(pathname, store.id)
        if (!target) {
          setMessage('No store workspace is assigned to this account.')
          return
        }
        navigateTo(target + window.location.search)
      })
      .catch((exception) => {
        if (!active) return
        setMessage(exception instanceof Error ? exception.message : 'Unable to open store workspace')
      })
    return () => {
      active = false
    }
  }, [pathname, user?.role_code])

  return (
    <div className="min-h-screen bg-[var(--surface)] px-6 py-8 text-[var(--on-surface)]">
      <div className="mx-auto max-w-[760px] rounded-[30px] bg-white px-7 py-8 shadow-[0_22px_54px_rgba(26,28,25,0.1)]">
        <div className="text-[1rem] font-bold text-[var(--muted)]">{message}</div>
      </div>
    </div>
  )
}

function App() {
  const [pathname, setPathname] = useState(window.location.pathname)

  useEffect(() => {
    const handlePopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const requiredFeature = getRequiredFeatureForPath(pathname)
  if (!isFeatureEnabled(requiredFeature)) {
    return (
      <AppShell>
        <FeatureDisabledPage feature={requiredFeature} />
      </AppShell>
    )
  }

  if (pathname.startsWith('/login')) {
    return (
      <AppShell>
        <Login />
      </AppShell>
    )
  }

  const storeRoute = stripStorePrefix(pathname)
  const routePath = storeRoute.path
  const storeId = storeRoute.storeId

  if (pathname === '/owner' || pathname === '/owner/' || pathname === '/owner/dashboard' || pathname === '/owner/stores') {
    return <AppShell>{guard(<OwnerDashboard />, ADMIN_ROLES)}</AppShell>
  }

  if (!storeId && (
    pathname === '/'
    || pathname.startsWith('/frontdesk')
    || pathname.startsWith('/menu')
    || pathname.startsWith('/orders')
    || pathname.startsWith('/pickup')
    || pathname.startsWith('/kds/')
    || pathname === '/admin'
    || pathname === '/admin/'
    || pathname.startsWith('/admin/')
  )) {
    return <AppShell>{guard(<LegacyStoreRedirect pathname={pathname} />, ALL_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/kds/grab')) {
    return <AppShell>{storePage(storeId, <KdsNoodle />, PASS_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/kds/hot-kitchen')) {
    return <AppShell>{storePage(storeId, <KdsHotKitchen />, HOT_KITCHEN_ROLES)}</AppShell>
  }

  if (storeId && (routePath.startsWith('/kds/noodle') || routePath.startsWith('/kds/ramen'))) {
    return <AppShell>{storePage(storeId, <KdsRamen />, NOODLE_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/kds/history')) {
    return <AppShell>{storePage(storeId, <KdsHistory />, ADMIN_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/frontdesk/order')) {
    return <AppShell>{storePage(storeId, <Orders />, FRONTDESK_ROLES)}</AppShell>
  }

  if (storeId && (routePath === '/admin' || routePath === '/admin/' || routePath.startsWith('/admin/dashboard'))) {
    return <AppShell>{storePage(storeId, ownerAdminPage(<AdminDashboard />, 'Dashboard', 'Monitor restaurant performance and operating status.'), ADMIN_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/admin/staff')) {
    return (
      <AppShell>
        {storePage(storeId, ownerAdminPage(<AdminStaff />, 'Staff Management', 'Manage manager and frontdesk access for this restaurant.'), ADMIN_ROLES)}
      </AppShell>
    )
  }

  if (storeId && (routePath.startsWith('/admin/audit-logs') || routePath.startsWith('/admin/audit'))) {
    return (
      <AppShell>
        {storePage(storeId, ownerAdminPage(<AdminAuditLogs />, 'Audit Logs', 'Review account, menu, printing, and order operations.'), ADMIN_ROLES)}
      </AppShell>
    )
  }

  if (storeId && routePath.startsWith('/admin/settings/tables')) {
    return <AppShell>{storePage(storeId, ownerAdminPage(<AdminDiningTables />, 'Dining Tables', 'Maintain table labels, areas, capacity, and active status.'), ADMIN_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/admin/menu/items')) {
    return <AppShell>{storePage(storeId, ownerAdminPage(<AdminMenuItems />, 'Menu Management', 'Maintain menu items, pricing, cost, and options.'), ADMIN_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/admin/settings/printing')) {
    return <AppShell>{storePage(storeId, ownerAdminPage(<AdminPrintingSettings />, 'Printing Settings', 'Configure printers, assignments, test prints, and print jobs.'), ADMIN_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/admin/reports/sales')) {
    return <AppShell>{storePage(storeId, ownerAdminPage(<AdminReportsSales />, 'Sales Report', 'Review sales summaries from analytics tables.'), ADMIN_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/admin/reports/items')) {
    return <AppShell>{storePage(storeId, ownerAdminPage(<AdminReportsItems />, 'Item Sales Report', 'Review top and low-performing menu items.'), ADMIN_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/admin/reports/profit')) {
    return <AppShell>{storePage(storeId, ownerAdminPage(<AdminReportsProfit />, 'Profit Report', 'Review estimated cost, profit, and margin trends.'), ADMIN_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/admin/reports/stores')) {
    return <AppShell>{storePage(storeId, ownerAdminPage(<AdminReportsStores />, 'Store Comparison', 'Compare store-level sales and operations.'), ADMIN_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/admin/platform')) {
    return <AppShell>{storePage(storeId, <AdminPlatform />, OWNER_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/pickup')) {
    return <AppShell>{storePage(storeId, <PickupBoard />, PASS_ROLES)}</AppShell>
  }

  if (storeId && routePath.startsWith('/frontdesk/menu')) {
    return <AppShell>{storePage(storeId, <DineIn />, FRONTDESK_ROLES)}</AppShell>
  }

  if (storeId && (routePath === '/frontdesk' || routePath === '/frontdesk/')) {
    return <AppShell>{storePage(storeId, <DineIn />, FRONTDESK_ROLES)}</AppShell>
  }

  return (
    <AppShell>
      <Home />
    </AppShell>
  )
}

export default App
