import { useEffect, useMemo, useState } from 'react'
import { navigateTo } from '../frontdesk/navigation'
import { isFeatureEnabled, type FeaturePackage } from '../feature-flags/featureConfig'
import {
  fetchOwnerDashboard,
  type OwnerDashboardItemPerformance,
  type OwnerDashboardRange,
  type OwnerDashboardResponse,
  type OwnerDashboardStoreSummary,
  type OwnerDashboardTrendPoint,
} from '../../services/ownerDashboardService'
import { fetchPlatformOverview, type PlatformAdminOverview } from '../../services/platformAdminService'

type OwnerSection = 'home' | 'stores' | 'menu' | 'reports' | 'integrations' | 'settings'

const sidebarItems: { id: OwnerSection; label: string; icon: string; description: string; feature: FeaturePackage | null }[] = [
  { id: 'home', label: 'Home', icon: '⌂', description: 'Daily operating overview', feature: 'ADMIN' },
  { id: 'stores', label: 'Stores', icon: '▣', description: 'Store portfolio and health', feature: 'ADMIN' },
  { id: 'menu', label: 'Menu Management', icon: '☰', description: 'Menu maintenance workspace', feature: 'ADMIN' },
  { id: 'reports', label: 'Reports', icon: '◫', description: 'Sales and performance reports', feature: 'ANALYTICS' },
  { id: 'integrations', label: 'Integrations', icon: '◎', description: 'Delivery and platform links', feature: null },
  { id: 'settings', label: 'Settings', icon: '⚙', description: 'Organization-level settings', feature: 'PRINTING' },
]

const RANGE_OPTIONS: { value: OwnerDashboardRange; label: string }[] = [
  { value: 'today', label: 'Today' },
  { value: 'week', label: 'Week' },
  { value: 'month', label: 'Month' },
]

function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency: 'CAD',
    minimumFractionDigits: 2,
  }).format(value)
}

function formatPercent(value: number) {
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(1)}%`
}

function getChangeTone(value: number) {
  if (value > 0) {
    return 'text-emerald-700 bg-[rgba(18,141,77,0.1)]'
  }
  if (value < 0) {
    return 'text-[var(--primary)] bg-[rgba(97,0,0,0.08)]'
  }
  return 'text-[var(--muted)] bg-[rgba(26,28,25,0.06)]'
}

function getSeverityTone(severity: string) {
  switch (severity) {
    case 'critical':
      return 'border-[rgba(97,0,0,0.18)] bg-[rgba(97,0,0,0.08)] text-[var(--primary)]'
    case 'warning':
      return 'border-[rgba(191,104,32,0.18)] bg-[rgba(191,104,32,0.08)] text-[rgb(140,76,17)]'
    case 'success':
      return 'border-[rgba(18,141,77,0.18)] bg-[rgba(18,141,77,0.08)] text-[rgb(25,112,69)]'
    default:
      return 'border-[rgba(26,28,25,0.12)] bg-[rgba(26,28,25,0.04)] text-[var(--on-surface)]'
  }
}

function PlaceholderSection({ title, description }: { title: string; description: string }) {
  return (
    <div className="rounded-[28px] bg-[rgba(255,255,255,0.82)] p-6 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="text-[1.4rem] font-black tracking-[-0.04em] text-[var(--on-surface)]">{title}</div>
      <div className="mt-2 max-w-2xl text-[0.95rem] leading-6 text-[var(--muted)]">
        {description}
      </div>
      <div className="mt-6 rounded-[22px] border border-dashed border-[rgba(97,0,0,0.18)] bg-[rgba(97,0,0,0.03)] px-5 py-6 text-[0.92rem] font-medium text-[var(--primary)]">
        This module shell is ready. We can layer in live owner workflows here without changing the dashboard layout.
      </div>
    </div>
  )
}

function MetricCard({
  label,
  value,
  change,
}: {
  label: string
  value: string
  change: number
}) {
  return (
    <div className="rounded-[24px] bg-[rgba(255,255,255,0.82)] px-5 py-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="text-[0.78rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">{label}</div>
      <div className="mt-3 text-[1.9rem] font-black tracking-[-0.05em] text-[var(--on-surface)]">{value}</div>
      <div className={`mt-3 inline-flex rounded-full px-2.5 py-1 text-[0.78rem] font-semibold ${getChangeTone(change)}`}>
        {formatPercent(change)} vs previous period
      </div>
    </div>
  )
}

function TrendChart({
  points,
  granularity,
}: {
  points: OwnerDashboardTrendPoint[]
  granularity: string
}) {
  const maxValue = Math.max(...points.map((point) => point.value), 1)

  return (
    <div className="mt-5 grid gap-3" style={{ gridTemplateColumns: `repeat(${Math.max(points.length, 1)}, minmax(0, 1fr))` }}>
      {points.map((point) => (
        <div key={point.label} className="flex min-w-0 flex-col items-center gap-2">
          <div className="flex h-[190px] w-full items-end rounded-[16px] bg-[rgba(26,28,25,0.04)] px-1.5 pb-2">
            <div
              className="w-full rounded-[12px] bg-[linear-gradient(180deg,rgba(138,40,20,0.78)_0%,rgba(97,0,0,0.92)_100%)]"
              style={{ height: `${Math.max((point.value / maxValue) * 100, point.value > 0 ? 8 : 0)}%` }}
            />
          </div>
          <div className="text-center text-[0.76rem] font-semibold text-[var(--muted)]">{point.label}</div>
          <div className="text-center text-[0.82rem] font-bold text-[var(--on-surface)]">{formatCurrency(point.value)}</div>
        </div>
      ))}
      {!points.length ? (
        <div className="col-span-full rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
          No sales trend data for this range yet.
        </div>
      ) : null}
      <div className="col-span-full mt-1 text-[0.8rem] text-[var(--muted)]">
        Real {granularity} sales trend based on completed orders.
      </div>
    </div>
  )
}

function ItemPerformanceList({
  title,
  description,
  items,
  emptyMessage,
}: {
  title: string
  description: string
  items: OwnerDashboardItemPerformance[]
  emptyMessage: string
}) {
  return (
    <div className="rounded-[26px] bg-[rgba(255,255,255,0.82)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">{title}</div>
      <div className="mt-1 text-[0.85rem] text-[var(--muted)]">{description}</div>
      <div className="mt-4 space-y-3">
        {items.length ? (
          items.map((item, index) => (
            <div key={`${title}-${item.item_name}-${index}`} className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-3">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="truncate text-[0.96rem] font-bold text-[var(--on-surface)]">{item.item_name}</div>
                  <div className="mt-1 text-[0.78rem] text-[var(--muted)]">
                    {item.quantity} sold · {formatCurrency(item.revenue)} revenue
                  </div>
                </div>
                <div className={`shrink-0 rounded-full px-2.5 py-1 text-[0.76rem] font-semibold ${getChangeTone(item.quantity_change)}`}>
                  {item.quantity_change > 0 ? '+' : ''}
                  {item.quantity_change} qty
                </div>
              </div>
            </div>
          ))
        ) : (
          <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.88rem] text-[var(--muted)]">
            {emptyMessage}
          </div>
        )}
      </div>
    </div>
  )
}

export function OwnerAdminDashboardPage() {
  const [overview, setOverview] = useState<PlatformAdminOverview | null>(null)
  const [dashboard, setDashboard] = useState<OwnerDashboardResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [dashboardLoading, setDashboardLoading] = useState(true)
  const [activeSection, setActiveSection] = useState<OwnerSection>('home')
  const [selectedStoreId, setSelectedStoreId] = useState<string>('ALL')
  const [selectedRange, setSelectedRange] = useState<OwnerDashboardRange>('today')
  const [compareEnabled, setCompareEnabled] = useState(true)
  const [clock, setClock] = useState(new Date())
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const timer = window.setInterval(() => setClock(new Date()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    const loadOverview = async () => {
      setLoading(true)
      setError(null)
      try {
        const data = await fetchPlatformOverview(1)
        setOverview(data)
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : 'Failed to load owner dashboard data')
      } finally {
        setLoading(false)
      }
    }

    void loadOverview()
  }, [])

  const organization = overview?.organizations?.[0]
  const stores = useMemo<OwnerDashboardStoreSummary[]>(
    () =>
      (overview?.stores ?? []).map((store) => ({
        id: Number(store.id),
        name: String(store.name ?? `Store ${store.id}`),
        code: String(store.code ?? store.id),
      })),
    [overview],
  )

  useEffect(() => {
    if (!overview || activeSection !== 'home') {
      return
    }

    const loadDashboard = async () => {
      setDashboardLoading(true)
      setError(null)
      try {
        const nextDashboard = await fetchOwnerDashboard({
          organizationId: Number(organization?.id ?? dashboard?.organization_id ?? 1),
          storeId: selectedStoreId === 'ALL' ? null : Number(selectedStoreId),
          range: selectedRange,
          compare: compareEnabled,
        })
        setDashboard(nextDashboard)
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : 'Failed to load analytics dashboard')
      } finally {
        setDashboardLoading(false)
      }
    }

    void loadDashboard()
  }, [activeSection, compareEnabled, dashboard?.organization_id, organization?.id, overview, selectedRange, selectedStoreId])

  const currentStoreLabel =
    selectedStoreId === 'ALL' ? 'All Stores' : stores.find((store) => String(store.id) === selectedStoreId)?.name ?? 'Store'

  const salesTrendPoints = dashboard?.trend.points ?? []
  const topItems = dashboard?.top_items ?? []
  const worstItems = dashboard?.worst_items ?? []
  const recentOrders = dashboard?.recent_orders ?? []
  const storeComparison = dashboard?.store_comparison ?? []
  const statusPanel = dashboard?.order_status ?? { pending: 0, preparing: 0, ready: 0 }

  return (
    <div className="min-h-screen bg-[linear-gradient(180deg,#f6f3ec_0%,#efe9dd_100%)] text-[var(--on-surface)]">
      <div className="grid min-h-screen xl:grid-cols-[260px_minmax(0,1fr)]">
        <aside className="border-r border-[rgba(97,0,0,0.08)] bg-[rgba(255,255,255,0.8)] px-4 py-5 backdrop-blur-sm">
          <div className="rounded-[24px] bg-[rgba(97,0,0,0.04)] px-4 py-4">
            <div className="text-[1.6rem] font-black tracking-[-0.05em] text-[var(--primary)]">Owner Console</div>
            <div className="mt-1 text-[0.84rem] leading-5 text-[var(--muted)]">
              {String(organization?.name ?? dashboard?.organization_name ?? 'Restaurant Organization')}
            </div>
          </div>

          <nav className="mt-5 space-y-2">
            {sidebarItems.filter((item) => item.feature == null || isFeatureEnabled(item.feature)).map((item) => {
              const active = item.id === activeSection
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => {
                    if (item.id === 'reports') {
                      navigateTo('/admin/reports/sales')
                      return
                    }
                    if (item.id === 'stores') {
                      navigateTo('/admin/settings/tables')
                      return
                    }
                    if (item.id === 'menu') {
                      navigateTo('/admin/menu/items')
                      return
                    }
                    if (item.id === 'settings') {
                      navigateTo('/admin/settings/printing')
                      return
                    }
                    setActiveSection(item.id)
                  }}
                  className={`w-full rounded-[20px] px-4 py-3 text-left transition ${
                    active
                      ? 'bg-[var(--primary)] text-white shadow-[0_18px_34px_rgba(97,0,0,0.18)]'
                      : 'bg-transparent text-[rgba(26,28,25,0.78)] hover:bg-[rgba(97,0,0,0.06)]'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <span className="text-[1.2rem] leading-none">{item.icon}</span>
                    <div>
                      <div className="text-[0.98rem] font-semibold">{item.label}</div>
                      <div className={`mt-0.5 text-[0.76rem] ${active ? 'text-[rgba(255,255,255,0.82)]' : 'text-[var(--muted)]'}`}>
                        {item.description}
                      </div>
                    </div>
                  </div>
                </button>
              )
            })}
          </nav>
        </aside>

        <main className="px-5 py-5 xl:px-6">
          <div className="mx-auto max-w-[1600px] space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-4 rounded-[28px] bg-[rgba(255,255,255,0.84)] px-5 py-4 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div>
                <div className="text-[1.7rem] font-black tracking-[-0.05em] text-[var(--on-surface)]">
                  {activeSection === 'home' ? 'Restaurant Performance Dashboard' : sidebarItems.find((item) => item.id === activeSection)?.label}
                </div>
                <div className="mt-1 text-[0.9rem] text-[var(--muted)]">
                  {activeSection === 'home'
                    ? 'Production analytics for owners and store managers.'
                    : 'Section shell ready for the next phase of owner tooling.'}
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <div className="rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-2.5">
                  <div className="text-[0.72rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">Store Scope</div>
                  <select
                    value={selectedStoreId}
                    onChange={(event) => setSelectedStoreId(event.target.value)}
                    className="mt-1 min-w-[220px] bg-transparent text-[0.95rem] font-semibold text-[var(--on-surface)] outline-none"
                  >
                    <option value="ALL">All Stores</option>
                    {stores.map((store) => (
                      <option key={store.id} value={String(store.id)}>
                        {store.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-2.5">
                  <div className="text-[0.72rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">Date Range</div>
                  <div className="mt-1 inline-flex rounded-full bg-[rgba(26,28,25,0.05)] p-1">
                    {RANGE_OPTIONS.map((option) => (
                      <button
                        key={option.value}
                        type="button"
                        onClick={() => setSelectedRange(option.value)}
                        className={`rounded-full px-3 py-1.5 text-[0.78rem] font-semibold transition ${
                          selectedRange === option.value
                            ? 'bg-[var(--surface-container-lowest)] text-[var(--primary)] shadow-[0_8px_18px_rgba(26,28,25,0.08)]'
                            : 'text-[var(--muted)]'
                        }`}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                </div>

                <label className="flex cursor-pointer items-center gap-3 rounded-[16px] bg-[rgba(26,28,25,0.04)] px-4 py-3">
                  <input
                    type="checkbox"
                    checked={compareEnabled}
                    onChange={(event) => setCompareEnabled(event.target.checked)}
                    className="h-4 w-4 accent-[var(--primary)]"
                  />
                  <div>
                    <div className="text-[0.72rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">Compare</div>
                    <div className="mt-1 text-[0.9rem] font-semibold text-[var(--on-surface)]">Previous period</div>
                  </div>
                </label>

                <div className="rounded-[16px] bg-[rgba(97,0,0,0.06)] px-4 py-2.5 text-right">
                  <div className="text-[0.72rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">Live Clock</div>
                  <div className="mt-1 text-[1.15rem] font-bold text-[var(--primary)]">
                    {clock.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit', second: '2-digit' })}
                  </div>
                </div>
              </div>
            </div>

            {loading ? (
              <div className="rounded-[26px] bg-[rgba(255,255,255,0.82)] px-5 py-6 text-[0.95rem] text-[var(--muted)] shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                Loading owner console shell...
              </div>
            ) : error ? (
              <div className="rounded-[24px] bg-[rgba(97,0,0,0.08)] px-5 py-4 text-[0.95rem] font-semibold text-[var(--primary)]">
                {error}
              </div>
            ) : activeSection !== 'home' ? (
              <PlaceholderSection
                title={sidebarItems.find((item) => item.id === activeSection)?.label ?? 'Owner Module'}
                description={`The ${sidebarItems.find((item) => item.id === activeSection)?.label ?? 'module'} area will live under the same owner-admin shell and store selector.`}
              />
            ) : dashboardLoading || !dashboard ? (
              <div className="rounded-[26px] bg-[rgba(255,255,255,0.82)] px-5 py-6 text-[0.95rem] text-[var(--muted)] shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                Loading analytics dashboard...
              </div>
            ) : (
              <>
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                  <MetricCard label="Sales" value={formatCurrency(dashboard.kpis.sales.value)} change={dashboard.kpis.sales.change_pct} />
                  <MetricCard label="Orders" value={dashboard.kpis.orders.value.toFixed(0)} change={dashboard.kpis.orders.change_pct} />
                  <MetricCard
                    label="Average Order Value"
                    value={formatCurrency(dashboard.kpis.average_order_value.value)}
                    change={dashboard.kpis.average_order_value.change_pct}
                  />
                  <MetricCard
                    label="Active Orders"
                    value={dashboard.kpis.active_orders.value.toFixed(0)}
                    change={dashboard.kpis.active_orders.change_pct}
                  />
                </div>

                <div className="grid gap-4 xl:grid-cols-[1.25fr_0.75fr]">
                  <div className="rounded-[26px] bg-[rgba(255,255,255,0.82)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Sales Trend</div>
                        <div className="mt-1 text-[0.85rem] text-[var(--muted)]">
                          {currentStoreLabel} · {dashboard.trend.granularity}
                        </div>
                      </div>
                      <div className="rounded-full bg-[rgba(97,0,0,0.06)] px-3 py-1 text-[0.78rem] font-semibold text-[var(--primary)]">
                        {selectedRange}
                      </div>
                    </div>
                    <TrendChart points={salesTrendPoints} granularity={dashboard.trend.granularity} />
                  </div>

                  <div className="space-y-4">
                    <div className="rounded-[26px] bg-[rgba(255,255,255,0.82)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                      <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Order Status</div>
                      <div className="mt-1 text-[0.85rem] text-[var(--muted)]">Live active order workload.</div>
                      <div className="mt-4 grid grid-cols-3 gap-3">
                        {[
                          { label: 'Pending', value: statusPanel.pending, tone: 'bg-[rgba(191,104,32,0.08)] text-[rgb(140,76,17)]' },
                          { label: 'Preparing', value: statusPanel.preparing, tone: 'bg-[rgba(97,0,0,0.08)] text-[var(--primary)]' },
                          { label: 'Ready', value: statusPanel.ready, tone: 'bg-[rgba(18,141,77,0.08)] text-[rgb(25,112,69)]' },
                        ].map((status) => (
                          <div key={status.label} className={`rounded-[18px] px-4 py-4 ${status.tone}`}>
                            <div className="text-[0.76rem] font-semibold uppercase tracking-[0.14em]">{status.label}</div>
                            <div className="mt-2 text-[1.6rem] font-black tracking-[-0.05em]">{status.value}</div>
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="rounded-[26px] bg-[rgba(255,255,255,0.82)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                      <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Alerts & Insights</div>
                      <div className="mt-1 text-[0.85rem] text-[var(--muted)]">Automated checks from current period performance.</div>
                      <div className="mt-4 space-y-3">
                        {dashboard.insights.map((insight) => (
                          <div key={`${insight.type}-${insight.title}`} className={`rounded-[18px] border px-4 py-3 ${getSeverityTone(insight.severity)}`}>
                            <div className="text-[0.92rem] font-bold">{insight.title}</div>
                            <div className="mt-1 text-[0.82rem] leading-5">{insight.message}</div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>

                <div className="grid gap-4 xl:grid-cols-[0.9fr_0.9fr_1.2fr]">
                  <ItemPerformanceList
                    title="Top Selling Items"
                    description="Best performers by quantity and revenue."
                    items={topItems}
                    emptyMessage="No item sales in the selected period yet."
                  />

                  <ItemPerformanceList
                    title="Worst Items"
                    description="Lowest revenue items in the selected period."
                    items={worstItems}
                    emptyMessage="No completed sales to rank yet."
                  />

                  <div className="rounded-[26px] bg-[rgba(255,255,255,0.82)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                    <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Recent Orders</div>
                    <div className="mt-1 text-[0.85rem] text-[var(--muted)]">Tap an order to jump into checkout and detail view.</div>
                    <div className="mt-4 space-y-3">
                      {recentOrders.length ? (
                        recentOrders.map((order) => (
                          <button
                            key={order.order_id}
                            type="button"
                            onClick={() => navigateTo(`/frontdesk/order?orderId=${order.order_id}`)}
                            className="flex w-full items-center justify-between gap-4 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-3 text-left transition hover:bg-[rgba(97,0,0,0.05)]"
                          >
                            <div className="min-w-0">
                              <div className="truncate text-[0.96rem] font-bold text-[var(--on-surface)]">{order.label}</div>
                              <div className="mt-0.5 text-[0.78rem] text-[var(--muted)]">
                                {order.order_no} · {order.order_type === 'pickup' ? 'Takeout' : 'Dine-in'} · {order.occurred_at_label}
                              </div>
                            </div>
                            <div className="text-right">
                              <div className="text-[0.96rem] font-bold text-[var(--primary)]">{formatCurrency(order.total_amount)}</div>
                              <div className="mt-0.5 text-[0.78rem] text-[var(--muted)]">{order.status}</div>
                            </div>
                          </button>
                        ))
                      ) : (
                        <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.88rem] text-[var(--muted)]">
                          No recent orders in this scope yet.
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                <div className="rounded-[26px] bg-[rgba(255,255,255,0.82)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                  <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Sales by Store</div>
                  <div className="mt-1 text-[0.85rem] text-[var(--muted)]">Organization-wide comparison with trend indicators.</div>
                  <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                    {storeComparison.length ? (
                      storeComparison.map((store) => (
                        <div key={store.store_id} className="rounded-[20px] bg-[rgba(26,28,25,0.04)] px-4 py-4">
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <div className="text-[0.98rem] font-bold text-[var(--on-surface)]">{store.store_name}</div>
                              <div className="mt-1 text-[0.8rem] text-[var(--muted)]">{store.active_orders} active orders</div>
                            </div>
                            <div className={`shrink-0 rounded-full px-2.5 py-1 text-[0.76rem] font-semibold ${getChangeTone(store.change_pct)}`}>
                              {formatPercent(store.change_pct)}
                            </div>
                          </div>
                          <div className="mt-4 text-[1.2rem] font-black tracking-[-0.04em] text-[var(--primary)]">
                            {formatCurrency(store.sales)}
                          </div>
                          <div className="mt-1 text-[0.78rem] text-[var(--muted)]">
                            Previous: {formatCurrency(store.previous_sales)}
                          </div>
                        </div>
                      ))
                    ) : (
                      <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.88rem] text-[var(--muted)]">
                        No store comparison rows available for this scope yet.
                      </div>
                    )}
                  </div>
                </div>
              </>
            )}
          </div>
        </main>
      </div>
    </div>
  )
}
