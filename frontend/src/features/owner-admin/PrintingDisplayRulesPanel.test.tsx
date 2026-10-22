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

it('shows eighteen shared Remove rows, resolves legacy aliases and preserves unrelated published customizations', async () => {
  const defaults: Record<string, string> = {
    cilantro: '走香', green_onion: '走葱', beef: '走牛', radish: '走萝', noodle: '走面', less_noodle: '少面',
    bok_choy: '走上海青', broccoli: '走西兰', corn: '走玉米', mushroom: '走菇', seaweed: '走海', carrot: '走胡',
    cucumber: '走黄瓜', edamame: '走毛豆', peanut: '走花生', cabbage: '走包', meat: '走肉', green_pepper: '走青椒',
  }
  const content = { schema_version: 'PRINTING_DISPLAY_RULES_V1', dictionaries: {
    MODIFIER_REMOVE: [['remove_beef', '牛自定'], ['meat', '肉自定'], ['carrot_slice', '旧片别名']],
    MODIFIER_ADD: [['egg', '+旧蛋']],
    SPICINESS: [{ semantic_code: 'NO_SPICY', outputs: { GRAB: '（走s）', HOT_KITCHEN: '（走s）', FRONTDESK_RECEIPT: '不辣' } }],
  } }
  vi.mocked(fetchPrintingDisplayRules).mockResolvedValue({ store_id: 2, rule_set_id: 2, revisions: [],
    active_revision: { id: 10, revision_number: 6, status: 'PUBLISHED', schema_version: 'PRINTING_DISPLAY_RULES_V1', fingerprint_sha256: 'fixture', content },
    remove_aliases: Object.entries(defaults).map(([code, text]) => ({ code, name_zh: text, default_print_text: text, source: 'MENU', alias_codes: [code, `remove_${code}`] })),
  } as Awaited<ReturnType<typeof fetchPrintingDisplayRules>>)
  vi.mocked(savePrintingDisplayRuleDraft).mockResolvedValue({ id: 11, revision_number: 7, status: 'DRAFT' } as Awaited<ReturnType<typeof savePrintingDisplayRuleDraft>>)
  await act(async () => { view = create(<PrintingDisplayRulesPanel storeId={2} />) })
  const section = view.root.findByProps({ 'aria-label': 'Remove print aliases' })
  expect(section.findAllByType('input')).toHaveLength(18)
  expect(section.findAllByType('button')).toHaveLength(18)
  expect(section.findByProps({ 'aria-label': 'Remove print alias beef' }).props.value).toBe('牛自定')
  expect(section.findByProps({ 'aria-label': 'Remove print alias meat' }).props.value).toBe('肉自定')
  expect(section.findAllByType('input').every(input => input.props['aria-label'].startsWith('Remove print alias '))).toBe(true)
  const diagnostic = section.findByProps({ 'aria-label': 'Legacy Remove alias diagnostics' })
  expect(diagnostic.props.open).toBeUndefined()
  expect(diagnostic.findAllByType('code').map(code => code.children.join(''))).toContain('carrot_slice')
  await act(async () => section.findByProps({ 'aria-label': 'Remove print alias beef' }).props.onChange({ target: { value: '牛新别名' } }))
  await act(async () => section.findAllByType('button')[16].props.onClick())
  await act(async () => view.root.findAllByType('button').find(b => b.children.join('') === 'Save Draft')!.props.onClick())
  const saved = vi.mocked(savePrintingDisplayRuleDraft).mock.calls[0][1] as typeof content
  expect(saved.dictionaries.MODIFIER_REMOVE).toContainEqual(['beef', '牛新别名'])
  expect(saved.dictionaries.MODIFIER_REMOVE).not.toContainEqual(['remove_beef', '牛自定'])
  expect(saved.dictionaries.MODIFIER_REMOVE).toContainEqual(['meat', null])
  expect(saved.dictionaries.MODIFIER_REMOVE).toContainEqual(['carrot_slice', '旧片别名'])
  expect(saved.dictionaries.SPICINESS).toEqual(content.dictionaries.SPICINESS)
  expect(saved.dictionaries.MODIFIER_ADD).toEqual(content.dictionaries.MODIFIER_ADD)
  expect(vi.mocked(savePrintingDisplayRuleDraft).mock.calls[0][0]).toBe(2)
})

it('keeps conflicting Remove aliases until explicit edit and automatically shows a new shared master', async () => {
  const content = { dictionaries: { MODIFIER_REMOVE: [['beef', '牛A'], ['remove_beef', '牛B'], ['cucumber_shred', '丝旧值']] } }
  vi.mocked(fetchPrintingDisplayRules).mockResolvedValue({ store_id: 1, rule_set_id: 1, revisions: [],
    active_revision: { id: 5, revision_number: 2, status: 'PUBLISHED', schema_version: 'PRINTING_DISPLAY_RULES_V1', fingerprint_sha256: 'fixture', content },
    remove_aliases: ['beef', 'tofu'].map(code => ({ code, name_zh: code, default_print_text: code, source: 'MENU', alias_codes: [code, `remove_${code}`] })),
  } as Awaited<ReturnType<typeof fetchPrintingDisplayRules>>)
  vi.mocked(savePrintingDisplayRuleDraft).mockResolvedValue({ id: 9, revision_number: 3, status: 'DRAFT' } as Awaited<ReturnType<typeof savePrintingDisplayRuleDraft>>)
  await act(async () => { view = create(<PrintingDisplayRulesPanel storeId={1} />) })
  const section = view.root.findByProps({ 'aria-label': 'Remove print aliases' })
  expect(section.findAllByType('input')).toHaveLength(2)
  expect(section.findAllByProps({ role: 'alert' })).toHaveLength(1)
  expect(section.findByProps({ 'aria-label': 'Remove print alias tofu' }).props.value).toBe('')
  await act(async () => view.root.findAllByType('button').find(b => b.children.join('') === 'Save Draft')!.props.onClick())
  expect((vi.mocked(savePrintingDisplayRuleDraft).mock.calls[0][1] as typeof content).dictionaries.MODIFIER_REMOVE).toEqual(content.dictionaries.MODIFIER_REMOVE)
  await act(async () => section.findByProps({ 'aria-label': 'Remove print alias beef' }).props.onChange({ target: { value: '确认牛' } }))
  await act(async () => view.root.findAllByType('button').find(b => b.children.join('') === 'Save Draft')!.props.onClick())
  const saved = vi.mocked(savePrintingDisplayRuleDraft).mock.calls[1][1] as typeof content
  expect(saved.dictionaries.MODIFIER_REMOVE).toEqual([['cucumber_shred', '丝旧值'], ['beef', '确认牛']])
})
