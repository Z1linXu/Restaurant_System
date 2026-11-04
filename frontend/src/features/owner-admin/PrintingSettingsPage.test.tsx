import { appI18n } from '../../i18n/engine'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { PrintingSettingsPage } from './PrintingSettingsPage'
import { PrintingDisplayRulesPanel } from './PrintingDisplayRulesPanel'
import { fetchWorkspaces } from '../../services/storeWorkspaceService'
import { fetchPrintCenterOverview, fetchPrintJobs, fetchStoreDevices, fetchPrintingDisplayRules } from '../../services/printingAdminService'

const scope = vi.hoisted(() => ({ storeId: 2, station: false }))
vi.mock('../store/useStoreContext', () => ({ useCurrentStore: () => ({ storeId: scope.storeId, storeName: `Store ${scope.storeId}`, storeCode: `S${scope.storeId}`, organizationId: 1, roleCode: 'OWNER', presentation: scope.station ? { kitchen_configuration: 'STATION' } : undefined }) }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 7 }, isOfflineRestricted: false }) }))
vi.mock('../feature-flags/featureConfig', () => ({ isFeatureEnabled: () => true }))
vi.mock('../../services/storeWorkspaceService', () => ({ fetchWorkspaces: vi.fn() }))
vi.mock('../../services/printingAdminService', async () => {
  const actual = await vi.importActual<typeof import('../../services/printingAdminService')>('../../services/printingAdminService')
  return { ...actual, fetchPrintCenterOverview: vi.fn(), fetchPrintJobs: vi.fn(), fetchStoreDevices: vi.fn(), fetchPrintingDisplayRules: vi.fn() }
})
vi.mock('./PrintingDisplayRulesPanel', () => ({ PrintingDisplayRulesPanel: vi.fn(() => <div data-testid="old-printing-editor" />) }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
let view: ReactTestRenderer | undefined
beforeEach(() => {
  void appI18n.changeLanguage('en')
  vi.clearAllMocks()
  scope.storeId = 2
  scope.station = false
  vi.mocked(fetchWorkspaces).mockResolvedValue({ default_store_id: 2, organizations: [], stores: [1, 2].map((id) => ({
    id, name: `Store ${id}`, code: `S${id}`, status: 'active', organization_id: 1, role_code: 'OWNER',
  })) })
  vi.mocked(fetchPrintCenterOverview).mockImplementation(async (id) => ({ store_id: id, printing_enabled: false, printing_mode: 'MOCK', printers: [], assignments: [] }))
  vi.mocked(fetchPrintJobs).mockResolvedValue([])
  vi.mocked(fetchStoreDevices).mockResolvedValue([])
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })

it('keeps the Print Center controls and only links to the current Store Menu tab, without mounting another rules editor', async () => {
  await act(async () => { view = create(<PrintingSettingsPage />) })
  const rulesLink = () => view!.root.findAllByType('a').find((node) => node.children.join('').includes('Open Printing Display Rules'))!
  expect(rulesLink().props.href).toBe('/stores/2/admin/menu/items?section=printing-display-rules')
  expect(PrintingDisplayRulesPanel).not.toHaveBeenCalled()
  expect(fetchPrintingDisplayRules).not.toHaveBeenCalled()
  expect(JSON.stringify(view!.toJSON())).toContain('Print Center')
  expect(JSON.stringify(view!.toJSON())).toContain('Printer assignments')
  const selector = view!.root.findAllByType('select').find((node) => node.findAllByType('option').some((option) => option.children.includes('Store 1')))!
  await act(async () => selector.props.onChange({ target: { value: '1' } }))
  expect(rulesLink().props.href).toBe('/stores/1/admin/menu/items?section=printing-display-rules')
  expect(PrintingDisplayRulesPanel).not.toHaveBeenCalled()
  expect(fetchPrintCenterOverview).toHaveBeenLastCalledWith(1)
  expect(fetchPrintJobs).toHaveBeenLastCalledWith({ storeId: 1 })
  expect(fetchStoreDevices).toHaveBeenLastCalledWith(1)
})


it('uses existing printer controls for explicit Station stores without a second routing or diagnostics workspace', async () => {
  scope.station = true
  await act(async () => { view = create(<PrintingSettingsPage />) })
  const json = JSON.stringify(view!.toJSON())
  expect(json).toContain('preparation station')
  expect(json).toContain('Printer assignments')
  expect(json).not.toContain('Persistent kitchen ticket diagnostics')
  expect(json).not.toContain('Kitchen ticket groups')
  expect(fetchPrintJobs).toHaveBeenCalledWith({ storeId: 2 })
})
