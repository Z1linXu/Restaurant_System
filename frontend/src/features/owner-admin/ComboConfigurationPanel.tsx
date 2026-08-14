import { useEffect, useMemo, useState } from 'react'
import {
  fetchStoreComboConfiguration,
  updateStoreComboConfiguration,
  type StoreComboConfigurationGroupRecord,
  type StoreComboConfigurationRecord,
} from '../../services/ownerMenuOptionService'
import type { MenuItemAdminRecord } from '../../services/platformAdminService'
import {
  comboConfigurationLayoutClasses,
  moveWithDisplayOrder,
} from './comboConfigurationLayout'

interface ComboConfigurationPanelProps {
  storeId: number
  menuItems?: MenuItemAdminRecord[]
  onSaved?: (message: string) => void
}

type ErrorState = string | null

function cloneConfiguration(configuration: StoreComboConfigurationRecord | null): StoreComboConfigurationRecord | null {
  if (!configuration) return null
  return {
    ...configuration,
    groups: configuration.groups.map((group) => ({
      ...group,
      components: group.components.map((component) => ({ ...component })),
    })),
  }
}

function canonical(configuration: StoreComboConfigurationRecord | null) {
  if (!configuration) return ''
  return JSON.stringify(configuration.groups.map((group) => ({
    group_id: group.group_id ?? null,
    group_code: group.group_code ?? group.component_group ?? null,
    name_zh: group.name_zh,
    name_en: group.name_en,
    selection_rule: group.selection_rule ?? 'EXACTLY_ONE',
    required: group.required ?? (group.selection_rule !== 'OPTIONAL_ONE'),
    enabled: group.enabled ?? true,
    display_order: group.display_order ?? 0,
    default_component_code: group.default_component_code ?? null,
    components: group.components.map((component) => ({
      id: component.id ?? null,
      component_code: component.component_code ?? null,
      name_zh: component.name_zh,
      name_en: component.name_en,
      enabled: component.enabled,
      display_order: component.display_order,
      is_default: component.is_default,
      linked_menu_item_id: component.linked_menu_item_id ?? null,
      business_behavior: component.business_behavior ?? 'NO_KITCHEN_TASK',
    })),
  })))
}

function nextOrder(values: Array<{ display_order?: number | null }>) {
  return (values.reduce((max, value) => Math.max(max, value.display_order ?? 0), 0) || values.length * 10) + 10
}

function groupCode(group: StoreComboConfigurationGroupRecord) {
  return (group.group_code ?? group.component_group ?? '').trim().toUpperCase()
}

export function ComboConfigurationPanel({ storeId, menuItems = [], onSaved }: ComboConfigurationPanelProps) {
  const [configuration, setConfiguration] = useState<StoreComboConfigurationRecord | null>(null)
  const [draft, setDraft] = useState<StoreComboConfigurationRecord | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<ErrorState>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const nextConfiguration = await fetchStoreComboConfiguration(storeId)
        if (cancelled) return
        setConfiguration(nextConfiguration)
        setDraft(cloneConfiguration(nextConfiguration))
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : 'Failed to load Combo Configuration')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [storeId])

  const dirty = useMemo(() => canonical(configuration) !== canonical(draft), [configuration, draft])
  const activeMenuItems = useMemo(
    () => menuItems.filter((item) => item.is_active),
    [menuItems],
  )

  const patchGroup = (groupIndex: number, patch: Partial<StoreComboConfigurationGroupRecord>) => {
    if (!draft) return
    setDraft({
      ...draft,
      groups: draft.groups.map((group, index) => (index === groupIndex ? { ...group, ...patch } : group)),
    })
  }

  const patchComponent = (
    groupIndex: number,
    componentIndex: number,
    patch: Partial<StoreComboConfigurationGroupRecord['components'][number]>,
  ) => {
    if (!draft) return
    setDraft({
      ...draft,
      groups: draft.groups.map((group, index) => (
        index !== groupIndex
          ? group
          : {
              ...group,
              components: group.components.map((component, innerIndex) => (
                innerIndex === componentIndex ? { ...component, ...patch } : component
              )),
            }
      )),
    })
  }

  const addGroup = () => {
    if (!draft) return
    setDraft({
      ...draft,
      groups: [
        ...draft.groups,
        {
          group_id: null,
          group_code: null,
          component_group: '',
          name_zh: '',
          name_en: '',
          selection_rule: 'EXACTLY_ONE',
          required: true,
          enabled: true,
          display_order: nextOrder(draft.groups),
          default_component_code: null,
          components: [],
        },
      ],
    })
  }

  const addComponent = (groupIndex: number) => {
    if (!draft) return
    setDraft({
      ...draft,
      groups: draft.groups.map((group, index) => (
        index !== groupIndex
          ? group
          : {
              ...group,
              components: [
                ...group.components,
                {
                  id: null,
                  group_id: group.group_id ?? null,
                  component_group: groupCode(group),
                  component_code: '',
                  name_zh: '',
                  name_en: '',
                  enabled: true,
                  display_order: nextOrder(group.components),
                  is_default: group.components.length === 0,
                  linked_menu_item_id: null,
                  business_behavior: 'NO_KITCHEN_TASK',
                },
              ],
            }
      )),
    })
  }

  const removeGroup = (groupIndex: number) => {
    if (!draft) return
    setDraft({ ...draft, groups: draft.groups.filter((_, index) => index !== groupIndex) })
  }

  const removeComponent = (groupIndex: number, componentIndex: number) => {
    if (!draft) return
    setDraft({
      ...draft,
      groups: draft.groups.map((group, index) => (
        index !== groupIndex
          ? group
          : {
              ...group,
              components: group.components.filter((_, innerIndex) => innerIndex !== componentIndex),
            }
      )),
    })
  }

  const moveGroup = (groupIndex: number, direction: -1 | 1) => {
    if (!draft) return
    setDraft({
      ...draft,
      groups: moveWithDisplayOrder(draft.groups, groupIndex, direction),
    })
  }

  const moveComponent = (groupIndex: number, componentIndex: number, direction: -1 | 1) => {
    if (!draft) return
    setDraft({
      ...draft,
      groups: draft.groups.map((group, index) => (
        index !== groupIndex
          ? group
          : {
              ...group,
              components: moveWithDisplayOrder(group.components, componentIndex, direction),
            }
      )),
    })
  }

  const handleSave = async () => {
    if (!draft) return
    try {
      setSaving(true)
      setError(null)
      const saved = await updateStoreComboConfiguration({
        store_id: storeId,
        groups: draft.groups.map((group) => ({
          group_id: group.group_id ?? null,
          group_code: group.group_code ?? group.component_group ?? null,
          name_zh: group.name_zh.trim(),
          name_en: group.name_en.trim(),
          selection_rule: group.selection_rule ?? 'EXACTLY_ONE',
          required: group.selection_rule === 'OPTIONAL_ONE' ? false : true,
          enabled: group.enabled ?? true,
          display_order: group.display_order ?? 0,
          default_component_code: group.default_component_code ?? null,
          components: group.components.map((component) => ({
            id: component.id ?? null,
            group_id: component.group_id ?? group.group_id ?? null,
            component_group: groupCode(group),
            component_code: component.component_code || null,
            name_zh: component.name_zh.trim(),
            name_en: component.name_en.trim(),
            enabled: component.enabled,
            display_order: component.display_order ?? 0,
            is_default: component.is_default,
            linked_menu_item_id: component.linked_menu_item_id ?? null,
            business_behavior: component.business_behavior ?? 'NO_KITCHEN_TASK',
          })),
        })),
      })
      setConfiguration(saved)
      setDraft(cloneConfiguration(saved))
      onSaved?.(`Combo Configuration saved at menu revision ${saved.menu_revision}. Pads will refresh the complete menu snapshot.`)
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Failed to save Combo Configuration')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Combo Configuration / 套餐配置</div>
          <div className="mt-1 text-[0.85rem] text-[var(--muted)]">
            Store-level combo groups and components. Combo price still comes from Pricing Rules.
          </div>
          {configuration ? (
            <div className="mt-1 text-[0.76rem] text-[var(--muted)]">Menu revision {configuration.menu_revision}</div>
          ) : null}
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={addGroup}
            disabled={loading || saving || !draft}
            className="rounded-[14px] border border-[rgba(97,0,0,0.18)] bg-white px-3 py-2 text-[0.82rem] font-semibold text-[var(--primary)] disabled:opacity-60"
          >
            + New Group
          </button>
          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={loading || saving || !dirty || !draft}
            className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white disabled:opacity-60"
          >
            {saving ? 'Saving...' : 'Save'}
          </button>
        </div>
      </div>

      {error ? (
        <div className="mt-4 rounded-[16px] border border-[rgba(97,0,0,0.18)] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.82rem] font-semibold text-[var(--primary)]">
          {error}
        </div>
      ) : null}

      {loading || !draft ? (
        <div className="mt-4 rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-3 text-[0.84rem] text-[var(--muted)]">
          Loading Combo Configuration...
        </div>
      ) : (
        <div className="mt-4 grid gap-3">
          {draft.groups.map((group, groupIndex) => (
            <div key={`${group.group_id ?? 'new'}:${group.group_code ?? groupIndex}`} className="rounded-[20px] bg-[rgba(26,28,25,0.035)] px-4 py-4">
              <div className={comboConfigurationLayoutClasses.groupRow}>
                <label className={comboConfigurationLayoutClasses.controlField}>
                  <span className="text-[0.68rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Name ZH</span>
                  <input
                    value={group.name_zh}
                    onChange={(event) => patchGroup(groupIndex, { name_zh: event.target.value })}
                    placeholder="Group Chinese name"
                    className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none"
                  />
                </label>
                <label className={comboConfigurationLayoutClasses.controlField}>
                  <span className="text-[0.68rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Name EN</span>
                  <input
                    value={group.name_en}
                    onChange={(event) => patchGroup(groupIndex, { name_en: event.target.value })}
                    placeholder="Group English name"
                    className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none"
                  />
                </label>
                <label className={comboConfigurationLayoutClasses.controlField}>
                  <span className="text-[0.68rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Selection</span>
                  <select
                    value={group.selection_rule ?? 'EXACTLY_ONE'}
                    onChange={(event) => patchGroup(groupIndex, {
                      selection_rule: event.target.value,
                      required: event.target.value !== 'OPTIONAL_ONE',
                    })}
                    className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none"
                  >
                    <option value="EXACTLY_ONE">Choose exactly one</option>
                    <option value="OPTIONAL_ONE">Optional choose one</option>
                  </select>
                </label>
                <label className={comboConfigurationLayoutClasses.displayOrderField}>
                    <span className="text-[0.68rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Display Order / 排序</span>
                    <input
                      type="number"
                      value={group.display_order ?? 0}
                      onChange={(event) => patchGroup(groupIndex, { display_order: Number(event.target.value) })}
                      className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none"
                      aria-label="Group display order"
                    />
                </label>
                <div className={comboConfigurationLayoutClasses.reorderControls} aria-label="Group reorder controls">
                  <button
                    type="button"
                    onClick={() => moveGroup(groupIndex, -1)}
                    disabled={groupIndex === 0}
                    className="min-h-10 rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)] disabled:opacity-40"
                    aria-label="Move group up"
                  >
                    ↑ Up
                  </button>
                  <button
                    type="button"
                    onClick={() => moveGroup(groupIndex, 1)}
                    disabled={groupIndex === draft.groups.length - 1}
                    className="min-h-10 rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)] disabled:opacity-40"
                    aria-label="Move group down"
                  >
                    ↓ Down
                  </button>
                </div>
                <button
                  type="button"
                  onClick={() => patchGroup(groupIndex, { enabled: !(group.enabled ?? true) })}
                  className="min-h-10 rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)] xl:self-end"
                >
                  {group.enabled ?? true ? 'Enabled' : 'Disabled'}
                </button>
                <button
                  type="button"
                  onClick={() => addComponent(groupIndex)}
                  className="min-h-10 rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)] xl:self-end"
                >
                  + Add Item
                </button>
                <button
                  type="button"
                  onClick={() => removeGroup(groupIndex)}
                  className="min-h-10 rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)] xl:self-end"
                >
                  Delete
                </button>
              </div>

              <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                <div className="text-[0.75rem] font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
                  {groupCode(group) || 'Code generated after save'}
                </div>
              </div>

              <div className="mt-3 space-y-2">
                {group.components.map((component, componentIndex) => (
                  <div
                    key={`${component.id ?? 'new'}:${component.component_code ?? componentIndex}`}
                    className={comboConfigurationLayoutClasses.componentRow}
                  >
                    <label className={comboConfigurationLayoutClasses.controlField}>
                      <span className="text-[0.66rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">Name ZH</span>
                      <input
                        value={component.name_zh}
                        onChange={(event) => patchComponent(groupIndex, componentIndex, { name_zh: event.target.value })}
                        placeholder="Item Chinese name"
                        className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.84rem] outline-none"
                      />
                    </label>
                    <label className={comboConfigurationLayoutClasses.controlField}>
                      <span className="text-[0.66rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">Name EN</span>
                      <input
                        value={component.name_en}
                        onChange={(event) => patchComponent(groupIndex, componentIndex, { name_en: event.target.value })}
                        placeholder="Item English name"
                        className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.84rem] outline-none"
                      />
                    </label>
                    <label className={comboConfigurationLayoutClasses.displayOrderField}>
                      <span className="text-[0.66rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">Display Order / 排序</span>
                      <input
                        type="number"
                        value={component.display_order ?? 0}
                        onChange={(event) => patchComponent(groupIndex, componentIndex, { display_order: Number(event.target.value) })}
                        className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.84rem] outline-none"
                        aria-label="Component display order"
                      />
                    </label>
                    <div className={comboConfigurationLayoutClasses.reorderControls} aria-label="Component reorder controls">
                      <button
                        type="button"
                        onClick={() => moveComponent(groupIndex, componentIndex, -1)}
                        disabled={componentIndex === 0}
                        className="min-h-10 rounded-[10px] bg-[rgba(26,28,25,0.05)] px-2.5 py-2 text-[0.76rem] font-semibold text-[var(--on-surface)] disabled:opacity-40"
                        aria-label="Move component up"
                      >
                        ↑ Up
                      </button>
                      <button
                        type="button"
                        onClick={() => moveComponent(groupIndex, componentIndex, 1)}
                        disabled={componentIndex === group.components.length - 1}
                        className="min-h-10 rounded-[10px] bg-[rgba(26,28,25,0.05)] px-2.5 py-2 text-[0.76rem] font-semibold text-[var(--on-surface)] disabled:opacity-40"
                        aria-label="Move component down"
                      >
                        ↓ Down
                      </button>
                    </div>
                    <label className={comboConfigurationLayoutClasses.mappingField}>
                      <span className="text-[0.66rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">Mapping</span>
                      <select
                        value={component.business_behavior ?? 'NO_KITCHEN_TASK'}
                        onChange={(event) => patchComponent(groupIndex, componentIndex, {
                          business_behavior: event.target.value,
                          linked_menu_item_id: event.target.value === 'LINKED_MENU_ITEM' ? component.linked_menu_item_id ?? null : null,
                        })}
                        className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.84rem] outline-none"
                      >
                        <option value="NO_KITCHEN_TASK">No kitchen task</option>
                        <option value="LINKED_MENU_ITEM">Link menu item</option>
                        <option value="LEGACY_COMBO_SIDE_TASK">Legacy side task</option>
                      </select>
                    </label>
                    <label className={comboConfigurationLayoutClasses.linkedItemField}>
                      <span className="text-[0.66rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">Linked menu item</span>
                      <select
                        value={component.linked_menu_item_id ?? ''}
                        onChange={(event) => patchComponent(groupIndex, componentIndex, {
                          linked_menu_item_id: event.target.value ? Number(event.target.value) : null,
                        })}
                        disabled={(component.business_behavior ?? 'NO_KITCHEN_TASK') !== 'LINKED_MENU_ITEM'}
                        className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.84rem] outline-none disabled:opacity-50"
                      >
                        <option value="">No linked item</option>
                        {activeMenuItems.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.name_zh} / {item.name_en || item.sku}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button
                      type="button"
                      onClick={() => patchComponent(groupIndex, componentIndex, { enabled: !component.enabled })}
                      className="min-h-10 rounded-[12px] bg-[rgba(26,28,25,0.05)] px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)] xl:self-end"
                    >
                      {component.enabled ? 'On' : 'Off'}
                    </button>
                    <button
                      type="button"
                      onClick={() => removeComponent(groupIndex, componentIndex)}
                      className="min-h-10 rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)] xl:self-end"
                    >
                      Delete
                    </button>
                    <label className={comboConfigurationLayoutClasses.defaultRow}>
                      <input
                        type="radio"
                        checked={component.is_default || group.default_component_code === component.component_code}
                        onChange={() => patchGroup(groupIndex, { default_component_code: component.component_code || null, components: group.components.map((current, index) => ({
                          ...current,
                          is_default: index === componentIndex,
                        })) })}
                      />
                      Default component {component.component_code ? `· ${component.component_code}` : '· generated after save'}
                    </label>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
