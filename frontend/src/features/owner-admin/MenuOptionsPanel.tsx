import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  createOwnerMenuItemOption,
  fetchOwnerMenuItemOptions,
  reorderOwnerMenuItemOptions,
  updateOwnerMenuItemComboPolicy,
  updateOwnerMenuItemOption,
  updateOwnerMenuItemSizeConfiguration,
  type MenuItemOptionAdminRecord,
  type MenuItemOptionPayload,
  type StandardSizeCode,
} from '../../services/ownerMenuOptionService'
import {
  buildDefaultNoodleTypeOrder,
  defaultNoodleTypeOptionId,
} from './menuOptionDefaults'

type EditableOptionGroup = 'REMOVE' | 'ADD_ON'
type DisplayOptionGroup = EditableOptionGroup | 'NOODLE_TYPE'

const DISPLAY_GROUPS: DisplayOptionGroup[] = ['NOODLE_TYPE', 'REMOVE', 'ADD_ON']

const GROUP_LABELS: Record<DisplayOptionGroup, string> = {
  NOODLE_TYPE: '面型 / Noodle Type',
  REMOVE: 'Remove',
  ADD_ON: 'Add-on',
}

const STANDARD_SIZES: Array<{ code: StandardSizeCode; zh: string; en: string }> = [
  { code: 'size_small', zh: '小碗', en: 'Small' },
  { code: 'size_regular', zh: '中碗', en: 'Regular' },
  { code: 'size_large', zh: '大碗', en: 'Large' },
]

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

function optionTypeForGroup(group: EditableOptionGroup) {
  return group === 'REMOVE' ? 'remove' : 'addon'
}

function editableGroupFromDraft(draft: MenuItemOptionPayload): EditableOptionGroup {
  const group = draft.option_group?.toUpperCase()
  if (group === 'REMOVE' || group === 'ADD_ON') {
    return group
  }
  const optionType = draft.option_type?.toLowerCase()
  return optionType === 'remove' ? 'REMOVE' : 'ADD_ON'
}

function standardSizeCode(option: MenuItemOptionAdminRecord): StandardSizeCode | null {
  const code = option.option_code?.trim().toLowerCase()
  if (code === 'size_small' || code === 'size_regular' || code === 'size_large') {
    return code
  }
  const zh = option.name_zh?.trim()
  const en = option.name_en?.trim().toLowerCase()
  const match = STANDARD_SIZES.find((size) => size.zh === zh || size.en.toLowerCase() === en)
  return match?.code ?? null
}

function isSizeOption(option: MenuItemOptionAdminRecord) {
  return option.option_group?.toUpperCase() === 'SIZE' || option.option_type?.toLowerCase() === 'size'
}

function isComboUpcharge(option: MenuItemOptionAdminRecord) {
  if (option.option_group?.toUpperCase() === 'COMBO' || option.option_code?.toLowerCase() === 'combo') {
    return true
  }
  return option.option_type?.toLowerCase() === 'addon'
    && (option.name_zh === '套餐' || option.name_en?.toLowerCase() === 'combo')
}

function normalizeGroup(option: MenuItemOptionAdminRecord): DisplayOptionGroup | null {
  const optionGroup = option.option_group?.toUpperCase()
  if (optionGroup === 'REMOVE' || optionGroup === 'ADD_ON' || optionGroup === 'NOODLE_TYPE') {
    return optionGroup
  }

  if (optionGroup === 'SIZE' || optionGroup === 'COMBO') {
    return null
  }

  const optionType = option.option_type?.toLowerCase()
  if (optionType === 'noodle_type') {
    return 'NOODLE_TYPE'
  }
  if (optionType === 'remove') {
    return 'REMOVE'
  }

  if (optionType === 'addon') {
    if (isComboUpcharge(option)) {
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
  return {
    option_type: option.option_type,
    option_code: option.option_code ?? '',
    option_group: option.option_group,
    parent_option_id: option.parent_option_id,
    sort_order: option.sort_order,
    name_zh: option.name_zh,
    name_en: option.name_en,
    price_delta: Number(option.price_delta ?? 0),
    is_active: option.is_active,
  }
}

function nextSortOrder(options: MenuItemOptionAdminRecord[], group: EditableOptionGroup) {
  const groupOptions = options.filter((option) => normalizeGroup(option) === group)
  const maxSort = groupOptions.reduce((max, option) => Math.max(max, option.sort_order ?? 0), 0)
  return maxSort + 10
}

function extractEnabledSizes(options: MenuItemOptionAdminRecord[]) {
  return STANDARD_SIZES
    .filter((size) => options.some((option) => isSizeOption(option) && option.is_active && standardSizeCode(option) === size.code))
    .map((size) => size.code)
}

function extractDefaultSize(options: MenuItemOptionAdminRecord[], enabledCodes: StandardSizeCode[]) {
  const firstActiveSize = options
    .filter((option) => isSizeOption(option) && option.is_active)
    .sort(sortOptions)
    .map(standardSizeCode)
    .find((code): code is StandardSizeCode => code != null && enabledCodes.includes(code))
  if (firstActiveSize) {
    return firstActiveSize
  }
  if (enabledCodes.includes('size_regular')) {
    return 'size_regular'
  }
  return enabledCodes[0] ?? 'size_regular'
}

function formatMoney(value: number | string | null | undefined) {
  return `$${Number(value ?? 0).toFixed(2)}`
}

export function MenuOptionsPanel({ itemId, itemName }: MenuOptionsPanelProps) {
  const [options, setOptions] = useState<MenuItemOptionAdminRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [draft, setDraft] = useState<MenuItemOptionPayload>(DEFAULT_DRAFT)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [enabledSizeCodes, setEnabledSizeCodes] = useState<StandardSizeCode[]>(['size_regular'])
  const [defaultSizeCode, setDefaultSizeCode] = useState<StandardSizeCode>('size_regular')
  const [comboAllowed, setComboAllowed] = useState(false)

  const applyLoadedOptions = useCallback((nextOptions: MenuItemOptionAdminRecord[]) => {
    setOptions(nextOptions)
    const nextEnabledSizes = extractEnabledSizes(nextOptions)
    const safeEnabledSizes: StandardSizeCode[] = nextEnabledSizes.length ? nextEnabledSizes : ['size_regular']
    setEnabledSizeCodes(safeEnabledSizes)
    setDefaultSizeCode(extractDefaultSize(nextOptions, safeEnabledSizes))
    setComboAllowed(nextOptions.some((option) => isComboUpcharge(option) && option.is_active))
  }, [])

  const loadOptions = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      applyLoadedOptions(await fetchOwnerMenuItemOptions(itemId))
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load item options')
    } finally {
      setLoading(false)
    }
  }, [applyLoadedOptions, itemId])

  useEffect(() => {
    setDraft(DEFAULT_DRAFT)
    setEditingId(null)
    setFormOpen(false)
    void loadOptions()
  }, [itemId, loadOptions])

  const groupedOptions = useMemo(
    () =>
      DISPLAY_GROUPS.map((group) => ({
        group,
        options: options
          .filter((option) => normalizeGroup(option) === group)
          .sort(sortOptions),
      })),
    [options],
  )
  const defaultNoodleTypeId = useMemo(() => defaultNoodleTypeOptionId(options), [options])

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
    if (isSizeOption(option) || isComboUpcharge(option)) {
      return
    }
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
    const group = editableGroupFromDraft(draft)
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
        price_delta: Number(draft.price_delta ?? 0).toFixed(2),
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
    if (isSizeOption(option) || isComboUpcharge(option)) {
      return
    }
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

  const setDefaultNoodleType = async (option: MenuItemOptionAdminRecord) => {
    const reorderPayload = buildDefaultNoodleTypeOrder(options, option.id)
    if (!reorderPayload) return
    try {
      setSaving(true)
      setError(null)
      await reorderOwnerMenuItemOptions(itemId, reorderPayload)
      await loadOptions()
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Failed to set default noodle type')
    } finally {
      setSaving(false)
    }
  }

  const toggleSize = (code: StandardSizeCode, checked: boolean) => {
    setEnabledSizeCodes((current) => {
      const next = checked
        ? Array.from(new Set([...current, code]))
        : current.filter((candidate) => candidate !== code)
      if (!next.includes(defaultSizeCode)) {
        setDefaultSizeCode(next.includes('size_regular') ? 'size_regular' : next[0] ?? code)
      }
      return next
    })
  }

  const saveSizeConfiguration = async () => {
    try {
      setSaving(true)
      setError(null)
      if (enabledSizeCodes.length === 0) {
        throw new Error('At least one Size must be enabled.')
      }
      const safeDefault = enabledSizeCodes.includes(defaultSizeCode)
        ? defaultSizeCode
        : enabledSizeCodes.includes('size_regular')
          ? 'size_regular'
          : enabledSizeCodes[0]
      applyLoadedOptions(await updateOwnerMenuItemSizeConfiguration(itemId, {
        enabled_size_codes: enabledSizeCodes,
        default_size_code: safeDefault,
      }))
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Failed to save Size configuration')
    } finally {
      setSaving(false)
    }
  }

  const saveComboPolicy = async (allowed: boolean) => {
    try {
      setSaving(true)
      setError(null)
      setComboAllowed(allowed)
      applyLoadedOptions(await updateOwnerMenuItemComboPolicy(itemId, { combo_allowed: allowed }))
    } catch (saveError) {
      setComboAllowed(!allowed)
      setError(saveError instanceof Error ? saveError.message : 'Failed to save Combo policy')
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

      <section className="mt-4 rounded-[20px] border border-[rgba(26,28,25,0.06)] bg-white/70 p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="text-[0.9rem] font-black uppercase tracking-[0.12em] text-[var(--primary)]">Size Configuration / 规格</div>
            <div className="mt-1 text-[0.76rem] text-[var(--muted)]">
              系统只允许 Small / Regular / Large；价格由 Store Pricing Rules 统一控制。
            </div>
          </div>
          <button
            type="button"
            onClick={() => void saveSizeConfiguration()}
            disabled={saving || loading}
            className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.78rem] font-semibold text-white disabled:opacity-60"
          >
            Save Sizes
          </button>
        </div>

        <div className="mt-3 grid gap-2 sm:grid-cols-3">
          {STANDARD_SIZES.map((size) => {
            const enabled = enabledSizeCodes.includes(size.code)
            return (
              <div key={size.code} className={`rounded-[16px] border px-3 py-3 ${enabled ? 'border-[rgba(64,124,73,0.24)] bg-[rgba(64,124,73,0.08)]' : 'border-[rgba(26,28,25,0.06)] bg-[rgba(26,28,25,0.03)]'}`}>
                <label className="flex min-h-10 items-center gap-2 text-[0.86rem] font-semibold text-[var(--on-surface)]">
                  <input
                    type="checkbox"
                    checked={enabled}
                    onChange={(event) => toggleSize(size.code, event.target.checked)}
                    className="h-4 w-4 accent-[var(--primary)]"
                  />
                  {size.zh} / {size.en}
                </label>
                <label className="mt-2 flex min-h-10 items-center gap-2 text-[0.78rem] text-[var(--muted)]">
                  <input
                    type="radio"
                    checked={defaultSizeCode === size.code}
                    onChange={() => setDefaultSizeCode(size.code)}
                    disabled={!enabled}
                    className="h-4 w-4 accent-[var(--primary)] disabled:opacity-40"
                  />
                  Default
                </label>
              </div>
            )
          })}
        </div>
      </section>

      <section className="mt-4 rounded-[20px] border border-[rgba(26,28,25,0.06)] bg-white/70 p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="text-[0.9rem] font-black uppercase tracking-[0.12em] text-[var(--primary)]">Combo Policy / 套餐</div>
            <div className="mt-1 text-[0.76rem] text-[var(--muted)]">
              Item only controls whether Combo is allowed; Combo price is Store-level.
            </div>
          </div>
          <button
            type="button"
            onClick={() => void saveComboPolicy(!comboAllowed)}
            disabled={saving || loading}
            className={`rounded-[14px] px-3 py-2 text-[0.78rem] font-semibold disabled:opacity-60 ${comboAllowed ? 'bg-[rgba(64,124,73,0.14)] text-[rgb(48,96,56)]' : 'bg-[rgba(26,28,25,0.06)] text-[var(--on-surface)]'}`}
          >
            {comboAllowed ? 'Combo Allowed' : 'Combo Disabled'}
          </button>
        </div>
      </section>

      {formOpen ? (
        <div className="mt-4 rounded-[20px] border border-[rgba(26,28,25,0.06)] bg-[rgba(26,28,25,0.02)] p-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block">
              <div className="text-[0.72rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Group</div>
              <select
                value={editableGroupFromDraft(draft)}
                onChange={(event) => {
                  const group = event.target.value as EditableOptionGroup
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
                onChange={(event) => setDraft({ ...draft, price_delta: event.target.value })}
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
        <div className="text-[0.86rem] font-black uppercase tracking-[0.12em] text-[var(--muted)]">Item Options</div>
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
              {group === 'NOODLE_TYPE' ? (
                <div className="mt-1 text-[0.76rem] text-[var(--muted)]">
                  第一个启用的面型是新点单默认值。点击“设为默认”会保存排序并刷新菜单版本。
                </div>
              ) : null}
              <div className="mt-2 space-y-2">
                {groupOptions.length ? groupOptions.map((option, optionIndex) => {
                  const isDefaultNoodleType = group === 'NOODLE_TYPE' && option.id === defaultNoodleTypeId
                  return (
                    <div
                      key={option.id}
                      className={`rounded-[14px] px-3 py-2 ${option.is_active ? 'bg-[rgba(26,28,25,0.035)]' : 'bg-[rgba(26,28,25,0.08)] opacity-70'}`}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2 font-semibold text-[var(--on-surface)]">
                            <span>{option.name_zh} <span className="text-[0.76rem] font-normal text-[var(--muted)]">/ {option.name_en || '-'}</span></span>
                            {isDefaultNoodleType ? (
                              <span className="rounded-full bg-[rgba(64,124,73,0.14)] px-2 py-1 text-[0.68rem] font-black text-[rgb(48,96,56)]">
                                默认
                              </span>
                            ) : null}
                          </div>
                          <div className="mt-0.5 text-[0.72rem] text-[var(--muted)]">
                            {option.option_code || 'no code'} · {formatMoney(option.price_delta)} · {option.is_active ? 'active' : 'inactive'}
                          </div>
                        </div>
                        <div className="flex shrink-0 flex-wrap justify-end gap-1">
                          {group === 'NOODLE_TYPE' && option.is_active && !isDefaultNoodleType ? (
                            <button
                              type="button"
                              onClick={() => void setDefaultNoodleType(option)}
                              disabled={saving}
                              className="min-h-10 rounded-full bg-[var(--primary)] px-3 py-1 text-[0.72rem] font-semibold text-white disabled:opacity-50"
                            >
                              设为默认
                            </button>
                          ) : null}
                          <button type="button" aria-label={`上移 ${option.name_zh}`} onClick={() => void moveOption(option, -1)} disabled={saving || optionIndex === 0} className="min-h-10 rounded-full bg-white px-3 py-1 text-[0.72rem] font-semibold disabled:opacity-35">Up</button>
                          <button type="button" aria-label={`下移 ${option.name_zh}`} onClick={() => void moveOption(option, 1)} disabled={saving || optionIndex === groupOptions.length - 1} className="min-h-10 rounded-full bg-white px-3 py-1 text-[0.72rem] font-semibold disabled:opacity-35">Down</button>
                          {group !== 'NOODLE_TYPE' ? (
                            <button type="button" onClick={() => beginEdit(option)} disabled={saving} className="min-h-10 rounded-full bg-white px-3 py-1 text-[0.72rem] font-semibold disabled:opacity-50">Edit</button>
                          ) : null}
                          <button
                            type="button"
                            onClick={() => void setOptionActive(option, !option.is_active)}
                            disabled={saving}
                            className="min-h-10 rounded-full bg-[rgba(97,0,0,0.08)] px-3 py-1 text-[0.72rem] font-semibold text-[var(--primary)] disabled:opacity-50"
                          >
                            {option.is_active ? 'Deactivate' : 'Reactivate'}
                          </button>
                        </div>
                      </div>
                    </div>
                  )
                }) : (
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
