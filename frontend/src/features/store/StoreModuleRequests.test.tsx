import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import App from '../../App'
import { activateOfflineAccount } from '../../offline/environmentScope'
import { fetchStoreContext } from '../../services/storeWorkspaceService'
import { fetchNoodleDisplayTasks, fetchHotKitchenDisplayTasks, fetchKdsHistory, subscribeToNoodleDisplay, subscribeToHotKitchen } from '../../services/kdsService'
import { fetchServingShelf, subscribeToServingShelf } from '../../services/pickupService'
import { fetchUberOrders } from '../../services/uberEatsService'
import { subscribeToFrontdeskOrders } from '../../services/orderService'
import { apiRequest } from '../../services/apiClient'
import { StoreContext, mapStoreContext } from './StoreContextCore'
import { RequireStoreModule } from './StoreContext'
import { useAnalyticsReports } from '../reports/useAnalyticsReports'
import { fetchPlatformOverview } from '../../services/platformAdminService'
import { fetchAnalyticsSummaries } from '../../services/analyticsReportService'
import type { StoreContextResponse } from '../../services/storeWorkspaceService'

const state = vi.hoisted(() => ({ enabled: false, stopKds: vi.fn(), stopUber: vi.fn() }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 1, role_code: 'OWNER' }, loading: false, isOfflineRestricted: false, features: {}, permissions: [], signOut: vi.fn() }) }))
vi.mock('../dev/DevRoleSwitcher', () => ({ DevRoleSwitcher: () => null }))
vi.mock('./StoreSwitcher', () => ({ StoreSwitcher: () => null }))
vi.mock('../../services/storeWorkspaceService', async original => ({ ...await original<typeof import('../../services/storeWorkspaceService')>(), fetchStoreContext: vi.fn() }))
vi.mock('../../services/kdsService', async original => ({ ...await original<typeof import('../../services/kdsService')>(), fetchNoodleDisplayTasks: vi.fn(async () => []), fetchHotKitchenDisplayTasks: vi.fn(async () => []), fetchKdsHistory: vi.fn(async () => []), subscribeToNoodleDisplay: vi.fn(() => state.stopKds), subscribeToHotKitchen: vi.fn(() => state.stopKds) }))
vi.mock('../../services/pickupService', async original => ({ ...await original<typeof import('../../services/pickupService')>(), fetchServingShelf: vi.fn(async () => []), subscribeToServingShelf: vi.fn(() => state.stopKds) }))
vi.mock('../../services/uberEatsService', async original => ({ ...await original<typeof import('../../services/uberEatsService')>(), fetchUberOrders: vi.fn(async () => []) }))
vi.mock('../../services/orderService', async original => ({ ...await original<typeof import('../../services/orderService')>(), subscribeToFrontdeskOrders: vi.fn(() => state.stopUber) }))
vi.mock('../../services/apiClient', async original => ({ ...await original<typeof import('../../services/apiClient')>(), apiRequest: vi.fn() }))
vi.mock('../../services/platformAdminService', async original => ({ ...await original<typeof import('../../services/platformAdminService')>(), fetchPlatformOverview: vi.fn(async () => ({ organizations: [{ id: 1 }], stores: [] })) }))
vi.mock('../../services/analyticsReportService', async original => ({ ...await original<typeof import('../../services/analyticsReportService')>(), fetchAnalyticsSummaries: vi.fn(async () => ({})) }))

let view: ReactTestRenderer | undefined
function context(): StoreContextResponse {
  return { id: 1, name: 'Store', organization_id: 1, module_configuration: {
    store_id: 1, modules: ['KDS', 'UBER_EATS', 'REPORTING_CORE', 'ANALYTICS_ADVANCED'].map(module_key => ({ module_key, persisted: true, enabled: module_key === 'REPORTING_CORE' || state.enabled })), validation_issues: [],
  } } as unknown as StoreContextResponse
}
beforeEach(async () => {
  await Promise.all([import('../../pages/KdsRamen'), import('../uber-eats/UberInboxPage')])
  vi.useFakeTimers(); vi.clearAllMocks(); state.enabled = false
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', Object.assign(new EventTarget(), { location: { origin: 'https://synthetic.invalid', pathname: '/stores/1/kds/ramen' }, setInterval, clearInterval, setTimeout, clearTimeout }))
  vi.stubGlobal('document', Object.assign(new EventTarget(), { visibilityState: 'visible', hidden: false }))
  vi.stubGlobal('navigator', { onLine: true })
  activateOfflineAccount(1)
  vi.mocked(fetchStoreContext).mockImplementation(async () => context())
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.useRealTimers(); vi.unstubAllGlobals() })

it.each(['/kds/ramen', '/kds/noodle', '/kds/hot-kitchen', '/kds/history', '/pickup', '/frontdesk/uber-eats', '/admin/integrations/uber-eats'])(
  'makes zero business reads or subscriptions for disabled actual App route %s', async path => {
    window.location.pathname = '/stores/1' + path
    await act(async () => { view = create(<App />) })
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    for (const operation of [fetchNoodleDisplayTasks, fetchHotKitchenDisplayTasks, fetchKdsHistory, fetchServingShelf, fetchUberOrders, subscribeToNoodleDisplay, subscribeToHotKitchen, subscribeToServingShelf, subscribeToFrontdeskOrders, apiRequest]) expect(operation).not.toHaveBeenCalled()
    expect(JSON.stringify(view!.toJSON())).toContain('功能未启用')
  },
)

it.each(['/kds/ramen', '/frontdesk/uber-eats'])('unsubscribes and stops polling when enabled actual route %s closes', async path => {
  state.enabled = true; window.location.pathname = '/stores/1' + path
  await act(async () => { view = create(<App />) })
  const read = path.startsWith('/kds') ? fetchNoodleDisplayTasks : fetchUberOrders
  const stop = path.startsWith('/kds') ? state.stopKds : state.stopUber
  expect(read).toHaveBeenCalled()
  state.enabled = false
  await act(async () => { window.dispatchEvent(new Event('online')) })
  expect(stop).toHaveBeenCalled()
  const calls = vi.mocked(read).mock.calls.length
  await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
  expect(read).toHaveBeenCalledTimes(calls)
})

it('retains Reporting Core requests with Advanced Analytics disabled', async () => {
  function CoreReport() { useAnalyticsReports(); return <div>Core report</div> }
  await act(async () => { view = create(<StoreContext.Provider value={mapStoreContext(1, context(), false, null)}><RequireStoreModule moduleKey="REPORTING_CORE"><CoreReport /></RequireStoreModule></StoreContext.Provider>) })
  expect(fetchPlatformOverview).toHaveBeenCalledTimes(1)
  expect(fetchAnalyticsSummaries).toHaveBeenCalledTimes(2)
})
