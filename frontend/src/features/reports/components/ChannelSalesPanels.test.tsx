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
