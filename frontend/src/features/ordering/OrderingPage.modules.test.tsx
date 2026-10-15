import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { OrderingPage } from './OrderingPage'
import { StoreContext, mapStoreContext } from '../store/StoreContextCore'
import type { StoreContextResponse } from '../../services/storeWorkspaceService'

const state = vi.hoisted(() => ({ submit: vi.fn(), kick: vi.fn(async () => '{}'), status: vi.fn(async () => '{}'), outbox: null as null | { state: string; serverOrderId: number } }))
vi.mock('../../hooks/useIpadLandscape', () => ({ useIpadLandscape: () => true }))
vi.mock('../../hooks/useConnectionStatus', () => ({ useConnectionStatus: () => ({ state: 'ONLINE' }) }))
vi.mock('../../hooks/useDraftOrder', () => ({ useDraftOrder: () => ({
  session: { status: 'draft', items: [] }, localSubmitState: 'DRAFT', submitOrder: state.submit,
  refreshOrder: vi.fn(), outboxRecord: state.outbox,
}) }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 1 } }) }))
vi.mock('../frontdesk/components/FrontdeskTopNav', () => ({ FrontdeskTopNav: () => null }))
vi.mock('./components/OrderSummaryPanel', () => ({ OrderSummaryPanel: ({ onSubmitOrder }: { onSubmitOrder: () => void }) => <button onClick={onSubmitOrder}>Submit fixture</button> }))
vi.mock('../../types/androidPadBridge', () => ({ getAndroidPadDeviceBridge: () => ({ kickPrintWorker: state.kick, getPrintWorkerStatus: state.status }), parseAndroidBridgeJson: (raw: string) => JSON.parse(raw) }))
let view: ReactTestRenderer | undefined
const submitted = vi.fn()
const submitButton = () => view!.root.findAllByType('button').find(node => node.children.includes('Submit fixture'))!
const catalog = { catalog: null, items: [], categories: [], loading: false, error: null, source: null, lastUpdatedAt: null, updating: false, updateError: null, refreshStatus: 'IDLE' as const, refreshCatalog: vi.fn(), cacheStale: false }
function page(enabled: boolean) {
  const value = mapStoreContext(1, { id: 1, organization_id: 1, module_configuration: { store_id: 1, modules: [{ module_key: 'PRINTING', persisted: true, enabled }], validation_issues: [] } } as unknown as StoreContextResponse, false, null)
  return <StoreContext.Provider value={value}><OrderingPage catalog={catalog} slotLabel="A1" tableLabel="A1" storeId={1} onBack={vi.fn()} onDraftCancelled={vi.fn()} onOrderSubmitted={submitted} /></StoreContext.Provider>
}
beforeEach(() => {
  vi.useFakeTimers(); vi.clearAllMocks(); state.outbox = null
  state.submit.mockResolvedValue({ id: 10, current_revision: 1, items: [] })
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', Object.assign(new EventTarget(), { location: { origin: 'https://synthetic.invalid' }, setTimeout, clearTimeout, setInterval, clearInterval }))
  vi.stubGlobal('document', Object.assign(new EventTarget(), { visibilityState: 'visible' }))
  vi.stubGlobal('navigator', { onLine: true })
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.useRealTimers(); vi.unstubAllGlobals() })

it('commits a normal order with no worker status, kick or replayed kick when Printing is disabled', async () => {
  await act(async () => { view = create(page(false)) })
  await act(async () => submitButton().props.onClick())
  expect(state.submit).toHaveBeenCalledTimes(1)
  expect(submitted).toHaveBeenCalledTimes(1)
  expect(state.kick).not.toHaveBeenCalled()
  await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
  expect(state.status).not.toHaveBeenCalled()
  await act(async () => view!.update(page(true)))
  expect(state.kick).not.toHaveBeenCalled()
})

it('does not kick for an outbox completion while Printing is disabled', async () => {
  state.outbox = { state: 'SUBMITTED', serverOrderId: 20 }
  await act(async () => { view = create(page(false)) })
  expect(submitted).toHaveBeenCalledWith('A1', 'A1', 20, null)
  expect(state.kick).not.toHaveBeenCalled()
})

it('uses the latest module state when a pending submit resolves after Printing closes', async () => {
  let resolve!: (value: unknown) => void
  state.submit.mockImplementationOnce(() => new Promise(done => { resolve = done }))
  await act(async () => { view = create(page(true)) })
  let pending!: Promise<void>
  await act(async () => { pending = submitButton().props.onClick() })
  await act(async () => view!.update(page(false)))
  await act(async () => { resolve({ id: 10, items: [] }); await pending })
  expect(submitted).toHaveBeenCalledTimes(1)
  expect(state.kick).not.toHaveBeenCalled()
})

it('still kicks once for a normal enabled Printing submit', async () => {
  await act(async () => { view = create(page(true)) })
  await act(async () => submitButton().props.onClick())
  expect(state.kick).toHaveBeenCalledTimes(1)
})
