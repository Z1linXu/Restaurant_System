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
  price_delta: number
  is_active: boolean
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
