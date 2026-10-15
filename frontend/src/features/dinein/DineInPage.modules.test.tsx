import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { DineInPage } from './DineInPage'
import { StoreContext, mapStoreContext } from '../store/StoreContextCore'
import type { StoreContextResponse } from '../../services/storeWorkspaceService'
import { fetchOrderPrintOptions, reprintOrderReceipt, startOrderPrintJobCoordinator } from '../../services/orderService'
import { followReprint } from '../../services/manualReprintService'
import { getAndroidPadDeviceBridge } from '../../types/androidPadBridge'

const state = vi.hoisted(() => ({
  editing: false,
  cancel: vi.fn(),
  stopFollow: vi.fn(),
  status: vi.fn(async () => '{}'),
}))
vi.mock('../../hooks/useMenuCatalog', () => ({ useMenuCatalog: () => ({ catalog: null, items: [], categories: [] }) }))
vi.mock('../../hooks/useIpadLandscape', () => ({ useIpadLandscape: () => true }))
vi.mock('../../hooks/useTableBoard', () => ({ useTableBoard: () => ({
  tableSlots: [{ id: '1', label: 'A1', baseTableLabel: 'A1', zone: 'Main', status: 'occupied', action: 'edit', mode: 'full', orderDbId: 10, orderStatus: 'submitted' }],
  statusCounts: { available: 0, occupied: 1, alert: 0 }, isOnline: true, refreshFromBackend: vi.fn(),
}) }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 1 } }) }))
vi.mock('../offline/useStoreOfflineOrders', () => ({ useStoreOfflineOrders: () => [] }))
vi.mock('../frontdesk/components/FrontdeskTopNav', () => ({ FrontdeskTopNav: () => null }))
vi.mock('./components/DineInSidebar', () => ({ DineInSidebar: () => null }))
vi.mock('../frontdesk/navigation', async original => ({
  ...await original<typeof import('../frontdesk/navigation')>(), navigateTo: vi.fn(),
  parseMenuRoute: () => state.editing ? { slotLabel: 'A1', tableLabel: 'A1', orderType: 'dine_in', pickupLabel: null, workstation: null } : null,
}))
vi.mock('../ordering/OrderingPage', () => ({ OrderingPage: ({ onOrderSubmitted }: { onOrderSubmitted: (slot: string, table: string, id: number) => void }) => <button onClick={() => onOrderSubmitted('A1', 'A1', 10)}>Submit fixture</button> }))
vi.mock('../../services/orderService', () => ({
  completeOrder: vi.fn(), fetchOrderPrintOptions: vi.fn(), reprintOrderReceipt: vi.fn(),
  startOrderPrintJobCoordinator: vi.fn(() => ({ key: '1:10', cancel: state.cancel })),
}))
vi.mock('../../services/manualReprintService', () => ({ followReprint: vi.fn(() => state.stopFollow), printResultMessage: () => 'Late print result', reprintErrorMessage: () => 'Print failed' }))
vi.mock('../../types/androidPadBridge', () => ({ getAndroidPadDeviceBridge: vi.fn(() => ({ getPrintWorkerStatus: state.status })), parseAndroidBridgeJson: (raw: string) => raw ? JSON.parse(raw) : null }))

let view: ReactTestRenderer | undefined
function page(enabled: boolean) {
  const context = mapStoreContext(1, { id: 1, name: 'Current Store', organization_id: 1,
    module_configuration: { store_id: 1, modules: [{ module_key: 'PRINTING', persisted: true, enabled }], validation_issues: [] },
  } as unknown as StoreContextResponse, false, null)
  return <StoreContext.Provider value={context}><DineInPage routePath="/frontdesk" routeSearch="" /></StoreContext.Provider>
}
function button(label: string) { return view!.root.findAllByType('button').find(node => node.children.includes(label) || node.findAllByType('span').some(span => span.children.includes(label)))! }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done }); return { promise, resolve } }
beforeEach(() => {
  vi.useFakeTimers(); vi.clearAllMocks(); state.editing = false
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', Object.assign(new EventTarget(), { location: { origin: 'https://synthetic.invalid' }, setTimeout, clearTimeout, setInterval, clearInterval }))
  vi.stubGlobal('document', Object.assign(new EventTarget(), { visibilityState: 'visible' }))
  vi.mocked(fetchOrderPrintOptions).mockResolvedValue([{ module_code: 'FRONTDESK_RECEIPT', label: 'Receipt', available: true, unavailable_reason: null }])
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.useRealTimers(); vi.unstubAllGlobals() })

it('makes no print read, subscription, native status or manual action when Printing is disabled', async () => {
  await act(async () => { view = create(page(false)); await vi.advanceTimersByTimeAsync(15000) })
  expect(button('Print')).toBeUndefined()
  expect(fetchOrderPrintOptions).not.toHaveBeenCalled()
  expect(reprintOrderReceipt).not.toHaveBeenCalled()
  expect(startOrderPrintJobCoordinator).not.toHaveBeenCalled()
  expect(followReprint).not.toHaveBeenCalled()
  expect(getAndroidPadDeviceBridge).not.toHaveBeenCalled()
  expect(state.status).not.toHaveBeenCalled()
})

it('does not create a post-submit print coordinator for disabled Printing', async () => {
  state.editing = true
  await act(async () => { view = create(page(false)) })
  await act(async () => button('Submit fixture').props.onClick())
  expect(startOrderPrintJobCoordinator).not.toHaveBeenCalled()
})

it('cancels the coordinator and status polling on closing and rejects stale print actions', async () => {
  state.editing = true
  await act(async () => { view = create(page(true)) })
  await act(async () => button('Submit fixture').props.onClick())
  const attention = vi.mocked(startOrderPrintJobCoordinator).mock.calls[0][0].onUnavailable!
  state.editing = false
  await act(async () => view!.update(page(true)))
  await act(async () => { await vi.advanceTimersByTimeAsync(0) })
  expect(state.status).toHaveBeenCalled()
  const print = button('Print').props.onClick
  await act(async () => view!.update(page(false)))
  expect(state.cancel).toHaveBeenCalled()
  const statusCalls = state.status.mock.calls.length
  await act(async () => { print(); attention(new Error('late')); await vi.advanceTimersByTimeAsync(15000) })
  expect(fetchOrderPrintOptions).not.toHaveBeenCalled()
  expect(state.status).toHaveBeenCalledTimes(statusCalls)
  expect(JSON.stringify(view!.toJSON())).not.toContain('暂时无法读取打印状态')
})

it('ignores late options after closing and does not follow a late manual reprint result', async () => {
  const options = deferred<Awaited<ReturnType<typeof fetchOrderPrintOptions>>>()
  vi.mocked(fetchOrderPrintOptions).mockReturnValueOnce(options.promise)
  await act(async () => { view = create(page(true)) })
  await act(async () => { button('Print').props.onClick() })
  await act(async () => view!.update(page(false)))
  await act(async () => options.resolve([{ module_code: 'FRONTDESK_RECEIPT', label: 'Late receipt', available: true, unavailable_reason: null }]))
  expect(JSON.stringify(view!.toJSON())).not.toContain('Late receipt')
  await act(async () => view!.update(page(true)))
  await act(async () => button('Print').props.onClick())
  const receipt = button('Receipt')
  const lateManual = receipt.props.onClick
  const result = deferred<Awaited<ReturnType<typeof reprintOrderReceipt>>>()
  vi.mocked(reprintOrderReceipt).mockReturnValueOnce(result.promise)
  await act(async () => { lateManual() })
  await act(async () => view!.update(page(false)))
  await act(async () => result.resolve({} as Awaited<ReturnType<typeof reprintOrderReceipt>>))
  expect(followReprint).not.toHaveBeenCalled()
  await act(async () => { lateManual() })
  expect(reprintOrderReceipt).toHaveBeenCalledTimes(1)
})
