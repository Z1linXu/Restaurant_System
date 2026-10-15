import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { OrdersPage } from './OrdersPage'
import { StoreContext, mapStoreContext } from '../store/StoreContextCore'
import type { StoreContextResponse } from '../../services/storeWorkspaceService'
import { fetchOrderDetail, fetchOrderPrintJobs, fetchOrderPrintOptions, fetchTodayOrderHistory, reprintOrderReceipt } from '../../services/orderService'
import { followReprint } from '../../services/manualReprintService'

const stop = vi.hoisted(() => vi.fn())
vi.mock('../../hooks/useIpadLandscape', () => ({ useIpadLandscape: () => false }))
vi.mock('../frontdesk/components/FrontdeskTopNav', () => ({ FrontdeskTopNav: () => null }))
vi.mock('./components/OrderMiniCard', () => ({ OrderMiniCard: () => null }))
vi.mock('../../services/orderService', () => ({ fetchOrderDetail: vi.fn(), fetchOrderPrintJobs: vi.fn(), fetchOrderPrintOptions: vi.fn(), fetchTodayOrderHistory: vi.fn(), reprintOrderReceipt: vi.fn() }))
vi.mock('../../services/manualReprintService', () => ({ followReprint: vi.fn(() => stop), printResultMessage: () => 'Print result', reprintErrorMessage: () => 'Print failed' }))
let view: ReactTestRenderer | undefined
function page(enabled: boolean) {
  const value = mapStoreContext(1, { id: 1, module_configuration: { store_id: 1, modules: [{ module_key: 'PRINTING', persisted: true, enabled }], validation_issues: [] } } as unknown as StoreContextResponse, false, null)
  return <StoreContext.Provider value={value}><OrdersPage /></StoreContext.Provider>
}
const printButton = () => view!.root.findAllByType('button').find(node => node.props.disabled === false)!
beforeEach(() => {
  vi.clearAllMocks(); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.mocked(fetchTodayOrderHistory).mockResolvedValue([{ order_id: 10 }] as Awaited<ReturnType<typeof fetchTodayOrderHistory>>)
  vi.mocked(fetchOrderDetail).mockResolvedValue({ id: 10, items: [], status: 'submitted', total_amount: 10 } as unknown as Awaited<ReturnType<typeof fetchOrderDetail>>)
  vi.mocked(fetchOrderPrintOptions).mockResolvedValue([{ module_code: 'GRAB', available: true, label: 'Kitchen', unavailable_reason: null }])
  vi.mocked(fetchOrderPrintJobs).mockResolvedValue([])
  vi.mocked(reprintOrderReceipt).mockResolvedValue({ id: 11, status: 'PENDING' } as Awaited<ReturnType<typeof reprintOrderReceipt>>)
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.unstubAllGlobals() })

it('keeps order detail available without print options, job reads or manual calls when Printing is disabled', async () => {
  await act(async () => { view = create(page(false)) })
  expect(fetchTodayOrderHistory).toHaveBeenCalledTimes(1)
  expect(fetchOrderDetail).toHaveBeenCalledTimes(1)
  expect(fetchOrderPrintOptions).not.toHaveBeenCalled()
  expect(fetchOrderPrintJobs).not.toHaveBeenCalled()
  expect(reprintOrderReceipt).not.toHaveBeenCalled()
  expect(followReprint).not.toHaveBeenCalled()
  expect(JSON.stringify(view!.toJSON())).not.toContain('重打完整订单')
})

it('rejects a stale manual action and stops the existing follow subscription when Printing closes', async () => {
  await act(async () => { view = create(page(true)) })
  const action = printButton().props.onClick
  await act(async () => action())
  expect(followReprint).toHaveBeenCalledTimes(1)
  await act(async () => view!.update(page(false)))
  expect(stop).toHaveBeenCalled()
  const reads = vi.mocked(fetchOrderPrintJobs).mock.calls.length
  await act(async () => action())
  expect(reprintOrderReceipt).toHaveBeenCalledTimes(1)
  expect(fetchOrderPrintJobs).toHaveBeenCalledTimes(reads)
  expect(JSON.stringify(view!.toJSON())).not.toContain('重打完整订单')
})

it('does not follow a manual result returned after Printing closes', async () => {
  let resolve!: (value: Awaited<ReturnType<typeof reprintOrderReceipt>>) => void
  vi.mocked(reprintOrderReceipt).mockImplementationOnce(() => new Promise(done => { resolve = done }))
  await act(async () => { view = create(page(true)) })
  await act(async () => { printButton().props.onClick() })
  const isActive = vi.mocked(reprintOrderReceipt).mock.calls[0][2]!
  expect(isActive()).toBe(true)
  await act(async () => view!.update(page(false)))
  expect(isActive()).toBe(false)
  await act(async () => resolve({ id: 11 } as Awaited<ReturnType<typeof reprintOrderReceipt>>))
  expect(followReprint).not.toHaveBeenCalled()
})
