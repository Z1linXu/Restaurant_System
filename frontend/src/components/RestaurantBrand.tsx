import { useState } from 'react'
import { useOptionalCurrentStore } from '../features/store/useStoreContext'
import { useLocale } from '../i18n/useLocale'

export function RestaurantBrand({ compact = false }: { compact?: boolean }) {
  const store = useOptionalCurrentStore()
  const { t } = useLocale()
  const [failedLogo, setFailedLogo] = useState<string | null>(null)
  const bundledBrand = typeof window !== 'undefined' ? window.__RESTAURANT_APP_CONFIG__ : undefined
  const name = store?.presentation?.display_name || store?.storeName || bundledBrand?.brandName || t('auth.pos')
  const configuredLogo = store?.presentation?.logo_url || bundledBrand?.brandLogo
  // Customer assets are local deployment resources, never arbitrary tracking URLs.
  const logo = configuredLogo?.startsWith('/') && !configuredLogo.startsWith('//') ? configuredLogo : null
  return <span className="inline-flex max-w-full items-center gap-2 text-left">
    {logo && failedLogo !== logo ? <img src={logo} alt={name} onError={() => setFailedLogo(logo)} className={compact ? 'max-h-10 max-w-36 object-contain' : 'max-h-14 max-w-48 object-contain'} /> :
      <span title={store?.presentation ? t('brand.temporary') : undefined} className={`font-display font-extrabold tracking-tight text-[var(--primary)] ${compact ? 'text-lg' : 'text-2xl'}`}>{name}</span>}
  </span>
}
