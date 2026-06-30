import { useEffect, useState } from 'react'
import { useIpadLandscape } from '../../../hooks/useIpadLandscape'
import { useHotKitchenOrders } from '../../../hooks/useHotKitchenOrders'
import { KdsTopBar, type KdsDisplaySizeMode } from '../noodle/components/KdsTopBar'
import { HotKitchenOrderCard } from './components/HotKitchenOrderCard'
import { useCurrentStore } from '../../store/StoreContext'

const PREP_TIME_FORMATTER = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
})

const DISPLAY_MODE_STORAGE_KEY = 'restaurant.kds.hot-kitchen.display-size'

export function HotKitchenPage() {
  const { storeId } = useCurrentStore()
  const compact = useIpadLandscape()
  const { orders, loading, refreshing, error, activeTaskCount, refresh } = useHotKitchenOrders(storeId)
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

  const cardMinWidth =
    displayMode === 'compact'
      ? 250
      : displayMode === 'large'
        ? 360
        : displayMode === 'xlarge'
          ? 430
          : 300

  return (
    <div className="min-h-screen bg-[var(--surface)] text-[var(--on-surface)]">
      <main className={compact ? 'px-3.5 py-3' : 'px-5 py-4 md:px-7 xl:px-8'}>
        <div className={`mx-auto ${compact ? 'max-w-none space-y-3.5' : 'max-w-[1760px] space-y-6'}`}>
          <KdsTopBar
            currentTimeLabel={now.toLocaleTimeString('en-US', { hour12: false })}
            currentDateLabel={PREP_TIME_FORMATTER.format(now).toUpperCase()}
            compact={compact}
            displayMode={displayMode}
            onDisplayModeChange={setDisplayMode}
            title="Hot Kitchen"
            subtitle="Wok / Fried Noodles / Fried Items"
            badgeLabel={`${activeTaskCount} Active`}
          />

          {error ? (
            <div className={`rounded-[24px] bg-[rgba(97,0,0,0.08)] font-semibold text-[var(--primary)] ${compact ? 'px-4 py-3 text-[0.92rem]' : 'px-5 py-4 text-base'}`}>
              {error}
            </div>
          ) : null}

          <div
            className="grid gap-4"
            style={{
              gridTemplateColumns: `repeat(auto-fit, minmax(${cardMinWidth}px, 1fr))`,
            }}
          >
            {loading
              ? Array.from({ length: 6 }).map((_, index) => (
                  <div
                    key={`hot-kitchen-loading-${index}`}
                    className={`rounded-[24px] animate-pulse bg-[rgba(255,255,255,0.72)] ${
                      displayMode === 'compact'
                        ? 'h-[13rem]'
                        : displayMode === 'large'
                          ? 'h-[18rem]'
                          : displayMode === 'xlarge'
                            ? 'h-[22rem]'
                            : 'h-[15.5rem]'
                    }`}
                  />
                ))
              : orders.map((order) => (
                  <HotKitchenOrderCard
                    key={order.detail.id}
                    order={order}
                    now={now}
                    compact={compact}
                    displayMode={displayMode}
                    onCompleted={() => void refresh()}
                  />
                ))}
          </div>

          {!loading && orders.length === 0 ? (
            <div className={`bg-[rgba(255,255,255,0.76)] text-center shadow-[0_14px_32px_rgba(26,28,25,0.05)] ${compact ? 'rounded-[22px] px-6 py-10' : 'rounded-[28px] px-8 py-16'}`}>
              <div className={`font-display font-extrabold tracking-[-0.05em] text-[var(--on-surface)] ${compact ? 'text-[1.7rem]' : 'text-[2.2rem]'}`}>
                No active hot-kitchen items
              </div>
              <div className={`${compact ? 'mt-1.5 text-[0.95rem]' : 'mt-2 text-lg'} text-[var(--muted)]`}>
                Wok dishes, fried noodles, and fried items will appear here.
              </div>
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
  )
}
