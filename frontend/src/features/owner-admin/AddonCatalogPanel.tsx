import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  createStoreAddon,
  fetchStoreAddons,
  updateStoreAddon,
  type StoreAddon,
  type StoreAddonCatalog,
} from '../../services/ownerAddonService'

interface AddonCatalogPanelProps {
  storeId: number
  onSaved?: (message: string) => void
}

interface AddonDraft {
  id: number | null
  code: string
  name_zh: string
  name_en: string
  price: string
  active: boolean
}

const inputClass = 'mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.12)] bg-white px-3 py-2.5 text-[0.9rem] outline-none read-only:bg-[rgba(26,28,25,0.04)]'
const buttonClass = 'rounded-[14px] bg-[rgba(26,28,25,0.06)] px-3 py-2 text-[0.82rem] font-semibold disabled:opacity-50'

function money(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}

export function AddonCatalogPanel({ storeId, onSaved }: AddonCatalogPanelProps) {
  const [catalog, setCatalog] = useState<StoreAddonCatalog | null>(null)
  const [draft, setDraft] = useState<AddonDraft | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const scope = useRef(0)

  useEffect(() => {
    const requestScope = ++scope.current
    setLoading(true)
    setCatalog(null)
    setDraft(null)
    setSaving(false)
    setError(null)
    void fetchStoreAddons(storeId).then((result) => {
      if (scope.current === requestScope) setCatalog(result)
    }).catch((loadError: unknown) => {
      if (scope.current === requestScope) {
        setError(loadError instanceof Error ? loadError.message : 'Failed to load Add-ons.')
      }
    }).finally(() => {
      if (scope.current === requestScope) setLoading(false)
    })
    return () => { scope.current += 1 }
  }, [storeId, reload])

  const conflictingCodes = new Set(catalog?.conflicts.map((conflict) => conflict.code) ?? [])

  const edit = (addon: StoreAddon) => {
    if (saving || conflictingCodes.has(addon.code)) return
    setError(null)
    setDraft({
      id: addon.id,
      code: addon.code,
      name_zh: addon.name_zh ?? '',
      name_en: addon.name_en ?? '',
      price: Number(addon.price).toFixed(2),
      active: addon.active,
    })
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!draft || !catalog || saving) return
    const requestScope = scope.current
    setError(null)
    const price = Number(draft.price.trim())
    if (!draft.code.trim() || !draft.name_zh.trim()) {
      setError('Code and Chinese name are required. / 请填写代码和中文名称。')
      return
    }
    if (conflictingCodes.has(draft.code.trim())) {
      setError('This code has existing values awaiting an Owner decision. / 此代码的现有值待店主确认。')
      return
    }
    if (!/^\d+(\.\d{0,2})?$/.test(draft.price.trim()) || !Number.isFinite(price) || price < 0) {
      setError('Enter a non-negative price with at most two decimal places. / 请输入非负价格，最多两位小数。')
      return
    }
    const values = { name_zh: draft.name_zh.trim(), name_en: draft.name_en.trim(), price, active: draft.active }
    setSaving(true)
    try {
      const saved = draft.id === null
        ? await createStoreAddon({ store_id: storeId, code: draft.code.trim(), ...values })
        : await updateStoreAddon(draft.id, values)
      if (scope.current !== requestScope) return
      setCatalog((current) => current ? {
        ...current,
        addons: current.addons.some((addon) => addon.id === saved.id)
          ? current.addons.map((addon) => addon.id === saved.id ? saved : addon)
          : [...current.addons, saved],
      } : current)
      setDraft(null)
      onSaved?.('Add-on saved. Changes apply to future orders. / 加料已保存，将用于之后的订单。')
    } catch (saveError) {
      if (scope.current === requestScope) {
        setError(saveError instanceof Error ? saveError.message : 'Failed to save Add-on.')
      }
    } finally {
      if (scope.current === requestScope) setSaving(false)
    }
  }

  return (
    <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-[1.1rem] font-bold text-[var(--on-surface)]">Add-ons / 加料</h2>
          <p className="mt-1 text-[0.85rem] text-[var(--muted)]">
            Edit names, prices and availability once for this Store. Choose eligible items in each item's options.
            <br />在此统一维护加料名称、价格和全店可用性，在菜品选项中勾选适用的加料。
          </p>
        </div>
        <button
          type="button"
          disabled={loading || saving || !catalog}
          className={buttonClass}
          onClick={() => {
            setError(null)
            setDraft({ id: null, code: '', name_zh: '', name_en: '', price: '0.00', active: true })
          }}
        >Add Add-on / 添加加料</button>
      </div>

      {error ? (
        <div role="alert" className="mt-4 rounded-[16px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.84rem] text-[var(--primary)]">
          <p>{error}</p>
          {!loading && !catalog ? <button type="button" className={`${buttonClass} mt-2`} onClick={() => setReload((value) => value + 1)}>Retry / 重试</button> : null}
        </div>
      ) : null}

      {loading ? <p role="status" className="mt-4 text-[var(--muted)]">Loading Add-ons... / 正在加载加料...</p> : null}

      {draft ? (
        <form onSubmit={(event) => void save(event)} className="mt-4 rounded-[18px] border border-[rgba(26,28,25,0.08)] bg-white/80 p-4">
          <h3 className="font-semibold">{draft.id === null ? 'New Add-on / 新增加料' : 'Edit Add-on / 编辑加料'}</h3>
          <fieldset disabled={saving} className="mt-3 grid gap-3 sm:grid-cols-2">
            <label className="text-[0.84rem]">Code / 代码
              <input aria-label="Add-on code" className={inputClass} value={draft.code} readOnly={draft.id !== null} required maxLength={255}
                onChange={(event) => setDraft({ ...draft, code: event.target.value })} />
              <span className="mt-1 block text-[0.76rem] text-[var(--muted)]">{draft.id === null ? 'Use a unique code, e.g. extra_beef. It cannot be changed after creation. / 创建后不可修改。' : 'Code cannot be changed. / 代码不可修改。'}</span>
            </label>
            <label className="text-[0.84rem]">Chinese name / 中文名称
              <input aria-label="Add-on Chinese name" className={inputClass} value={draft.name_zh} required maxLength={255}
                onChange={(event) => setDraft({ ...draft, name_zh: event.target.value })} />
            </label>
            <label className="text-[0.84rem]">English name / 英文名称
              <input aria-label="Add-on English name" className={inputClass} value={draft.name_en} maxLength={255}
                onChange={(event) => setDraft({ ...draft, name_en: event.target.value })} />
            </label>
            <label className="text-[0.84rem]">Price / 价格
              <input aria-label="Add-on price" className={inputClass} value={draft.price} inputMode="decimal" required
                onChange={(event) => setDraft({ ...draft, price: event.target.value })} />
            </label>
            <label className="flex items-center gap-2 text-[0.84rem]">
              <input aria-label="Add-on active" type="checkbox" checked={draft.active}
                onChange={(event) => setDraft({ ...draft, active: event.target.checked })} />
              Active for this Store / 全店可用
            </label>
          </fieldset>
          <div className="mt-4 flex gap-2">
            <button type="submit" disabled={saving} className="rounded-[14px] bg-[var(--primary)] px-4 py-2 text-[0.84rem] font-semibold text-white disabled:opacity-50">{saving ? 'Saving... / 保存中...' : 'Save Add-on / 保存加料'}</button>
            <button type="button" disabled={saving} className={buttonClass} onClick={() => { setDraft(null); setError(null) }}>Cancel / 取消</button>
          </div>
        </form>
      ) : null}

      {catalog ? (
        <div className="mt-4">
          {catalog.addons.length === 0 ? <p className="text-[0.84rem] text-[var(--muted)]">No catalog Add-ons yet. / 暂无加料目录记录。</p> : (
            <ul className="space-y-2">
              {catalog.addons.map((addon) => (
                <li key={addon.id} className="flex flex-wrap items-center justify-between gap-3 rounded-[16px] bg-[rgba(26,28,25,0.035)] p-3">
                  <div className="min-w-0">
                    <div className="break-words font-semibold">{addon.name_zh} {addon.name_en}</div>
                    <div className="mt-1 break-words text-[0.78rem] text-[var(--muted)]">{addon.code} · {money(addon.price)} · {addon.active ? 'Active / 可用' : 'Inactive / 停用'}</div>
                    <div className="mt-1 text-[0.76rem] text-[var(--muted)]">{addon.printing_configured ? 'Printing configured / 已配置打印' : 'Using fallback / 使用默认打印显示'}</div>
                    {conflictingCodes.has(addon.code) ? <p className="mt-1 text-[0.78rem] text-[var(--primary)]">Read-only: Owner value decision pending. / 只读：待店主确认值。</p> : null}
                  </div>
                  <button type="button" aria-label={`Edit Add-on ${addon.code}`} className={buttonClass} disabled={saving || conflictingCodes.has(addon.code)} onClick={() => edit(addon)}>Edit / 编辑</button>
                </li>
              ))}
            </ul>
          )}

          {catalog.conflicts.length > 0 ? (
            <section className="mt-4 rounded-[18px] bg-[rgba(176,125,41,0.09)] p-4" aria-label="Add-on values awaiting Owner decision">
              <h3 className="font-semibold">Values awaiting Owner decision / 待店主确认的值</h3>
              <p className="mt-1 text-[0.82rem] text-[var(--muted)]">These existing Add-ons are read-only until their intended names and prices are confirmed. Current item Add-ons remain available as before. / 以下现有加料需先确认名称和价格，暂为只读；菜品现有加料保留。</p>
              <ul className="mt-3 space-y-2">
                {catalog.conflicts.map((conflict, index) => (
                  <li key={`${conflict.code ?? 'missing'}-${index}`} className="rounded-[14px] bg-white/65 p-3 text-[0.84rem]">
                    <div className="break-words font-semibold">{conflict.code || 'Missing code / 缺少代码'}</div>
                    <div className="mt-1 break-words">{[...conflict.names_zh, ...conflict.names_en].filter(Boolean).join(' / ') || 'Missing name / 缺少名称'}</div>
                    <div className="mt-1 text-[var(--muted)]">{conflict.prices.map((price) => price == null ? 'Missing price / 缺少价格' : money(price)).join(' / ')}</div>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}
