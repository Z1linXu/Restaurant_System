import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocale } from '../../i18n/useLocale'
import { ApiRequestError } from '../../services/apiClient'
import {
  applyCategoryBasePrices,
  fetchStorePricingCapabilities,
  previewCategoryBasePrices,
  type CategoryPriceAdjustmentRequest,
  type CategoryPriceApplyRequest,
  type CategoryPricePreview,
  type StorePricingCapabilities,
} from '../../services/ownerMenuPricingService'

export interface CategoryBulkPriceAdjustmentPanelProps {
  storeId: number
  categories: Array<{ id: number; label: string }>
  onSaved?: (message: string) => void
}

const copy = {
  en: {
    title: 'Category prices', description: 'Adjust item base prices by a fixed CAD amount. Free refills and fixed offers stay unchanged.',
    loading: 'Loading pricing settings…', loadError: 'Could not load pricing settings.', retry: 'Retry', categories: 'Categories',
    increase: 'Increase', decrease: 'Decrease', amount: 'Amount (CAD)', inactive: 'Include inactive items', preview: 'Preview prices',
    previewError: 'Could not preview prices.', negative: 'The adjustment would make an item price negative. Reduce the amount.', invalid: 'Choose categories and enter a positive amount with up to two decimal places.',
    old: 'Current price', next: 'New price', item: 'Item', changes: 'items will change', excluded: 'Excluded items',
    REFILL_ITEM: 'Free refill', FIXED_OFFER: 'Fixed offer', INACTIVE_ITEM: 'Inactive item', save: 'Confirm & save', saving: 'Saving…',
    saved: 'Category prices saved.', stale: 'The menu changed or this preview expired. Preview the prices again.',
    uncertain: 'The save could not be confirmed. Retry save to check the same adjustment.', retrySave: 'Retry save',
    saveError: 'Prices could not be saved.', none: 'No eligible items in these categories.',
  },
  'zh-CN': {
    title: '分类调价', description: '按固定加元金额调整菜品基础价。免费续加和固定套餐保持原价。',
    loading: '正在加载价格设置…', loadError: '无法加载价格设置。', retry: '重试', categories: '分类',
    increase: '加价', decrease: '降价', amount: '金额（CAD）', inactive: '包含已下架菜品', preview: '预览价格',
    previewError: '无法预览价格。', negative: '调整后有菜品价格小于零，请减小调价金额。', invalid: '请选择分类，并输入最多两位小数的正数金额。',
    old: '现价', next: '新价', item: '菜品', changes: '道菜品将调价', excluded: '不参与调价',
    REFILL_ITEM: '免费续加', FIXED_OFFER: '固定套餐', INACTIVE_ITEM: '已下架菜品', save: '确认保存', saving: '正在保存…',
    saved: '分类价格已保存。', stale: '菜单已变化或预览已过期，请重新预览价格。',
    uncertain: '尚未确认保存结果。请重试保存以核实同一次调价。', retrySave: '重试保存',
    saveError: '无法保存价格。', none: '所选分类没有可调价菜品。',
  },
  'fr-CA': {
    title: 'Prix par catégorie', description: 'Ajustez les prix de base avec un montant fixe en CAD. Les remplissages gratuits et offres fixes gardent leur prix.',
    loading: 'Chargement des paramètres de prix…', loadError: 'Impossible de charger les paramètres de prix.', retry: 'Réessayer', categories: 'Catégories',
    increase: 'Augmenter', decrease: 'Diminuer', amount: 'Montant (CAD)', inactive: 'Inclure les articles inactifs', preview: 'Aperçu des prix',
    previewError: 'Impossible de préparer l’aperçu.', negative: 'Un prix deviendrait négatif. Réduisez le montant.', invalid: 'Choisissez des catégories et un montant positif avec au plus deux décimales.',
    old: 'Prix actuel', next: 'Nouveau prix', item: 'Article', changes: 'articles seront modifiés', excluded: 'Articles exclus',
    REFILL_ITEM: 'Remplissage gratuit', FIXED_OFFER: 'Offre fixe', INACTIVE_ITEM: 'Article inactif', save: 'Confirmer et enregistrer', saving: 'Enregistrement…',
    saved: 'Prix des catégories enregistrés.', stale: 'Le menu a changé ou l’aperçu a expiré. Préparez un nouvel aperçu.',
    uncertain: 'L’enregistrement n’a pas pu être confirmé. Réessayez pour vérifier le même ajustement.', retrySave: 'Réessayer l’enregistrement',
    saveError: 'Impossible d’enregistrer les prix.', none: 'Aucun article admissible dans ces catégories.',
  },
} as const

const controlClass = 'min-h-12 rounded-[14px] border border-[rgba(26,28,25,0.16)] bg-white px-3 py-2 disabled:opacity-50'
const buttonClass = `${controlClass} font-semibold`

function saveIdentity() {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID() : `category-price-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function CategoryBulkPriceAdjustmentPanel({ storeId, categories, onSaved }: CategoryBulkPriceAdjustmentPanelProps) {
  const { locale, label } = useLocale()
  const text = copy[locale]
  const [capabilities, setCapabilities] = useState<StorePricingCapabilities | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<'preview' | 'save' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [categoryIds, setCategoryIds] = useState<number[]>([])
  const [direction, setDirection] = useState<'INCREASE' | 'DECREASE'>('INCREASE')
  const [amount, setAmount] = useState('')
  const [includeInactive, setIncludeInactive] = useState(false)
  const [preview, setPreview] = useState<CategoryPricePreview | null>(null)
  const [uncertain, setUncertain] = useState(false)
  const [reload, setReload] = useState(0)
  const scope = useRef(0)
  const inFlight = useRef(false)
  const pendingApply = useRef<CategoryPriceApplyRequest | null>(null)

  useEffect(() => {
    const requestScope = ++scope.current
    setLoading(true)
    setCapabilities(null)
    setCategoryIds([])
    setDirection('INCREASE')
    setAmount('')
    setIncludeInactive(false)
    setPreview(null)
    setError(null)
    setBusy(null)
    setUncertain(false)
    inFlight.current = false
    pendingApply.current = null
    void fetchStorePricingCapabilities(storeId).then((value) => {
      if (scope.current !== requestScope) return
      if (value.store_id === storeId) setCapabilities(value)
      else setError('load')
    }).catch(() => {
      if (scope.current === requestScope) setError('load')
    }).finally(() => {
      if (scope.current === requestScope) setLoading(false)
    })
    return () => { scope.current += 1 }
  }, [storeId, reload])

  const clearPreview = () => {
    if (inFlight.current || uncertain) return
    setPreview(null)
    pendingApply.current = null
    setError(null)
  }

  const requestPreview = async (event: FormEvent) => {
    event.preventDefault()
    if (inFlight.current || uncertain || !capabilities?.category_bulk_enabled) return
    const value = amount.trim()
    if (!categoryIds.length || categoryIds.some((id) => !categories.some((category) => category.id === id))
      || !/^\d{1,6}(\.\d{1,2})?$/.test(value) || Number(value) <= 0) {
      setError('invalid')
      return
    }
    const requestScope = scope.current
    const payload: CategoryPriceAdjustmentRequest = {
      store_id: storeId, category_ids: [...categoryIds].sort((a, b) => a - b), direction,
      amount: Number(value).toFixed(2), currency: 'CAD', include_inactive: includeInactive,
    }
    inFlight.current = true
    setBusy('preview')
    setError(null)
    setPreview(null)
    pendingApply.current = null
    try {
      const result = await previewCategoryBasePrices(payload)
      if (scope.current !== requestScope || result.store_id !== storeId) return
      pendingApply.current = { ...payload, preview_id: result.preview_id, expected_revision: result.menu_revision,
        fingerprint: result.fingerprint, idempotency_key: saveIdentity() }
      setPreview(result)
    } catch (previewError) {
      if (scope.current === requestScope) setError(previewError instanceof ApiRequestError && previewError.code === 'CATEGORY_PRICING_NEGATIVE_PRICE' ? 'negative' : 'previewError')
    } finally {
      if (scope.current === requestScope) { inFlight.current = false; setBusy(null) }
    }
  }

  const save = async () => {
    const payload = pendingApply.current
    if (inFlight.current || !payload || !preview?.affected_item_count || payload.store_id !== storeId) return
    const requestScope = scope.current
    inFlight.current = true
    setBusy('save')
    setError(null)
    try {
      await applyCategoryBasePrices(payload)
      if (scope.current !== requestScope) return
      pendingApply.current = null
      setPreview(null)
      setAmount('')
      setUncertain(false)
      onSaved?.(text.saved)
    } catch (saveError) {
      if (scope.current !== requestScope) return
      // A proxy 5xx/timeout can arrive after the backend committed. Only explicit request/auth/conflict rejections prove no save.
      const rejectedBeforeCommit = saveError instanceof ApiRequestError && [400, 401, 403, 404, 409, 422].includes(saveError.status)
      if (rejectedBeforeCommit && saveError instanceof ApiRequestError) {
        setError(saveError.status === 409 ? 'stale' : 'saveError')
        setPreview(null)
        pendingApply.current = null
        setUncertain(false)
      } else {
        // A lost response may follow a committed save. Freeze the draft and retain exactly the same retry identity.
        setUncertain(true)
        setError('uncertain')
      }
    } finally {
      if (scope.current === requestScope) { inFlight.current = false; setBusy(null) }
    }
  }

  const formatMoney = (value: number | string) => new Intl.NumberFormat(locale, { style: 'currency', currency: 'CAD' }).format(Number(value))
  const locked = busy !== null || uncertain

  if (!loading && !error && capabilities && !capabilities.category_bulk_enabled) return null

  return <section aria-label={text.title} className="rounded-[26px] bg-white/85 p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
    <h2 className="text-lg font-bold">{text.title}</h2>
    <p className="mt-1 text-sm text-[var(--muted)]">{text.description}</p>
    {loading ? <p role="status" className="mt-4">{text.loading}</p> : null}
    {error ? <div role="alert" className="mt-4 rounded-2xl bg-red-50 p-3 text-sm">
      {text[error as keyof typeof text] ?? text.saveError}
      {!loading && !capabilities ? <button type="button" className={`${buttonClass} ml-3`} onClick={() => setReload((value) => value + 1)}>{text.retry}</button> : null}
    </div> : null}
    {capabilities?.category_bulk_enabled ? <>
      <form onSubmit={(event) => void requestPreview(event)} className="mt-4 space-y-4">
        <fieldset disabled={locked}>
          <legend className="mb-2 font-semibold">{text.categories}</legend>
          <div className="flex flex-wrap gap-2">{categories.map((category) => <label key={category.id} className={`${controlClass} flex items-center gap-3`}>
            <input type="checkbox" aria-label={`Category ${category.id}`} checked={categoryIds.includes(category.id)} onChange={(event) => {
              clearPreview()
              setCategoryIds((current) => event.target.checked ? [...current, category.id] : current.filter((id) => id !== category.id))
            }} className="h-5 w-5" />{category.label}
          </label>)}</div>
        </fieldset>
        <div className="flex flex-wrap items-end gap-3">
          <select aria-label="Adjustment direction" value={direction} disabled={locked} className={controlClass} onChange={(event) => { clearPreview(); setDirection(event.target.value as 'INCREASE' | 'DECREASE') }}>
            <option value="INCREASE">{text.increase}</option><option value="DECREASE">{text.decrease}</option>
          </select>
          <label className="grid gap-1 text-sm">{text.amount}<input aria-label="Adjustment amount" value={amount} disabled={locked} type="text" inputMode="decimal" placeholder="0.00" className={`${controlClass} w-40`} onChange={(event) => { clearPreview(); setAmount(event.target.value) }} /></label>
          <label className="flex min-h-12 items-center gap-3 text-sm"><input aria-label="Include inactive items" type="checkbox" checked={includeInactive} disabled={locked} className="h-5 w-5" onChange={(event) => { clearPreview(); setIncludeInactive(event.target.checked) }} />{text.inactive}</label>
          <button type="submit" disabled={locked || !categoryIds.length || !amount.trim()} className={buttonClass}>{text.preview}</button>
        </div>
      </form>
      {preview ? <div className="mt-5 space-y-3">
        <p role="status" className="font-semibold">{preview.affected_item_count} {text.changes}</p>
        {preview.items.length ? <div className="max-h-96 overflow-auto rounded-2xl border border-black/10"><table className="w-full text-left text-sm">
          <thead className="sticky top-0 bg-white"><tr><th className="p-3">{text.item}</th><th className="p-3">{text.old}</th><th className="p-3">{text.next}</th></tr></thead>
          <tbody>{preview.items.map((item) => <tr key={item.item_id} className="border-t border-black/5"><td className="p-3">{label({ zh: item.name_zh, en: item.name_en, fr: item.name_fr })}{item.sku ? <span className="ml-2 text-xs text-[var(--muted)]">{item.sku}</span> : null}</td><td className="p-3 whitespace-nowrap">{formatMoney(item.old_base_price)}</td><td className="p-3 font-semibold whitespace-nowrap">{formatMoney(item.new_base_price)}</td></tr>)}</tbody>
        </table></div> : <p>{text.none}</p>}
        {preview.excluded_items.length ? <details className="text-sm text-[var(--muted)]"><summary className="min-h-12 cursor-pointer py-3">{text.excluded} ({preview.excluded_item_count})</summary>
          <ul className="space-y-2">{preview.excluded_items.map((item) => <li key={item.item_id}>{label({ zh: item.name_zh, en: item.name_en, fr: item.name_fr })} · {text[item.reason]}</li>)}</ul>
        </details> : null}
        <button type="button" disabled={busy !== null || preview.affected_item_count === 0} className={`${buttonClass} bg-[var(--primary)] text-white`} onClick={() => void save()}>{busy === 'save' ? text.saving : uncertain ? text.retrySave : text.save}</button>
      </div> : null}
    </> : null}
  </section>
}
