import { appI18n } from '../../i18n/engine'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { testConfiguredPrinterConnection } from '../../services/printerConnectionService'
import { PrintingSettingsPage } from './PrintingSettingsPage'
import { PrintingDisplayRulesPanel } from './PrintingDisplayRulesPanel'
import { fetchWorkspaces } from '../../services/storeWorkspaceService'
import { fetchPrintCenterOverview, fetchPrintJobs, fetchStoreDevices, fetchPrintingDisplayRules } from '../../services/printingAdminService'

const scope = vi.hoisted(() => ({ storeId: 2, station: false }))
vi.mock('../store/useStoreContext', () => ({ useCurrentStore: () => ({ storeId: scope.storeId, storeName: `Store ${scope.storeId}`, storeCode: `S${scope.storeId}`, organizationId: 1, roleCode: 'OWNER', presentation: scope.station ? { kitchen_configuration: 'STATION' } : undefined }) }))
vi.mock('../../services/printerConnectionService', async () => {
 const actual = await vi.importActual<typeof import('../../services/printerConnectionService')>('../../services/printerConnectionService')
 return { ...actual, testConfiguredPrinterConnection: vi.fn() }
})
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


it('shows native connection success and then failure on the printer card, without keeping an old success badge', async () => {
  const printer = { id: 3, store_id: 2, name: 'Local printer', ip_address: '192.0.2.10', port: 9100, enabled: true, timeout_ms: 1000, printer_type: 'ESC_POS_TCP', paper_width_mm: 80 }
  vi.mocked(fetchPrintCenterOverview).mockResolvedValue({ store_id: 2, printing_enabled: true, printing_mode: 'PAD_DIRECT', printers: [printer], assignments: [] })
  await act(async () => { view = create(<PrintingSettingsPage />) })
  const button = () => view!.root.findAllByType('button').find((node) => node.children.join('').toLowerCase().includes('connection test'))!
  vi.mocked(testConfiguredPrinterConnection).mockResolvedValue({ success: true, message: 'Connected', checked_at: '2026-11-13T18:00:00Z', local_printer: printer })
  await act(async () => button().props.onClick())
  expect(testConfiguredPrinterConnection).toHaveBeenCalledWith(2, 3)
  expect(JSON.stringify(view!.toJSON())).toContain(appI18n.t('printing.connectionOK'))
  vi.mocked(testConfiguredPrinterConnection).mockResolvedValue({ success: false, message: 'Connect timed out', checked_at: '2026-11-13T18:01:00Z', local_printer: printer })
  await act(async () => button().props.onClick())
  expect(JSON.stringify(view!.toJSON())).toContain('Connect timed out')
  expect(JSON.stringify(view!.toJSON())).not.toContain(appI18n.t('printing.connectionOK'))
})
