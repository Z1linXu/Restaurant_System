import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ReceiptLanguagePanel } from './ReceiptLanguagePanel'
import { fetchReceiptPresentation, saveReceiptPresentation, type ReceiptPresentationSettings } from '../../services/printingAdminService'
import { appI18n } from '../../i18n/engine'

vi.mock('../../services/printingAdminService', () => ({ fetchReceiptPresentation: vi.fn(), saveReceiptPresentation: vi.fn() }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
let view: ReactTestRenderer | undefined
const original: ReceiptPresentationSettings = { enabled: true, default_language: 'zh-CN', kitchen_language: null, frontdesk_language: 'en', kitchen_station_codes: ['BACK'], revision: 7 }
beforeEach(async () => { vi.clearAllMocks(); await appI18n.changeLanguage('en'); vi.mocked(fetchReceiptPresentation).mockResolvedValue(original) })
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })

it('saves real language overrides while preserving server-owned station codes and revision', async () => {
  vi.mocked(saveReceiptPresentation).mockImplementation(async (_storeId, request) => ({ ...request, revision: 8 }))
  await act(async () => { view = create(<ReceiptLanguagePanel storeId={12} canManage />) })
  const kitchen = view!.root.findAllByType('select').find((node) => node.props['aria-label'] === 'Kitchen tickets')!
  await act(async () => kitchen.props.onChange({ target: { value: 'fr-CA' } }))
  await act(async () => view!.root.findByType('button').props.onClick())
  expect(saveReceiptPresentation).toHaveBeenCalledWith(12, { ...original, kitchen_language: 'fr-CA' })
  expect(JSON.stringify(view!.toJSON())).toContain('Existing print jobs were not changed')
  expect(original.kitchen_language).toBeNull()
})

it('keeps an unsaved receipt choice when switching the independent app language', async () => {
  await act(async () => { view = create(<ReceiptLanguagePanel storeId={12} canManage />) })
  await act(async () => view!.root.findAllByType('select')[0].props.onChange({ target: { value: 'fr-CA' } }))
  await act(async () => { await appI18n.changeLanguage('zh-CN') })
  expect(fetchReceiptPresentation).toHaveBeenCalledTimes(1)
  expect(view!.root.findAllByType('select')[0].props.value).toBe('fr-CA')
  expect(saveReceiptPresentation).not.toHaveBeenCalled()
})

it('does not activate or write legacy disabled settings', async () => {
  vi.mocked(fetchReceiptPresentation).mockResolvedValue({ ...original, enabled: false })
  await act(async () => { view = create(<ReceiptLanguagePanel storeId={2} canManage />) })
  expect(view!.root.findAllByType('select')).toHaveLength(0)
  expect(view!.root.findAllByType('button')).toHaveLength(0)
  expect(saveReceiptPresentation).not.toHaveBeenCalled()
  expect(JSON.stringify(view!.toJSON())).toContain('existing format is preserved')
})

it('shows failed saves honestly and offers no management write to frontdesk viewers', async () => {
  vi.mocked(saveReceiptPresentation).mockRejectedValue(new Error('Rejected by server'))
  await act(async () => { view = create(<ReceiptLanguagePanel storeId={12} canManage />) })
  await act(async () => view!.root.findByType('button').props.onClick())
  expect(view!.root.findByProps({ role: 'alert' }).children).toContain('Rejected by server')
  expect(view!.root.findAllByProps({ role: 'status' })).toHaveLength(0)
  await act(async () => view!.update(<ReceiptLanguagePanel storeId={12} canManage={false} />))
  expect(view!.root.findAllByType('button')).toHaveLength(0)
  expect(view!.root.findAllByType('select').every((node) => node.props.disabled)).toBe(true)
})
