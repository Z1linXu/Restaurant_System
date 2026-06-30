import { KdsSidebar } from '../noodle/components/KdsSidebar'
import { KdsTopBar } from '../noodle/components/KdsTopBar'
import { useKdsHistory } from '../../../hooks/useKdsHistory'
import { useIpadLandscape } from '../../../hooks/useIpadLandscape'
import { useCurrentStore } from '../../store/StoreContext'
import { buildStorePath } from '../../store/storeRoutes'

const ASSEMBLING_STATIONS = ['NOODLE', 'WOK', 'COLD', 'DEEPFRIED'] as const

function groupLabelForStation(stationCode: string | null) {
  if (stationCode === 'NOODLE') {
    return 'NOODLE'
  }
  if (stationCode === 'WOK') {
    return 'WOK'
  }
  if (stationCode === 'COLD') {
    return 'SIDE'
  }
  if (stationCode === 'DEEPFRIED') {
    return 'FRIED'
  }
  return 'OTHER'
}

const DATE_FORMATTER = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
})

function navigateTo(path: string) {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export function KdsHistoryPage() {
  const { storeId } = useCurrentStore()
  const { orders, loading, error } = useKdsHistory(storeId, 'ASSEMBLING')
  const compact = useIpadLandscape()
  const now = new Date()

  return (
    <div className="min-h-screen bg-[var(--surface)] text-[var(--on-surface)]">
      <div className={`grid min-h-screen ${compact ? 'grid-cols-[4.75rem_minmax(0,1fr)]' : 'grid-cols-[5.75rem_minmax(0,1fr)]'}`}>
        <KdsSidebar
          activeItem="history"
          compact={compact}
          onNavigate={(target) => navigateTo(buildStorePath(storeId, target === 'orders' ? '/kds/grab' : '/kds/history'))}
        />

        <main className={compact ? 'px-3.5 py-3' : 'px-5 py-4 md:px-7 xl:px-8'}>
          <div className={`mx-auto ${compact ? 'max-w-none space-y-3.5' : 'max-w-[1760px] space-y-6'}`}>
            <KdsTopBar
              currentTimeLabel={now.toLocaleTimeString('en-US', { hour12: false })}
              currentDateLabel={DATE_FORMATTER.format(now).toUpperCase()}
              compact={compact}
            />

            <div className={`bg-[rgba(255,255,255,0.8)] shadow-[0_14px_34px_rgba(26,28,25,0.05)] ${compact ? 'rounded-[22px] px-4 py-3.5' : 'rounded-[28px] px-6 py-5'}`}>
              <div className={`font-display font-extrabold tracking-[-0.05em] text-[var(--on-surface)] ${compact ? 'text-[1.45rem]' : 'text-[2rem]'}`}>Order History</div>
              <div className={`${compact ? 'mt-1 text-[0.85rem]' : 'mt-1 text-base'} text-[var(--muted)]`}>Completed assembling work for noodle, side dish, and fried items is moved here.</div>
            </div>

            {error ? (
              <div className={`rounded-[24px] bg-[rgba(97,0,0,0.08)] font-semibold text-[var(--primary)] ${compact ? 'px-4 py-3 text-[0.92rem]' : 'px-5 py-4 text-base'}`}>
                {error}
              </div>
            ) : null}

            <div className={compact ? 'space-y-3' : 'space-y-4'}>
              {loading
                ? Array.from({ length: 3 }).map((_, index) => (
                    <div key={`history-loading-${index}`} className={`${compact ? 'h-32 rounded-[22px]' : 'h-40 rounded-[28px]'} animate-pulse bg-[rgba(255,255,255,0.72)]`} />
                  ))
                : orders.map((order) => (
                    <div
                      key={order.order_id}
                      className={`bg-[rgba(255,255,255,0.84)] shadow-[0_14px_34px_rgba(26,28,25,0.05)] ${compact ? 'rounded-[22px] px-4 py-3.5' : 'rounded-[28px] px-6 py-5'}`}
                    >
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <div className="flex items-center gap-3">
                            <div className={`font-display font-extrabold tracking-[-0.06em] text-[var(--on-surface)] ${compact ? 'text-[1.7rem]' : 'text-[2.3rem]'}`}>
                              {order.table_no ?? order.pickup_no ?? `#${order.order_id}`}
                            </div>
                            {order.is_modified_after_submit ? (
                              <span className="rounded-full bg-[rgba(138,22,22,0.1)] px-3 py-1 text-[0.76rem] font-bold tracking-[0.14em] text-[var(--primary)]">
                                UPDATED
                              </span>
                            ) : null}
                          </div>
                          <div className={`${compact ? 'mt-0.5 text-[0.76rem]' : 'mt-1 text-sm'} font-semibold text-[var(--muted)]`}>
                            {order.order_no} · {order.order_status.toUpperCase()}
                          </div>
                        </div>
                        <div className={`text-right font-medium text-[var(--muted)] ${compact ? 'text-[0.74rem]' : 'text-sm'}`}>
                          <div>Ready: {order.ready_at ? new Date(order.ready_at).toLocaleTimeString('en-US', { hour12: false }) : 'UNKNOWN'}</div>
                          <div>Completed: {order.completed_at ? new Date(order.completed_at).toLocaleTimeString('en-US', { hour12: false }) : '—'}</div>
                        </div>
                      </div>

                      <div className={`${compact ? 'mt-3 space-y-3' : 'mt-4 space-y-4'}`}>
                        {['NOODLE', 'WOK', 'SIDE', 'FRIED'].map((groupLabel) => {
                          const groupItems = order.items.filter(
                            (item) => ASSEMBLING_STATIONS.includes((item.station_code ?? '') as (typeof ASSEMBLING_STATIONS)[number])
                              && groupLabelForStation(item.station_code) === groupLabel,
                          )

                          if (groupItems.length === 0) {
                            return null
                          }

                          return (
                            <div key={`${order.order_id}-${groupLabel}`} className={compact ? 'space-y-2' : 'space-y-3'}>
                              <div className={`${compact ? 'text-[0.72rem]' : 'text-[0.88rem]'} font-bold uppercase tracking-[0.16em] text-[var(--secondary)]`}>{groupLabel}</div>
                              {groupItems.map((item) => (
                                <div
                                  key={`${order.order_id}-${item.order_item_id}`}
                                  className={`bg-[rgba(26,28,25,0.04)] ${compact ? 'rounded-[16px] px-3.5 py-3' : 'rounded-[20px] px-4 py-4'}`}
                                >
                                  <div className="flex flex-wrap items-center gap-2">
                                    <span className={`font-display font-bold tracking-[-0.03em] ${compact ? 'text-[0.95rem]' : 'text-[1.08rem]'}`}>{item.item_name_snapshot_en}</span>
                                    <span className={`font-display font-bold tracking-[-0.03em] ${compact ? 'text-[0.95rem]' : 'text-[1.08rem]'}`}>x{item.quantity}</span>
                                    {item.is_modified_after_submit ? (
                                      <span className="rounded-full bg-[rgba(138,22,22,0.1)] px-2.5 py-1 text-[0.7rem] font-bold tracking-[0.12em] text-[var(--primary)]">
                                        UPDATED
                                      </span>
                                    ) : null}
                                  </div>
                                  <div className={`${compact ? 'mt-0.5 text-[0.82rem]' : 'mt-1 text-[0.98rem]'} font-medium text-[var(--muted)]`}>{item.item_name_snapshot_zh}</div>
                                  {item.special_instructions_snapshot ? (
                                    <div className={`${compact ? 'mt-1.5 text-[0.84rem] leading-6' : 'mt-2 text-[0.96rem] leading-7'} text-[var(--on-surface)]`}>{item.special_instructions_snapshot}</div>
                                  ) : null}
                                </div>
                              ))}
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  ))}
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}
