import { expect, it } from 'vitest'
import type { SalesDailySummaryRecord, SalesHourlySummaryRecord } from '../../services/analyticsReportService'
import { buildSalesTrendPoints } from './reportUtils'

it('shows exactly trading-hour buckets without losing same-hour store totals', () => {
  const rows = [
    { hour_of_day: 9, sales_amount: 999 }, { hour_of_day: 18, sales_amount: 12 },
    { hour_of_day: 18, sales_amount: 15 }, { hour_of_day: 23, sales_amount: 999 },
  ] as SalesHourlySummaryRecord[]
  const points = buildSalesTrendPoints('today', [], rows)
  expect(points).toHaveLength(13)
  expect(points[0].label).toBe('10:00')
  expect(points[12].label).toBe('22:00')
  expect(points.find((row) => row.label === '18:00')?.value).toBe(27)
  expect(points.reduce((sum, row) => sum + row.value, 0)).toBe(27)
})

it('keeps daily week and month totals including orders outside trading hours', () => {
  const daily = [{ summary_date: '2026-10-01', net_sales: 100 }] as SalesDailySummaryRecord[]
  for (const range of ['week', 'month'] as const) {
    expect(buildSalesTrendPoints(range, daily, [], '2026-10-01', '2026-10-02'))
      .toEqual([{ label: '10-01', value: 100 }, { label: '10-02', value: 0 }])
  }
})
