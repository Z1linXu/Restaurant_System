import { useEffect, useRef, useState } from 'react'
import { fetchReceiptPresentation, saveReceiptPresentation, type ReceiptPresentationSettings } from '../../services/printingAdminService'
import { useLocale } from '../../i18n/useLocale'
import type { AppLocale } from '../../i18n/locale'
import { translate } from '../../i18n/engine'

export function ReceiptLanguagePanel({ storeId, canManage }: { storeId: number; canManage: boolean }) {
  const { t } = useLocale()
  const [settings, setSettings] = useState<ReceiptPresentationSettings | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const scope = useRef(0)
  useEffect(() => {
    const current = ++scope.current
    setSettings(null); setError(null); setSaved(false); setBusy(false)
    void fetchReceiptPresentation(storeId).then((result) => { if (scope.current === current) setSettings(result) })
      .catch((reason) => { if (scope.current === current) setError(reason instanceof Error ? reason.message : translate('common.error')) })
    return () => { scope.current++ }
  }, [storeId])

  const save = async () => {
    if (!settings?.enabled || !canManage || busy) return
    const current = scope.current
    setBusy(true); setError(null); setSaved(false)
    try {
      const result = await saveReceiptPresentation(storeId, settings)
      if (scope.current === current) { setSettings(result); setSaved(true) }
    } catch (reason) {
      if (scope.current === current) setError(reason instanceof Error ? reason.message : t('common.error'))
    } finally { if (scope.current === current) setBusy(false) }
  }
  const selector = (field: 'default_language' | 'kitchen_language' | 'frontdesk_language', caption: string) => <label className="flex flex-col gap-2 text-sm font-semibold">
    {caption}
    <select aria-label={caption} className="min-h-12 rounded-xl border border-stone-200 bg-white px-3" value={settings?.[field] ?? ''} disabled={!canManage || busy}
      onChange={(event) => { setSaved(false); setSettings((value) => value ? { ...value, [field]: event.target.value || null } : value) }}>
      {field !== 'default_language' ? <option value="">{t('printing.followDefault')}</option> : null}
      {(['zh-CN', 'en', 'fr-CA'] as AppLocale[]).map((locale) => <option key={locale} value={locale}>{locale === 'zh-CN' ? '简体中文' : locale === 'fr-CA' ? 'Français' : 'English'}</option>)}
    </select>
  </label>
  return <section className="rounded-[26px] bg-white/85 p-5">
    <h2 className="text-lg font-bold">{t('printing.receiptLanguage')}</h2>
    <p className="mt-1 text-sm text-[var(--muted)]">{t('printing.languageHint')}</p>
    {error ? <p role="alert" className="mt-3 text-sm text-red-800">{error}</p> : null}
    {!settings && !error ? <p className="mt-3 text-sm">{t('common.loading')}</p> : null}
    {settings?.enabled ? <>
      <div className="mt-4 grid gap-3 sm:grid-cols-3">
        {selector('default_language', t('printing.defaultLanguage'))}
        {selector('kitchen_language', t('printing.kitchenLanguage'))}
        {selector('frontdesk_language', t('printing.frontdeskLanguage'))}
      </div>
      {canManage ? <button type="button" className="mt-4 min-h-11 rounded-xl bg-[var(--primary)] px-5 font-semibold text-white disabled:opacity-50" disabled={busy} onClick={() => void save()}>{t(busy ? 'common.saving' : 'common.save')}</button> : null}
      {saved ? <p role="status" className="mt-2 text-sm text-green-800">{t('printing.languageSaved')}</p> : null}
    </> : settings ? <p className="mt-3 text-sm text-[var(--muted)]">{t('printing.languageLegacy')}</p> : null}
  </section>
}
