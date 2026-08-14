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
      setToast('Category saved. Pads will refresh after menu revision update.')
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
      setToast('Empty category deleted.')
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
          <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Categories / 分类</div>
          <div className="mt-1 text-[0.85rem] text-[var(--muted)]">Names, ordering, enable/disable, and safe delete.</div>
        </div>
        <button type="button" onClick={openNew} className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white">
          + New Category
        </button>
      </div>

      <div className="mt-4 grid gap-2">
        {categories.map((category) => (
          <div key={category.id ?? category.code} className="flex flex-wrap items-center justify-between gap-2 rounded-[16px] bg-[rgba(26,28,25,0.035)] px-3 py-3">
            <div>
              <div className="font-semibold text-[var(--on-surface)]">{category.name_zh} / {category.name_en}</div>
              <div className="text-[0.74rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{category.code} · order {category.sort_order ?? 0} · {category.is_active ? 'Enabled' : 'Disabled'}</div>
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => setDraft({ ...category })} className="rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)]">Edit</button>
              <button type="button" onClick={() => void remove(category)} disabled={saving} className="rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)] disabled:opacity-60">Delete</button>
            </div>
          </div>
        ))}
      </div>

      {draft ? (
        <div className="mt-4 grid gap-3 rounded-[18px] bg-[rgba(26,28,25,0.035)] p-4 md:grid-cols-2">
          <input value={draft.name_zh} onChange={(event) => setDraft({ ...draft, name_zh: event.target.value })} placeholder="Chinese name" className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" />
          <input value={draft.name_en} onChange={(event) => setDraft({ ...draft, name_en: event.target.value })} placeholder="English name" className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" />
          <input type="number" value={draft.sort_order ?? 0} onChange={(event) => setDraft({ ...draft, sort_order: Number(event.target.value) })} className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" aria-label="Category display order" />
          <button type="button" onClick={() => setDraft({ ...draft, is_active: !draft.is_active })} className="rounded-[14px] bg-white px-3 py-2 text-[0.88rem] font-semibold text-[var(--on-surface)]">
            {draft.is_active ? 'Enabled' : 'Disabled'}
          </button>
          <div className="flex gap-2 md:col-span-2">
            <button type="button" onClick={() => void save()} disabled={saving} className="rounded-[14px] bg-[var(--primary)] px-4 py-2 text-[0.88rem] font-semibold text-white disabled:opacity-60">Save</button>
            <button type="button" onClick={() => setDraft(null)} className="rounded-[14px] bg-white px-4 py-2 text-[0.88rem] font-semibold text-[var(--on-surface)]">Cancel</button>
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
      setToast('Station saved. Routing and menu revision were updated.')
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
      setToast('Unused station deleted.')
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
          <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Stations / 出餐站点</div>
          <div className="mt-1 text-[0.85rem] text-[var(--muted)]">Station names, type, ordering, safe enable/disable, and routing guards.</div>
        </div>
        <button type="button" onClick={openNew} className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white">
          + New Station
        </button>
      </div>

      <div className="mt-4 grid gap-2">
        {stations.map((station) => (
          <div key={station.id ?? station.code} className="flex flex-wrap items-center justify-between gap-2 rounded-[16px] bg-[rgba(26,28,25,0.035)] px-3 py-3">
            <div>
              <div className="font-semibold text-[var(--on-surface)]">{station.name_zh ?? station.name} / {station.name_en ?? station.name}</div>
              <div className="text-[0.74rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{station.code} · {station.station_type ?? 'KITCHEN'} · order {station.sort_order ?? 0} · {station.is_active ? 'Enabled' : 'Disabled'}</div>
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => setDraft({ ...station })} className="rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)]">Edit</button>
              <button type="button" onClick={() => void remove(station)} disabled={saving} className="rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)] disabled:opacity-60">Delete</button>
            </div>
          </div>
        ))}
      </div>

      {draft ? (
        <div className="mt-4 grid gap-3 rounded-[18px] bg-[rgba(26,28,25,0.035)] p-4 md:grid-cols-2">
          <input value={draft.name_zh ?? ''} onChange={(event) => setDraft({ ...draft, name_zh: event.target.value })} placeholder="Chinese name" className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" />
          <input value={draft.name_en ?? ''} onChange={(event) => setDraft({ ...draft, name_en: event.target.value })} placeholder="English name" className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" />
          <select value={draft.station_type ?? 'KITCHEN'} onChange={(event) => setDraft({ ...draft, station_type: event.target.value })} className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none">
            <option value="KITCHEN">Kitchen</option>
            <option value="COLD">Cold</option>
            <option value="BAR">Bar</option>
            <option value="PASS">Pass</option>
            <option value="OTHER">Other</option>
          </select>
          <input type="number" value={draft.sort_order ?? 0} onChange={(event) => setDraft({ ...draft, sort_order: Number(event.target.value) })} className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none" aria-label="Station display order" />
          <button type="button" onClick={() => setDraft({ ...draft, is_active: !draft.is_active })} className="rounded-[14px] bg-white px-3 py-2 text-[0.88rem] font-semibold text-[var(--on-surface)]">
            {draft.is_active ? 'Enabled' : 'Disabled'}
          </button>
          <div className="flex gap-2 md:col-span-2">
            <button type="button" onClick={() => void save()} disabled={saving} className="rounded-[14px] bg-[var(--primary)] px-4 py-2 text-[0.88rem] font-semibold text-white disabled:opacity-60">Save</button>
            <button type="button" onClick={() => setDraft(null)} className="rounded-[14px] bg-white px-4 py-2 text-[0.88rem] font-semibold text-[var(--on-surface)]">Cancel</button>
          </div>
        </div>
      ) : null}
    </section>
  )
}
