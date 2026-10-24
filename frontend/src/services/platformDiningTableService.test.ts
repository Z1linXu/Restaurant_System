import { expect, it, vi } from 'vitest'
import { apiRequest } from './apiClient'
import { deleteDiningTable } from './platformAdminService'
vi.mock('./apiClient', () => ({ apiRequest: vi.fn() }))
it('deletes only the exact table under its explicit Store context', async () => {
  const tombstone = { id: 12, store_id: 2, is_active: false }
  vi.mocked(apiRequest).mockResolvedValue(tombstone)
  expect(await deleteDiningTable(2, 12)).toBe(tombstone)
  expect(apiRequest).toHaveBeenCalledWith('/api/v1/admin/platform/dining-tables/12?store_id=2', { method: 'DELETE' })
})
