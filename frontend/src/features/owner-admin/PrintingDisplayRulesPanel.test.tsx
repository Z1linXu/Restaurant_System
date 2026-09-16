import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, expect, it, vi } from 'vitest'
import { PrintingDisplayRulesPanel } from './PrintingDisplayRulesPanel'
import { fetchPrintingDisplayRules, savePrintingDisplayRuleDraft } from '../../services/printingAdminService'

vi.mock('../../services/printingAdminService', () => ({
  fetchPrintingDisplayRules: vi.fn(), savePrintingDisplayRuleDraft: vi.fn(),
  previewPrintingDisplayRules: vi.fn(), publishPrintingDisplayRuleDraft: vi.fn(), validatePrintingDisplayRules: vi.fn(),
}))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
let view: ReactTestRenderer
afterEach(async () => { if (view) await act(async () => view.unmount()); vi.resetAllMocks() })

it('derives Menu and Combo rows with alias-only editing, preserves explicit vocabulary and resets to null', async () => {
  const content = { schema_version: 'PRINTING_DISPLAY_RULES_V1', dictionaries: {
    MODIFIER_ADD: [['extra_noodle', '+面面'], ['fried_egg', '+煎'], ['combo_fried_egg', '+煎']],
  } }
  vi.mocked(fetchPrintingDisplayRules).mockResolvedValue({ store_id: 1, rule_set_id: 1, revisions: [],
    active_revision: { id: 1, revision_number: 1, status: 'PUBLISHED', schema_version: 'PRINTING_DISPLAY_RULES_V1', fingerprint_sha256: 'fixture', content },
    addon_aliases: ['extra_noodle', 'fried_egg', 'combo_fried_egg', 'extra_cheese'].map(code => ({
      code, name_zh: code === 'extra_cheese' ? '加芝士' : code, default_print_text: code === 'extra_cheese' ? '加芝士' : code, source: 'MENU',
    })),
  } as Awaited<ReturnType<typeof fetchPrintingDisplayRules>>)
  vi.mocked(savePrintingDisplayRuleDraft).mockResolvedValue({ id: 2, revision_number: 2, status: 'DRAFT' } as Awaited<ReturnType<typeof savePrintingDisplayRuleDraft>>)
  await act(async () => { view = create(<PrintingDisplayRulesPanel storeId={1} />) })
  const section = view.root.findByProps({ 'aria-label': 'Add-on print aliases' })
  expect(section.findAllByType('input')).toHaveLength(4)
  expect(section.findAllByType('button').every(b => b.children.join('') === 'Reset to Default / 恢复默认')).toBe(true)
  expect(section.findByProps({ 'aria-label': 'Print alias extra_noodle' }).props.value).toBe('+面面')
  expect(section.findByProps({ 'aria-label': 'Print alias extra_cheese' }).props.value).toBe('')
  await act(async () => section.findByProps({ 'aria-label': 'Print alias extra_cheese' }).props.onChange({ target: { value: '+芝' } }))
  await act(async () => section.findAllByType('button')[1].props.onClick())
  await act(async () => view.root.findAllByType('button').find(b => b.children.join('') === 'Save Draft')!.props.onClick())
  const saved = vi.mocked(savePrintingDisplayRuleDraft).mock.calls[0][1] as typeof content
  expect(saved.dictionaries.MODIFIER_ADD).toContainEqual(['extra_noodle', '+面面'])
  expect(saved.dictionaries.MODIFIER_ADD).toContainEqual(['fried_egg', null])
  expect(saved.dictionaries.MODIFIER_ADD).toContainEqual(['combo_fried_egg', '+煎'])
  expect(saved.dictionaries.MODIFIER_ADD).toContainEqual(['extra_cheese', '+芝'])
})
