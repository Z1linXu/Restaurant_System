import { beforeEach, expect, it, vi } from 'vitest'
import { apiRequest } from './apiClient'
import { applyCategoryBasePrices, fetchStorePricingCapabilities, previewCategoryBasePrices, type CategoryPriceApplyRequest } from './ownerMenuPricingService'

vi.mock('./apiClient', () => ({ apiRequest: vi.fn() }))
beforeEach(() => vi.clearAllMocks())

it('uses explicit Store scope, fixed CAD amounts and server preview identity', async () => {
  const adjustment = { store_id: 18, category_ids: [11, 12], direction: 'DECREASE' as const, amount: '1.25', currency: 'CAD' as const, include_inactive: false }
  const save: CategoryPriceApplyRequest = { ...adjustment, preview_id: 'preview-1', expected_revision: 94, fingerprint: 'fingerprint', idempotency_key: 'stable-save' }
  await fetchStorePricingCapabilities(18)
  await previewCategoryBasePrices(adjustment)
  await applyCategoryBasePrices(save)
  await applyCategoryBasePrices(save)
  expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/v1/admin/menu/pricing-capabilities?store_id=18')
  expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/v1/admin/menu/category-pricing/preview', { method: 'POST', body: JSON.stringify(adjustment) })
  expect(apiRequest).toHaveBeenNthCalledWith(3, '/api/v1/admin/menu/category-pricing/apply', { method: 'POST', body: JSON.stringify(save) })
  expect(vi.mocked(apiRequest).mock.calls[2]).toEqual(vi.mocked(apiRequest).mock.calls[3])
})
