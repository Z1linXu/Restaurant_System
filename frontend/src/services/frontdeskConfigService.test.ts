import { beforeEach, expect, it, vi } from 'vitest'
import { apiRequest } from './apiClient'
import { requireActiveDiningTable } from './frontdeskConfigService'
import type { BackendDiningTableConfig } from '../types/dinein'
vi.mock('./apiClient', () => ({ apiRequest: vi.fn() }))
const table = (name = '1-左', code = 'T1'): BackendDiningTableConfig => ({ id: 1, store_id: 2, table_name: name, table_code: code, area_name: 'Main', table_config: 'split_supported', capacity: 4, supports_split: true, sort_order: 1, is_active: true })
beforeEach(() => { vi.resetAllMocks(); vi.stubGlobal('navigator', { onLine: true }) })
it('freshly validates full names, complete split prefixes and a unique legacy code without changing raw identities', async () => {
  vi.mocked(apiRequest).mockResolvedValue([table()])
  for (const raw of ['1-左', '1-左-A', '1-左-B', 'T1', 'T1-A']) expect(await requireActiveDiningTable(2, raw)).toEqual(table())
  expect(apiRequest).toHaveBeenCalledTimes(5)
  expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/frontdesk/dining-tables?store_id=2', { cache: 'no-store' })
})
it('prefers a literal full name ending in A over an assumed seat', async () => {
  const literal = { ...table('1-A', 'T2'), id: 2 }
  vi.mocked(apiRequest).mockResolvedValue([table('1'), literal])
  expect(await requireActiveDiningTable(2, '1-A')).toBe(literal)
})
it('rejects deleted, unknown, cross-Store and ambiguous contexts even if display caches could contain active=true', async () => {
  vi.mocked(apiRequest).mockResolvedValue([{ ...table(), is_active: false }, { ...table(), store_id: 1 }])
  await expect(requireActiveDiningTable(2, '1-左-A')).rejects.toThrow('已删除或不可用')
  vi.mocked(apiRequest).mockResolvedValue([table(), { ...table(), id: 2 }])
  await expect(requireActiveDiningTable(2, '1-左-A')).rejects.toThrow('身份不明确')
  vi.mocked(apiRequest).mockResolvedValue([])
  await expect(requireActiveDiningTable(2, 'old-A')).rejects.toThrow('草稿已保留')
})
it('fails closed offline or on request failure without any cache fallback', async () => {
  vi.stubGlobal('navigator', { onLine: false })
  await expect(requireActiveDiningTable(2, '1-左-A')).rejects.toThrow('联网后重试')
  expect(apiRequest).not.toHaveBeenCalled()
  vi.stubGlobal('navigator', { onLine: true })
  vi.mocked(apiRequest).mockRejectedValue(new Error('network unavailable'))
  await expect(requireActiveDiningTable(2, '1-左-A')).rejects.toThrow('检查网络')
})
