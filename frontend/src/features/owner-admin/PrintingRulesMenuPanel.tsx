import { useEffect, useState } from 'react'
import { fetchStoreContext, type StoreModuleConfiguration } from '../../services/storeWorkspaceService'
import { useAuth } from '../auth/useAuth'
import { evaluateStoreModuleManagementAccess } from '../store/storeModuleAccess'
import { isFeatureEnabled } from '../feature-flags/featureConfig'
import { PrintingDisplayRulesPanel } from './PrintingDisplayRulesPanel'

export function PrintingRulesMenuPanel({ storeId, onToast }: {
  storeId: number
  onToast: (message: string, kind?: 'success' | 'error') => void
}) {
  const { permissions, user } = useAuth()
  const authorized = (permissions ?? []).some((value) => value === 'admin:printing_manage' || value === 'admin:store_config')
  const printingEnabled = isFeatureEnabled('PRINTING')
  return <PrintingRulesMenuContent key={`${storeId}:${user?.id}:${authorized}:${printingEnabled}`} storeId={storeId}
    onToast={onToast} authorized={authorized} printingEnabled={printingEnabled} userId={user?.id} />
}

function PrintingRulesMenuContent({ storeId, onToast, authorized, printingEnabled, userId }: Parameters<typeof PrintingRulesMenuPanel>[0] & {
  authorized: boolean; printingEnabled: boolean; userId?: number
}) {
  const [configuration, setConfiguration] = useState<StoreModuleConfiguration | null>(null)
  const [loading, setLoading] = useState(authorized && printingEnabled)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    let current = true
    if (!authorized || !printingEnabled) return
    void fetchStoreContext(storeId, userId).then((context) => {
      if (current && context.id === storeId) setConfiguration(context.module_configuration ?? null)
    }).catch((failure: unknown) => {
      if (current) setError(failure instanceof Error ? failure.message : 'Unable to load printing access. / 无法读取打印权限。')
    }).finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [authorized, printingEnabled, storeId, userId])
  const allowed = authorized && printingEnabled && evaluateStoreModuleManagementAccess(configuration, 'PRINTING').allowed
  if (loading) return <p role="status" className="rounded-[22px] bg-white p-5">Loading printing rules... / 正在加载打印规则...</p>
  if (!allowed) return <p role="alert" className="rounded-[22px] bg-white p-5">{error || 'Printing configuration access is required for this Store. / 需要当前门店的打印配置权限。'}</p>
  return <PrintingDisplayRulesPanel storeId={storeId} onToast={onToast} />
}
