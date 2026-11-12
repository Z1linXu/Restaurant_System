import { useEffect, useState } from 'react'
import { useLocale } from '../../i18n/useLocale'
import { fetchStorePricingCapabilities, type StorePricingCapabilities } from '../../services/ownerMenuPricingService'
import { PricingRulesPanel } from './PricingRulesPanel'
import { CategoryBulkPriceAdjustmentPanel } from './CategoryBulkPriceAdjustmentPanel'

export function StorePricingPanel({ storeId, categories, onSaved }: { storeId: number; categories: Array<{id:number;label:string}>; onSaved?: (message:string)=>void }) {
  const { t } = useLocale()
  const [capability, setCapability] = useState<StorePricingCapabilities | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let current = true
    setCapability(null); setError(null)
    void fetchStorePricingCapabilities(storeId).then(value => { if (current) setCapability(value) })
      .catch(reason => { if (current) setError(reason instanceof Error ? reason.message : 'Could not load pricing settings.') })
    return () => { current = false }
  }, [storeId, retry])
  if (error) return <div role="alert" className="rounded-2xl bg-white p-5 text-red-800">{error} <button type="button" className="ml-3 min-h-11 underline" onClick={() => setRetry(x => x + 1)}>↻</button></div>
  if (!capability) return <p role="status" className="rounded-2xl bg-white p-5">{t('common.loading')}</p>
  return <div className="space-y-4">
    {capability.category_bulk_enabled ? <CategoryBulkPriceAdjustmentPanel storeId={storeId} categories={categories} onSaved={onSaved} /> : null}
    {capability.standard_size_enabled ? <PricingRulesPanel storeId={storeId} onSaved={onSaved} /> : null}
    {!capability.category_bulk_enabled && !capability.standard_size_enabled ? <p className="rounded-2xl bg-white p-5">{t('menu.items')}</p> : null}
  </div>
}
