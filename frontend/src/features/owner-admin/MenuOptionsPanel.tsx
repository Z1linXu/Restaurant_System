import { useEffect, useMemo, useState } from 'react'
import {
  createOwnerMenuItemOption,
  fetchOwnerMenuItemOptions,
  reorderOwnerMenuItemOptions,
  updateOwnerMenuItemOption,
  type MenuItemOptionAdminRecord,
  type MenuItemOptionPayload,
} from '../../services/ownerMenuOptionService'

type SimpleOptionGroup = 'REMOVE' | 'ADD_ON'

const SIMPLE_GROUPS: SimpleOptionGroup[] = ['REMOVE', 'ADD_ON']

const GROUP_LABELS: Record<SimpleOptionGroup, string> = {
  REMOVE: 'Remove',
  ADD_ON: 'Add-on',
}

const DEFAULT_DRAFT: MenuItemOptionPayload = {
  option_type: 'addon',
  option_code: '',
  option_group: 'ADD_ON',
  parent_option_id: null,
  sort_order: null,
  name_zh: '',
  name_en: '',
  price_delta: 0,
  is_active: true,
}

interface MenuOptionsPanelProps {
  itemId: number
  itemName: string
}

function optionTypeForGroup(group: SimpleOptionGroup) {
  return group === 'REMOVE' ? 'remove' : 'addon'
}

function normalizeGroup(option: MenuItemOptionAdminRecord): SimpleOptionGroup | null {
  const optionGroup = option.option_group?.toUpperCase()
  if (optionGroup === 'REMOVE' || optionGroup === 'ADD_ON') {
    return optionGroup
  }

  if (optionGroup) {
    return null
  }

  const optionType = option.option_type?.toLowerCase()
  if (optionType === 'remove') {
    return 'REMOVE'
  }

  if (optionType === 'addon') {
    const label = `${option.name_zh ?? ''} ${option.name_en ?? ''}`.toLowerCase()
    // Legacy fallback: old combo rows sometimes only had option_type=addon.
    if (label.includes('combo') || label.includes('套餐')) {
      return null
    }
    return 'ADD_ON'
  }

  return null
}

function sortOptions(left: MenuItemOptionAdminRecord, right: MenuItemOptionAdminRecord) {
  return (left.sort_order ?? 999999) - (right.sort_order ?? 999999) || left.id - right.id
}

function toPayload(option: MenuItemOptionAdminRecord): MenuItemOptionPayload {
  const group = normalizeGroup(option) ?? 'ADD_ON'
  return {
    option_type: optionTypeForGroup(group),
    option_code: option.option_code ?? '',
    option_group: group,
    parent_option_id: null,
    sort_order: option.sort_order,
    name_zh: option.name_zh,
    name_en: option.name_en,
    price_delta: Number(option.price_delta ?? 0),
    is_active: option.is_active,
  }
}

function nextSortOrder(options: MenuItemOptionAdminRecord[], group: SimpleOptionGroup) {
  const groupOptions = options.filter((option) => normalizeGroup(option) === group)
  const maxSort = groupOptions.reduce((max, option) => Math.max(max, option.sort_order ?? 0), 0)
  return maxSort + 10
}

export function MenuOptionsPanel({ itemId, itemName }: MenuOptionsPanelProps) {
  const [options, setOptions] = useState<MenuItemOptionAdminRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [draft, setDraft] = useState<MenuItemOptionPayload>(DEFAULT_DRAFT)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [formOpen, setFormOpen] = useState(false)

  const loadOptions = async () => {
    setLoading(true)
    setError(null)
    try {
      setOptions(await fetchOwnerMenuItemOptions(itemId))
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load item options')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    setDraft(DEFAULT_DRAFT)
    setEditingId(null)
    setFormOpen(false)
    void loadOptions()
  }, [itemId])

  const groupedOptions = useMemo(
    () =>
      SIMPLE_GROUPS.map((group) => ({
        group,
        options: options
          .filter((option) => normalizeGroup(option) === group)
          .sort(sortOptions),
      })),
    [options],
  )

  const beginCreate = () => {
    setEditingId(null)
    setDraft({
      ...DEFAULT_DRAFT,
      option_group: 'ADD_ON',
      option_type: 'addon',
      parent_option_id: null,
      sort_order: nextSortOrder(options, 'ADD_ON'),
      is_active: true,
    })
    setFormOpen(true)
  }

  const beginEdit = (option: MenuItemOptionAdminRecord) => {
    setEditingId(option.id)
    setDraft(toPayload(option))
    setFormOpen(true)
  }

  const resetForm = () => {
    setEditingId(null)
    setDraft(DEFAULT_DRAFT)
    setFormOpen(false)
  }

  const saveDraft = async () => {
    const group = draft.option_group === 'REMOVE' ? 'REMOVE' : 'ADD_ON'
    try {
      setSaving(true)
      setError(null)
      const payload: MenuItemOptionPayload = {
        ...draft,
        option_type: optionTypeForGroup(group),
        option_group: group,
        parent_option_id: null,
        option_code: draft.option_code?.trim() || null,
        name_zh: draft.name_zh.trim(),
        name_en: draft.name_en?.trim() || '',
        price_delta: Number(draft.price_delta ?? 0),
        sort_order: draft.sort_order ?? nextSortOrder(options, group),
        is_active: draft.is_active,
      }

      if (!payload.name_zh) {
        throw new Error('Chinese name is required.')
      }

      if (editingId) {
        await updateOwnerMenuItemOption(itemId, editingId, payload)
      } else {
        await createOwnerMenuItemOption(itemId, payload)
      }
      resetForm()
      await loadOptions()
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Failed to save option')
    } finally {
      setSaving(false)
    }
  }

  const setOptionActive = async (option: MenuItemOptionAdminRecord, isActive: boolean) => {
    try {
      setSaving(true)
      setError(null)
      await updateOwnerMenuItemOption(itemId, option.id, {
        ...toPayload(option),
        is_active: isActive,
      })
      await loadOptions()
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Failed to update option status')
    } finally {
      setSaving(false)
    }
  }

  const moveOption = async (option: MenuItemOptionAdminRecord, direction: -1 | 1) => {
    const group = normalizeGroup(option)
    if (!group) {
      return
    }
    const sameGroup = options
      .filter((candidate) => normalizeGroup(candidate) === group)
      .sort(sortOptions)
    const index = sameGroup.findIndex((candidate) => candidate.id === option.id)
    const swapWith = sameGroup[index + direction]
    if (!swapWith) {
      return
    }

    const currentSort = option.sort_order ?? index * 10
    const swapSort = swapWith.sort_order ?? (index + direction) * 10
    try {
      setSaving(true)
      setError(null)
      await reorderOwnerMenuItemOptions(itemId, [
        { id: option.id, sort_order: swapSort },
        { id: swapWith.id, sort_order: currentSort },
      ])
      await loadOptions()
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Failed to reorder option')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Options</div>
          <div className="mt-1 text-[0.85rem] text-[var(--muted)]">{itemName}</div>
        </div>
        <button
          type="button"
          onClick={beginCreate}
          className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white"
        >
          New Option
        </button>
      </div>

      {error ? (
        <div className="mt-4 rounded-[16px] border border-[rgba(97,0,0,0.18)] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.82rem] font-semibold text-[var(--primary)]">
          {error}
        </div>
      ) : null}

      {formOpen ? (
        <div className="mt-4 rounded-[20px] border border-[rgba(26,28,25,0.06)] bg-[rgba(26,28,25,0.02)] p-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block">
              <div className="text-[0.72rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Group</div>
              <select
                value={draft.option_group === 'REMOVE' ? 'REMOVE' : 'ADD_ON'}
                onChange={(event) => {
                  const group = event.target.value as SimpleOptionGroup
                  setDraft({
                    ...draft,
                    option_group: group,
                    option_type: optionTypeForGroup(group),
                    parent_option_id: null,
                    sort_order: editingId ? draft.sort_order : nextSortOrder(options, group),
                  })
                }}
                className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2.5 text-[0.88rem] outline-none"
              >
                <option value="ADD_ON">Add-on</option>
                <option value="REMOVE">Remove</option>
              </select>
            </label>

            <label className="block">
              <div className="text-[0.72rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Price Delta</div>
              <input
                type="number"
                step="0.01"
                value={draft.price_delta}
                onChange={(event) => setDraft({ ...draft, price_delta: Number(event.target.value) })}
                className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2.5 text-[0.88rem] outline-none"
              />
            </label>
          </div>

          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <input
              value={draft.name_zh}
              onChange={(event) => setDraft({ ...draft, name_zh: event.target.value })}
              placeholder="中文名称"
              className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2.5 text-[0.88rem] outline-none"
            />
            <input
              value={draft.name_en}
              onChange={(event) => setDraft({ ...draft, name_en: event.target.value })}
              placeholder="English name"
              className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2.5 text-[0.88rem] outline-none"
            />
            <input
              value={draft.option_code ?? ''}
              onChange={(event) => setDraft({ ...draft, option_code: event.target.value })}
              placeholder="Option code"
              className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2.5 text-[0.88rem] outline-none"
            />
            <label className="flex items-center gap-2 rounded-[14px] bg-white px-3 py-2.5 text-[0.86rem] font-semibold text-[var(--on-surface)]">
              <input
                type="checkbox"
                checked={draft.is_active}
                onChange={(event) => setDraft({ ...draft, is_active: event.target.checked })}
                className="h-4 w-4 accent-[var(--primary)]"
              />
              Active
            </label>
          </div>

          <div className="mt-3 flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={resetForm}
              className="rounded-[14px] bg-[rgba(26,28,25,0.06)] px-3 py-2 text-[0.82rem] font-semibold"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={saveDraft}
              disabled={saving}
              className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white disabled:opacity-60"
            >
              {saving ? 'Saving...' : editingId ? 'Save Option' : 'Add Option'}
            </button>
          </div>
        </div>
      ) : null}

      <div className="mt-5 flex items-center justify-between gap-3">
        <div className="text-[0.86rem] font-black uppercase tracking-[0.12em] text-[var(--muted)]">Active Options</div>
      </div>

      {loading ? (
        <div className="mt-4 rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-3 text-[0.84rem] text-[var(--muted)]">
          Loading options...
        </div>
      ) : (
        <div className="mt-3 space-y-4">
          {groupedOptions.map(({ group, options: groupOptions }) => (
            <section key={group} className="rounded-[18px] border border-[rgba(26,28,25,0.06)] bg-white/70 p-3">
              <div className="text-[0.82rem] font-black uppercase tracking-[0.12em] text-[var(--primary)]">{GROUP_LABELS[group]}</div>
              <div className="mt-2 space-y-2">
                {groupOptions.length ? groupOptions.map((option) => (
                  <div
                    key={option.id}
                    className={`rounded-[14px] px-3 py-2 ${option.is_active ? 'bg-[rgba(26,28,25,0.035)]' : 'bg-[rgba(26,28,25,0.08)] opacity-70'}`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="font-semibold text-[var(--on-surface)]">
                          {option.name_zh} <span className="text-[0.76rem] font-normal text-[var(--muted)]">/ {option.name_en || '-'}</span>
                        </div>
                        <div className="mt-0.5 text-[0.72rem] text-[var(--muted)]">
                          {option.option_code || 'no code'} · ${Number(option.price_delta ?? 0).toFixed(2)} · {option.is_active ? 'active' : 'inactive'}
                        </div>
                      </div>
                      <div className="flex shrink-0 flex-wrap justify-end gap-1">
                        <button type="button" onClick={() => moveOption(option, -1)} disabled={saving} className="rounded-full bg-white px-2 py-1 text-[0.72rem] font-semibold disabled:opacity-50">Up</button>
                        <button type="button" onClick={() => moveOption(option, 1)} disabled={saving} className="rounded-full bg-white px-2 py-1 text-[0.72rem] font-semibold disabled:opacity-50">Down</button>
                        <button type="button" onClick={() => beginEdit(option)} disabled={saving} className="rounded-full bg-white px-2 py-1 text-[0.72rem] font-semibold disabled:opacity-50">Edit</button>
                        <button
                          type="button"
                          onClick={() => setOptionActive(option, !option.is_active)}
                          disabled={saving}
                          className="rounded-full bg-[rgba(97,0,0,0.08)] px-2 py-1 text-[0.72rem] font-semibold text-[var(--primary)] disabled:opacity-50"
                        >
                          {option.is_active ? 'Deactivate' : 'Reactivate'}
                        </button>
                      </div>
                    </div>
                  </div>
                )) : (
                  <div className="rounded-[12px] bg-[rgba(26,28,25,0.035)] px-3 py-2 text-[0.8rem] text-[var(--muted)]">
                    No {GROUP_LABELS[group].toLowerCase()} options.
                  </div>
                )}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
