import type { BackendMenuCatalogResponse } from '../types/ordering'

const DEFAULT_STORE_ID = '1'
const DEFAULT_USER_ID = '1'

export async function fetchMenuCatalog(storeId = DEFAULT_STORE_ID, userId = DEFAULT_USER_ID) {
  const response = await fetch(`/api/v1/menu/catalog?store_id=${storeId}`, {
    headers: {
      'X-User-Id': userId,
    },
  })

  if (!response.ok) {
    throw new Error(`Failed to load menu catalog (${response.status})`)
  }

  const payload = (await response.json()) as BackendMenuCatalogResponse
  if (!payload.success) {
    throw new Error(payload.message || 'Failed to load menu catalog')
  }

  return payload.data
}
