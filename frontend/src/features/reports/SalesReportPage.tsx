import { useEffect } from 'react'
import { ReportEmptyState } from './components/ReportEmptyState'
import { ReportMetricCard } from './components/ReportMetricCard'
import { OwnerAdminReportsShell } from './components/OwnerAdminReportsShell'
import { ReportPanel } from './components/ReportPanel'
import { ReportTrendChart } from './components/ReportTrendChart'
import { ReportsTopBar } from './components/ReportsTopBar'
import { aggregateDailyTotals, buildSalesTrendPoints, fillMissingDailySummaries, formatCurrency, percentageChange } from './reportUtils'
import { useAnalyticsReports } from './useAnalyticsReports'

export function SalesReportPage() {
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

  const displayDailyRows = fillMissingDailySummaries(
    currentSummary?.sales_daily_summaries ?? [],
    currentSummary?.start_date,
    currentSummary?.end_date,
  )
  const previousDisplayDailyRows = fillMissingDailySummaries(
    previousSummary?.sales_daily_summaries ?? [],
    previousSummary?.start_date,
    previousSummary?.end_date,
  )

  const currentTotals = aggregateDailyTotals(displayDailyRows)
  const previousTotals = aggregateDailyTotals(previousDisplayDailyRows)
  const salesChange = compareEnabled ? percentageChange(currentTotals.totalSales, previousTotals.totalSales) : 0
  const orderChange = compareEnabled ? percentageChange(currentTotals.completedOrders, previousTotals.completedOrders) : 0
  const aovChange = compareEnabled ? percentageChange(currentTotals.averageOrderValue, previousTotals.averageOrderValue) : 0

  const trendPoints = buildSalesTrendPoints(
    selectedRange,
    displayDailyRows,
    currentSummary?.sales_hourly_summaries ?? [],
    currentSummary?.start_date,
    currentSummary?.end_date,
  )
  const visibleDailyRows = displayDailyRows.filter((row) => {
    const sales = Number(row.net_sales ?? 0)
    const orders = Number(row.completed_order_count ?? row.order_count ?? 0)
    return sales > 0 || orders > 0
  })

  useEffect(() => {
    if (!currentSummary) {
      return
    }
    console.log('[SalesReport]', {
      selectedRange,
      startDate: currentSummary.start_date,
      endDate: currentSummary.end_date,
      dailySummariesLength: displayDailyRows.length,
      totalSales: currentTotals.totalSales,
      orderCount: currentTotals.completedOrders,
    })
  }, [
    currentSummary,
    currentTotals.completedOrders,
    currentTotals.totalSales,
    displayDailyRows.length,
    selectedRange,
  ])

  return (
    <OwnerAdminReportsShell
      activeReport="sales"
      title="Sales Report"
      description="Daily revenue, order volume, and trend reporting driven by analytics summary tables."
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
        <ReportMetricCard label="Total Sales" value={formatCurrency(currentTotals.totalSales)} compareValue={salesChange} />
        <ReportMetricCard label="Order Count" value={String(currentTotals.completedOrders)} compareValue={orderChange} />
        <ReportMetricCard label="Average Order Value" value={formatCurrency(currentTotals.averageOrderValue)} compareValue={aovChange} />
        <ReportMetricCard
          label="Sales Change"
          value={compareEnabled ? `${salesChange >= 0 ? '+' : ''}${salesChange.toFixed(1)}%` : '—'}
          compareValue={salesChange}
        />
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1.8fr)_minmax(360px,1fr)]">
        <ReportPanel
          title="Sales Trend"
      description={selectedRange === 'today' ? 'Hourly sales for the current day.' : 'Daily sales trend across the selected period.'}
        >
          {loading ? (
            <ReportEmptyState message="Loading summary trend..." />
          ) : (
            <ReportTrendChart
              points={trendPoints}
              caption={selectedRange === 'today' ? 'Hourly sales and order counts from sales_hourly_summary.' : 'Daily sales from sales_daily_summary.'}
              compactXAxis={selectedRange === 'today'}
              dailyXAxisStep={selectedRange === 'week' ? 1 : 3}
            />
          )}
        </ReportPanel>

        <ReportPanel
          title="Hourly Breakdown"
          description="Sales and order count by hour."
        >
          {selectedRange !== 'today' ? (
            <ReportEmptyState message="Hourly breakdown is available for Today only." />
          ) : loading ? (
            <ReportEmptyState message="Loading hourly summary..." />
          ) : currentSummary?.sales_hourly_summaries?.length ? (
            <div className="max-h-[460px] overflow-auto rounded-[18px] border border-[rgba(26,28,25,0.06)]">
              <table className="min-w-full text-left text-[0.88rem]">
                <thead className="sticky top-0 bg-[rgba(246,243,236,0.94)] text-[0.76rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                  <tr>
                    <th className="px-4 py-3">Hour</th>
                    <th className="px-4 py-3">Sales</th>
                    <th className="px-4 py-3">Orders</th>
                  </tr>
                </thead>
                <tbody>
                  {currentSummary.sales_hourly_summaries.map((row) => (
                    <tr key={`${row.summary_date}-${row.hour_of_day}`} className="border-t border-[rgba(26,28,25,0.06)]">
                      <td className="px-4 py-3 font-semibold text-[var(--on-surface)]">{String(row.hour_of_day).padStart(2, '0')}:00</td>
                      <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(Number(row.sales_amount ?? 0))}</td>
                      <td className="px-4 py-3 text-[var(--muted)]">{Number(row.order_count ?? 0)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <ReportEmptyState message="No hourly analytics summary data for this day." />
          )}
        </ReportPanel>
      </div>

      <ReportPanel title="Daily Summary Table" description="Aggregated daily sales, order count, and average order value.">
        {loading ? (
          <ReportEmptyState message="Loading daily summary..." />
        ) : visibleDailyRows.length ? (
          <div className="overflow-auto rounded-[18px] border border-[rgba(26,28,25,0.06)]">
            <table className="min-w-full text-left text-[0.9rem]">
              <thead className="bg-[rgba(246,243,236,0.94)] text-[0.76rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                <tr>
                  <th className="px-4 py-3">Date</th>
                  <th className="px-4 py-3">Sales</th>
                  <th className="px-4 py-3">Orders</th>
                  <th className="px-4 py-3">AOV</th>
                </tr>
              </thead>
              <tbody>
                {visibleDailyRows.map((row) => (
                  <tr key={row.id} className="border-t border-[rgba(26,28,25,0.06)]">
                    <td className="px-4 py-3 font-semibold text-[var(--on-surface)]">{row.summary_date}</td>
                    <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(Number(row.net_sales ?? 0))}</td>
                    <td className="px-4 py-3 text-[var(--muted)]">{Number(row.completed_order_count ?? row.order_count ?? 0)}</td>
                    <td className="px-4 py-3 text-[var(--muted)]">{formatCurrency(Number(row.average_order_value ?? 0))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <ReportEmptyState message="No sales data for this period." />
        )}
      </ReportPanel>
    </OwnerAdminReportsShell>
  )
}
