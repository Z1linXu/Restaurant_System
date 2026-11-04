import { useLocale } from '../../i18n/useLocale'
import { useMemo, useState } from 'react'
import {
  createMenuCategory,
  createMenuStation,
  deleteMenuCategory,
  deleteMenuStation,
  updateMenuCategory,
  updateMenuStation,
  type MenuCategoryAdminRecord,
  type StationAdminRecord,
} from '../../services/platformAdminService'

type ToastSetter = (message: string, kind?: 'success' | 'error') => void

function asNumber(value: unknown, fallback = 0) {
  const next = Number(value)
  return Number.isFinite(next) ? next : fallback
}

function asString(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback
}

function asBoolean(value: unknown, fallback = false) {
  return typeof value === 'boolean' ? value : fallback
}

function categoryRecord(record: Record<string, unknown>): MenuCategoryAdminRecord {
  return {
    id: asNumber(record.id, 0) || undefined,
    store_id: asNumber(record.store_id, 0),
    code: asString(record.code),
    name_zh: asString(record.name_zh),
    name_en: asString(record.name_en),
    name_fr: asString(record.name_fr),
    sort_order: record.sort_order == null ? null : asNumber(record.sort_order),
    is_active: asBoolean(record.is_active, true),
  }
}

function stationRecord(record: Record<string, unknown>): StationAdminRecord {
  return {
    id: asNumber(record.id, 0) || undefined,
    store_id: asNumber(record.store_id, 0),
    code: asString(record.code),
    name: asString(record.name),
    name_zh: asString(record.name_zh, asString(record.name)),
    name_en: asString(record.name_en, asString(record.name)),
    station_type: asString(record.station_type, 'KITCHEN'),
    sort_order: record.sort_order == null ? null : asNumber(record.sort_order),
    is_active: asBoolean(record.is_active, true),
  }
}

function nextSortOrder(values: Array<{ sort_order: number | null }>) {
  return (values.reduce((max, value) => Math.max(max, value.sort_order ?? 0), 0) || values.length * 10) + 10
}

export function CategoryManagementPanel({
  storeId,
  records,
  onChanged,
  setToast,
}: {
  storeId: number
  records: Record<string, unknown>[]
  onChanged: () => Promise<void>
  setToast: ToastSetter
}) {
  const { t, label } = useLocale()
  const categories = useMemo(
    () => records.map(categoryRecord).filter((category) => category.store_id === storeId),
    [records, storeId],
  )
  const [draft, setDraft] = useState<MenuCategoryAdminRecord | null>(null)
  const [saving, setSaving] = useState(false)

  const openNew = () => setDraft({
    store_id: storeId,
    code: '',
    name_zh: '',
    name_en: '',
    name_fr: '',
    sort_order: nextSortOrder(categories),
    is_active: true,
  })

  const save = async () => {
    if (!draft) return
    try {
      setSaving(true)
      const payload = {
        store_id: storeId,
        name_zh: draft.name_zh,
        name_en: draft.name_en,
        name_fr: draft.name_fr,
        sort_order: draft.sort_order ?? 0,
        enabled: draft.is_active,
      }
      if (draft.id) {
        await updateMenuCategory(storeId, draft.id, payload)
      } else {
        await createMenuCategory(storeId, payload)
      }
      setDraft(null)
      await onChanged()
      setToast(t('structure.categorySaved'))
    } catch (error) {
      setToast(error instanceof Error ? error.message : 'Failed to save category', 'error')
    } finally {
      setSaving(false)
    }
  }

  const remove = async (category: MenuCategoryAdminRecord) => {
    if (!category.id) return
    try {
      setSaving(true)
      await deleteMenuCategory(storeId, category.id)
      await onChanged()
      setToast(t('structure.categoryDeleted'))
    } catch (error) {
      setToast(error instanceof Error ? error.message : 'Category delete rejected', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">{t('menu.categories')}</div>
          <div className="mt-1 text-[0.85rem] text-[var(--muted)]">{t('structure.hint')}</div>
        </div>
        <button type="button" onClick={openNew} className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white">
          {t('structure.newCategory')}
        </button>
      </div>

      <div className="mt-4 grid gap-2">
        {categories.map((category) => (
          <div key={category.id ?? category.code} className="flex flex-wrap items-center justify-between gap-2 rounded-[16px] bg-[rgba(26,28,25,0.035)] px-3 py-3">
            <div>
              <div className="font-semibold text-[var(--on-surface)]">{label({ en: category.name_en, zh: category.name_zh, fr: category.name_fr })}</div>
              <div className="text-[0.74rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{category.code} · order {category.sort_order ?? 0} · {category.is_active ? t('common.enabled') : t('common.disabled')}</div>
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => setDraft({ ...category })} className="rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)]">{t('common.edit')}</button>
              <button type="button" onClick={() => void remove(category)} disabled={saving} className="rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)] disabled:opacity-60">{t('structure.delete')}</button>
            </div>
          </div>
        ))}
      </div>

      {draft ? (
        <div className="mt-4 grid gap-3 rounded-[18px] bg-[rgba(26,28,25,0.035)] p-4 md:grid-cols-2">
          <input value={draft.name_zh} onChange={(event) => setDraft({ ...draft, name_zh: event.target.value })} placeholder={t('common.chinese')} className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" />
          <input value={draft.name_en} onChange={(event) => setDraft({ ...draft, name_en: event.target.value })} placeholder={t('common.english')} className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" />
          <input value={draft.name_fr ?? ''} onChange={(event) => setDraft({ ...draft, name_fr: event.target.value })} placeholder={t('common.french')} className="rounded-[14px] border border-stone-200 bg-white px-3 py-2 text-sm" />
          <input type="number" value={draft.sort_order ?? 0} onChange={(event) => setDraft({ ...draft, sort_order: Number(event.target.value) })} className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" aria-label="Category display order" />
          <button type="button" onClick={() => setDraft({ ...draft, is_active: !draft.is_active })} className="rounded-[14px] bg-white px-3 py-2 text-[0.88rem] font-semibold text-[var(--on-surface)]">
            {draft.is_active ? t('common.enabled') : t('common.disabled')}
          </button>
          <div className="flex gap-2 md:col-span-2">
            <button type="button" onClick={() => void save()} disabled={saving} className="rounded-[14px] bg-[var(--primary)] px-4 py-2 text-[0.88rem] font-semibold text-white disabled:opacity-60">{t('common.save')}</button>
            <button type="button" onClick={() => setDraft(null)} className="rounded-[14px] bg-white px-4 py-2 text-[0.88rem] font-semibold text-[var(--on-surface)]">{t('common.cancel')}</button>
          </div>
        </div>
      ) : null}
    </section>
  )
}

export function StationManagementPanel({
  storeId,
  records,
  onChanged,
  setToast,
}: {
  storeId: number
  records: Record<string, unknown>[]
  onChanged: () => Promise<void>
  setToast: ToastSetter
}) {
  const { t, label } = useLocale()
  const stations = useMemo(
    () => records.map(stationRecord).filter((station) => station.store_id === storeId),
    [records, storeId],
  )
  const [draft, setDraft] = useState<StationAdminRecord | null>(null)
  const [saving, setSaving] = useState(false)

  const openNew = () => setDraft({
    store_id: storeId,
    code: '',
    name: '',
    name_zh: '',
    name_en: '',
    station_type: 'KITCHEN',
    sort_order: nextSortOrder(stations),
    is_active: true,
  })

  const save = async () => {
    if (!draft) return
    try {
      setSaving(true)
      const payload = {
        store_id: storeId,
        name_zh: draft.name_zh ?? draft.name,
        name_en: draft.name_en ?? draft.name,
        station_type: draft.station_type ?? 'KITCHEN',
        sort_order: draft.sort_order ?? 0,
        enabled: draft.is_active,
      }
      if (draft.id) {
        await updateMenuStation(storeId, draft.id, payload)
      } else {
        await createMenuStation(storeId, payload)
      }
      setDraft(null)
      await onChanged()
      setToast(t('structure.stationSaved'))
    } catch (error) {
      setToast(error instanceof Error ? error.message : 'Failed to save station', 'error')
    } finally {
      setSaving(false)
    }
  }

  const remove = async (station: StationAdminRecord) => {
    if (!station.id) return
    try {
      setSaving(true)
      await deleteMenuStation(storeId, station.id)
      await onChanged()
      setToast(t('structure.stationDeleted'))
    } catch (error) {
      setToast(error instanceof Error ? error.message : 'Station delete rejected', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">{t('menu.stations')}</div>
          <div className="mt-1 text-[0.85rem] text-[var(--muted)]">{t('structure.stationHint')} {t('structure.stationPrintingHint')}</div>
        </div>
        <button type="button" onClick={openNew} className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white">
          {t('structure.newStation')}
        </button>
      </div>

      <div className="mt-4 grid gap-2">
        {stations.map((station) => (
          <div key={station.id ?? station.code} className="flex flex-wrap items-center justify-between gap-2 rounded-[16px] bg-[rgba(26,28,25,0.035)] px-3 py-3">
            <div>
              <div className="font-semibold text-[var(--on-surface)]">{label({ en: station.name_en, zh: station.name_zh, name: station.name })}</div>
              <div className="text-[0.74rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{station.code} · {station.station_type ?? 'KITCHEN'} · order {station.sort_order ?? 0} · {station.is_active ? t('common.enabled') : t('common.disabled')}</div>
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => setDraft({ ...station })} className="rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)]">{t('common.edit')}</button>
              <button type="button" onClick={() => void remove(station)} disabled={saving} className="rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)] disabled:opacity-60">{t('structure.delete')}</button>
            </div>
          </div>
        ))}
      </div>

      {draft ? (
        <div className="mt-4 grid gap-3 rounded-[18px] bg-[rgba(26,28,25,0.035)] p-4 md:grid-cols-2">
          <input value={draft.name_zh ?? ''} onChange={(event) => setDraft({ ...draft, name_zh: event.target.value })} placeholder={t('common.chinese')} className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" />
          <input value={draft.name_en ?? ''} onChange={(event) => setDraft({ ...draft, name_en: event.target.value })} placeholder={t('common.english')} className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" />
          <select value={draft.station_type ?? 'KITCHEN'} onChange={(event) => setDraft({ ...draft, station_type: event.target.value })} className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none">
            <option value="KITCHEN">{t('structure.kitchen')}</option>
            <option value="COLD">{t('structure.cold')}</option>
            <option value="BAR">{t('structure.bar')}</option>
            <option value="PASS">{t('structure.pass')}</option>
            <option value="OTHER">{t('structure.other')}</option>
          </select>
          <input type="number" value={draft.sort_order ?? 0} onChange={(event) => setDraft({ ...draft, sort_order: Number(event.target.value) })} className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" aria-label="Station display order" />
          <button type="button" onClick={() => setDraft({ ...draft, is_active: !draft.is_active })} className="rounded-[14px] bg-white px-3 py-2 text-[0.88rem] font-semibold text-[var(--on-surface)]">
            {draft.is_active ? t('common.enabled') : t('common.disabled')}
          </button>
          <div className="flex gap-2 md:col-span-2">
            <button type="button" onClick={() => void save()} disabled={saving} className="rounded-[14px] bg-[var(--primary)] px-4 py-2 text-[0.88rem] font-semibold text-white disabled:opacity-60">{t('common.save')}</button>
            <button type="button" onClick={() => setDraft(null)} className="rounded-[14px] bg-white px-4 py-2 text-[0.88rem] font-semibold text-[var(--on-surface)]">{t('common.cancel')}</button>
          </div>
        </div>
      ) : null}
    </section>
  )
}
