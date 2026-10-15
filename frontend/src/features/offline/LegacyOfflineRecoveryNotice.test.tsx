import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LegacyOfflineRecoveryNotice } from './LegacyOfflineRecoveryNotice'
import { hasLegacyOfflineOrders } from '../../offline/legacyOfflineRecords'
vi.mock('../../offline/legacyOfflineRecords', () => ({ hasLegacyOfflineOrders: vi.fn() }))
let view: ReactTestRenderer | undefined
afterEach(async () => { if (view) await act(async () => view!.unmount()); vi.unstubAllGlobals() })
describe('unknown-provenance local order recovery notice', () => {
  it.each([true, false])('retained=%s gives visible recovery instructions without exposing another merchant order', async (retained) => {
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
    vi.mocked(hasLegacyOfflineOrders).mockResolvedValue(retained)
    await act(async () => { view = create(<LegacyOfflineRecoveryNotice />) })
    if (retained) {
      const text = JSON.stringify(view!.toJSON())
      expect(text).toContain('旧版本机订单已安全保留'); expect(text).toContain('核对原服务器'); expect(text).toContain('请勿清理应用数据')
      expect(text).not.toContain('clientOrderId')
    } else expect(view!.toJSON()).toBeNull()
  })
})
