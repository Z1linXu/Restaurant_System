import { ReportEmptyState } from './components/ReportEmptyState'
import { OwnerAdminReportsShell } from './components/OwnerAdminReportsShell'
import { ReportPanel } from './components/ReportPanel'
import { ReportsTopBar } from './components/ReportsTopBar'
import { ChannelNoodleSales, ChannelRevenueMix, ChannelSalesKpis, ChannelSalesTable, ChannelTrend } from './components/ChannelSalesPanels'
import { useAnalyticsReports } from './useAnalyticsReports'

export function SalesReportPage() {
  const report = useAnalyticsReports()
  const summary = report.currentSummary?.channel_sales
  return <OwnerAdminReportsShell activeReport="sales" title="Sales Report" description="Actual merchandise sales by channel, including tax and excluding platform fees and tips." topBar={
    <ReportsTopBar stores={report.stores} selectedStoreId={report.selectedStoreId} selectedRange={report.selectedRange} compareEnabled={report.compareEnabled}
      customStartDate={report.customStartDate} customEndDate={report.customEndDate} onStoreChange={report.setSelectedStoreId} onRangeChange={report.setSelectedRange}
      onCompareToggle={report.setCompareEnabled} onCustomStartDateChange={report.setCustomStartDate} onCustomEndDateChange={report.setCustomEndDate} />
  }>
    {report.error ? <ReportPanel title="Reports unavailable"><ReportEmptyState message={report.error} /></ReportPanel> : null}
    {report.loading || !summary ? <ReportEmptyState message="Loading channel sales..." /> : <>
      <ChannelSalesKpis summary={summary} />
      {report.compareEnabled && report.previousSummary?.channel_sales ? <ReportPanel title="Previous period"><ChannelSalesTable label="Period" count="orders" showAov rows={[{ ...report.previousSummary.channel_sales.totals, item_key: 'previous', item_name: `${report.previousSummary.start_date} – ${report.previousSummary.end_date}` }]} /></ReportPanel> : null}
      <div className="grid gap-5 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
        <ReportPanel title="Sales Trend" description={report.selectedRange === 'today' ? 'Hourly 10:00–22:00 · placed/submitted time' : 'Daily sales · placed/submitted time'}><ChannelTrend points={summary.trend} /></ReportPanel>
        <ChannelRevenueMix summary={summary} range={report.selectedRange} />
      </div>
      {report.selectedRange === 'today' ? <ReportPanel title="Hourly Breakdown" description="Trading hours 10:00–22:00; complete daily totals retain sales outside this range.">
        <ChannelSalesTable label="Hour" count="orders" rows={summary.trend.map((row) => ({ ...row, item_key: row.label, item_name: row.label }))} />
      </ReportPanel> : null}
      <ChannelNoodleSales rows={summary.noodle_sales} range={report.selectedRange} />
      <ReportPanel title="Daily Summary Table" description="Actual channel quantities and sales for the selected dates.">
        <ChannelSalesTable label="Date" count="orders" showAov rows={summary.daily.map((row) => ({ ...row, item_key: row.date, item_name: row.date }))} />
      </ReportPanel>
    </>}
  </OwnerAdminReportsShell>
}
