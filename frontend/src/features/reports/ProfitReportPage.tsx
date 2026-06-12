import { useMemo } from 'react'
import { ReportEmptyState } from './components/ReportEmptyState'
import { ReportMetricCard } from './components/ReportMetricCard'
import { OwnerAdminReportsShell } from './components/OwnerAdminReportsShell'
import { ReportPanel } from './components/ReportPanel'
import { ReportTrendChart } from './components/ReportTrendChart'
import { ReportsTopBar } from './components/ReportsTopBar'
import { aggregateDailyTotals, aggregateItemSales, fillMissingDailySummaries, formatCurrency, formatPercent } from './reportUtils'
import { useAnalyticsReports } from './useAnalyticsReports'

export function ProfitReportPage() {
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

  const displayDailyRows = fillMissingDailySummaries(
    currentSummary?.sales_daily_summaries ?? [],
    currentSummary?.start_date,
    currentSummary?.end_date,
  )
  const totals = aggregateDailyTotals(displayDailyRows)
  const itemRows = useMemo(() => aggregateItemSales(currentSummary?.menu_item_sales_summaries ?? []), [currentSummary?.menu_item_sales_summaries])

  const topProfitableItems = useMemo(
    () => [...itemRows].sort((left, right) => right.totalProfit - left.totalProfit).slice(0, 10),
    [itemRows],
  )
  const worstMarginItems = useMemo(
    () =>
      [...itemRows]
        .filter((item) => item.revenue > 0)
        .sort((left, right) => left.marginPct - right.marginPct || left.totalProfit - right.totalProfit)
        .slice(0, 10),
    [itemRows],
  )
  const profitTrendPoints = displayDailyRows.map((row) => ({
    label: row.summary_date.slice(5),
    value: Number(row.total_profit ?? 0),
  }))
  const visibleDailyRows = displayDailyRows.filter((row) => Number(row.net_sales ?? 0) > 0 || Number(row.order_count ?? 0) > 0)

  return (
    <OwnerAdminReportsShell
      activeReport="profit"
      title="Profit Report"
      description="Profitability reporting built from sales_daily_summary and menu_item_sales_summary."
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
        <ReportMetricCard label="Total Sales" value={formatCurrency(totals.totalSales)} compareValue={0} />
        <ReportMetricCard label="Total Cost" value={formatCurrency(totals.totalCost)} compareValue={0} />
        <ReportMetricCard label="Total Profit" value={formatCurrency(totals.totalProfit)} compareValue={0} />
        <ReportMetricCard label="Profit Margin" value={formatPercent(totals.profitMargin)} compareValue={0} />
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1.5fr)_minmax(420px,1fr)]">
        <ReportPanel
          title="Daily Profit Trend"
          description="Daily profit across the selected analytics summary range."
        >
          {loading ? (
            <ReportEmptyState message="Loading profit trend..." />
          ) : (
            <ReportTrendChart
              points={profitTrendPoints}
              caption="Daily profit from sales_daily_summary."
              compactXAxis={false}
              dailyXAxisStep={selectedRange === 'week' ? 1 : 3}
            />
          )}
        </ReportPanel>

        <ReportPanel title="Profit by Day" description="Days with recorded sales and profit.">
          {loading ? (
            <ReportEmptyState message="Loading daily profit rows..." />
          ) : visibleDailyRows.length ? (
            <div className="overflow-auto rounded-[18px] border border-[rgba(26,28,25,0.06)]">
              <table className="min-w-full text-left text-[0.9rem]">
                <thead className="bg-[rgba(246,243,236,0.94)] text-[0.76rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                  <tr>
                    <th className="px-4 py-3">Date</th>
                    <th className="px-4 py-3">Sales</th>
                    <th className="px-4 py-3">Cost</th>
                    <th className="px-4 py-3">Profit</th>
                    <th className="px-4 py-3">Margin</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleDailyRows.map((row) => (
                    <tr key={row.id} className="border-t border-[rgba(26,28,25,0.06)]">
                      <td className="px-4 py-3 font-semibold text-[var(--on-surface)]">{row.summary_date}</td>
                      <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(Number(row.net_sales ?? 0))}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{formatCurrency(Number(row.total_cost ?? 0))}</td>
                      <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(Number(row.total_profit ?? 0))}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{formatPercent(Number(row.profit_margin ?? 0))}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <ReportEmptyState message="No profit data for this period." />
          )}
        </ReportPanel>
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <ReportPanel title="Top Profitable Items" description="Highest total profit by item in the selected period.">
          {loading ? (
            <ReportEmptyState message="Loading profitable item summaries..." />
          ) : topProfitableItems.length ? (
            <div className="overflow-auto rounded-[18px] border border-[rgba(26,28,25,0.06)]">
              <table className="min-w-full text-left text-[0.9rem]">
                <thead className="bg-[rgba(246,243,236,0.94)] text-[0.76rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                  <tr>
                    <th className="px-4 py-3">Item</th>
                    <th className="px-4 py-3">Qty</th>
                    <th className="px-4 py-3">Revenue</th>
                    <th className="px-4 py-3">Cost</th>
                    <th className="px-4 py-3">Profit</th>
                  </tr>
                </thead>
                <tbody>
                  {topProfitableItems.map((item) => (
                    <tr key={`profit-top-${item.key}`} className="border-t border-[rgba(26,28,25,0.06)]">
                      <td className="px-4 py-3 font-semibold text-[var(--on-surface)]">{item.itemName}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{item.quantity}</td>
                      <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(item.revenue)}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{formatCurrency(item.totalCost)}</td>
                      <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(item.totalProfit)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <ReportEmptyState message="No item profit summary data is available for this period." />
          )}
        </ReportPanel>

        <ReportPanel title="Worst Margin Items" description="Lowest margin items ranked by profit percentage.">
          {loading ? (
            <ReportEmptyState message="Loading margin analytics..." />
          ) : worstMarginItems.length ? (
            <div className="overflow-auto rounded-[18px] border border-[rgba(26,28,25,0.06)]">
              <table className="min-w-full text-left text-[0.9rem]">
                <thead className="bg-[rgba(246,243,236,0.94)] text-[0.76rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                  <tr>
                    <th className="px-4 py-3">Item</th>
                    <th className="px-4 py-3">Revenue</th>
                    <th className="px-4 py-3">Profit</th>
                    <th className="px-4 py-3">Margin</th>
                  </tr>
                </thead>
                <tbody>
                  {worstMarginItems.map((item) => (
                    <tr key={`margin-worst-${item.key}`} className="border-t border-[rgba(26,28,25,0.06)]">
                      <td className="px-4 py-3 font-semibold text-[var(--on-surface)]">{item.itemName}</td>
                      <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(item.revenue)}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{formatCurrency(item.totalProfit)}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{formatPercent(item.marginPct)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <ReportEmptyState message="No margin summary data is available for this period." />
          )}
        </ReportPanel>
      </div>
    </OwnerAdminReportsShell>
  )
}
