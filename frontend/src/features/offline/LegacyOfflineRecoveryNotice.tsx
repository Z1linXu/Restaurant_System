import { useEffect, useState } from 'react'
import { hasLegacyOfflineOrders } from '../../offline/legacyOfflineRecords'

export function LegacyOfflineRecoveryNotice() {
  const [retained, setRetained] = useState(false)
  useEffect(() => {
    let active = true
    void hasLegacyOfflineOrders().then((value) => { if (active) setRetained(value) }).catch(() => undefined)
    return () => { active = false }
  }, [])
  if (!retained) return null
  return <div role="status" className="border-b border-amber-300 bg-amber-50 px-5 py-3 text-base text-amber-950">
    旧版本机订单已安全保留，尚未恢复。请联系支持核对原服务器后恢复，请勿清理应用数据。
    <span className="block text-sm">Previous local orders are preserved. Contact support to verify their original server before recovery.</span>
  </div>
}
