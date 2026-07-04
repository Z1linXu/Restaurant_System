import { useEffect, useMemo, useState } from 'react'
import {
  fetchAdminMenuItems,
  fetchPlatformOverview,
  rebuildAnalyticsForDate,
  savePlatformEntity,
  type MenuItemAdminRecord,
  type PlatformAdminOverview,
} from '../../services/platformAdminService'
import { MenuOptionsPanel } from './MenuOptionsPanel'
import { useCurrentStore } from '../store/StoreContext'

interface MenuItemEditorState {
  id?: number
  store_id: number
  category_id: number
  station_id: number
  name_zh: string
  name_en: string
  sku: string
  item_type: string
  base_price: number
  cost_per_item: number
  is_active: boolean
  is_sold_out: boolean
}

type ToastState =
  | { kind: 'success' | 'error'; message: string }
  | null

type StatusFilter = 'all' | 'active' | 'inactive' | 'sold_out' | 'available'

function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency: 'CAD',
    minimumFractionDigits: 2,
  }).format(value)
}

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

function normalizeMoney(value: number) {
  return Number(value.toFixed(2))
}

function buildDraft(overview: PlatformAdminOverview, storeId: number): MenuItemEditorState {
  const defaultCategory = overview.menu_categories.find((category) => Number(category.store_id) === storeId)
  const defaultStation = overview.stations.find((station) => Number(station.store_id) === storeId)

  return {
    store_id: storeId,
    category_id: asNumber(defaultCategory?.id, 0),
    station_id: asNumber(defaultStation?.id, 0),
    name_zh: '',
    name_en: '',
    sku: '',
    item_type: 'menu_item',
    base_price: 0,
    cost_per_item: 0,
    is_active: true,
    is_sold_out: false,
  }
}

function toEditorState(record: Record<string, unknown> | MenuItemAdminRecord, fallbackStoreId: number): MenuItemEditorState {
  return {
    id: asNumber(record.id, 0) || undefined,
    store_id: asNumber(record.store_id, fallbackStoreId),
    category_id: asNumber(record.category_id, 0),
    station_id: asNumber(record.station_id, 0),
    name_zh: asString(record.name_zh),
    name_en: asString(record.name_en),
    sku: asString(record.sku),
    item_type: asString(record.item_type, 'menu_item'),
    base_price: asNumber(record.base_price, 0),
    cost_per_item: asNumber(record.cost_per_item, 0),
    is_active: asBoolean(record.is_active, true),
    is_sold_out: asBoolean(record.is_sold_out, false),
  }
}

function sameSavedValues(expected: MenuItemEditorState, actual: MenuItemEditorState) {
  return (
    normalizeMoney(expected.base_price) === normalizeMoney(actual.base_price) &&
    normalizeMoney(expected.cost_per_item) === normalizeMoney(actual.cost_per_item) &&
    expected.is_active === actual.is_active &&
    expected.is_sold_out === actual.is_sold_out &&
    expected.category_id === actual.category_id &&
    expected.station_id === actual.station_id
  )
}

export function MenuManagementPage() {
  const { storeId } = useCurrentStore()
  const [overview, setOverview] = useState<PlatformAdminOverview | null>(null)
  const [menuItems, setMenuItems] = useState<MenuItemEditorState[]>([])
  const [selectedStoreId, setSelectedStoreId] = useState(String(storeId))
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [rebuilding, setRebuilding] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [editor, setEditor] = useState<MenuItemEditorState | null>(null)
  const [optionsItem, setOptionsItem] = useState<MenuItemEditorState | null>(null)
  const [analyticsNotice, setAnalyticsNotice] = useState<string | null>(null)
  const [toast, setToast] = useState<ToastState>(null)
  const [searchTerm, setSearchTerm] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('all')
  const [stationFilter, setStationFilter] = useState('all')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')
  const [rebuildDate, setRebuildDate] = useState(new Date().toISOString().slice(0, 10))

  const loadOverview = async (storeId: number) => {
    setLoading(true)
    setError(null)
    try {
      const [nextOverview, nextMenuItems] = await Promise.all([
        fetchPlatformOverview(storeId),
        fetchAdminMenuItems(storeId),
      ])
      setOverview(nextOverview)
      setMenuItems(
        nextMenuItems
          .map((record) => toEditorState(record, storeId))
          .sort((left, right) => left.name_zh.localeCompare(right.name_zh, 'zh-Hans')),
      )
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load menu management data')
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
    () => (overview?.stores ?? []).map((store) => ({
      id: String(store.id),
      label: asString(store.name, `Store ${store.id}`),
    })),
    [overview],
  )

  const categories = useMemo(
    () =>
      (overview?.menu_categories ?? [])
        .filter((category) => Number(category.store_id) === Number(selectedStoreId))
        .map((category) => ({
          id: asNumber(category.id),
          label: `${asString(category.name_zh)} / ${asString(category.name_en)}`,
        })),
    [overview, selectedStoreId],
  )

  const stations = useMemo(
    () =>
      (overview?.stations ?? [])
        .filter((station) => Number(station.store_id) === Number(selectedStoreId))
        .map((station) => ({
          id: asNumber(station.id),
          label: `${asString(station.name)} (${asString(station.code)})`,
        })),
    [overview, selectedStoreId],
  )

  const categoryNameById = useMemo(
    () => new Map(categories.map((category) => [category.id, category.label])),
    [categories],
  )

  const stationNameById = useMemo(
    () => new Map(stations.map((station) => [station.id, station.label])),
    [stations],
  )

  const filteredMenuItems = useMemo(() => {
    const query = searchTerm.trim().toLowerCase()

    return menuItems.filter((item) => {
      if (categoryFilter !== 'all' && String(item.category_id) !== categoryFilter) {
        return false
      }

      if (stationFilter !== 'all' && String(item.station_id) !== stationFilter) {
        return false
      }

      if (statusFilter === 'active' && !item.is_active) {
        return false
      }
      if (statusFilter === 'inactive' && item.is_active) {
        return false
      }
      if (statusFilter === 'sold_out' && !item.is_sold_out) {
        return false
      }
      if (statusFilter === 'available' && item.is_sold_out) {
        return false
      }

      if (!query) {
        return true
      }

      return [item.name_zh, item.name_en, item.sku]
        .map((value) => value.toLowerCase())
        .some((value) => value.includes(query))
    })
  }, [categoryFilter, menuItems, searchTerm, stationFilter, statusFilter])

  const openCreate = () => {
    if (!overview) {
      return
    }
    setToast(null)
    setEditor(buildDraft(overview, Number(selectedStoreId)))
  }

  const openEdit = (item: MenuItemEditorState) => {
    setToast(null)
    setEditor({ ...item })
  }

  const openOptions = (item: MenuItemEditorState) => {
    setToast(null)
    setOptionsItem(item)
  }

  const refreshMenuItems = async (storeId: number) => {
    const nextMenuItems = await fetchAdminMenuItems(storeId)
    const normalized = nextMenuItems
      .map((record) => toEditorState(record, storeId))
      .sort((left, right) => left.name_zh.localeCompare(right.name_zh, 'zh-Hans'))
    setMenuItems(normalized)
    return normalized
  }

  const handleSave = async () => {
    if (!editor) {
      return
    }

    const previous = menuItems.find((item) => item.id === editor.id)
    const priceChanged = previous == null ? editor.base_price > 0 : normalizeMoney(previous.base_price) !== normalizeMoney(editor.base_price)
    const costChanged = previous == null ? editor.cost_per_item > 0 : normalizeMoney(previous.cost_per_item) !== normalizeMoney(editor.cost_per_item)

    try {
      setSaving(true)
      setToast(null)

      const saved = await savePlatformEntity(
        'menu/items',
        {
          ...editor,
          base_price: normalizeMoney(Number(editor.base_price)),
          cost_per_item: normalizeMoney(Number(editor.cost_per_item)),
        },
        editor.id,
      )

      const persistedId = asNumber(saved.id ?? editor.id, 0)
      const reloadedItems = await refreshMenuItems(Number(selectedStoreId))
      const persistedItem = reloadedItems.find((item) => item.id === persistedId)

      if (!persistedItem) {
        throw new Error('Saved item could not be reloaded from backend.')
      }

      if (!sameSavedValues(editor, persistedItem)) {
        throw new Error('Backend save verification failed. Please review the latest values and try again.')
      }

      setEditor(null)
      setToast({ kind: 'success', message: `Saved ${persistedItem.name_zh || persistedItem.name_en || 'menu item'}.` })
      setAnalyticsNotice(
        priceChanged || costChanged
          ? 'Price or cost changes affect future orders immediately. Historical profit reports require analytics rebuild.'
          : null,
      )
    } catch (saveError) {
      setToast({
        kind: 'error',
        message: saveError instanceof Error ? saveError.message : 'Failed to save menu item',
      })
    } finally {
      setSaving(false)
    }
  }

  const handleQuickToggle = async (
    item: MenuItemEditorState,
    patch: Partial<Pick<MenuItemEditorState, 'is_active' | 'is_sold_out'>>,
  ) => {
    try {
      setToast(null)
      await savePlatformEntity(
        'menu/items',
        {
          ...item,
          ...patch,
          base_price: normalizeMoney(item.base_price),
          cost_per_item: normalizeMoney(item.cost_per_item),
        },
        item.id,
      )

      const reloadedItems = await refreshMenuItems(Number(selectedStoreId))
      const persistedItem = reloadedItems.find((candidate) => candidate.id === item.id)
      if (!persistedItem) {
        throw new Error('Menu item could not be reloaded after update.')
      }

      if (
        (patch.is_active != null && persistedItem.is_active !== patch.is_active) ||
        (patch.is_sold_out != null && persistedItem.is_sold_out !== patch.is_sold_out)
      ) {
        throw new Error('Backend update verification failed.')
      }

      if (editor?.id === persistedItem.id) {
        setEditor({ ...persistedItem })
      }

      setToast({
        kind: 'success',
        message: `${persistedItem.name_zh || persistedItem.name_en || 'Item'} updated.`,
      })
    } catch (toggleError) {
      setToast({
        kind: 'error',
        message: toggleError instanceof Error ? toggleError.message : 'Failed to update menu item',
      })
    }
  }

  const handleEditorActiveAction = async () => {
    if (!editor?.id) {
      return
    }

    const nextActive = !editor.is_active
    if (!nextActive) {
      const confirmed = window.confirm(
        [
          `Deactivate menu item "${editor.name_zh || '-'} / ${editor.name_en || '-'}"?`,
          editor.sku ? `SKU: ${editor.sku}` : '',
          'This will hide the item from future ordering but will not affect historical orders.',
        ].filter(Boolean).join('\n'),
      )
      if (!confirmed) {
        return
      }
    }

    try {
      setSaving(true)
      setToast(null)
      const savedPayload = {
        ...editor,
        is_active: nextActive,
        base_price: normalizeMoney(editor.base_price),
        cost_per_item: normalizeMoney(editor.cost_per_item),
      }
      await savePlatformEntity('menu/items', savedPayload, editor.id)

      const reloadedItems = await refreshMenuItems(Number(selectedStoreId))
      const persistedItem = reloadedItems.find((item) => item.id === editor.id)
      if (!persistedItem || persistedItem.is_active !== nextActive) {
        throw new Error('Backend status verification failed.')
      }

      setEditor({ ...persistedItem })
      setToast({
        kind: 'success',
        message: `${persistedItem.name_zh || persistedItem.name_en || 'Item'} ${nextActive ? 'reactivated' : 'deactivated'}.`,
      })
    } catch (actionError) {
      setToast({
        kind: 'error',
        message: actionError instanceof Error ? actionError.message : 'Failed to update menu item status',
      })
    } finally {
      setSaving(false)
    }
  }

  const handleRebuildAnalytics = async () => {
    if (!rebuildDate) {
      setToast({ kind: 'error', message: 'Select a rebuild date first.' })
      return
    }

    try {
      setRebuilding(true)
      setToast(null)
      await rebuildAnalyticsForDate(rebuildDate, Number(selectedStoreId))
      setToast({ kind: 'success', message: `Analytics rebuild triggered for ${rebuildDate}.` })
    } catch (rebuildError) {
      setToast({
        kind: 'error',
        message: rebuildError instanceof Error ? rebuildError.message : 'Failed to rebuild analytics',
      })
    } finally {
      setRebuilding(false)
    }
  }

  return (
    <div className="space-y-5">
            <div className="rounded-[28px] bg-[rgba(255,255,255,0.84)] px-5 py-4 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <div className="text-[1.7rem] font-black tracking-[-0.05em] text-[var(--on-surface)]">Menu Management</div>
                  <div className="mt-1 text-[0.9rem] text-[var(--muted)]">
                    Maintain menu items, selling prices, and cost data used by profit analytics.
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
                    New Menu Item
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

            {analyticsNotice ? (
              <div className="rounded-[20px] border border-[rgba(191,104,32,0.18)] bg-[rgba(191,104,32,0.08)] px-4 py-4 text-[0.9rem] text-[rgb(140,76,17)]">
                <div className="font-semibold">
                  Price or cost changes affect future orders immediately. Historical profit reports require analytics rebuild.
                </div>
                <div className="mt-3 flex flex-wrap items-center gap-3">
                  <input
                    type="date"
                    value={rebuildDate}
                    onChange={(event) => setRebuildDate(event.target.value)}
                    className="rounded-[14px] border border-[rgba(191,104,32,0.18)] bg-white px-3 py-2 text-[0.88rem] text-[var(--on-surface)] outline-none"
                  />
                  <button
                    type="button"
                    onClick={handleRebuildAnalytics}
                    disabled={rebuilding}
                    className="rounded-[14px] bg-[rgb(140,76,17)] px-3.5 py-2 text-[0.88rem] font-semibold text-white disabled:opacity-60"
                  >
                    {rebuilding ? 'Rebuilding...' : 'Rebuild Analytics'}
                  </button>
                </div>
              </div>
            ) : null}

            {error ? (
              <div className="rounded-[20px] border border-[rgba(97,0,0,0.18)] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.92rem] font-semibold text-[var(--primary)]">
                {error}
              </div>
            ) : null}

            <div className="grid gap-5 xl:grid-cols-[minmax(0,1.55fr)_420px]">
              <div className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                <div className="flex flex-wrap items-end justify-between gap-3">
                  <div>
                    <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Menu Items</div>
                    <div className="mt-1 text-[0.85rem] text-[var(--muted)]">Edit price, cost, availability, category, and station assignments.</div>
                  </div>
                </div>

                <div className="mt-5 grid gap-3 xl:grid-cols-[minmax(0,1.5fr)_220px_220px_180px]">
                  <input
                    value={searchTerm}
                    onChange={(event) => setSearchTerm(event.target.value)}
                    placeholder="Search name or SKU"
                    className="rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                  />

                  <select
                    value={categoryFilter}
                    onChange={(event) => setCategoryFilter(event.target.value)}
                    className="rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                  >
                    <option value="all">All Categories</option>
                    {categories.map((category) => (
                      <option key={category.id} value={String(category.id)}>
                        {category.label}
                      </option>
                    ))}
                  </select>

                  <select
                    value={stationFilter}
                    onChange={(event) => setStationFilter(event.target.value)}
                    className="rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                  >
                    <option value="all">All Stations</option>
                    {stations.map((station) => (
                      <option key={station.id} value={String(station.id)}>
                        {station.label}
                      </option>
                    ))}
                  </select>

                  <select
                    value={statusFilter}
                    onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
                    className="rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                  >
                    <option value="all">All Status</option>
                    <option value="active">Active</option>
                    <option value="inactive">Inactive</option>
                    <option value="sold_out">Sold Out</option>
                    <option value="available">Available</option>
                  </select>
                </div>

                {loading ? (
                  <div className="mt-5 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
                    Loading menu items...
                  </div>
                ) : filteredMenuItems.length ? (
                  <div className="mt-5 overflow-auto rounded-[18px] border border-[rgba(26,28,25,0.06)]">
                    <table className="min-w-full text-left text-[0.9rem]">
                      <thead className="bg-[rgba(246,243,236,0.94)] text-[0.76rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                        <tr>
                          <th className="px-4 py-3">Item</th>
                          <th className="px-4 py-3">Category</th>
                          <th className="px-4 py-3">Station</th>
                          <th className="px-4 py-3">Price</th>
                          <th className="px-4 py-3">Cost</th>
                          <th className="px-4 py-3">Status</th>
                          <th className="px-4 py-3"></th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredMenuItems.map((item) => (
                          <tr key={item.id ?? item.sku ?? item.name_zh} className="border-t border-[rgba(26,28,25,0.06)]">
                            <td className="px-4 py-3">
                              <div className="font-semibold text-[var(--on-surface)]">{item.name_zh}</div>
                              <div className="text-[0.78rem] text-[var(--muted)]">{item.name_en || '—'} {item.sku ? `· ${item.sku}` : ''}</div>
                            </td>
                            <td className="px-4 py-3 text-[var(--muted)]">{categoryNameById.get(item.category_id) ?? 'Unassigned'}</td>
                            <td className="px-4 py-3 text-[var(--muted)]">{stationNameById.get(item.station_id) ?? 'Unassigned'}</td>
                            <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(item.base_price)}</td>
                            <td className="px-4 py-3 text-[var(--on-surface)]">{formatCurrency(item.cost_per_item)}</td>
                            <td className="px-4 py-3">
                              <div className="flex flex-wrap gap-2">
                                <button
                                  type="button"
                                  onClick={() => void handleQuickToggle(item, { is_active: !item.is_active })}
                                  className={`rounded-full px-3 py-1.5 text-[0.78rem] font-semibold ${
                                    item.is_active
                                      ? 'bg-[rgba(64,124,73,0.12)] text-[rgb(48,96,56)]'
                                      : 'bg-[rgba(26,28,25,0.08)] text-[var(--muted)]'
                                  }`}
                                >
                                  {item.is_active ? 'Active' : 'Inactive'}
                                </button>
                                <button
                                  type="button"
                                  onClick={() => void handleQuickToggle(item, { is_sold_out: !item.is_sold_out })}
                                  className={`rounded-full px-3 py-1.5 text-[0.78rem] font-semibold ${
                                    item.is_sold_out
                                      ? 'bg-[rgba(191,104,32,0.14)] text-[rgb(140,76,17)]'
                                      : 'bg-[rgba(64,124,73,0.12)] text-[rgb(48,96,56)]'
                                  }`}
                                >
                                  {item.is_sold_out ? 'Sold Out' : 'Available'}
                                </button>
                              </div>
                            </td>
                            <td className="px-4 py-3 text-right">
                              <div className="flex justify-end gap-2">
                                <button
                                  type="button"
                                  onClick={() => openOptions(item)}
                                  className="rounded-full bg-[rgba(97,0,0,0.08)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--primary)]"
                                >
                                  Options
                                </button>
                                <button
                                  type="button"
                                  onClick={() => openEdit(item)}
                                  className="rounded-full bg-[rgba(26,28,25,0.05)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--on-surface)]"
                                >
                                  Edit
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : menuItems.length ? (
                  <div className="mt-5 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
                    No items match the current search and filters.
                  </div>
                ) : (
                  <div className="mt-5 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
                    No menu items found for this store.
                  </div>
                )}
              </div>

              <div className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
                <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">{editor?.id ? 'Edit Menu Item' : 'Create Menu Item'}</div>
                <div className="mt-1 text-[0.85rem] text-[var(--muted)]">Changes save directly to `menu_items` after backend verification.</div>

                {editor ? (
                  <div className="mt-5 space-y-4">
                    <label className="block">
                      <div className="text-[0.8rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Chinese Name</div>
                      <input
                        value={editor.name_zh}
                        onChange={(event) => setEditor({ ...editor, name_zh: event.target.value })}
                        className="mt-1 w-full rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                      />
                    </label>

                    <label className="block">
                      <div className="text-[0.8rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">English Name</div>
                      <input
                        value={editor.name_en}
                        onChange={(event) => setEditor({ ...editor, name_en: event.target.value })}
                        className="mt-1 w-full rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                      />
                    </label>

                    <label className="block">
                      <div className="text-[0.8rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">SKU</div>
                      <input
                        value={editor.sku}
                        onChange={(event) => setEditor({ ...editor, sku: event.target.value })}
                        className="mt-1 w-full rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                      />
                    </label>

                    <div className="grid gap-4 sm:grid-cols-2">
                      <label className="block">
                        <div className="text-[0.8rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Category</div>
                        <select
                          value={String(editor.category_id)}
                          onChange={(event) => setEditor({ ...editor, category_id: Number(event.target.value) })}
                          className="mt-1 w-full rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                        >
                          {categories.map((category) => (
                            <option key={category.id} value={category.id}>
                              {category.label}
                            </option>
                          ))}
                        </select>
                      </label>

                      <label className="block">
                        <div className="text-[0.8rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Station</div>
                        <select
                          value={String(editor.station_id)}
                          onChange={(event) => setEditor({ ...editor, station_id: Number(event.target.value) })}
                          className="mt-1 w-full rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                        >
                          {stations.map((station) => (
                            <option key={station.id} value={station.id}>
                              {station.label}
                            </option>
                          ))}
                        </select>
                      </label>
                    </div>

                    <div className="grid gap-4 sm:grid-cols-2">
                      <label className="block">
                        <div className="text-[0.8rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Base Price</div>
                        <input
                          type="number"
                          step="0.01"
                          value={editor.base_price}
                          onChange={(event) => setEditor({ ...editor, base_price: Number(event.target.value) })}
                          className="mt-1 w-full rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                        />
                      </label>

                      <label className="block">
                        <div className="text-[0.8rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Cost Per Item</div>
                        <input
                          type="number"
                          step="0.01"
                          value={editor.cost_per_item}
                          onChange={(event) => setEditor({ ...editor, cost_per_item: Number(event.target.value) })}
                          className="mt-1 w-full rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
                        />
                      </label>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2">
                      <label className="flex items-center gap-3 rounded-[16px] bg-[rgba(26,28,25,0.04)] px-4 py-3">
                        <input
                          type="checkbox"
                          checked={editor.is_active}
                          onChange={(event) => setEditor({ ...editor, is_active: event.target.checked })}
                          className="h-4 w-4 accent-[var(--primary)]"
                        />
                        <div>
                          <div className="text-[0.9rem] font-semibold text-[var(--on-surface)]">Active</div>
                          <div className="text-[0.78rem] text-[var(--muted)]">Visible to the restaurant.</div>
                        </div>
                      </label>

                      <label className="flex items-center gap-3 rounded-[16px] bg-[rgba(26,28,25,0.04)] px-4 py-3">
                        <input
                          type="checkbox"
                          checked={editor.is_sold_out}
                          onChange={(event) => setEditor({ ...editor, is_sold_out: event.target.checked })}
                          className="h-4 w-4 accent-[var(--primary)]"
                        />
                        <div>
                          <div className="text-[0.9rem] font-semibold text-[var(--on-surface)]">Sold Out</div>
                          <div className="text-[0.78rem] text-[var(--muted)]">Hide from ordering temporarily.</div>
                        </div>
                      </label>
                    </div>

                    <div className="rounded-[18px] border border-[rgba(191,104,32,0.18)] bg-[rgba(191,104,32,0.08)] px-4 py-3 text-[0.82rem] leading-5 text-[rgb(140,76,17)]">
                      Price or cost changes affect future orders immediately. Historical profit reports require analytics rebuild.
                    </div>

                    {editor.id ? (
                      <div className="rounded-[18px] border border-[rgba(97,0,0,0.16)] bg-[rgba(97,0,0,0.055)] px-4 py-3">
                        <div className="text-[0.86rem] font-bold text-[var(--primary)]">
                          {editor.is_active ? 'Deactivate Menu Item' : 'Reactivate Menu Item'}
                        </div>
                        <div className="mt-1 text-[0.78rem] leading-5 text-[var(--muted)]">
                          {editor.is_active
                            ? 'Hide this item from future ordering. Historical orders and print previews stay unchanged.'
                            : 'Make this item visible again for future ordering.'}
                        </div>
                        <button
                          type="button"
                          onClick={() => void handleEditorActiveAction()}
                          disabled={saving}
                          className={`mt-3 rounded-[14px] px-3.5 py-2 text-[0.84rem] font-semibold disabled:opacity-60 ${
                            editor.is_active
                              ? 'bg-[var(--primary)] text-white'
                              : 'bg-[rgba(64,124,73,0.14)] text-[rgb(48,96,56)]'
                          }`}
                        >
                          {editor.is_active ? 'Delete / Deactivate Item' : 'Reactivate Item'}
                        </button>
                      </div>
                    ) : null}

                    <div className="flex items-center justify-end gap-3">
                      <button
                        type="button"
                        onClick={() => setEditor(null)}
                        className="rounded-[16px] bg-[rgba(26,28,25,0.05)] px-4 py-3 text-[0.9rem] font-semibold text-[var(--on-surface)]"
                      >
                        Cancel
                      </button>
                      <button
                        type="button"
                        onClick={handleSave}
                        disabled={saving}
                        className="rounded-[16px] bg-[var(--primary)] px-4 py-3 text-[0.9rem] font-semibold text-white shadow-[0_14px_24px_rgba(97,0,0,0.16)] disabled:opacity-60"
                      >
                        {saving ? 'Saving...' : 'Save Item'}
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="mt-5 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
                    Select a menu item to edit, or create a new one.
                  </div>
                )}
              </div>

              {optionsItem?.id ? (
                <MenuOptionsPanel
                  itemId={optionsItem.id}
                  itemName={`${optionsItem.name_zh || optionsItem.name_en || 'Menu Item'}${optionsItem.sku ? ` · ${optionsItem.sku}` : ''}`}
                />
              ) : null}
            </div>
    </div>
  )
}
