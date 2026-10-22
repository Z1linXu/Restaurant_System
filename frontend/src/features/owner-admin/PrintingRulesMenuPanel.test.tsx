import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PrintingRulesMenuPanel } from './PrintingRulesMenuPanel'
import { PrintingDisplayRulesPanel } from './PrintingDisplayRulesPanel'
import { fetchStoreContext, type StoreContextResponse, type StoreModuleConfiguration } from '../../services/storeWorkspaceService'
import { fetchPrintingDisplayRules, previewPrintingDisplayRules, publishPrintingDisplayRuleDraft, savePrintingDisplayRuleDraft, type PrintingDisplayRuleSettings } from '../../services/printingAdminService'

const access = vi.hoisted(() => ({ permissions: ['ADMIN_PRINTING_MANAGE'], accountId: 7, printingFeature: true }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ permissions: access.permissions, user: { id: access.accountId } }) }))
vi.mock('../feature-flags/featureConfig', () => ({ isFeatureEnabled: () => access.printingFeature }))
vi.mock('../../services/storeWorkspaceService', () => ({ fetchStoreContext: vi.fn() }))
vi.mock('../../services/printingAdminService', () => ({ fetchPrintingDisplayRules: vi.fn(), savePrintingDisplayRuleDraft: vi.fn(), publishPrintingDisplayRuleDraft: vi.fn(), previewPrintingDisplayRules: vi.fn(), validatePrintingDisplayRules: vi.fn() }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
let view: ReactTestRenderer | undefined
const onToast = vi.fn()

function context(id: number, enabled = true): StoreContextResponse {
  return {
    id, name: `Store ${id}`, code: `S${id}`, status: 'active', organization_id: 1,
    organization_name: 'Test', organization_code: 'TEST', role_code: 'OWNER',
    module_configuration: {
      store_id: id, catalog_version: 'v1', dependency_graph_version: 'v1', valid: true, validation_status: 'VALID',
      environment_capability_source: 'TEST', hardware_capability_source: 'TEST', legacy_compatibility_status: 'NONE', legacy_precedence: 'NONE',
      environment_capabilities: [], hardware_capabilities: [], validation_issues: [],
      modules: [{ module_key: 'PRINTING', display_name: 'Printing', enabled, persisted: true,
        classification: 'OPTIONAL', category: 'PRINTING', default_enabled: true, core_required: false,
        active_normal_store_required: false, activation_blocking: false, source: 'STORE', configuration_status: 'VALID',
        profile_code: null, profile_version: null }],
    } satisfies StoreModuleConfiguration,
  }
}

function settings(storeId: number): PrintingDisplayRuleSettings {
  return {
    store_id: storeId, rule_set_id: storeId, revisions: [],
    draft_revision: { id: storeId * 10, revision_number: 1, status: 'DRAFT', schema_version: 'PRINTING_DISPLAY_RULES_V1', fingerprint_sha256: 'fixture',
      content: { schema_version: 'PRINTING_DISPLAY_RULES_V1', dictionaries: { MODIFIER_ADD: [['extra_noodle', `Store ${storeId} alias`]] } } },
    addon_aliases: [{ code: 'extra_noodle', name_zh: '加面', default_print_text: '+面', source: 'MENU' }],
  } as PrintingDisplayRuleSettings
}

async function mount(storeId = 2) { await act(async () => { view = create(<PrintingRulesMenuPanel storeId={storeId} onToast={onToast} />) }) }
const editor = () => view!.root.findAllByType(PrintingDisplayRulesPanel)
const button = (label: string) => view!.root.findAllByType('button').find((node) => node.children.join('') === label)!

beforeEach(() => {
  vi.clearAllMocks()
  access.permissions = ['ADMIN_PRINTING_MANAGE']; access.accountId = 7; access.printingFeature = true
  vi.mocked(fetchStoreContext).mockImplementation(async (id) => context(id))
  vi.mocked(fetchPrintingDisplayRules).mockImplementation(async (id) => settings(id))
  vi.mocked(savePrintingDisplayRuleDraft).mockResolvedValue({ id: 20, revision_number: 1, status: 'DRAFT' } as never)
  vi.mocked(publishPrintingDisplayRuleDraft).mockResolvedValue({ id: 20, revision_number: 1, status: 'PUBLISHED' } as never)
  vi.mocked(previewPrintingDisplayRules).mockResolvedValue({ grab_preview: '（走s）', hot_kitchen_preview: '（走s）', frontdesk_receipt_preview: '不辣' } as never)
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })

describe('Printing Rules Menu permission and Store boundary', () => {
  it('does not mount or fetch the rule editor for a menu-only staff member', async () => {
    access.permissions = ['ADMIN_MENU_MANAGE']
    await mount()
    expect(fetchStoreContext).not.toHaveBeenCalled()
    expect(fetchPrintingDisplayRules).not.toHaveBeenCalled()
    expect(editor()).toHaveLength(0)
    expect(view!.root.findAllByProps({ role: 'alert' })).toHaveLength(1)
  })

  it('does not mount the editor when the selected Store disables Printing', async () => {
    vi.mocked(fetchStoreContext).mockResolvedValue(context(2, false))
    await mount()
    expect(fetchStoreContext).toHaveBeenCalledWith(2, 7)
    expect(editor()).toHaveLength(0)
    expect(fetchPrintingDisplayRules).not.toHaveBeenCalled()
  })

  it('fails closed when context belongs to another Store or loading fails', async () => {
    vi.mocked(fetchStoreContext).mockResolvedValue(context(1))
    await mount()
    expect(editor()).toHaveLength(0)
    await act(async () => view!.unmount())
    vi.mocked(fetchStoreContext).mockRejectedValue(new Error('STORE_ACCESS_DENIED'))
    await mount()
    expect(editor()).toHaveLength(0)
    expect(JSON.stringify(view!.toJSON())).toContain('STORE_ACCESS_DENIED')
  })

  it('shows a permission result when the Printing feature is unavailable, without a permanent loading state', async () => {
    access.printingFeature = false
    await mount()
    expect(editor()).toHaveLength(0)
    expect(fetchStoreContext).not.toHaveBeenCalled()
    expect(view!.root.findAllByProps({ role: 'status' })).toHaveLength(0)
    expect(view!.root.findAllByProps({ role: 'alert' })).toHaveLength(1)
  })

  it('unmounts the previous Store editor immediately and never accepts a late previous-Store access response', async () => {
    let resolveStoreOne!: (value: StoreContextResponse) => void
    let resolveStoreTwo!: (value: StoreContextResponse) => void
    vi.mocked(fetchStoreContext).mockImplementation((id) => new Promise((resolve) => {
      if (id === 1) resolveStoreOne = resolve
      else resolveStoreTwo = resolve
    }))
    await mount(1)
    await act(async () => view!.update(<PrintingRulesMenuPanel storeId={2} onToast={onToast} />))
    await act(async () => resolveStoreOne(context(1)))
    expect(editor()).toHaveLength(0)
    expect(fetchPrintingDisplayRules).not.toHaveBeenCalled()
    await act(async () => resolveStoreTwo(context(2)))
    expect(editor()).toHaveLength(1)
    expect(editor()[0].props.storeId).toBe(2)
    expect(fetchPrintingDisplayRules).toHaveBeenCalledExactlyOnceWith(2)
  })

  it('does not carry unsaved previous-Store editor state into Store Two', async () => {
    await mount(1)
    await act(async () => view!.root.findByProps({ 'aria-label': 'Print alias extra_noodle' }).props.onChange({ target: { value: 'Unsaved Store One' } }))
    let resolveStoreTwo!: (value: StoreContextResponse) => void
    vi.mocked(fetchStoreContext).mockImplementation(() => new Promise((resolve) => { resolveStoreTwo = resolve }))
    await act(async () => view!.update(<PrintingRulesMenuPanel storeId={2} onToast={onToast} />))
    expect(editor()).toHaveLength(0)
    expect(JSON.stringify(view!.toJSON())).not.toContain('Unsaved Store One')
    await act(async () => resolveStoreTwo(context(2)))
    expect(editor()[0].props.storeId).toBe(2)
    expect(view!.root.findByProps({ 'aria-label': 'Print alias extra_noodle' }).props.value).toBe('Store 2 alias')
  })

  it('removes the editor when printing permission is revoked while access is still loading', async () => {
    let finish!: (value: StoreContextResponse) => void
    vi.mocked(fetchStoreContext).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    await mount()
    access.permissions = ['ADMIN_MENU_MANAGE']
    await act(async () => view!.update(<PrintingRulesMenuPanel storeId={2} onToast={onToast} />))
    expect(editor()).toHaveLength(0)
    expect(view!.root.findAllByProps({ role: 'status' })).toHaveLength(0)
    await act(async () => finish(context(2)))
    expect(editor()).toHaveLength(0)
    expect(fetchPrintingDisplayRules).not.toHaveBeenCalled()
  })

  it.each(['ADMIN_PRINTING_MANAGE', 'ADMIN_STORE_CONFIG'])('reuses the original Save, Publish and Preview actions with permission %s', async (permission) => {
    access.permissions = [permission]
    await mount()
    expect(editor()).toHaveLength(1)
    expect(editor()[0].props.storeId).toBe(2)
    await act(async () => button('Save Draft').props.onClick())
    expect(savePrintingDisplayRuleDraft).toHaveBeenCalledWith(2, expect.objectContaining({ schema_version: 'PRINTING_DISPLAY_RULES_V1' }), expect.any(String))
    await act(async () => button('Publish Draft').props.onClick())
    expect(publishPrintingDisplayRuleDraft).toHaveBeenCalledWith(2, 20)
    const previewButton = view!.root.findAllByType('button').find((node) => node.children.join('') === 'Generate Preview')!
    await act(async () => previewButton.props.onClick())
    expect(previewPrintingDisplayRules).toHaveBeenCalledWith(expect.objectContaining({ store_id: 2 }))
  })
})
