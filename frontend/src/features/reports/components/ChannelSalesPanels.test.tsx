import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, expect, it, vi } from 'vitest'
import type { ChannelMetric, ChannelSalesSummary, ChannelSplit } from '../../../services/channelSales'
import { ChannelNoodleSales, ChannelRevenueMix, ChannelSalesKpis, ChannelSalesTable, ChannelTrend } from './ChannelSalesPanels'

vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
let view: ReactTestRenderer | undefined
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })
function metric(revenue: number | null, quantity: number): ChannelMetric {
  return { revenue, known_revenue: revenue ?? 0, unknown_amount_count: revenue == null ? 1 : 0, order_count: quantity ? 1 : 0, quantity, average_order_value: revenue, percentage: revenue == null ? null : 100 }
}
function split(): ChannelSplit { return { in_store: metric(10, 1), uber_eats: metric(19.99, 2), total: metric(29.99, 3) } }
function summary(): ChannelSalesSummary {
  return { revenue_basis: 'MERCHANDISE_INCLUDING_TAX_EXCLUDING_PLATFORM_FEES_TIPS', currency: 'CAD', revenue_note: '', totals: split(),
    categories: [{ ...split(), reporting_group: 'SOUP_NOODLE' }], noodle_sales: [{ ...split(), reporting_group: 'SOUP_NOODLE' }], items: [],
    trend: [{ ...split(), label: '18:00', date: '2026-10-03', hour: 18 }], daily: [], stores: [] }
}
function text() { return JSON.stringify(view!.toJSON()) }

it('defaults donut to Total and switches to actual In-store and Uber amounts', async () => {
  await act(async () => { view = create(<ChannelRevenueMix summary={summary()} range="today" />) })
  expect(text()).toContain('Total Sales')
  expect(text()).toContain('$29.99')
  const button = (label: string) => view!.root.findAllByType('button').find((node) => node.children.join('') === label)!
  await act(async () => button('Uber Eats').props.onClick())
  expect(text()).toContain('Uber Eats Sales')
  expect(text()).toContain('$19.99')
  expect(text()).not.toContain('$29.99')
  await act(async () => button('In-store').props.onClick())
  expect(text()).toContain('In-store Sales')
  expect(text()).toContain('$10.00')
})

it('shows all quantity and revenue channels in noodle list and KPIs', async () => {
  const data = summary()
  await act(async () => { view = create(<><ChannelSalesKpis summary={data} /><ChannelNoodleSales rows={data.noodle_sales} range="today" /></>) })
  expect(text()).toContain('Total Sales')
  expect(text()).toContain('In-store Sales')
  expect(text()).toContain('Uber Eats Sales')
  expect(text()).toContain('Total Orders')
  expect(text()).toContain('汤面')
  expect(text()).toContain('$29.99')
  expect(text()).toContain('$19.99')
  expect(text()).toContain('not payout or profit')
})

it('never renders unknown Uber financial values as zero or a complete donut', async () => {
  const data = summary(); data.totals.uber_eats = metric(null, 2); data.totals.total = metric(null, 3)
  data.categories[0].uber_eats = metric(null, 2); data.categories[0].total = metric(null, 3)
  await act(async () => { view = create(<ChannelRevenueMix summary={data} range="today" />) })
  expect(text()).toContain('Pending')
  expect(text()).toContain('Category amounts pending')
  expect(text()).not.toContain('$0.00')
  expect(view!.root.findAllByType('circle')).toHaveLength(1)
})

it('trend exposes both series and total, with scrollable Pad layout', async () => {
  await act(async () => { view = create(<ChannelTrend points={summary().trend} />) })
  expect(text()).toContain('18:00')
  expect(text()).toContain('In-store $10.00; Uber $19.99; Total $29.99')
  expect(text()).toContain('Uber placed time')
  expect(text()).toContain('overflow-x-auto')
})

it('shared report table displays channel quantities and nullable amounts', async () => {
  const row = { ...split(), item_key: 'SKU_traditional_beef_noodle', item_name: '牛肉面' }
  row.uber_eats = metric(null, 2); row.total = metric(null, 3)
  await act(async () => { view = create(<ChannelSalesTable rows={[row]} label="Item" />) })
  expect(text()).toContain('牛肉面')
  expect(text()).toContain('Uber Eats')
  expect(text()).toContain('Pending')
  expect(text()).toContain('$10.00')
})

it('shows subtotal without a Known prefix and distinct pending orders without implying complete AOV or percentages', async () => {
  const data = summary()
  data.totals.total = { ...metric(null, 100), known_revenue: 3742.38, unknown_order_count: 14, unknown_line_count: 35, pending_reasons: { PROMO_TOTAL_RECONCILIATION_REQUIRED: 14 } }
  data.categories[0].total = data.totals.total
  await act(async () => { view = create(<><ChannelSalesKpis summary={data} /><ChannelRevenueMix summary={data} range="today" /><ChannelTrend points={[{ ...data.totals, label: '11-02', date: '2026-11-02', hour: null }]} /><ChannelNoodleSales rows={data.categories} range="week" /><ChannelSalesTable rows={[{ ...data.totals, item_key: 'synthetic', item_name: 'Synthetic item' }]} label="Item" /></>) })
  expect(text()).toContain('$3,742.38')
  expect(text()).not.toMatch(/\bknown\b/i)
  expect(text()).toContain('Incomplete')
  expect(text()).toContain('orders')
  expect(text()).toContain('lines')
  expect(text()).toContain('Pending')
  expect(data.totals.total.average_order_value).toBeNull()
  expect(text()).not.toContain('PROMO_TOTAL_RECONCILIATION_REQUIRED')
  expect(view!.root.findAllByType('circle')).toHaveLength(1)
})

it('keeps confirmed order Sales and AOV visible while category amounts remain pending review', async () => {
  const data=summary()
  data.totals.total={ ...metric(4357.72,100),order_count:106,average_order_value:41.11,unknown_order_count:0,unknown_line_count:12,item_pending_order_count:6,item_pending_reasons:{PROMO_COMPONENT_ROUNDING_REVIEW:6} }
  data.totals.uber_eats={...data.totals.total,revenue:1718.47,known_revenue:1718.47,order_count:46,average_order_value:37.36}
  data.categories[0].total={...metric(null,18),known_revenue:100,unknown_order_count:6,unknown_line_count:12,item_pending_order_count:6}
  await act(async () => { view=create(<><ChannelSalesKpis summary={data}/><ChannelRevenueMix summary={data} range="today"/></>) })
  expect(text()).toContain('$4,357.72')
  expect(text()).toContain('$1,718.47')
  expect(text()).toContain('$41.11')
  expect(text()).toContain('Item amounts pending review')
  expect(text()).not.toContain('PROMO_COMPONENT_ROUNDING_REVIEW')
  expect(text()).not.toContain('AOV Pending')
  expect(view!.root.findAllByType('circle')).toHaveLength(1)
  expect(text()).toContain('Category amounts pending reconciliation')
})
