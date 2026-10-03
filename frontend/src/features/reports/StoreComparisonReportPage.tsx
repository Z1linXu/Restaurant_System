import { ReportEmptyState } from './components/ReportEmptyState'
import { OwnerAdminReportsShell } from './components/OwnerAdminReportsShell'
import { ReportPanel } from './components/ReportPanel'
import { ReportsTopBar } from './components/ReportsTopBar'
import { ChannelSalesKpis, ChannelSalesTable } from './components/ChannelSalesPanels'
import { useAnalyticsReports } from './useAnalyticsReports'

export function StoreComparisonReportPage() {
  const report = useAnalyticsReports()
  const summary = report.currentSummary?.channel_sales
  const names = new Map(report.stores.map((store) => [Number(store.id), store.label]))
  return <OwnerAdminReportsShell activeReport="stores" title="Store Comparison Report" description="Actual Store sales split between In-store and Uber Eats." topBar={
    <ReportsTopBar stores={report.stores} selectedStoreId={report.selectedStoreId} selectedRange={report.selectedRange} compareEnabled={report.compareEnabled}
      customStartDate={report.customStartDate} customEndDate={report.customEndDate} onStoreChange={report.setSelectedStoreId} onRangeChange={report.setSelectedRange}
      onCompareToggle={report.setCompareEnabled} onCustomStartDateChange={report.setCustomStartDate} onCustomEndDateChange={report.setCustomEndDate} />
  }>
    {report.error ? <ReportEmptyState message={report.error} /> : null}
    {report.loading || !summary ? <ReportEmptyState message="Loading Store channel sales..." /> : <>
      <ChannelSalesKpis summary={summary} />
      <ReportPanel title="Store sales by channel"><ChannelSalesTable label="Store" count="orders" showAov rows={summary.stores.map((row) => ({ ...row, item_key: String(row.store_id), item_name: names.get(row.store_id) ?? `Store ${row.store_id}` }))} /></ReportPanel>
      {report.compareEnabled && report.previousSummary?.channel_sales ? <ReportPanel title="Previous period"><ChannelSalesTable label="Store" count="orders" showAov rows={report.previousSummary.channel_sales.stores.map((row) => ({ ...row, item_key: String(row.store_id), item_name: names.get(row.store_id) ?? `Store ${row.store_id}` }))} /></ReportPanel> : null}
    </>}
  </OwnerAdminReportsShell>
}
