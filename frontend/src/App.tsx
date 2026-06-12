import { Suspense, lazy, useEffect, useState } from 'react'
import { FeatureDisabledPage } from './features/feature-flags/FeatureDisabledPage'
import { getRequiredFeatureForPath, isFeatureEnabled } from './features/feature-flags/featureConfig'

const DineIn = lazy(() => import('./pages/DineIn'))
const AdminDashboard = lazy(() => import('./pages/AdminDashboard'))
const AdminDiningTables = lazy(() => import('./pages/AdminDiningTables'))
const AdminMenuItems = lazy(() => import('./pages/AdminMenuItems'))
const AdminPrintingSettings = lazy(() => import('./pages/AdminPrintingSettings'))
const AdminPlatform = lazy(() => import('./pages/AdminPlatform'))
const AdminReportsItems = lazy(() => import('./pages/AdminReportsItems'))
const AdminReportsProfit = lazy(() => import('./pages/AdminReportsProfit'))
const AdminReportsSales = lazy(() => import('./pages/AdminReportsSales'))
const AdminReportsStores = lazy(() => import('./pages/AdminReportsStores'))
const Home = lazy(() => import('./pages/Home'))
const KdsHistory = lazy(() => import('./pages/KdsHistory'))
const KdsHotKitchen = lazy(() => import('./pages/KdsHotKitchen'))
const KdsNoodle = lazy(() => import('./pages/KdsNoodle'))
const KdsRamen = lazy(() => import('./pages/KdsRamen'))
const Login = lazy(() => import('./pages/Login'))
const Orders = lazy(() => import('./pages/Orders'))
const PickupBoard = lazy(() => import('./pages/PickupBoard'))

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
    </Suspense>
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

  if (pathname.startsWith('/kds/grab')) {
    return (
      <AppShell>
        <KdsNoodle />
      </AppShell>
    )
  }

  if (pathname.startsWith('/kds/hot-kitchen')) {
    return (
      <AppShell>
        <KdsHotKitchen />
      </AppShell>
    )
  }

  if (pathname.startsWith('/kds/noodle') || pathname.startsWith('/kds/ramen')) {
    return (
      <AppShell>
        <KdsRamen />
      </AppShell>
    )
  }

  if (pathname.startsWith('/kds/history')) {
    return (
      <AppShell>
        <KdsHistory />
      </AppShell>
    )
  }

  if (pathname.startsWith('/frontdesk/order') || pathname.startsWith('/orders')) {
    return (
      <AppShell>
        <Orders />
      </AppShell>
    )
  }

  if (pathname.startsWith('/admin/dashboard')) {
    return (
      <AppShell>
        <AdminDashboard />
      </AppShell>
    )
  }

  if (pathname.startsWith('/admin/settings/tables')) {
    return (
      <AppShell>
        <AdminDiningTables />
      </AppShell>
    )
  }

  if (pathname.startsWith('/admin/menu/items')) {
    return (
      <AppShell>
        <AdminMenuItems />
      </AppShell>
    )
  }

  if (pathname.startsWith('/admin/settings/printing')) {
    return (
      <AppShell>
        <AdminPrintingSettings />
      </AppShell>
    )
  }

  if (pathname.startsWith('/admin/reports/sales')) {
    return (
      <AppShell>
        <AdminReportsSales />
      </AppShell>
    )
  }

  if (pathname.startsWith('/admin/reports/items')) {
    return (
      <AppShell>
        <AdminReportsItems />
      </AppShell>
    )
  }

  if (pathname.startsWith('/admin/reports/profit')) {
    return (
      <AppShell>
        <AdminReportsProfit />
      </AppShell>
    )
  }

  if (pathname.startsWith('/admin/reports/stores')) {
    return (
      <AppShell>
        <AdminReportsStores />
      </AppShell>
    )
  }

  if (pathname.startsWith('/admin/platform')) {
    return (
      <AppShell>
        <AdminPlatform />
      </AppShell>
    )
  }

  if (pathname.startsWith('/pickup')) {
    return (
      <AppShell>
        <PickupBoard />
      </AppShell>
    )
  }

  if (pathname.startsWith('/frontdesk/menu') || pathname.startsWith('/menu')) {
    return (
      <AppShell>
        <DineIn />
      </AppShell>
    )
  }

  if (pathname === '/frontdesk' || pathname === '/frontdesk/') {
    return (
      <AppShell>
        <DineIn />
      </AppShell>
    )
  }

  return (
    <AppShell>
      <Home />
    </AppShell>
  )
}

export default App
