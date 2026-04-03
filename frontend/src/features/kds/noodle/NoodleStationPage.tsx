import { useEffect, useState } from 'react'
import { useIpadLandscape } from '../../../hooks/useIpadLandscape'
import { useNoodleStationOrders } from '../../../hooks/useNoodleStationOrders'
import { KdsOrderCard } from './components/KdsOrderCard'
import { KdsSidebar } from './components/KdsSidebar'
import { KdsTopBar, type KdsDisplaySizeMode } from './components/KdsTopBar'

const PREP_TIME_FORMATTER = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
})
const DISPLAY_MODE_STORAGE_KEY = 'restaurant.kds.assembling.display-size'

function navigateTo(path: string) {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export function NoodleStationPage() {
  const { orders, loading, refreshing, error, refresh } = useNoodleStationOrders(1)
  const compact = useIpadLandscape()
  const [now, setNow] = useState(() => new Date())
  const [displayMode, setDisplayMode] = useState<KdsDisplaySizeMode>(() => {
    const stored = window.localStorage.getItem(DISPLAY_MODE_STORAGE_KEY)
    if (stored === 'compact' || stored === 'standard' || stored === 'large' || stored === 'xlarge') {
      return stored
    }
    return 'standard'
  })

  useEffect(() => {
    window.localStorage.setItem(DISPLAY_MODE_STORAGE_KEY, displayMode)
  }, [displayMode])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  const compactWidths: Record<KdsDisplaySizeMode, string> = {
    compact: 'w-[11.6rem]',
    standard: 'w-[12.8rem]',
    large: 'w-[14.5rem]',
    xlarge: 'w-[16.2rem]',
  }

  const loadingWidths: Record<KdsDisplaySizeMode, string> = {
    compact: 'w-[11.6rem] h-[11rem]',
    standard: 'w-[12.8rem] h-[12.5rem]',
    large: 'w-[14.5rem] h-[14rem]',
    xlarge: 'w-[16.2rem] h-[15.5rem]',
  }

  return (
    <div className="min-h-screen bg-[var(--surface)] text-[var(--on-surface)]">
      <div className={`grid min-h-screen ${compact ? 'grid-cols-[4.75rem_minmax(0,1fr)]' : 'grid-cols-[5.75rem_minmax(0,1fr)]'}`}>
        <KdsSidebar
          activeItem="orders"
          compact={compact}
          onNavigate={(target) => navigateTo(target === 'orders' ? '/kds/grab' : '/kds/history')}
        />

        <main className={compact ? 'px-3.5 py-3' : 'px-5 py-4 md:px-7 xl:px-8'}>
          <div className={`mx-auto ${compact ? 'max-w-none space-y-3.5' : 'max-w-[1760px] space-y-6'}`}>
            <KdsTopBar
              currentTimeLabel={now.toLocaleTimeString('en-US', { hour12: false })}
              currentDateLabel={PREP_TIME_FORMATTER.format(now).toUpperCase()}
              compact={compact}
              displayMode={displayMode}
              onDisplayModeChange={setDisplayMode}
            />

            {error ? (
              <div className={`rounded-[24px] bg-[rgba(97,0,0,0.08)] font-semibold text-[var(--primary)] ${compact ? 'px-4 py-3 text-[0.92rem]' : 'px-5 py-4 text-base'}`}>
                {error}
              </div>
            ) : null}

            {compact ? (
              <div className="overflow-x-auto pb-2 [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
                <div className="flex min-w-max gap-3">
                  {loading
                    ? Array.from({ length: 6 }).map((_, index) => (
                        <div
                          key={`kds-loading-${index}`}
                          className={`${loadingWidths[displayMode]} flex-none rounded-[18px] animate-pulse bg-[rgba(255,255,255,0.72)]`}
                        />
                      ))
                    : orders.map((order) => (
                        <div key={order.detail.id} className={`${compactWidths[displayMode]} flex-none`}>
                          <KdsOrderCard
                            order={order}
                            now={now}
                            compact
                            displayMode={displayMode}
                            onCompleted={() => void refresh()}
                          />
                        </div>
                      ))}
                </div>
              </div>
            ) : (
              <div className="grid gap-5 xl:grid-cols-3">
                {loading
                  ? Array.from({ length: 6 }).map((_, index) => (
                      <div key={`kds-loading-${index}`} className="h-[30rem] rounded-[28px] animate-pulse bg-[rgba(255,255,255,0.72)]" />
                    ))
                  : orders.map((order) => (
                      <KdsOrderCard
                        key={order.detail.id}
                        order={order}
                        now={now}
                        displayMode={displayMode}
                        onCompleted={() => void refresh()}
                      />
                    ))}
              </div>
            )}

            {!loading && orders.length === 0 ? (
              <div className={`bg-[rgba(255,255,255,0.76)] text-center shadow-[0_14px_32px_rgba(26,28,25,0.05)] ${compact ? 'rounded-[22px] px-6 py-10' : 'rounded-[28px] px-8 py-16'}`}>
                <div className={`font-display font-extrabold tracking-[-0.05em] text-[var(--on-surface)] ${compact ? 'text-[1.7rem]' : 'text-[2.2rem]'}`}>
                  No active noodle-station orders
                </div>
                <div className={`${compact ? 'mt-1.5 text-[0.95rem]' : 'mt-2 text-lg'} text-[var(--muted)]`}>Submitted and preparing orders will appear here.</div>
                <div className={`${compact ? 'mt-1 text-[0.84rem]' : 'mt-1 text-base'} text-[var(--muted)]`}>Visible work includes noodle, side dish, and fried items for the same order.</div>
              </div>
            ) : null}

            {refreshing && !loading ? (
              <div className={`fixed rounded-full bg-[rgba(255,255,255,0.92)] font-semibold text-[var(--muted)] shadow-[0_10px_24px_rgba(26,28,25,0.08)] ${compact ? 'right-4 bottom-4 px-3 py-2 text-[0.78rem]' : 'right-6 bottom-6 px-4 py-3 text-sm'}`}>
                Refreshing...
              </div>
            ) : null}
          </div>
        </main>
      </div>
    </div>
  )
}
