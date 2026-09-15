import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './apiClient'
import { createStoreAddon, fetchItemAddons, fetchStoreAddons, updateItemAddon, updateStoreAddon } from './ownerAddonService'
import { updateOwnerMenuItemComboEggDefault } from './ownerMenuOptionService'

vi.mock('./apiClient', () => ({ apiRequest: vi.fn() }))

describe('menu simplification API contracts', () => {
  beforeEach(() => vi.clearAllMocks())
  it('uses Store catalog and item eligibility scopes independently', async () => {
    await fetchStoreAddons(7)
    await fetchItemAddons(123)
    await updateItemAddon(123, 5, false)
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/v1/admin/menu/addons?store_id=7')
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/v1/admin/menu/items/123/addons')
    expect(apiRequest).toHaveBeenNthCalledWith(3, '/api/v1/admin/menu/items/123/addons/5', { method: 'PUT', body: '{"enabled":false}' })
  })
  it('sends central values, with code supplied only on creation', async () => {
    const values = { name_zh: '加牛肉', name_en: 'Extra Beef', price: 3.5, active: false }
    await createStoreAddon({ ...values, store_id: 7, code: 'extra_beef' })
    await updateStoreAddon(5, values)
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/v1/admin/menu/addons', {
      method: 'POST', body: JSON.stringify({ ...values, store_id: 7, code: 'extra_beef' }),
    })
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/v1/admin/menu/addons/5', { method: 'PUT', body: JSON.stringify(values) })
  })
  it('removes a Combo egg exception by explicit null', async () => {
    await updateOwnerMenuItemComboEggDefault(123, 'combo_fried_egg')
    await updateOwnerMenuItemComboEggDefault(123, null)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/admin/menu/items/123/combo-egg-default', {
      method: 'PUT', body: '{"default_combo_egg_component_code":null}',
    })
  })
})
