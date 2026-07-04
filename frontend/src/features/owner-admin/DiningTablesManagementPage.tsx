import { useEffect, useMemo, useState } from 'react'
import {
  fetchPlatformOverview,
  savePlatformEntity,
  type DiningTableRecord,
  type PlatformAdminOverview,
} from '../../services/platformAdminService'
import { useCurrentStore } from '../store/StoreContext'

type ToastState =
  | { kind: 'success' | 'error'; message: string }
  | null

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

function toDiningTableRecord(record: Record<string, unknown>, fallbackStoreId: number): DiningTableRecord {
  return {
    id: asNumber(record.id, 0) || undefined,
    store_id: asNumber(record.store_id, fallbackStoreId),
    table_code: asString(record.table_code),
    table_name: asString(record.table_name),
    area_name: asString(record.area_name),
    table_config: asString(record.table_config, 'single_only'),
    capacity: asNumber(record.capacity, 4),
    supports_split: asBoolean(record.supports_split, false),
    sort_order: asNumber(record.sort_order, 1),
    is_active: asBoolean(record.is_active, true),
  }
}

function buildDraft(storeId: number) {
  return {
    store_id: storeId,
    table_code: '',
    table_name: '',
    area_name: 'Main Hall',
    table_config: 'single_only',
    capacity: 4,
    supports_split: false,
    sort_order: 1,
    is_active: true,
  } satisfies DiningTableRecord
}

function sameSavedValues(expected: DiningTableRecord, actual: DiningTableRecord) {
  return (
    expected.table_code === actual.table_code &&
    expected.table_name === actual.table_name &&
    expected.area_name === actual.area_name &&
    expected.table_config === actual.table_config &&
    expected.capacity === actual.capacity &&
    expected.supports_split === actual.supports_split &&
    expected.sort_order === actual.sort_order &&
    expected.is_active === actual.is_active
  )
}

export function DiningTablesManagementPage() {
  const { storeId } = useCurrentStore()
  const [overview, setOverview] = useState<PlatformAdminOverview | null>(null)
  const [tables, setTables] = useState<DiningTableRecord[]>([])
  const [selectedStoreId, setSelectedStoreId] = useState(String(storeId))
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<ToastState>(null)
  const [searchTerm, setSearchTerm] = useState('')
  const [areaFilter, setAreaFilter] = useState('all')
  const [editor, setEditor] = useState<DiningTableRecord | null>(null)

  const loadOverview = async (storeId: number) => {
    setLoading(true)
    setError(null)
    try {
      const nextOverview = await fetchPlatformOverview(storeId)
      setOverview(nextOverview)
      setTables(
        (nextOverview.dining_tables ?? [])
          .map((record) => toDiningTableRecord(record, storeId))
          .filter((record) => record.store_id === storeId)
          .sort((left, right) => left.sort_order - right.sort_order || left.table_name.localeCompare(right.table_name)),
      )
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load dining tables')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    setSelectedStoreId(String(storeId))
  }, [storeId])

  useEffect(() => {
    void loadOverview(Number(selectedStoreId))
  }, [selectedStoreId])

  const stores = useMemo(
    () =>
      (overview?.stores ?? []).map((store) => ({
        id: String(store.id),
        label: asString(store.name, `Store ${store.id}`),
      })),
    [overview],
  )

  const areas = useMemo(() => {
    const uniqueAreas = new Set(
      tables
        .map((table) => table.area_name.trim())
        .filter(Boolean),
    )
    return [...uniqueAreas].sort((left, right) => left.localeCompare(right))
  }, [tables])

  const filteredTables = useMemo(() => {
    const query = searchTerm.trim().toLowerCase()
    return tables.filter((table) => {
      if (areaFilter !== 'all' && table.area_name !== areaFilter) {
        return false
      }
      if (!query) {
        return true
      }
      return [table.table_name, table.table_code, table.area_name]
        .map((value) => value.toLowerCase())
        .some((value) => value.includes(query))
    })
  }, [areaFilter, searchTerm, tables])

  const openCreate = () => {
    setToast(null)
    setEditor(buildDraft(Number(selectedStoreId)))
  }

  const openEdit = (table: DiningTableRecord) => {
    setToast(null)
    setEditor({ ...table })
  }

  const refreshTables = async (storeId: number) => {
    const nextOverview = await fetchPlatformOverview(storeId)
    setOverview(nextOverview)
    const nextTables = (nextOverview.dining_tables ?? [])
      .map((record) => toDiningTableRecord(record, storeId))
      .filter((record) => record.store_id === storeId)
      .sort((left, right) => left.sort_order - right.sort_order || left.table_name.localeCompare(right.table_name))
    setTables(nextTables)
    return nextTables
  }

  const handleSave = async () => {
    if (!editor) {
      return
    }

    try {
      setSaving(true)
      setToast(null)
      const saved = await savePlatformEntity('dining-tables', editor as unknown as Record<string, unknown>, editor.id)
      const persistedId = asNumber(saved.id ?? editor.id, 0)
      const reloadedTables = await refreshTables(Number(selectedStoreId))
      const persistedTable = reloadedTables.find((table) => table.id === persistedId)

      if (!persistedTable) {
        throw new Error('Saved table could not be reloaded from backend.')
      }

      if (!sameSavedValues(editor, persistedTable)) {
        throw new Error('Backend save verification failed. Please review the latest values and try again.')
      }

      setEditor(null)
      setToast({ kind: 'success', message: `Saved ${persistedTable.table_name || persistedTable.table_code}.` })
    } catch (saveError) {
      setToast({
        kind: 'error',
        message: saveError instanceof Error ? saveError.message : 'Failed to save dining table',
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-5">
            <div className="rounded-[28px] bg-[rgba(255,255,255,0.84)] px-5 py-4 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <div className="text-[1.7rem] font-black tracking-[-0.05em] text-[var(--on-surface)]">Dining Tables</div>
                  <div className="mt-1 text-[0.9rem] text-[var(--muted)]">
                    Add tables, adjust dining zones, and control split-table behavior for the frontdesk board.
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-3">
                  <div className="rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-2.5">
                    <div className="text-[0.72rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">Store</div>
                    <select
                      value={selectedStoreId}
                      onChange={(event) => setSelectedStoreId(event.target.value)}
                      className="mt-1 min-w-[220px] bg-transparent text-[0.95rem] font-semibold text-[var(--on-surface)] outline-none"
                    >
                      {stores.map((store) => (
                        <option key={store.id} value={store.id}>
                          {store.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  <button
                    type="button"
                    onClick={openCreate}
                    className="rounded-[18px] bg-[var(--primary)] px-4 py-3 text-[0.92rem] font-semibold text-white shadow-[0_14px_24px_rgba(97,0,0,0.16)]"
                  >
                    Add Table
                  </button>
                </div>
              </div>
            </div>

            {toast ? (
              <div
                className={`rounded-[20px] px-4 py-3 text-[0.9rem] font-semibold ${
                  toast.kind === 'success'
                    ? 'border border-[rgba(64,124,73,0.18)] bg-[rgba(64,124,73,0.08)] text-[rgb(48,96,56)]'
                    : 'border border-[rgba(97,0,0,0.18)] bg-[rgba(97,0,0,0.08)] text-[var(--primary)]'
                }`}
              >
                {toast.message}
              </div>
            ) : null}

            {error ? (
              <div className="rounded-[20px] border border-[rgba(97,0,0,0.18)] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.92rem] font-semibold text-[var(--primary)]">
                {error}
              </div>
            ) : null}

            <div className="grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_420px]">
              <div className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                <div className="flex flex-wrap items-end justify-between gap-3">
                  <div>
                    <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Configured Tables</div>
                    <div className="mt-1 text-[0.85rem] text-[var(--muted)]">Edit table labels, zone, capacity, and split support.</div>
                  </div>
                </div>

                <div className="mt-5 grid gap-3 xl:grid-cols-[minmax(0,1.6fr)_220px]">
                  <input
                    value={searchTerm}
                    onChange={(event) => setSearchTerm(event.target.value)}
                    placeholder="Search table name / code / area"
                    className="rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                  />

                  <select
                    value={areaFilter}
                    onChange={(event) => setAreaFilter(event.target.value)}
                    className="rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                  >
                    <option value="all">All Areas</option>
                    {areas.map((area) => (
                      <option key={area} value={area}>
                        {area}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="mt-5 overflow-hidden rounded-[22px] border border-[rgba(26,28,25,0.06)]">
                  <div className="grid grid-cols-[1.05fr_1.2fr_1fr_0.9fr_0.9fr_0.8fr] bg-[rgba(26,28,25,0.04)] px-4 py-3 text-[0.75rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">
                    <span>Code</span>
                    <span>Name</span>
                    <span>Area</span>
                    <span>Config</span>
                    <span>Capacity</span>
                    <span>Status</span>
                  </div>

                  <div className="divide-y divide-[rgba(26,28,25,0.06)] bg-white">
                    {filteredTables.map((table) => (
                      <button
                        key={table.id ?? table.table_code}
                        type="button"
                        onClick={() => openEdit(table)}
                        className="grid w-full grid-cols-[1.05fr_1.2fr_1fr_0.9fr_0.9fr_0.8fr] items-center gap-3 px-4 py-3 text-left transition hover:bg-[rgba(97,0,0,0.03)]"
                      >
                        <span className="text-[0.92rem] font-semibold text-[var(--on-surface)]">{table.table_code}</span>
                        <span className="text-[0.92rem] text-[var(--on-surface)]">{table.table_name}</span>
                        <span className="text-[0.84rem] text-[var(--muted)]">{table.area_name}</span>
                        <span className="text-[0.84rem] text-[var(--muted)]">{table.supports_split ? 'Split' : 'Single'}</span>
                        <span className="text-[0.84rem] text-[var(--muted)]">{table.capacity}</span>
                        <span className={`inline-flex w-fit rounded-full px-2.5 py-1 text-[0.76rem] font-semibold ${table.is_active ? 'bg-[rgba(18,141,77,0.1)] text-[rgb(25,112,69)]' : 'bg-[rgba(26,28,25,0.08)] text-[var(--muted)]'}`}>
                          {table.is_active ? 'Active' : 'Inactive'}
                        </span>
                      </button>
                    ))}

                    {!loading && !filteredTables.length ? (
                      <div className="px-4 py-6 text-[0.9rem] text-[var(--muted)]">
                        No dining tables match the current filters.
                      </div>
                    ) : null}
                  </div>
                </div>
              </div>

              <div className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">{editor?.id ? 'Edit Table' : 'Table Details'}</div>
                <div className="mt-1 text-[0.85rem] text-[var(--muted)]">
                  {editor ? 'Save verification runs after every backend update.' : 'Select a table row or add a new table to start editing.'}
                </div>

                {editor ? (
                  <div className="mt-5 space-y-4">
                    <label className="block text-[0.84rem] text-[var(--muted)]">
                      Table Code
                      <input
                        value={editor.table_code}
                        onChange={(event) => setEditor((current) => (current ? { ...current, table_code: event.target.value } : current))}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>

                    <label className="block text-[0.84rem] text-[var(--muted)]">
                      Table Name
                      <input
                        value={editor.table_name}
                        onChange={(event) => setEditor((current) => (current ? { ...current, table_name: event.target.value } : current))}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>

                    <label className="block text-[0.84rem] text-[var(--muted)]">
                      Area
                      <input
                        value={editor.area_name}
                        onChange={(event) => setEditor((current) => (current ? { ...current, area_name: event.target.value } : current))}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>

                    <div className="grid gap-3 md:grid-cols-2">
                      <label className="block text-[0.84rem] text-[var(--muted)]">
                        Table Config
                        <select
                          value={editor.table_config}
                          onChange={(event) =>
                            setEditor((current) =>
                              current
                                ? {
                                    ...current,
                                    table_config: event.target.value,
                                    supports_split: event.target.value === 'split_supported',
                                  }
                                : current,
                            )
                          }
                          className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                        >
                          <option value="single_only">Single Only</option>
                          <option value="split_supported">Split Supported</option>
                        </select>
                      </label>

                      <label className="block text-[0.84rem] text-[var(--muted)]">
                        Capacity
                        <input
                          type="number"
                          min={1}
                          value={editor.capacity}
                          onChange={(event) => setEditor((current) => (current ? { ...current, capacity: Number(event.target.value) } : current))}
                          className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                        />
                      </label>
                    </div>

                    <div className="grid gap-3 md:grid-cols-2">
                      <label className="block text-[0.84rem] text-[var(--muted)]">
                        Sort Order
                        <input
                          type="number"
                          min={1}
                          value={editor.sort_order}
                          onChange={(event) => setEditor((current) => (current ? { ...current, sort_order: Number(event.target.value) } : current))}
                          className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                        />
                      </label>

                      <div className="flex items-center gap-4 pt-7">
                        <label className="inline-flex items-center gap-2 text-[0.88rem] font-medium text-[var(--on-surface)]">
                          <input
                            type="checkbox"
                            checked={editor.supports_split}
                            onChange={(event) =>
                              setEditor((current) =>
                                current
                                  ? {
                                      ...current,
                                      supports_split: event.target.checked,
                                      table_config: event.target.checked ? 'split_supported' : 'single_only',
                                    }
                                  : current,
                              )
                            }
                          />
                          Supports split
                        </label>
                        <label className="inline-flex items-center gap-2 text-[0.88rem] font-medium text-[var(--on-surface)]">
                          <input
                            type="checkbox"
                            checked={editor.is_active}
                            onChange={(event) => setEditor((current) => (current ? { ...current, is_active: event.target.checked } : current))}
                          />
                          Active
                        </label>
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-3 pt-2">
                      <button
                        type="button"
                        onClick={handleSave}
                        disabled={saving}
                        className="rounded-[16px] bg-[var(--primary)] px-4 py-3 text-[0.92rem] font-semibold text-white shadow-[0_14px_24px_rgba(97,0,0,0.16)] disabled:opacity-60"
                      >
                        {saving ? 'Saving...' : editor.id ? 'Save Table' : 'Create Table'}
                      </button>
                      <button
                        type="button"
                        onClick={() => setEditor(null)}
                        className="rounded-[16px] bg-[rgba(26,28,25,0.06)] px-4 py-3 text-[0.92rem] font-semibold text-[var(--on-surface)]"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="mt-5 rounded-[20px] border border-dashed border-[rgba(97,0,0,0.16)] bg-[rgba(97,0,0,0.03)] px-4 py-6 text-[0.9rem] text-[var(--primary)]">
                    Create a new table or choose one from the list to edit zone, split behavior, and frontdesk visibility.
                  </div>
                )}
              </div>
            </div>
    </div>
  )
}
