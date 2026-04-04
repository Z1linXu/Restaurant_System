import { Suspense, lazy, useEffect, useState } from 'react'

const DineIn = lazy(() => import('./pages/DineIn'))
const Home = lazy(() => import('./pages/Home'))
const KdsHistory = lazy(() => import('./pages/KdsHistory'))
const KdsHotKitchen = lazy(() => import('./pages/KdsHotKitchen'))
const KdsNoodle = lazy(() => import('./pages/KdsNoodle'))
const KdsRamen = lazy(() => import('./pages/KdsRamen'))
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
