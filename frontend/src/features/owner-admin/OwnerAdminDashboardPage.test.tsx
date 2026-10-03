import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { fetchOwnerDashboard, type OwnerDashboardResponse } from '../../services/ownerDashboardService'
import { fetchWorkspaces } from '../../services/storeWorkspaceService'
import { OwnerAdminDashboardPage } from './OwnerAdminDashboardPage'

vi.mock('../../services/ownerDashboardService', () => ({ fetchOwnerDashboard: vi.fn() }))
vi.mock('../../services/storeWorkspaceService', () => ({ fetchWorkspaces: vi.fn() }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 1 }, isOfflineRestricted: false }) }))
vi.mock('../store/useStoreContext', () => ({ useCurrentStore: () => ({ storeId: 1, storeName: 'Store', storeCode: 'ST1', organizationId: 1 }) }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
vi.stubGlobal('window', { setInterval: () => 1, clearInterval: vi.fn() })
let view: ReactTestRenderer | undefined
const metric = { value: 100, previous_value: 90, change_pct: 10 }
const soup = { reporting_group: 'SOUP_NOODLE' as const, quantity_sold: 2, revenue: 70, percentage: 70 }
const fried = { reporting_group: 'FRIED_NOODLE' as const, quantity_sold: 0, revenue: 0, percentage: 0 }
const data: OwnerDashboardResponse = {
  organization_id: 1, organization_name: 'Test', range: 'today', compare_enabled: true, stores: [],
  kpis: { sales: metric, orders: metric, average_order_value: metric, active_orders: metric }, insights: [],
  trend: { granularity: 'hourly', points: [{ label: '18:00', value: 100 }] }, top_items: [], worst_items: [],
  order_status: { pending: 1, preparing: 1, ready: 1 }, recent_orders: [], store_comparison: [], sales_timestamp: 'submitted_at',
  noodle_sales: [soup, fried], revenue_mix: [soup, fried, { reporting_group: 'OTHER', quantity_sold: 1, revenue: 30, percentage: 30 }],
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(fetchOwnerDashboard).mockResolvedValue(data)
  vi.mocked(fetchWorkspaces).mockResolvedValue({ stores: [{ id: 1, organization_id: 1, name: 'Store', code: 'ST1', status: 'ACTIVE', role_code: 'OWNER' }] } as Awaited<ReturnType<typeof fetchWorkspaces>>)
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })

it('replaces operational dashboard panels with readable noodle sales and revenue mix', async () => {
  await act(async () => { view = create(<OwnerAdminDashboardPage />) })
  const text = JSON.stringify(view!.toJSON())
  expect(text).not.toContain('Recent Orders')
  expect(text).not.toContain('Active Orders')
  expect(text).not.toContain('Order Status')
  expect(text).not.toContain('based on completed orders')
  expect(text).toContain('first submission time')
  expect(text).toContain('今日面类销售')
  expect(text).toContain('今日营业额构成')
  expect(text).toContain('炒面')
  expect(text).toContain('$0.00')
  expect(text).toContain('其他')
  expect(text).toContain('30.00')
  expect(view!.root.findAllByType('svg')).toHaveLength(1)
  expect(view!.root.findByProps({ 'aria-label': 'Revenue mix' }).props.className).toContain('min-w-0')
  expect(text).toContain('flex-wrap')
  expect(text).toContain('overflow-x-auto')
})

it('changes selected range without restoring removed panels', async () => {
  await act(async () => { view = create(<OwnerAdminDashboardPage />) })
  const week = view!.root.findAllByType('button').find((node) => node.children.join('') === 'Week')!
  await act(async () => week.props.onClick())
  expect(fetchOwnerDashboard).toHaveBeenLastCalledWith(expect.objectContaining({ storeId: 1, range: 'week' }))
  expect(JSON.stringify(view!.toJSON())).not.toContain('Today Sales')
})
