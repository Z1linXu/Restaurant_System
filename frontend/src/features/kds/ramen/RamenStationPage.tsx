import { useEffect, useState } from 'react'
import { useRamenStationOrders } from '../../../hooks/useRamenStationOrders'
import { RamenOrderCard } from './components/RamenOrderCard'

const DATE_FORMATTER = new Intl.DateTimeFormat('en-US', {
  weekday: 'long',
  month: 'short',
  day: 'numeric',
})

export function RamenStationPage() {
  const { orders, loading, error } = useRamenStationOrders(1)
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <div className="min-h-screen bg-[var(--surface)] px-3 py-3 text-[var(--on-surface)]">
      <div className="mx-auto max-w-[1800px] space-y-2.5">
        <header className="flex items-center justify-between rounded-[16px] bg-[rgba(255,255,255,0.96)] px-4 py-2.5 shadow-[0_12px_26px_rgba(26,28,25,0.05)] ring-1 ring-[rgba(26,28,25,0.05)]">
          <div className="min-w-0">
            <div className="text-[1.05rem] font-black tracking-[-0.06em] text-[var(--primary)]">
              RAMEN STATION A
            </div>
            <div className="text-[0.68rem] font-bold uppercase tracking-[0.16em] text-[var(--muted)]">
              {orders.length > 0 ? `${orders.length} Active Orders` : 'Waiting for New Orders'}
            </div>
          </div>
          <div className="text-right">
            <div className="text-[1.2rem] font-black tracking-[-0.06em] text-[var(--on-surface)]">
              {now.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}
            </div>
            <div className="text-[0.64rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">
              {DATE_FORMATTER.format(now)}
            </div>
          </div>
        </header>

        {error ? (
          <div className="rounded-[16px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.88rem] font-semibold text-[var(--primary)]">
            {error}
          </div>
        ) : null}

        {!loading && orders.length === 0 ? (
          <div className="rounded-[20px] bg-[rgba(255,255,255,0.88)] px-6 py-10 text-center shadow-[0_16px_36px_rgba(26,28,25,0.04)]">
            <div className="text-[1.8rem] font-black tracking-[-0.08em] text-[var(--on-surface)]">暂无拉面单</div>
            <div className="mt-1 text-[0.9rem] font-medium text-[var(--muted)]">等待新订单</div>
          </div>
        ) : (
          <section
            className="gap-0"
            style={{
              columnWidth: '270px',
              columnGap: '10px',
            }}
          >
            {loading
              ? Array.from({ length: 10 }).map((_, index) => (
                  <div
                    key={`ramen-loading-${index}`}
                    className="mb-2.5 h-[8.5rem] break-inside-avoid rounded-[16px] animate-pulse bg-[rgba(255,255,255,0.72)]"
                  />
                ))
              : orders.map((order, index) => (
                  <RamenOrderCard key={order.detail.id} order={order} orderIndex={index + 1} />
                ))}
          </section>
        )}
      </div>
    </div>
  )
}
