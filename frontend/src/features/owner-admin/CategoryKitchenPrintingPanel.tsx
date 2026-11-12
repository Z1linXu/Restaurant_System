import { useEffect, useRef, useState } from 'react'
import { useLocale } from '../../i18n/useLocale'
import { fetchReceiptPresentation, saveReceiptPresentation, type ReceiptPresentationSettings } from '../../services/printingAdminService'

const copy = {
  en: { title: 'Kitchen printing by category', hint: 'One GRAB ticket uses the existing kitchen assignment. Every sold item remains on the frontdesk receipt.', kitchen: 'Kitchen ticket', direct: 'Served at frontdesk', save: 'Save kitchen routing', saved: 'Kitchen routing saved.', reload: 'Reload' },
  'zh-CN': { title: '按分类设置厨房打印', hint: '通过现有厨房打印分配生成一张汇总 GRAB 单。前台收据始终保留全部售卖商品。', kitchen: '厨房单', direct: '前台直接出品', save: '保存厨房路由', saved: '厨房路由已保存。', reload: '重新加载' },
  'fr-CA': { title: 'Impression cuisine par catégorie', hint: 'Un seul billet GRAB utilise l’affectation cuisine existante. Tous les articles restent sur le reçu de caisse.', kitchen: 'Billet cuisine', direct: 'Servi au comptoir', save: 'Enregistrer le routage', saved: 'Routage enregistré.', reload: 'Recharger' },
} as const

export function CategoryKitchenPrintingPanel({ storeId, categories, onChanged }: { storeId: number; categories: Array<{code:string;label:string}>; onChanged?: () => void }) {
  const { locale, t } = useLocale()
  const text = copy[locale]
  const [settings, setSettings] = useState<ReceiptPresentationSettings | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [reload, setReload] = useState(0)
  const scope = useRef(0)
  useEffect(() => {
    const request = ++scope.current
    setSettings(null); setError(null); setSaved(false); setBusy(false)
    void fetchReceiptPresentation(storeId).then(value => { if (request === scope.current) setSettings(value) })
      .catch(reason => { if (request === scope.current) setError(reason instanceof Error ? reason.message : 'Could not load kitchen routing.') })
    return () => { scope.current++ }
  }, [storeId, reload])
  const save = async () => {
    if (!settings?.enabled || busy) return
    const request = scope.current
    setBusy(true); setError(null); setSaved(false)
    try {
      const value = await saveReceiptPresentation(storeId, settings)
      if (request === scope.current) { setSettings(value); setSaved(true); onChanged?.() }
    } catch (reason) {
      if (request === scope.current) setError(reason instanceof Error ? reason.message : 'Could not save kitchen routing.')
    } finally { if (request === scope.current) setBusy(false) }
  }
  if (settings && !settings.enabled) return null
  return <section className="mt-4 rounded-[26px] bg-white/85 p-5" aria-label={text.title}>
    <h2 className="text-lg font-bold">{text.title}</h2><p className="mt-2 text-sm text-[var(--muted)]">{text.hint}</p>
    {error ? <div role="alert" className="mt-3 text-red-800">{error} <button type="button" className="min-h-11 underline" onClick={() => setReload(x => x + 1)}>{text.reload}</button></div> : null}
    {!settings && !error ? <p role="status" className="mt-3">{t('common.loading')}</p> : null}
    {settings?.enabled ? <>
      <p className="mt-3 text-sm font-semibold">GRAB → {settings.kitchen_station_codes.join(', ')}</p>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">{categories.map(category => {
        const direct = ['DRINK', 'ALCOHOL'].includes(category.code)
        return <label key={category.code} className="flex min-h-20 items-center gap-3 rounded-xl border border-stone-200 bg-white p-3">
          <input type="checkbox" className="h-6 w-6" aria-label={`${category.label} ${text.kitchen}`} checked={!direct && settings.kitchen_category_enabled?.[category.code] !== false} disabled={direct || busy}
            onChange={event => { setSaved(false); setSettings({ ...settings, kitchen_category_enabled: { ...settings.kitchen_category_enabled, [category.code]: event.target.checked } }) }} />
          <span className="min-w-0"><strong>{category.label}</strong>{direct ? <span className="mt-1 block text-xs text-[var(--muted)]">{text.direct}</span> : null}</span>
        </label>
      })}</div>
      <button type="button" className="mt-4 min-h-12 rounded-xl bg-[var(--primary)] px-5 font-semibold text-white disabled:opacity-50" disabled={busy} onClick={() => void save()}>{text.save}</button>
      {saved ? <p role="status" className="mt-2 text-green-800">{text.saved}</p> : null}
    </> : null}
  </section>
}
