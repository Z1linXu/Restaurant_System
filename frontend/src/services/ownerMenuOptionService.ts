import { apiRequest } from './apiClient'

export type MenuOptionGroup =
  | 'SIZE'
  | 'SOUP_BASE'
  | 'NOODLE_TYPE'
  | 'SPICY_LEVEL'
  | 'ADD_ON'
  | 'REMOVE'
  | 'COMBO'
  | 'COMBO_EGG'
  | 'COMBO_SIDE'
  | 'COMBO_SIDE_REMOVE'

export interface MenuItemOptionAdminRecord {
  id: number
  menu_item_id: number
  option_type: string
  option_code: string | null
  option_group: MenuOptionGroup | string | null
  parent_option_id: number | null
  sort_order: number | null
  name_zh: string
  name_en: string
  price_delta: number
  is_active: boolean
  created_at?: string
  updated_at?: string
}

export interface MenuItemOptionPayload {
  option_type: string
  option_code: string | null
  option_group: MenuOptionGroup | string | null
  parent_option_id: number | null
  sort_order: number | null
  name_zh: string
  name_en: string
  price_delta: number | string
  is_active: boolean
}

export type StandardSizeCode = 'size_small' | 'size_regular' | 'size_large'

export interface StorePricingPolicyRecord {
  store_id: number
  policy_revision: number
  size_small_delta: number
  size_regular_delta: number
  size_large_delta: number
  combo_delta: number
}

export interface StorePricingPolicyPayload {
  store_id: number
  size_small_delta?: string | number | null
  size_regular_delta?: string | number | null
  size_large_delta?: string | number | null
  combo_delta?: string | number | null
}

export interface StorePricingPolicyPreviewImpactItem {
  item_id: number
  sku: string | null
  name_zh: string | null
  name_en: string | null
  old_price: number
  new_price: number
}

export interface StorePricingPolicyPreviewImpactGroup {
  policy_key: string
  old_delta: number
  new_delta: number
  affected_item_count: number
  sample_items: StorePricingPolicyPreviewImpactItem[]
}

export interface StorePricingPolicyPreviewRecord {
  store_id: number
  current_policy: StorePricingPolicyRecord
  proposed_policy: StorePricingPolicyRecord
  impact_groups: StorePricingPolicyPreviewImpactGroup[]
}

export interface MenuItemSizeConfigurationPayload {
  enabled_size_codes: StandardSizeCode[]
  default_size_code?: StandardSizeCode | null
}

export interface MenuItemComboPolicyPayload {
  combo_allowed: boolean
}

const request = apiRequest

export function fetchOwnerMenuItemOptions(itemId: number) {
  return request<MenuItemOptionAdminRecord[]>(`/api/v1/admin/menu/items/${itemId}/options`)
}

export function createOwnerMenuItemOption(itemId: number, payload: MenuItemOptionPayload) {
  return request<MenuItemOptionAdminRecord>(`/api/v1/admin/menu/items/${itemId}/options`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateOwnerMenuItemOption(itemId: number, optionId: number, payload: MenuItemOptionPayload) {
  return request<MenuItemOptionAdminRecord>(`/api/v1/admin/menu/items/${itemId}/options/${optionId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deactivateOwnerMenuItemOption(itemId: number, optionId: number) {
  return request<MenuItemOptionAdminRecord>(`/api/v1/admin/menu/items/${itemId}/options/${optionId}`, {
    method: 'DELETE',
  })
}

export function reorderOwnerMenuItemOptions(
  itemId: number,
  options: Array<{ id: number; sort_order: number }>,
) {
  return request<MenuItemOptionAdminRecord[]>(`/api/v1/admin/menu/items/${itemId}/options/reorder`, {
    method: 'PUT',
    body: JSON.stringify({ options }),
  })
}

export function fetchStorePricingPolicy(storeId: number) {
  return request<StorePricingPolicyRecord>(`/api/v1/admin/menu/pricing-policy?store_id=${storeId}`)
}

export function previewStorePricingPolicy(payload: StorePricingPolicyPayload) {
  return request<StorePricingPolicyPreviewRecord>('/api/v1/admin/menu/pricing-policy/preview', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateStorePricingPolicy(payload: StorePricingPolicyPayload) {
  return request<StorePricingPolicyRecord>('/api/v1/admin/menu/pricing-policy', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateOwnerMenuItemSizeConfiguration(
  itemId: number,
  payload: MenuItemSizeConfigurationPayload,
) {
  return request<MenuItemOptionAdminRecord[]>(`/api/v1/admin/menu/items/${itemId}/size-configuration`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateOwnerMenuItemComboPolicy(
  itemId: number,
  payload: MenuItemComboPolicyPayload,
) {
  return request<MenuItemOptionAdminRecord[]>(`/api/v1/admin/menu/items/${itemId}/combo-policy`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}
