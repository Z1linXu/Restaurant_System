import { useMemo, useState } from 'react'
import { ReportEmptyState } from './components/ReportEmptyState'
import { OwnerAdminReportsShell } from './components/OwnerAdminReportsShell'
import { ReportPanel } from './components/ReportPanel'
import { ReportsTopBar } from './components/ReportsTopBar'
import { aggregateItemSales, formatCurrency } from './reportUtils'
import { useAnalyticsReports } from './useAnalyticsReports'

type SortMode = 'revenue' | 'quantity'

export function ItemSalesReportPage() {
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
    loading,
    error,
  } = useAnalyticsReports()
  const [sortMode, setSortMode] = useState<SortMode>('revenue')

  const aggregatedItems = useMemo(() => {
    const rows = aggregateItemSales(currentSummary?.menu_item_sales_summaries ?? [])
    const sorted = [...rows].sort((left, right) =>
      sortMode === 'revenue'
        ? right.revenue - left.revenue
        : right.quantity - left.quantity,
    )
    return sorted
  }, [currentSummary?.menu_item_sales_summaries, sortMode])

  const topItems = aggregatedItems.slice(0, 10)
  const worstItems = [...aggregatedItems]
    .filter((item) => item.revenue >= 0)
    .sort((left, right) => left.revenue - right.revenue)
    .slice(0, 10)

  return (
    <OwnerAdminReportsShell
      activeReport="items"
      title="Item Sales Report"
      description="Menu item performance aggregated from menu_item_sales_summary."
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

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => setSortMode('revenue')}
          className={`rounded-full px-4 py-2 text-[0.84rem] font-semibold ${
            sortMode === 'revenue' ? 'bg-[var(--primary)] text-white' : 'bg-[rgba(26,28,25,0.05)] text-[var(--on-surface)]'
          }`}
        >
          Sort by Revenue
        </button>
        <button
          type="button"
          onClick={() => setSortMode('quantity')}
          className={`rounded-full px-4 py-2 text-[0.84rem] font-semibold ${
            sortMode === 'quantity' ? 'bg-[var(--primary)] text-white' : 'bg-[rgba(26,28,25,0.05)] text-[var(--on-surface)]'
          }`}
        >
          Sort by Quantity
        </button>
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <ReportPanel title="Top Items" description="Highest-performing items for the selected period.">
          {loading ? (
            <ReportEmptyState message="Loading item sales summaries..." />
          ) : topItems.length ? (
            <div className="overflow-auto rounded-[18px] border border-[rgba(26,28,25,0.06)]">
              <table className="min-w-full text-left text-[0.9rem]">
                <thead className="bg-[rgba(246,243,236,0.94)] text-[0.76rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                  <tr>
                    <th className="px-4 py-3">Item</th>
                    <th className="px-4 py-3">Qty Sold</th>
                    <th className="px-4 py-3">Revenue</th>
                    <th className="px-4 py-3">Orders</th>
                  </tr>
                </thead>
                <tbody>
                  {topItems.map((item) => (
                    <tr key={`top-${item.key}`} className="border-t border-[rgba(26,28,25,0.06)]">
                      <td className="px-4 py-3 font-semibold text-[var(--on-surface)]">{item.itemName}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{item.quantity}</td>
                      <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(item.revenue)}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{item.orderCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <ReportEmptyState message="No item sales summary data is available for this period." />
          )}
        </ReportPanel>

        <ReportPanel title="Worst Items" description="Lowest-performing items by revenue.">
          {loading ? (
            <ReportEmptyState message="Loading item sales summaries..." />
          ) : worstItems.length ? (
            <div className="overflow-auto rounded-[18px] border border-[rgba(26,28,25,0.06)]">
              <table className="min-w-full text-left text-[0.9rem]">
                <thead className="bg-[rgba(246,243,236,0.94)] text-[0.76rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                  <tr>
                    <th className="px-4 py-3">Item</th>
                    <th className="px-4 py-3">Qty Sold</th>
                    <th className="px-4 py-3">Revenue</th>
                    <th className="px-4 py-3">Orders</th>
                  </tr>
                </thead>
                <tbody>
                  {worstItems.map((item) => (
                    <tr key={`worst-${item.key}`} className="border-t border-[rgba(26,28,25,0.06)]">
                      <td className="px-4 py-3 font-semibold text-[var(--on-surface)]">{item.itemName}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{item.quantity}</td>
                      <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(item.revenue)}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{item.orderCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <ReportEmptyState message="No item sales summary data is available for this period." />
          )}
        </ReportPanel>
      </div>
    </OwnerAdminReportsShell>
  )
}
