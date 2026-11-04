import { useLocale } from '../../i18n/useLocale'
import { useState } from 'react'
import { ReportEmptyState } from './components/ReportEmptyState'
import { OwnerAdminReportsShell } from './components/OwnerAdminReportsShell'
import { ReportPanel } from './components/ReportPanel'
import { ReportsTopBar } from './components/ReportsTopBar'
import { ChannelSalesTable } from './components/ChannelSalesPanels'
import { useAnalyticsReports } from './useAnalyticsReports'

export function ItemSalesReportPage() {
  const { t } = useLocale()
  const report = useAnalyticsReports()
  const [sortMode, setSortMode] = useState<'revenue' | 'quantity'>('revenue')
  const rows = [...(report.currentSummary?.channel_sales?.items ?? [])].sort((left, right) => sortMode === 'quantity'
    ? right.total.quantity - left.total.quantity : right.total.known_revenue - left.total.known_revenue)
  return <OwnerAdminReportsShell activeReport="items" title={t('analytics.itemReport')} description={t('analytics.itemDescription')} topBar={
    <ReportsTopBar stores={report.stores} selectedStoreId={report.selectedStoreId} selectedRange={report.selectedRange} compareEnabled={report.compareEnabled}
      customStartDate={report.customStartDate} customEndDate={report.customEndDate} onStoreChange={report.setSelectedStoreId} onRangeChange={report.setSelectedRange}
      onCompareToggle={report.setCompareEnabled} onCustomStartDateChange={report.setCustomStartDate} onCustomEndDateChange={report.setCustomEndDate} />
  }>
    {report.error ? <ReportEmptyState message={report.error} /> : null}
    <div className="flex flex-wrap gap-2">{(['revenue', 'quantity'] as const).map((mode) => <button key={mode} type="button" onClick={() => setSortMode(mode)} aria-pressed={sortMode === mode} className={`rounded-full px-4 py-2 text-sm ${sortMode === mode ? 'bg-[var(--primary)] text-white' : 'bg-[rgba(26,28,25,0.06)]'}`}>{t(mode === 'revenue' ? 'analytics.sortRevenue' : 'analytics.sortQuantity')}</button>)}</div>
    <div className="grid gap-5 xl:grid-cols-2">
      <ReportPanel title={t('analytics.topItems')} description={t('analytics.topHint')}>
        {report.loading ? <ReportEmptyState message={t('common.loading')} /> : rows.length ? <ChannelSalesTable rows={rows.slice(0, 10)} label={t('analytics.item')} /> : <ReportEmptyState message={t('analytics.noSales')} />}
      </ReportPanel>
      <ReportPanel title={t('analytics.worstItems')} description={t('analytics.worstHint')}>
        {report.loading ? <ReportEmptyState message={t('common.loading')} /> : <ChannelSalesTable rows={rows.filter((row) => row.total.revenue != null).sort((a, b) => (a.total.revenue ?? 0) - (b.total.revenue ?? 0)).slice(0, 10)} label={t('analytics.item')} />}
      </ReportPanel>
    </div>
    <ReportPanel title={t('analytics.allItems')} description={t('analytics.unknownHint')}>
      {report.loading ? <ReportEmptyState message={t('common.loading')} /> : <ChannelSalesTable rows={rows} label={t('analytics.item')} />}
    </ReportPanel>
  </OwnerAdminReportsShell>
}
