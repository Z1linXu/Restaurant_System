import { apiRequest } from './apiClient'
import type { ItemCustomizationConfiguration } from '../types/ordering'

export function fetchItemCustomization(itemId: number) {
  return apiRequest<ItemCustomizationConfiguration | null>(`/api/v1/admin/menu/items/${itemId}/customization`)
}

export function updateItemCustomization(itemId: number, configuration: ItemCustomizationConfiguration) {
  return apiRequest<ItemCustomizationConfiguration>(`/api/v1/admin/menu/items/${itemId}/customization`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(configuration),
  })
}
