import { useCurrentStore } from '../store/useStoreContext'
import { useLocale } from '../../i18n/useLocale'
import { ReportEmptyState } from './components/ReportEmptyState'
import { OwnerAdminReportsShell } from './components/OwnerAdminReportsShell'
import { ReportPanel } from './components/ReportPanel'
import { ReportsTopBar } from './components/ReportsTopBar'
import { ChannelNoodleSales, ChannelRevenueMix, ChannelSalesKpis, ChannelSalesTable, ChannelTrend } from './components/ChannelSalesPanels'
import { useAnalyticsReports } from './useAnalyticsReports'

export function SalesReportPage() {
  const { t } = useLocale()
  const report = useAnalyticsReports()
  const generic = useCurrentStore().presentation?.reporting_style === 'GENERIC'
  const summary = report.currentSummary?.channel_sales
  return <OwnerAdminReportsShell activeReport="sales" title={t('analytics.salesReport')} description={t('analytics.description')} topBar={
    <ReportsTopBar stores={report.stores} selectedStoreId={report.selectedStoreId} selectedRange={report.selectedRange} compareEnabled={report.compareEnabled}
      customStartDate={report.customStartDate} customEndDate={report.customEndDate} onStoreChange={report.setSelectedStoreId} onRangeChange={report.setSelectedRange}
      onCompareToggle={report.setCompareEnabled} onCustomStartDateChange={report.setCustomStartDate} onCustomEndDateChange={report.setCustomEndDate} />
  }>
    {report.error ? <ReportPanel title={t('analytics.unavailable')}><ReportEmptyState message={report.error} /></ReportPanel> : null}
    {report.loading || !summary ? <ReportEmptyState message={t('common.loading')} /> : <>
      <p className="text-sm text-[var(--muted)]">{t('analytics.basis')}</p>
      <ChannelSalesKpis summary={summary} />
      {report.compareEnabled && report.previousSummary?.channel_sales ? <ReportPanel title={t('analytics.previous')}><ChannelSalesTable label={t('analytics.period')} count="orders" showAov rows={[{ ...report.previousSummary.channel_sales.totals, item_key: 'previous', item_name: `${report.previousSummary.start_date} – ${report.previousSummary.end_date}` }]} /></ReportPanel> : null}
      <div className="grid gap-5 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
        <ReportPanel title={t('analytics.trend')} description={t('analytics.trendBasis')}><ChannelTrend points={summary.trend} /></ReportPanel>
        {!generic ? <ChannelRevenueMix summary={summary} range={report.selectedRange} /> : null}
      </div>
      {report.selectedRange === 'today' ? <ReportPanel title={t('analytics.hourly')} description={t('analytics.hourlyHint')}>
        <ChannelSalesTable label={t('analytics.hour')} count="orders" rows={summary.trend.map((row) => ({ ...row, item_key: row.label, item_name: row.label }))} />
      </ReportPanel> : null}
      {!generic ? <ChannelNoodleSales rows={summary.noodle_sales} range={report.selectedRange} /> : null}
      <ReportPanel title={t('analytics.daily')} description={t('analytics.dailyHint')}>
        <ChannelSalesTable label={t('analytics.date')} count="orders" showAov rows={summary.daily.map((row) => ({ ...row, item_key: row.date, item_name: row.date }))} />
      </ReportPanel>
    </>}
  </OwnerAdminReportsShell>
}
