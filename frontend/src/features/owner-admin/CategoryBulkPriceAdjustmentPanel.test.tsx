import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { appI18n } from '../../i18n/engine'
import { ApiRequestError } from '../../services/apiClient'
import { applyCategoryBasePrices, fetchStorePricingCapabilities, previewCategoryBasePrices, type CategoryPricePreview, type CategoryPriceApplyResult } from '../../services/ownerMenuPricingService'
import { CategoryBulkPriceAdjustmentPanel } from './CategoryBulkPriceAdjustmentPanel'

vi.mock('../../services/ownerMenuPricingService', () => ({ applyCategoryBasePrices: vi.fn(), fetchStorePricingCapabilities: vi.fn(), previewCategoryBasePrices: vi.fn() }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)

const categories = [{ id: 11, label: 'Noodles' }, { id: 12, label: 'Drinks' }]
const serverPreview: CategoryPricePreview = {
  store_id: 18, category_ids: [11, 12], direction: 'INCREASE', amount: '1.25', currency: 'CAD', include_inactive: false,
  preview_id: 'preview-id', menu_revision: 94, fingerprint: 'server-fingerprint', expires_at: '2026-11-12T12:10:00Z',
  affected_item_count: 2, excluded_item_count: 2,
  items: [
    { item_id: 101, category_id: 11, sku: 'B1', name_zh: '牛肉面', name_en: 'Beef noodles', name_fr: 'Nouilles au bœuf', old_base_price: '15.00', new_base_price: '16.25' },
    { item_id: 102, category_id: 12, sku: 'D1', name_zh: '茶', name_en: 'Tea', name_fr: 'Thé', old_base_price: '3.00', new_base_price: '4.25' },
  ],
  excluded_items: [
    { item_id: 103, category_id: 11, sku: 'R1', name_zh: '续面', name_en: 'Refill', name_fr: 'Remplissage', reason: 'REFILL_ITEM' },
    { item_id: 104, category_id: 11, sku: 'C1', name_zh: '固定套餐', name_en: 'Combo', name_fr: 'Combo', reason: 'FIXED_OFFER' },
  ],
}
const result: CategoryPriceApplyResult = { ...serverPreview, before_revision: 94, menu_revision: 95, before_fingerprint: 'server-fingerprint', idempotency_key: 'saved' }
let view: ReactTestRenderer | undefined
let saved = vi.fn<(message: string) => void>()

const button = (label: string) => view!.root.findAllByType('button').find((node) => node.children.join('') === label)!
async function mount(storeId = 18) { await act(async () => { view = create(<CategoryBulkPriceAdjustmentPanel storeId={storeId} categories={categories} onSaved={saved} />) }) }
async function prepare() {
  for (const id of [11, 12]) await act(async () => view!.root.findByProps({ 'aria-label': `Category ${id}` }).props.onChange({ target: { checked: true } }))
  await act(async () => view!.root.findByProps({ 'aria-label': 'Adjustment amount' }).props.onChange({ target: { value: '1.25' } }))
}
async function preview() { await act(async () => view!.root.findByType('form').props.onSubmit({ preventDefault: vi.fn() })) }

beforeEach(async () => {
  await appI18n.changeLanguage('en')
  vi.resetAllMocks()
  saved = vi.fn<(message: string) => void>()
  vi.mocked(fetchStorePricingCapabilities).mockImplementation(async (storeId) => ({ store_id: storeId, category_bulk_enabled: true, standard_size_enabled: false }))
  vi.mocked(previewCategoryBasePrices).mockResolvedValue(serverPreview)
  vi.mocked(applyCategoryBasePrices).mockResolvedValue(result)
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })

it('requires explicit capability and hides the adjustment form for standard or neutral Stores', async () => {
  vi.mocked(fetchStorePricingCapabilities).mockResolvedValue({ store_id: 18, category_bulk_enabled: false, standard_size_enabled: true })
  await mount()
  expect(view!.toJSON()).toBeNull()
  expect(previewCategoryBasePrices).not.toHaveBeenCalled()
})

it('previews real backend old/new prices for multiple categories and saves only after confirmation', async () => {
  await mount(); await prepare(); await preview()
  expect(previewCategoryBasePrices).toHaveBeenCalledWith({ store_id: 18, category_ids: [11, 12], direction: 'INCREASE', amount: '1.25', currency: 'CAD', include_inactive: false })
  expect(applyCategoryBasePrices).not.toHaveBeenCalled()
  const output = JSON.stringify(view!.toJSON())
  expect(output).toContain('$15.00'); expect(output).toContain('$16.25')
  expect(output).toContain('Free refill'); expect(output).toContain('Fixed offer')
  await act(async () => button('Confirm & save').props.onClick())
  expect(applyCategoryBasePrices).toHaveBeenCalledWith(expect.objectContaining({ preview_id: 'preview-id', expected_revision: 94, fingerprint: 'server-fingerprint', currency: 'CAD', idempotency_key: expect.any(String) }))
  expect(saved).toHaveBeenCalledWith('Category prices saved.')
  expect(view!.root.findAllByType('table')).toHaveLength(0)
})

it('invalidates the preview when amount, direction, categories or inactive inclusion changes', async () => {
  await mount(); await prepare(); await preview()
  await act(async () => view!.root.findByProps({ 'aria-label': 'Adjustment direction' }).props.onChange({ target: { value: 'DECREASE' } }))
  expect(view!.root.findAllByType('table')).toHaveLength(0)
  await act(async () => view!.root.findByProps({ 'aria-label': 'Include inactive items' }).props.onChange({ target: { checked: true } }))
  await preview()
  expect(previewCategoryBasePrices).toHaveBeenLastCalledWith(expect.objectContaining({ direction: 'DECREASE', include_inactive: true }))
  await act(async () => view!.root.findByProps({ 'aria-label': 'Category 12' }).props.onChange({ target: { checked: false } }))
  expect(view!.root.findAllByType('table')).toHaveLength(0)
  expect(applyCategoryBasePrices).not.toHaveBeenCalled()
})

it.each(['0', '-1', '1.001', '1%', 'NaN'])('rejects invalid fixed amounts %s without any request', async (amount) => {
  await mount(); await prepare()
  await act(async () => view!.root.findByProps({ 'aria-label': 'Adjustment amount' }).props.onChange({ target: { value: amount } }))
  await preview()
  expect(previewCategoryBasePrices).not.toHaveBeenCalled()
  expect(view!.root.findByProps({ role: 'alert' }).children.join('')).toContain('positive amount')
})

it('blocks same-tick repeated confirmation clicks while save is pending', async () => {
  let finish!: (value: CategoryPriceApplyResult) => void
  vi.mocked(applyCategoryBasePrices).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
  await mount(); await prepare(); await preview()
  const confirm = button('Confirm & save')
  await act(async () => { confirm.props.onClick(); confirm.props.onClick() })
  expect(applyCategoryBasePrices).toHaveBeenCalledTimes(1)
  expect(button('Saving…').props.disabled).toBe(true)
  await act(async () => finish(result))
  expect(saved).toHaveBeenCalledTimes(1)
})

it('keeps the same save identity after an unknown network outcome and freezes draft changes until replay succeeds', async () => {
  vi.mocked(applyCategoryBasePrices).mockRejectedValueOnce(new ApiRequestError(0, 'Network error')).mockResolvedValueOnce(result)
  await mount(); await prepare(); await preview()
  await act(async () => button('Confirm & save').props.onClick())
  const firstRequest = vi.mocked(applyCategoryBasePrices).mock.calls[0][0]
  expect(view!.root.findByProps({ 'aria-label': 'Adjustment amount' }).props.disabled).toBe(true)
  expect(view!.root.findByProps({ role: 'alert' }).children.join('')).toContain('same adjustment')
  await act(async () => button('Retry save').props.onClick())
  expect(vi.mocked(applyCategoryBasePrices).mock.calls[1][0]).toEqual(firstRequest)
  expect(saved).toHaveBeenCalledTimes(1)
})

it.each([500, 502, 504])('replays the same committed adjustment after HTTP %s without creating a second price change', async (status) => {
  const ledger = new Map<string, CategoryPriceApplyResult>()
  let committedAdjustments = 0
  let attempts = 0
  vi.mocked(applyCategoryBasePrices).mockImplementation(async (request) => {
    attempts += 1
    let committed = ledger.get(request.idempotency_key)
    if (!committed) {
      committedAdjustments += 1
      committed = { ...result, idempotency_key: request.idempotency_key }
      ledger.set(request.idempotency_key, committed)
    }
    if (attempts === 1) throw new ApiRequestError(status, 'Gateway response lost after commit')
    return committed
  })
  await mount(); await prepare(); await preview()
  await act(async () => button('Confirm & save').props.onClick())
  expect(committedAdjustments).toBe(1)
  expect(saved).not.toHaveBeenCalled()
  expect(view!.root.findByProps({ 'aria-label': 'Adjustment amount' }).props.disabled).toBe(true)
  expect(view!.root.findByProps({ 'aria-label': 'Adjustment direction' }).props.disabled).toBe(true)
  expect(button('Preview prices').props.disabled).toBe(true)
  await act(async () => button('Retry save').props.onClick())
  expect(vi.mocked(applyCategoryBasePrices).mock.calls[1][0]).toEqual(vi.mocked(applyCategoryBasePrices).mock.calls[0][0])
  expect(committedAdjustments).toBe(1)
  expect(previewCategoryBasePrices).toHaveBeenCalledTimes(1)
  expect(saved).toHaveBeenCalledTimes(1)
})

it('requires a fresh preview after an expired or changed menu response', async () => {
  vi.mocked(applyCategoryBasePrices).mockRejectedValue(new ApiRequestError(409, 'Preview expired', undefined, undefined, 'CATEGORY_PRICING_PREVIEW_EXPIRED'))
  await mount(); await prepare(); await preview()
  await act(async () => button('Confirm & save').props.onClick())
  expect(view!.root.findByProps({ role: 'alert' }).children.join('')).toContain('Preview the prices again')
  expect(view!.root.findAllByType('table')).toHaveLength(0)
  expect(saved).not.toHaveBeenCalled()
})

it('preserves selected categories and the same preview when changing interface language', async () => {
  await mount(); await prepare(); await preview()
  await act(async () => { await appI18n.changeLanguage('fr-CA') })
  expect(view!.root.findByType('h2').children).toEqual(['Prix par catégorie'])
  expect(view!.root.findByProps({ 'aria-label': 'Category 11' }).props.checked).toBe(true)
  expect(JSON.stringify(view!.toJSON())).toContain('Nouilles au bœuf')
  await act(async () => { await appI18n.changeLanguage('zh-CN') })
  expect(view!.root.findByType('h2').children).toEqual(['分类调价'])
  expect(previewCategoryBasePrices).toHaveBeenCalledTimes(1)
  expect(applyCategoryBasePrices).not.toHaveBeenCalled()
})

it('discards late previous-Store preview data and cannot save it in the new Store', async () => {
  let finish!: (value: CategoryPricePreview) => void
  vi.mocked(previewCategoryBasePrices).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
  await mount(); await prepare(); await preview()
  await act(async () => view!.update(<CategoryBulkPriceAdjustmentPanel storeId={27} categories={categories} onSaved={saved} />))
  await act(async () => finish(serverPreview))
  expect(view!.root.findAllByType('table')).toHaveLength(0)
  expect(view!.root.findByProps({ 'aria-label': 'Category 11' }).props.checked).toBe(false)
  expect(applyCategoryBasePrices).not.toHaveBeenCalled()
  expect(fetchStorePricingCapabilities).toHaveBeenLastCalledWith(27)
})

it('allows retry after capability loading fails without exposing an enabled adjustment form', async () => {
  vi.mocked(fetchStorePricingCapabilities).mockRejectedValueOnce(new Error('unavailable'))
  await mount()
  expect(view!.root.findAllByType('form')).toHaveLength(0)
  await act(async () => button('Retry').props.onClick())
  expect(view!.root.findAllByType('form')).toHaveLength(1)
})
