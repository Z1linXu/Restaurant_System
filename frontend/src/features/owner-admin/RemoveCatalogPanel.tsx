import { useEffect, useRef, useState, type FormEvent } from 'react'
import { translate } from '../../i18n/engine'
import { useLocale } from '../../i18n/useLocale'
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
const message = (error: unknown) => error instanceof Error ? error.message : translate('management.removes.saveFailed')

export function RemoveCatalogPanel(props: Props) {
  return <RemoveCatalogContent key={props.storeId} {...props} />
}

function RemoveCatalogContent({ storeId, categories, onSaved, onChanged }: Props) {
  const { t } = useLocale()
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
      setError(t('management.codeRequired'))
      return
    }
    const current = generation.current
    setSaving(true)
    setError(null)
    try {
      const values = { name_zh: draft.name_zh.trim(), name_en: draft.name_en.trim(), active: draft.active, sort_order: draft.sort_order }
      if (draft.id == null) await createStoreRemove({ store_id: storeId, code: draft.code.trim(), ...values })
      else await updateStoreRemove(draft.id, storeId, values)
      if (current === generation.current) await changed(t('management.removes.saved'))
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
      if (current === generation.current) await changed(t('management.removes.eligibilitySaved'))
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
      if (current === generation.current) await changed(t('management.removes.orderSaved'))
    } catch (failure) { if (current === generation.current) setError(message(failure)) }
    finally { if (current === generation.current) setSaving(false) }
  }

  const legacyGroups = new Map<string, { code: string | null; name: string; count: number; items: Set<number>; active: number }>()
  for (const option of catalog?.legacy_options ?? []) {
    const key = `${option.code ?? ''}\u0000${option.name_zh}\u0000${option.name_en}`
    const group = legacyGroups.get(key) ?? { code: option.code, name: option.name_zh || option.name_en, count: 0, items: new Set<number>(), active: 0 }
    group.count += 1
    group.items.add(option.item_id)
    if (option.active) group.active += 1
    legacyGroups.set(key, group)
  }
  const affectedItems = new Set((catalog?.legacy_options ?? []).map((option) => option.item_id)).size
  const selectedRemove = catalog?.removes.find((remove) => remove.id === scopeDraft?.remove_id)
  return (
    <section className="rounded-[26px] bg-white/85 p-5" aria-label={t('management.removes.catalog')}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><h2 className="text-[1.1rem] font-bold">{t('management.removes.title')}</h2>
          <p className="mt-1 text-[0.85rem] text-[var(--muted)]">{t('management.removes.description')}</p></div>
        <button type="button" className={buttonClass} disabled={loading || saving || !catalog}
          onClick={() => { setError(null); setScopeDraft(null); setDraft({ id: null, code: '', name_zh: '', name_en: '', active: true, sort_order: (catalog?.removes.length ?? 0) * 10 + 10 }) }}>{t('management.removes.add')}</button>
      </div>
      {error ? <div role="alert" className="mt-4 rounded-[14px] bg-red-50 p-3 text-[var(--primary)]">{error}<button type="button" className={`${buttonClass} ml-2`} disabled={saving} onClick={() => setReload((value) => value + 1)}>{t('management.retry')}</button></div> : null}
      {loading ? <p role="status" className="mt-4">{t('management.removes.loading')}</p> : null}
      {draft ? <form onSubmit={(event) => void save(event)} className="mt-4 rounded-[18px] border border-black/10 p-4">
        <fieldset disabled={saving} className="grid gap-3 sm:grid-cols-2">
          <label>{t('management.code')}<input aria-label={t('management.removes.code')} className={inputClass} required maxLength={255} readOnly={draft.id != null} value={draft.code} onChange={(event) => setDraft({ ...draft, code: event.target.value })} /><span className="text-xs text-[var(--muted)]">{t('management.codeImmutable')}</span></label>
          <label>{t('management.chineseName')}<input aria-label={t('management.removes.chineseName')} className={inputClass} required maxLength={255} value={draft.name_zh} onChange={(event) => setDraft({ ...draft, name_zh: event.target.value })} /></label>
          <label>{t('management.englishName')}<input aria-label={t('management.removes.englishName')} className={inputClass} maxLength={255} value={draft.name_en} onChange={(event) => setDraft({ ...draft, name_en: event.target.value })} /></label>
          <label className="flex items-center gap-2"><input aria-label={t('management.removes.active')} type="checkbox" checked={draft.active} onChange={(event) => setDraft({ ...draft, active: event.target.checked })} />{t('management.active')}</label>
        </fieldset>
        <div className="mt-4 flex gap-2"><button type="submit" className={`${buttonClass} bg-[var(--primary)] text-white`} disabled={saving}>{t('management.removes.save')}</button><button type="button" className={buttonClass} disabled={saving} onClick={() => setDraft(null)}>{t('common.cancel')}</button></div>
      </form> : null}
      {scopeDraft ? <section className="mt-4 rounded-[18px] border border-black/10 p-4" aria-label={t('management.removes.applicability')}>
        <h3 className="font-bold">{selectedRemove?.name_zh} — {t('management.removes.eligibleItems')}</h3>
        <p className="mt-1 text-sm text-[var(--muted)]">{t('management.removes.inheritance')}</p>
        <fieldset disabled={saving} className="mt-3 flex flex-wrap gap-3">
          {categories.map((category) => <label key={category.id} className="flex min-h-11 items-center gap-2"><input type="checkbox" aria-label={t('management.removes.category', { id: category.id })} checked={scopeDraft.category_ids.includes(category.id)} onChange={(event) => setScopeDraft({ ...scopeDraft, category_ids: event.target.checked ? [...scopeDraft.category_ids, category.id] : scopeDraft.category_ids.filter((id) => id !== category.id) })} />{category.label}</label>)}
        </fieldset>
        <div className="mt-3 max-h-[420px] space-y-2 overflow-y-auto">
          {scopeDraft.items.map((item) => {
            const effective = item.mode === 'ALLOW' || (item.mode === 'INHERIT' && scopeDraft.category_ids.includes(item.category_id ?? -1))
            return <label key={item.item_id} className="flex flex-wrap items-center justify-between gap-2 rounded-[12px] bg-black/[0.025] p-3">
              <span>{item.name_zh || item.name_en} <span className="text-xs text-[var(--muted)]">{effective ? t('management.removes.eligible') : t('management.removes.notEligible')}</span></span>
              <select aria-label={t('management.removes.item', { id: item.item_id })} className="min-h-11 rounded-[12px] border border-black/10 bg-white px-3" disabled={saving} value={item.mode} onChange={(event) => setScopeDraft({ ...scopeDraft, items: scopeDraft.items.map((value) => value.item_id === item.item_id ? { ...value, mode: event.target.value as RemoveMode } : value) })}>
                <option value="INHERIT">{t('management.removes.inherit')}</option><option value="ALLOW">{t('management.removes.allow')}</option><option value="DENY">{t('management.removes.deny')}</option>
              </select>
            </label>
          })}
        </div>
        <div className="mt-4 flex gap-2"><button type="button" className={buttonClass} disabled={saving} onClick={() => void saveApplicability()}>{t('management.removes.saveEligibility')}</button><button type="button" className={buttonClass} disabled={saving} onClick={() => setScopeDraft(null)}>{t('common.cancel')}</button></div>
      </section> : null}
      {catalog ? <>
        <h3 className="mt-5 font-semibold">{t('management.removes.shared')}</h3>
        {catalog.removes.length === 0 ? <p className="mt-2 text-sm text-[var(--muted)]">{t('management.removes.empty')}</p> : <ul className="mt-3 space-y-2">
          {catalog.removes.map((remove, index) => <li key={remove.id} className="flex flex-wrap items-center justify-between gap-3 rounded-[16px] bg-black/[0.035] p-3">
            <div className="min-w-0"><div className="break-words font-semibold">{remove.name_zh} {remove.name_en}</div><div className="mt-1 break-words text-xs text-[var(--muted)]">{remove.code} · {remove.active ? t('management.active') : t('management.inactive')} · {t('management.removes.linkedCount', { count: (remove.item_ids ?? []).length })}</div></div>
            <div className="flex flex-wrap gap-2"><button type="button" className={buttonClass} disabled={saving} aria-label={t('management.removes.edit', { code: remove.code })} onClick={() => { setScopeDraft(null); setError(null); setDraft({ ...remove }) }}>{t('common.edit')}</button><button type="button" className={buttonClass} disabled={saving} aria-label={t('management.removes.items', { code: remove.code })} onClick={() => void openApplicability(remove)}>{t('menu.items')}</button><button type="button" className={buttonClass} disabled={saving || index === 0} aria-label={t('management.removes.moveUp', { code: remove.code })} onClick={() => void move(index, -1)}>↑</button><button type="button" className={buttonClass} disabled={saving || index === catalog.removes.length - 1} aria-label={t('management.removes.moveDown', { code: remove.code })} onClick={() => void move(index, 1)}>↓</button></div>
          </li>)}
        </ul>}
        {(catalog.legacy_options?.length || catalog.conflicts.length) ? <details className="mt-4 rounded-[16px] bg-black/[0.025] p-4" aria-label={t('management.removes.diagnostics')}>
          <summary className="cursor-pointer font-semibold">{t('management.removes.diagnosticsSummary', { unlinked: catalog.legacy_options?.length ?? 0, conflicts: catalog.conflicts.length, items: affectedItems })}</summary>
          <p className="mt-2 text-sm text-[var(--muted)]">{t('management.removes.diagnosticsDescription')}</p>
          <ul className="mt-3 space-y-2 text-sm">{[...legacyGroups.entries()].map(([key, group]) => <li key={key} className="break-words rounded-[12px] bg-white/70 p-3"><strong>{group.name || t('management.removes.unnamed')}</strong> · {group.code || t('management.missingCode')}<div>{t('management.removes.recordCounts', { records: group.count, items: group.items.size, active: group.active })}</div><div className="text-xs text-[var(--muted)]">{t('management.removes.itemIds')}: {[...group.items].sort((a, b) => a - b).join(', ')}</div></li>)}</ul>
          {catalog.conflicts.length ? <section className="mt-4 rounded-[18px] bg-amber-50 p-4" aria-label={t('management.removes.conflicts')}><h3 className="font-semibold">{t('management.removes.needsReview')}</h3><ul className="mt-3 space-y-2">{catalog.conflicts.map((conflict, index) => <li key={`${conflict.code}-${index}`} className="break-words rounded-[12px] bg-white/70 p-3 text-sm"><strong>{conflict.code || t('management.missingCode')}</strong><div>{[...conflict.names_zh, ...conflict.names_en].filter(Boolean).join(' / ')}</div><div className="text-xs text-[var(--muted)]">{conflict.reason} · {t('management.removes.recordCount', { count: conflict.option_ids.length })}</div></li>)}</ul></section> : null}
        </details> : null}
      </> : null}
    </section>
  )
}
