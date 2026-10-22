import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  createStoreRemove, fetchRemoveApplicability, fetchStoreRemoves, reorderStoreRemoves,
  updateRemoveApplicability, updateStoreRemove,
  type RemoveApplicability, type RemoveCatalog, type RemoveMode, type StoreRemove,
} from '../../services/ownerRemoveService'

interface Props {
  storeId: number
  categories: Array<{ id: number; label: string }>
  onSaved?: (message: string) => void
  onChanged?: () => void | Promise<void>
}
type Draft = { id: number | null; code: string; name_zh: string; name_en: string; active: boolean; sort_order: number }
const inputClass = 'mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.12)] bg-white px-3 py-2.5 text-[0.9rem] outline-none read-only:bg-[rgba(26,28,25,0.04)]'
const buttonClass = 'min-h-11 rounded-[14px] bg-[rgba(26,28,25,0.06)] px-3 py-2 text-[0.84rem] font-semibold disabled:opacity-50'
const message = (error: unknown) => error instanceof Error ? error.message : 'Unable to save Removes. / 减料保存失败。'

export function RemoveCatalogPanel(props: Props) {
  return <RemoveCatalogContent key={props.storeId} {...props} />
}

function RemoveCatalogContent({ storeId, categories, onSaved, onChanged }: Props) {
  const [catalog, setCatalog] = useState<RemoveCatalog | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [scopeDraft, setScopeDraft] = useState<RemoveApplicability | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const generation = useRef(0)

  useEffect(() => {
    const current = ++generation.current
    setLoading(true)
    setError(null)
    void fetchStoreRemoves(storeId).then((result) => {
      if (generation.current === current) setCatalog(result)
    }).catch((failure: unknown) => {
      if (generation.current === current) setError(message(failure))
    }).finally(() => {
      if (generation.current === current) setLoading(false)
    })
    return () => { generation.current += 1 }
  }, [storeId, reload])

  const changed = async (text: string) => {
    setSaving(false)
    setDraft(null)
    setScopeDraft(null)
    setReload((value) => value + 1)
    onSaved?.(text)
    await onChanged?.()
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!draft || saving) return
    if (!draft.code.trim() || !draft.name_zh.trim()) {
      setError('Code and Chinese name are required. / 请填写代码和中文名称。')
      return
    }
    const current = generation.current
    setSaving(true)
    setError(null)
    try {
      const values = { name_zh: draft.name_zh.trim(), name_en: draft.name_en.trim(), active: draft.active, sort_order: draft.sort_order }
      if (draft.id == null) await createStoreRemove({ store_id: storeId, code: draft.code.trim(), ...values })
      else await updateStoreRemove(draft.id, storeId, values)
      if (current === generation.current) await changed('Remove saved for all linked items. / 减料已统一保存。')
    } catch (failure) {
      if (current === generation.current) setError(message(failure))
    } finally { if (current === generation.current) setSaving(false) }
  }

  const openApplicability = async (remove: StoreRemove) => {
    const current = generation.current
    setSaving(true)
    setError(null)
    setDraft(null)
    setScopeDraft(null)
    try {
      const value = await fetchRemoveApplicability(remove.id, storeId)
      if (current === generation.current && value.store_id === storeId) setScopeDraft(value)
    } catch (failure) { if (current === generation.current) setError(message(failure)) }
    finally { if (current === generation.current) setSaving(false) }
  }

  const saveApplicability = async () => {
    if (!scopeDraft || saving) return
    const current = generation.current
    setSaving(true)
    setError(null)
    try {
      await updateRemoveApplicability(scopeDraft.remove_id, storeId, scopeDraft)
      if (current === generation.current) await changed('Eligible items saved. / 适用商品已保存。')
    } catch (failure) { if (current === generation.current) setError(message(failure)) }
    finally { if (current === generation.current) setSaving(false) }
  }

  const move = async (index: number, direction: -1 | 1) => {
    if (!catalog || saving || index + direction < 0 || index + direction >= catalog.removes.length) return
    const next = catalog.removes.map((remove) => remove.id)
    ;[next[index], next[index + direction]] = [next[index + direction], next[index]]
    const current = generation.current
    setSaving(true)
    setError(null)
    try {
      await reorderStoreRemoves(storeId, next)
      if (current === generation.current) await changed('Remove order saved. / 减料顺序已保存。')
    } catch (failure) { if (current === generation.current) setError(message(failure)) }
    finally { if (current === generation.current) setSaving(false) }
  }

  const selectedRemove = catalog?.removes.find((remove) => remove.id === scopeDraft?.remove_id)
  return (
    <section className="rounded-[26px] bg-white/85 p-5" aria-label="Remove catalog">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><h2 className="text-[1.1rem] font-bold">Removes / 减料</h2>
          <p className="mt-1 text-[0.85rem] text-[var(--muted)]">Maintain names once, then choose eligible categories and items. All Removes are free.<br />统一维护名称和适用范围；减料免费，不改变历史订单。</p></div>
        <button type="button" className={buttonClass} disabled={loading || saving || !catalog}
          onClick={() => { setError(null); setScopeDraft(null); setDraft({ id: null, code: '', name_zh: '', name_en: '', active: true, sort_order: (catalog?.removes.length ?? 0) * 10 + 10 }) }}>Add Remove / 添加减料</button>
      </div>
      {error ? <div role="alert" className="mt-4 rounded-[14px] bg-red-50 p-3 text-[var(--primary)]">{error}<button type="button" className={`${buttonClass} ml-2`} disabled={saving} onClick={() => setReload((value) => value + 1)}>Retry / 重试</button></div> : null}
      {loading ? <p role="status" className="mt-4">Loading Removes... / 正在加载减料...</p> : null}
      {draft ? <form onSubmit={(event) => void save(event)} className="mt-4 rounded-[18px] border border-black/10 p-4">
        <fieldset disabled={saving} className="grid gap-3 sm:grid-cols-2">
          <label>Code / 代码<input aria-label="Remove code" className={inputClass} required maxLength={255} readOnly={draft.id != null} value={draft.code} onChange={(event) => setDraft({ ...draft, code: event.target.value })} /><span className="text-xs text-[var(--muted)]">Cannot change after creation / 创建后不可修改</span></label>
          <label>Chinese name / 中文名称<input aria-label="Remove Chinese name" className={inputClass} required maxLength={255} value={draft.name_zh} onChange={(event) => setDraft({ ...draft, name_zh: event.target.value })} /></label>
          <label>English name / 英文名称<input aria-label="Remove English name" className={inputClass} maxLength={255} value={draft.name_en} onChange={(event) => setDraft({ ...draft, name_en: event.target.value })} /></label>
          <label className="flex items-center gap-2"><input aria-label="Remove active" type="checkbox" checked={draft.active} onChange={(event) => setDraft({ ...draft, active: event.target.checked })} />Active / 启用</label>
        </fieldset>
        <div className="mt-4 flex gap-2"><button type="submit" className={`${buttonClass} bg-[var(--primary)] text-white`} disabled={saving}>Save Remove / 保存减料</button><button type="button" className={buttonClass} disabled={saving} onClick={() => setDraft(null)}>Cancel / 取消</button></div>
      </form> : null}
      {scopeDraft ? <section className="mt-4 rounded-[18px] border border-black/10 p-4" aria-label="Remove applicability">
        <h3 className="font-bold">{selectedRemove?.name_zh} — Eligible items / 适用范围</h3>
        <p className="mt-1 text-sm text-[var(--muted)]">Category applies to current and future items. Each item may override it. / 分类适用于现有及新商品，单品可覆盖。</p>
        <fieldset disabled={saving} className="mt-3 flex flex-wrap gap-3">
          {categories.map((category) => <label key={category.id} className="flex min-h-11 items-center gap-2"><input type="checkbox" aria-label={`Remove category ${category.id}`} checked={scopeDraft.category_ids.includes(category.id)} onChange={(event) => setScopeDraft({ ...scopeDraft, category_ids: event.target.checked ? [...scopeDraft.category_ids, category.id] : scopeDraft.category_ids.filter((id) => id !== category.id) })} />{category.label}</label>)}
        </fieldset>
        <div className="mt-3 max-h-[420px] space-y-2 overflow-y-auto">
          {scopeDraft.items.map((item) => {
            const effective = item.mode === 'ALLOW' || (item.mode === 'INHERIT' && scopeDraft.category_ids.includes(item.category_id ?? -1))
            return <label key={item.item_id} className="flex flex-wrap items-center justify-between gap-2 rounded-[12px] bg-black/[0.025] p-3">
              <span>{item.name_zh || item.name_en} <span className="text-xs text-[var(--muted)]">{effective ? 'Eligible / 适用' : 'Not eligible / 不适用'}</span></span>
              <select aria-label={`Remove item ${item.item_id}`} className="min-h-11 rounded-[12px] border border-black/10 bg-white px-3" disabled={saving} value={item.mode} onChange={(event) => setScopeDraft({ ...scopeDraft, items: scopeDraft.items.map((value) => value.item_id === item.item_id ? { ...value, mode: event.target.value as RemoveMode } : value) })}>
                <option value="INHERIT">Use category / 跟随分类</option><option value="ALLOW">Allow / 允许</option><option value="DENY">Deny / 禁用</option>
              </select>
            </label>
          })}
        </div>
        <div className="mt-4 flex gap-2"><button type="button" className={buttonClass} disabled={saving} onClick={() => void saveApplicability()}>Save eligibility / 保存适用范围</button><button type="button" className={buttonClass} disabled={saving} onClick={() => setScopeDraft(null)}>Cancel / 取消</button></div>
      </section> : null}
      {catalog ? <>
        <h3 className="mt-5 font-semibold">Shared Removes / 共享减料</h3>
        {catalog.removes.length === 0 ? <p className="mt-2 text-sm text-[var(--muted)]">No shared Removes yet. / 暂无共享减料。</p> : <ul className="mt-3 space-y-2">
          {catalog.removes.map((remove, index) => <li key={remove.id} className="flex flex-wrap items-center justify-between gap-3 rounded-[16px] bg-black/[0.035] p-3">
            <div className="min-w-0"><div className="break-words font-semibold">{remove.name_zh} {remove.name_en}</div><div className="mt-1 break-words text-xs text-[var(--muted)]">{remove.code} · {remove.active ? 'Active / 可用' : 'Inactive / 停用'} · {(remove.item_ids ?? []).length} linked items / 关联商品</div></div>
            <div className="flex flex-wrap gap-2"><button type="button" className={buttonClass} disabled={saving} aria-label={`Edit Remove ${remove.code}`} onClick={() => { setScopeDraft(null); setError(null); setDraft({ ...remove }) }}>Edit / 编辑</button><button type="button" className={buttonClass} disabled={saving} aria-label={`Eligible items ${remove.code}`} onClick={() => void openApplicability(remove)}>Items / 商品</button><button type="button" className={buttonClass} disabled={saving || index === 0} aria-label={`Move Remove ${remove.code} up`} onClick={() => void move(index, -1)}>↑</button><button type="button" className={buttonClass} disabled={saving || index === catalog.removes.length - 1} aria-label={`Move Remove ${remove.code} down`} onClick={() => void move(index, 1)}>↓</button></div>
          </li>)}
        </ul>}
        {catalog.legacy_options?.length ? <details className="mt-4 rounded-[16px] bg-black/[0.025] p-4"><summary className="cursor-pointer font-semibold">Legacy item Removes / 原有单品减料 ({catalog.legacy_options.length})</summary><ul className="mt-3 space-y-2 text-sm">{catalog.legacy_options.map((option) => <li key={option.option_id}>{option.name_zh || option.name_en} · {option.code || 'Missing code / 缺少代码'} · #{option.item_id} · {option.active ? 'Active / 可用' : 'Inactive / 停用'}</li>)}</ul></details> : null}
        {catalog.conflicts.length ? <section className="mt-4 rounded-[18px] bg-amber-50 p-4" aria-label="Legacy Remove conflicts"><h3 className="font-semibold">Names needing review / 待确认名称</h3><p className="mt-1 text-sm">Existing item values are preserved. / 原有商品配置保持不变。</p><ul className="mt-3 space-y-2">{catalog.conflicts.map((conflict, index) => <li key={`${conflict.code}-${index}`} className="break-words rounded-[12px] bg-white/70 p-3 text-sm"><strong>{conflict.code || 'Missing code / 缺少代码'}</strong><div>{[...conflict.names_zh, ...conflict.names_en].filter(Boolean).join(' / ')}</div></li>)}</ul></section> : null}
      </> : null}
    </section>
  )
}
