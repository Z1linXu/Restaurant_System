import { apiRequest } from './apiClient'

export interface StorePricingCapabilities {
  store_id: number
  category_bulk_enabled: boolean
  standard_size_enabled: boolean
}

export interface CategoryPriceAdjustmentRequest {
  store_id: number
  category_ids: number[]
  direction: 'INCREASE' | 'DECREASE'
  amount: string
  currency: 'CAD'
  include_inactive: boolean
}

export interface CategoryPriceChange {
  item_id: number
  category_id: number
  sku: string | null
  name_zh: string | null
  name_en: string | null
  name_fr: string | null
  old_base_price: string | number
  new_base_price: string | number
}

export interface CategoryPriceExcludedItem extends Omit<CategoryPriceChange, 'old_base_price' | 'new_base_price'> {
  reason: 'REFILL_ITEM' | 'FIXED_OFFER' | 'INACTIVE_ITEM'
}

export interface CategoryPricePreview extends Omit<CategoryPriceAdjustmentRequest, 'amount'> {
  amount: string | number
  preview_id: string
  menu_revision: number
  fingerprint: string
  expires_at: string
  affected_item_count: number
  excluded_item_count: number
  items: CategoryPriceChange[]
  excluded_items: CategoryPriceExcludedItem[]
}

export interface CategoryPriceApplyRequest extends CategoryPriceAdjustmentRequest {
  preview_id: string
  expected_revision: number
  fingerprint: string
  idempotency_key: string
}

export interface CategoryPriceApplyResult extends Omit<CategoryPricePreview, 'expires_at' | 'fingerprint'> {
  before_revision: number
  before_fingerprint: string
  idempotency_key: string
}

const base = '/api/v1/admin/menu'

export function fetchStorePricingCapabilities(storeId: number) {
  return apiRequest<StorePricingCapabilities>(`${base}/pricing-capabilities?store_id=${storeId}`)
}

export function previewCategoryBasePrices(payload: CategoryPriceAdjustmentRequest) {
  return apiRequest<CategoryPricePreview>(`${base}/category-pricing/preview`, {
    method: 'POST', body: JSON.stringify(payload),
  })
}

export function applyCategoryBasePrices(payload: CategoryPriceApplyRequest) {
  return apiRequest<CategoryPriceApplyResult>(`${base}/category-pricing/apply`, {
    method: 'POST', body: JSON.stringify(payload),
  })
}
