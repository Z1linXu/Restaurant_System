import { useMemo } from 'react'
import { ReportEmptyState } from './components/ReportEmptyState'
import { ReportMetricCard } from './components/ReportMetricCard'
import { OwnerAdminReportsShell } from './components/OwnerAdminReportsShell'
import { ReportPanel } from './components/ReportPanel'
import { ReportsTopBar } from './components/ReportsTopBar'
import { aggregateStorePerformance, formatCurrency, percentageChange } from './reportUtils'
import { useAnalyticsReports } from './useAnalyticsReports'

export function StoreComparisonReportPage() {
  const {
    stores,
    selectedStoreId,
    setSelectedStoreId,
    selectedRange,
    setSelectedRange,
    compareEnabled,
    setCompareEnabled,
    customStartDate,
    setCustomStartDate,
    customEndDate,
    setCustomEndDate,
    currentSummary,
    previousSummary,
    loading,
    error,
  } = useAnalyticsReports()

  const storeNameMap = useMemo(
    () =>
      new Map(
        stores
          .filter((store) => store.id !== 'ALL')
          .map((store) => [Number(store.id), store.label]),
      ),
    [stores],
  )

  const currentStores = useMemo(
    () => aggregateStorePerformance(currentSummary?.store_performance_summaries ?? [], storeNameMap),
    [currentSummary?.store_performance_summaries, storeNameMap],
  )
  const previousStores = useMemo(
    () => aggregateStorePerformance(previousSummary?.store_performance_summaries ?? [], storeNameMap),
    [previousSummary?.store_performance_summaries, storeNameMap],
  )

  const previousByStore = useMemo(() => new Map(previousStores.map((store) => [store.storeId, store])), [previousStores])

  const rankedStores = useMemo(
    () =>
      currentStores
        .map((store) => {
          const previous = previousByStore.get(store.storeId)
          return {
            ...store,
            changePct: compareEnabled ? percentageChange(store.sales, previous?.sales ?? 0) : 0,
          }
        })
        .sort((left, right) => right.sales - left.sales),
    [compareEnabled, currentStores, previousByStore],
  )

  const totals = rankedStores.reduce(
    (accumulator, store) => {
      accumulator.sales += store.sales
      accumulator.orders += store.orderCount
      accumulator.active += store.activeOrders
      return accumulator
    },
    { sales: 0, orders: 0, active: 0 },
  )

  const previousTotals = previousStores.reduce(
    (accumulator, store) => {
      accumulator.sales += store.sales
      accumulator.orders += store.orderCount
      accumulator.active += store.activeOrders
      return accumulator
    },
    { sales: 0, orders: 0, active: 0 },
  )

  return (
    <OwnerAdminReportsShell
      activeReport="stores"
      title="Store Comparison Report"
      description="Cross-store performance built on store_performance_summary."
      topBar={
        <ReportsTopBar
          stores={stores}
          selectedStoreId={selectedStoreId}
          selectedRange={selectedRange}
          compareEnabled={compareEnabled}
          customStartDate={customStartDate}
          customEndDate={customEndDate}
          onStoreChange={setSelectedStoreId}
          onRangeChange={setSelectedRange}
          onCompareToggle={setCompareEnabled}
          onCustomStartDateChange={setCustomStartDate}
          onCustomEndDateChange={setCustomEndDate}
        />
      }
    >
      {error ? (
        <ReportPanel title="Reports unavailable">
          <ReportEmptyState message={error} />
        </ReportPanel>
      ) : null}

      <div className="grid gap-4 xl:grid-cols-4">
        <ReportMetricCard
          label="Store Sales"
          value={formatCurrency(totals.sales)}
          compareValue={compareEnabled ? percentageChange(totals.sales, previousTotals.sales) : 0}
        />
        <ReportMetricCard
          label="Store Orders"
          value={String(totals.orders)}
          compareValue={compareEnabled ? percentageChange(totals.orders, previousTotals.orders) : 0}
        />
        <ReportMetricCard
          label="Average Order Value"
          value={formatCurrency(totals.orders > 0 ? totals.sales / totals.orders : 0)}
          compareValue={
            compareEnabled
              ? percentageChange(
                  totals.orders > 0 ? totals.sales / totals.orders : 0,
                  previousTotals.orders > 0 ? previousTotals.sales / previousTotals.orders : 0,
                )
              : 0
          }
        />
        <ReportMetricCard
          label="Active Orders"
          value={String(totals.active)}
          compareValue={compareEnabled ? percentageChange(totals.active, previousTotals.active) : 0}
        />
      </div>

      <ReportPanel title="Store Ranking" description="Top-performing stores ranked by sales for the selected period.">
        {loading ? (
          <ReportEmptyState message="Loading store performance summaries..." />
        ) : rankedStores.length ? (
          <div className="overflow-auto rounded-[18px] border border-[rgba(26,28,25,0.06)]">
            <table className="min-w-full text-left text-[0.9rem]">
              <thead className="bg-[rgba(246,243,236,0.94)] text-[0.76rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                <tr>
                  <th className="px-4 py-3">Rank</th>
                  <th className="px-4 py-3">Store</th>
                  <th className="px-4 py-3">Sales</th>
                  <th className="px-4 py-3">Orders</th>
                  <th className="px-4 py-3">AOV</th>
                  <th className="px-4 py-3">Active Orders</th>
                  <th className="px-4 py-3">Trend</th>
                </tr>
              </thead>
              <tbody>
                {rankedStores.map((store, index) => (
                  <tr key={store.storeId} className="border-t border-[rgba(26,28,25,0.06)]">
                    <td className="px-4 py-3 font-semibold text-[var(--on-surface)]">#{index + 1}</td>
                    <td className="px-4 py-3 font-semibold text-[var(--on-surface)]">{store.storeName}</td>
                    <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(store.sales)}</td>
                    <td className="px-4 py-3 text-[var(--muted)]">{store.orderCount}</td>
                    <td className="px-4 py-3 text-[var(--muted)]">{formatCurrency(store.averageOrderValue)}</td>
                    <td className="px-4 py-3 text-[var(--muted)]">{store.activeOrders}</td>
                    <td className={`px-4 py-3 font-semibold ${store.changePct >= 0 ? 'text-emerald-700' : 'text-[var(--primary)]'}`}>
                      {store.changePct >= 0 ? '+' : ''}
                      {store.changePct.toFixed(1)}%
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <ReportEmptyState message="No store performance summary data is available for this period." />
        )}
      </ReportPanel>
    </OwnerAdminReportsShell>
  )
}
