import { expect, it, vi } from 'vitest'
import { apiRequest } from './apiClient'
import { createStoreRemove, fetchStoreRemoves, updateRemoveApplicability, updateStoreRemove } from './ownerRemoveService'
vi.mock('./apiClient', () => ({ apiRequest: vi.fn() }))

it('uses explicit Store scope and never sends Add-on price fields', async () => {
  await fetchStoreRemoves(18)
  const values = { name_zh: '走葱', name_en: 'No onion', active: true, sort_order: 10 }
  await createStoreRemove({ ...values, store_id: 18, code: 'remove_green_onion' })
  await updateStoreRemove(7, 18, values)
  await updateRemoveApplicability(7, 18, { store_id: 18, remove_id: 7, category_ids: [2], items: [{ item_id: 3, mode: 'DENY', effective: false, category_id: 2, name_zh: '面', name_en: 'Noodle' }] })
  expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/v1/admin/menu/removes?store_id=18')
  expect(apiRequest).toHaveBeenNthCalledWith(3, '/api/v1/admin/menu/removes/7?store_id=18', { method: 'PUT', body: JSON.stringify({ ...values, store_id: 18 }) })
  expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/admin/menu/removes/7/applicability?store_id=18', { method: 'PUT', body: JSON.stringify({ store_id: 18, category_ids: [2], items: [{ item_id: 3, mode: 'DENY' }] }) })
  expect(vi.mocked(apiRequest).mock.calls.every(([, options]) => !options?.body?.toString().includes('price'))).toBe(true)
})
