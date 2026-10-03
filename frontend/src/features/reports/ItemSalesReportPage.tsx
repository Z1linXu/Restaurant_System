import { useState } from 'react'
import { ReportEmptyState } from './components/ReportEmptyState'
import { OwnerAdminReportsShell } from './components/OwnerAdminReportsShell'
import { ReportPanel } from './components/ReportPanel'
import { ReportsTopBar } from './components/ReportsTopBar'
import { ChannelSalesTable } from './components/ChannelSalesPanels'
import { useAnalyticsReports } from './useAnalyticsReports'

export function ItemSalesReportPage() {
  const report = useAnalyticsReports()
  const [sortMode, setSortMode] = useState<'revenue' | 'quantity'>('revenue')
  const rows = [...(report.currentSummary?.channel_sales?.items ?? [])].sort((left, right) => sortMode === 'quantity'
    ? right.total.quantity - left.total.quantity : right.total.known_revenue - left.total.known_revenue)
  return <OwnerAdminReportsShell activeReport="items" title="Item Sales Report" description="In-store and Uber root-item quantities with actual channel financial snapshots." topBar={
    <ReportsTopBar stores={report.stores} selectedStoreId={report.selectedStoreId} selectedRange={report.selectedRange} compareEnabled={report.compareEnabled}
      customStartDate={report.customStartDate} customEndDate={report.customEndDate} onStoreChange={report.setSelectedStoreId} onRangeChange={report.setSelectedRange}
      onCompareToggle={report.setCompareEnabled} onCustomStartDateChange={report.setCustomStartDate} onCustomEndDateChange={report.setCustomEndDate} />
  }>
    {report.error ? <ReportEmptyState message={report.error} /> : null}
    <div className="flex flex-wrap gap-2">{(['revenue', 'quantity'] as const).map((mode) => <button key={mode} type="button" onClick={() => setSortMode(mode)} aria-pressed={sortMode === mode} className={`rounded-full px-4 py-2 text-sm ${sortMode === mode ? 'bg-[var(--primary)] text-white' : 'bg-[rgba(26,28,25,0.06)]'}`}>Sort by {mode === 'revenue' ? 'Known Revenue' : 'Quantity'}</button>)}</div>
    <div className="grid gap-5 xl:grid-cols-2">
      <ReportPanel title="Top Items" description="Top ten by the selected quantity or known-revenue ranking.">
        {report.loading ? <ReportEmptyState message="Loading item sales..." /> : rows.length ? <ChannelSalesTable rows={rows.slice(0, 10)} label="Item" /> : <ReportEmptyState message="No eligible item sales for this period." />}
      </ReportPanel>
      <ReportPanel title="Worst Items" description="Lowest known total revenue; pending amounts are not ranked as zero.">
        {report.loading ? <ReportEmptyState message="Loading item sales..." /> : <ChannelSalesTable rows={rows.filter((row) => row.total.revenue != null).sort((a, b) => (a.total.revenue ?? 0) - (b.total.revenue ?? 0)).slice(0, 10)} label="Item" />}
      </ReportPanel>
    </div>
    <ReportPanel title="All item sales by channel" description="Unknown Uber amounts remain pending; local menu prices are never substituted.">
      {report.loading ? <ReportEmptyState message="Loading item sales..." /> : <ChannelSalesTable rows={rows} label="Item" />}
    </ReportPanel>
  </OwnerAdminReportsShell>
}
