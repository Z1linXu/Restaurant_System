// @vitest-environment jsdom
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { OrdersPage } from './OrdersPage'
import { StoreContext, mapStoreContext } from '../store/StoreContextCore'
import type { StoreContextResponse } from '../../services/storeWorkspaceService'
import { ApiRequestError, apiRequest } from '../../services/apiClient'
import { fetchOrderDetail, fetchOrderPrintJobs, fetchOrderPrintOptions, fetchTodayOrderHistory } from '../../services/orderService'
import { confirmActivePrintJobs } from '../../services/manualReprintService'
import { StoreContextProvider, RequireStoreAccess } from '../store/StoreContext'
import { activateOfflineAccount } from '../../offline/environmentScope'

const kick = vi.hoisted(() => vi.fn(async () => '{}'))
vi.mock('../../hooks/useIpadLandscape', () => ({ useIpadLandscape: () => false }))
vi.mock('../frontdesk/components/FrontdeskTopNav', () => ({ FrontdeskTopNav: () => null }))
vi.mock('./components/OrderMiniCard', () => ({ OrderMiniCard: () => null }))
vi.mock('../../services/orderService', async original => ({ ...await original<typeof import('../../services/orderService')>(), fetchOrderDetail: vi.fn(), fetchOrderPrintJobs: vi.fn(), fetchOrderPrintOptions: vi.fn(), fetchTodayOrderHistory: vi.fn() }))
vi.mock('../../services/apiClient', async original => ({ ...await original<typeof import('../../services/apiClient')>(), apiRequest: vi.fn(), getAccessToken: () => `header.${btoa(JSON.stringify({ user_id: 1, organization_id: 9 }))}.signature` }))
vi.mock('../../types/androidPadBridge', () => ({ getAndroidPadDeviceBridge: () => ({ kickPrintWorker: kick }) }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 1 }, isOfflineRestricted: false }) }))
vi.mock('../../offline/workspaceSnapshot', async original => ({ ...await original<typeof import('../../offline/workspaceSnapshot')>(), saveStoreContextSnapshot: vi.fn(async () => undefined) }))

let view: ReactTestRenderer | undefined
const intentKeys = () => Object.keys(localStorage).filter(key => key.startsWith('restaurant-manual-reprint:'))
function page(enabled = true, storeId = 1) {
  const value = mapStoreContext(storeId, { id: storeId, name: `Store ${storeId}`, module_configuration: { store_id: storeId, modules: [{ module_key: 'PRINTING', persisted: true, enabled }], validation_issues: [] } } as unknown as StoreContextResponse, false, null)
  return <StoreContext.Provider value={value}><OrdersPage /></StoreContext.Provider>
}
beforeEach(() => {
  vi.clearAllMocks(); localStorage.clear(); document.body.replaceChildren()
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  // jsdom supplies the actual dialog/tree; only the browser's top-layer methods need shims.
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: vi.fn(function (this: HTMLDialogElement) { this.open = true }) })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: vi.fn(function (this: HTMLDialogElement) { this.open = false }) })
  vi.mocked(fetchTodayOrderHistory).mockImplementation(async storeId => [{ order_id: storeId * 100 }] as Awaited<ReturnType<typeof fetchTodayOrderHistory>>)
  vi.mocked(fetchOrderDetail).mockImplementation(async id => ({ id, items: [], status: 'submitted', total_amount: 10 }) as unknown as Awaited<ReturnType<typeof fetchOrderDetail>>)
  vi.mocked(fetchOrderPrintOptions).mockResolvedValue([{ module_code: 'GRAB', available: true, label: 'Kitchen', unavailable_reason: null }])
  vi.mocked(fetchOrderPrintJobs).mockResolvedValue([])
  vi.mocked(apiRequest).mockRejectedValue(new ApiRequestError(409, 'confirm', undefined, { data: { confirmation_fingerprint: 'A', active_jobs: [{ job_id: 777, status: 'PRINTING', module: 'GRAB', claimed_device_id: 888, age_seconds: 30 }] } }, 'REPRINT_CONFIRMATION_REQUIRED'))
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.restoreAllMocks(); vi.unstubAllGlobals(); document.body.replaceChildren() })
async function openDefaultDialog() {
  await act(async () => { view = create(page()) })
  const print = view!.root.findAllByType('button').find(node => node.props.disabled === false)!
  await act(async () => { print.props.onClick() })
  const dialog = document.querySelector('dialog')!
  expect(dialog.open).toBe(true)
  expect(dialog.textContent).toContain('Pad 888')
  expect(dialog.textContent).toContain('#777')
  return dialog
}

it.each(['module-close', 'store-switch', 'logout'])(
  'removes the real default confirmation DOM on %s, retains its intent and never posts/kicks again', async change => {
    const dialog = await openDefaultDialog()
    const oldConfirm = dialog.querySelectorAll('button')[1]
    const key = localStorage.getItem(intentKeys()[0])
    await act(async () => {
      if (change === 'module-close') view!.update(page(false))
      else if (change === 'store-switch') view!.update(page(true, 2))
      else window.dispatchEvent(new Event('restaurant-auth-expired'))
    })
    expect(document.querySelector('dialog')).toBeNull()
    expect(document.body.textContent).not.toMatch(/777|888/)
    expect(dialog.isConnected).toBe(false)
    await act(async () => oldConfirm.click())
    expect(apiRequest).toHaveBeenCalledTimes(1)
    expect(kick).not.toHaveBeenCalled()
    expect(intentKeys()).toHaveLength(1)
    expect(localStorage.getItem(intentKeys()[0])).toBe(key)
  },
)

it('still allows deliberate operator cancellation through the real default dialog', async () => {
  const dialog = await openDefaultDialog()
  await act(async () => dialog.querySelector('button')!.click())
  expect(document.querySelector('dialog')).toBeNull()
  expect(intentKeys()).toHaveLength(0)
  expect(apiRequest).toHaveBeenCalledTimes(1)
  expect(kick).not.toHaveBeenCalled()
})

it('resolves the real default dialog as false and removes its DOM on an AbortSignal', async () => {
  const controller = new AbortController()
  const result = confirmActivePrintJobs({ confirmation_fingerprint: 'A', active_jobs: [] }, controller.signal)
  expect(document.querySelector('dialog')?.open).toBe(true)
  controller.abort()
  await expect(result).resolves.toBe(false)
  expect(document.querySelector('dialog')).toBeNull()
})

it('closes an already-open default dialog when the actual StoreContextProvider learns Printing was disabled on focus', async () => {
  activateOfflineAccount(1)
  let enabled = true
  const confirmation = new ApiRequestError(409, 'confirm', undefined, { data: { confirmation_fingerprint: 'A', active_jobs: [{ job_id: 777, status: 'PRINTING', module: 'GRAB', claimed_device_id: 888, age_seconds: 30 }] } }, 'REPRINT_CONFIRMATION_REQUIRED')
  vi.mocked(apiRequest).mockImplementation(async path => {
    if (path.endsWith('/context')) return { id: 1, organization_id: 9, module_configuration: { store_id: 1, modules: [{ module_key: 'PRINTING', persisted: true, enabled }], validation_issues: [] } } as never
    throw confirmation
  })
  await act(async () => { view = create(<StoreContextProvider storeId={1}><RequireStoreAccess><OrdersPage /></RequireStoreAccess></StoreContextProvider>) })
  const print = view!.root.findAllByType('button').find(node => node.props.disabled === false)!
  await act(async () => { print.props.onClick() })
  expect(document.querySelector('dialog')?.textContent).toContain('Pad 888')
  enabled = false
  await act(async () => { window.dispatchEvent(new Event('focus')) })
  expect(document.querySelector('dialog')).toBeNull()
  expect(document.body.textContent).not.toContain('Pad 888')
  expect(vi.mocked(apiRequest).mock.calls.filter(([path]) => path.endsWith('/reprint'))).toHaveLength(1)
  expect(kick).not.toHaveBeenCalled()
  expect(intentKeys()).toHaveLength(1)
})
