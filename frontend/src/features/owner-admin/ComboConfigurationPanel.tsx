import { useEffect, useMemo, useState } from 'react'
import { translate } from '../../i18n/engine'
import { useLocale } from '../../i18n/useLocale'
import {
  fetchStoreComboConfiguration,
  updateStoreComboConfiguration,
  type StoreComboConfigurationGroupRecord,
  type StoreComboConfigurationRecord,
} from '../../services/ownerMenuOptionService'
import type { MenuItemAdminRecord } from '../../services/platformAdminService'
import { ComboEggOverridesPanel } from './ComboEggOverridesPanel'
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
  const { t } = useLocale()
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
          setError(loadError instanceof Error ? loadError.message : translate('management.combo.loadFailed'))
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
  const eggGroupIndex = draft?.groups.findIndex((group) => groupCode(group) === 'COMBO_EGG') ?? -1
  const eggGroup = draft?.groups[eggGroupIndex]
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
      onSaved?.(t('management.combo.saved'))
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t('management.combo.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">{t('management.combo.title')}</div>
          <div className="mt-1 text-[0.85rem] text-[var(--muted)]">
            {t('management.combo.description')}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={addGroup}
            disabled={loading || saving || !draft}
            className="rounded-[14px] border border-[rgba(97,0,0,0.18)] bg-white px-3 py-2 text-[0.82rem] font-semibold text-[var(--primary)] disabled:opacity-60"
          >
            {t('management.combo.newGroup')}
          </button>
          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={loading || saving || !dirty || !draft}
            className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white disabled:opacity-60"
          >
            {saving ? t('common.saving') : t('common.save')}
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
          {t('management.combo.loading')}
        </div>
      ) : (
        <div className="mt-4 grid gap-3">
          {eggGroup ? (
            <div className="rounded-[20px] bg-[rgba(26,28,25,0.035)] p-4">
              <label className="grid gap-2 font-semibold">
                {t('management.combo.defaultEgg')}
                <select
                  aria-label={t('management.combo.defaultEgg')}
                  value={eggGroup.default_component_code ?? ''}
                  disabled={saving || eggGroup.enabled === false}
                  onChange={(event) => patchGroup(eggGroupIndex, {
                    default_component_code: event.target.value || null,
                    components: eggGroup.components.map((component) => ({
                      ...component, is_default: component.component_code === event.target.value,
                    })),
                  })}
                  className="min-h-11 rounded-[12px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2"
                >
                  <option value="">{t('management.combo.chooseEgg')}</option>
                  {eggGroup.components.filter((component) => component.enabled && component.component_code).map((component) => (
                    <option key={component.component_code} value={component.component_code}>{component.name_zh} / {component.name_en}</option>
                  ))}
                </select>
              </label>
              <p className="mt-2 text-sm text-[var(--muted)]">{t('management.combo.defaultEggHint')}</p>
            </div>
          ) : null}
          {configuration ? (
            <ComboEggOverridesPanel key={storeId} storeId={storeId} configuration={configuration} onChanged={(next) => {
              setConfiguration(next)
              setDraft((current) => current ? { ...current, item_overrides: next.item_overrides } : current)
              onSaved?.(t('management.combo.itemEggSaved'))
            }} />
          ) : null}
          {draft.groups.map((group, groupIndex) => (
            <div key={`${group.group_id ?? 'new'}:${group.group_code ?? groupIndex}`} className="rounded-[20px] bg-[rgba(26,28,25,0.035)] px-4 py-4">
              <div className={comboConfigurationLayoutClasses.groupRow}>
                <label className={comboConfigurationLayoutClasses.controlField}>
                  <span className="text-[0.68rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{t('management.chineseName')}</span>
                  <input
                    value={group.name_zh}
                    onChange={(event) => patchGroup(groupIndex, { name_zh: event.target.value })}
                    placeholder={t('management.combo.groupChineseName')}
                    className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none"
                  />
                </label>
                <label className={comboConfigurationLayoutClasses.controlField}>
                  <span className="text-[0.68rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{t('management.englishName')}</span>
                  <input
                    value={group.name_en}
                    onChange={(event) => patchGroup(groupIndex, { name_en: event.target.value })}
                    placeholder={t('management.combo.groupEnglishName')}
                    className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none"
                  />
                </label>
                <label className={comboConfigurationLayoutClasses.controlField}>
                  <span className="text-[0.68rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{t('management.combo.selection')}</span>
                  <select
                    value={group.selection_rule ?? 'EXACTLY_ONE'}
                    onChange={(event) => patchGroup(groupIndex, {
                      selection_rule: event.target.value,
                      required: event.target.value !== 'OPTIONAL_ONE',
                    })}
                    className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none"
                  >
                    <option value="EXACTLY_ONE">{t('management.combo.exactlyOne')}</option>
                    <option value="OPTIONAL_ONE">{t('management.combo.optionalOne')}</option>
                  </select>
                </label>
                <label className={comboConfigurationLayoutClasses.displayOrderField}>
                    <span className="text-[0.68rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{t('management.combo.displayOrder')}</span>
                    <input
                      type="number"
                      value={group.display_order ?? 0}
                      onChange={(event) => patchGroup(groupIndex, { display_order: Number(event.target.value) })}
                      className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] outline-none"
                      aria-label={t('management.combo.groupDisplayOrder')}
                    />
                </label>
                <div className={comboConfigurationLayoutClasses.reorderControls} aria-label={t('management.combo.groupReorder')}>
                  <button
                    type="button"
                    onClick={() => moveGroup(groupIndex, -1)}
                    disabled={groupIndex === 0}
                    className="min-h-10 rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)] disabled:opacity-40"
                    aria-label={t('management.combo.groupUp')}
                  >
                    {t('management.combo.up')}
                  </button>
                  <button
                    type="button"
                    onClick={() => moveGroup(groupIndex, 1)}
                    disabled={groupIndex === draft.groups.length - 1}
                    className="min-h-10 rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)] disabled:opacity-40"
                    aria-label={t('management.combo.groupDown')}
                  >
                    {t('management.combo.down')}
                  </button>
                </div>
                <button
                  type="button"
                  onClick={() => patchGroup(groupIndex, { enabled: !(group.enabled ?? true) })}
                  className="min-h-10 rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--on-surface)] xl:self-end"
                >
                  {group.enabled ?? true ? t('common.enabled') : t('common.disabled')}
                </button>
                <button
                  type="button"
                  onClick={() => addComponent(groupIndex)}
                  className="min-h-10 rounded-[12px] bg-white px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)] xl:self-end"
                >
                  {t('management.combo.addItem')}
                </button>
                <button
                  type="button"
                  onClick={() => removeGroup(groupIndex)}
                  className="min-h-10 rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)] xl:self-end"
                >
                  {t('management.combo.delete')}
                </button>
              </div>

              <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                <div className="text-[0.75rem] font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
                  {groupCode(group) || t('management.combo.generatedCode')}
                </div>
              </div>

              <div className="mt-3 space-y-2">
                {group.components.map((component, componentIndex) => (
                  <div
                    key={`${component.id ?? 'new'}:${component.component_code ?? componentIndex}`}
                    className={comboConfigurationLayoutClasses.componentRow}
                  >
                    <label className={comboConfigurationLayoutClasses.controlField}>
                      <span className="text-[0.66rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">{t('management.chineseName')}</span>
                      <input
                        value={component.name_zh}
                        onChange={(event) => patchComponent(groupIndex, componentIndex, { name_zh: event.target.value })}
                        placeholder={t('management.combo.itemChineseName')}
                        className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.84rem] outline-none"
                      />
                    </label>
                    <label className={comboConfigurationLayoutClasses.controlField}>
                      <span className="text-[0.66rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">{t('management.englishName')}</span>
                      <input
                        value={component.name_en}
                        onChange={(event) => patchComponent(groupIndex, componentIndex, { name_en: event.target.value })}
                        placeholder={t('management.combo.itemEnglishName')}
                        className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.84rem] outline-none"
                      />
                    </label>
                    <label className={comboConfigurationLayoutClasses.displayOrderField}>
                      <span className="text-[0.66rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">{t('management.combo.displayOrder')}</span>
                      <input
                        type="number"
                        value={component.display_order ?? 0}
                        onChange={(event) => patchComponent(groupIndex, componentIndex, { display_order: Number(event.target.value) })}
                        className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.84rem] outline-none"
                        aria-label={t('management.combo.componentDisplayOrder')}
                      />
                    </label>
                    <div className={comboConfigurationLayoutClasses.reorderControls} aria-label={t('management.combo.componentReorder')}>
                      <button
                        type="button"
                        onClick={() => moveComponent(groupIndex, componentIndex, -1)}
                        disabled={componentIndex === 0}
                        className="min-h-10 rounded-[10px] bg-[rgba(26,28,25,0.05)] px-2.5 py-2 text-[0.76rem] font-semibold text-[var(--on-surface)] disabled:opacity-40"
                        aria-label={t('management.combo.componentUp')}
                      >
                        {t('management.combo.up')}
                      </button>
                      <button
                        type="button"
                        onClick={() => moveComponent(groupIndex, componentIndex, 1)}
                        disabled={componentIndex === group.components.length - 1}
                        className="min-h-10 rounded-[10px] bg-[rgba(26,28,25,0.05)] px-2.5 py-2 text-[0.76rem] font-semibold text-[var(--on-surface)] disabled:opacity-40"
                        aria-label={t('management.combo.componentDown')}
                      >
                        {t('management.combo.down')}
                      </button>
                    </div>
                    <label className={comboConfigurationLayoutClasses.mappingField}>
                      <span className="text-[0.66rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">{t('management.combo.mapping')}</span>
                      <select
                        value={component.business_behavior ?? 'NO_KITCHEN_TASK'}
                        onChange={(event) => patchComponent(groupIndex, componentIndex, {
                          business_behavior: event.target.value,
                          linked_menu_item_id: event.target.value === 'LINKED_MENU_ITEM' ? component.linked_menu_item_id ?? null : null,
                        })}
                        className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.84rem] outline-none"
                      >
                        <option value="NO_KITCHEN_TASK">{t('management.combo.noTask')}</option>
                        <option value="LINKED_MENU_ITEM">{t('management.combo.linkItem')}</option>
                        <option value="LEGACY_COMBO_SIDE_TASK">{t('management.combo.legacyTask')}</option>
                      </select>
                    </label>
                    <label className={comboConfigurationLayoutClasses.linkedItemField}>
                      <span className="text-[0.66rem] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">{t('management.combo.linkedItem')}</span>
                      <select
                        value={component.linked_menu_item_id ?? ''}
                        onChange={(event) => patchComponent(groupIndex, componentIndex, {
                          linked_menu_item_id: event.target.value ? Number(event.target.value) : null,
                        })}
                        disabled={(component.business_behavior ?? 'NO_KITCHEN_TASK') !== 'LINKED_MENU_ITEM'}
                        className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.84rem] outline-none disabled:opacity-50"
                      >
                        <option value="">{t('management.combo.noLinkedItem')}</option>
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
                      {component.enabled ? t('common.enabled') : t('common.disabled')}
                    </button>
                    <button
                      type="button"
                      onClick={() => removeComponent(groupIndex, componentIndex)}
                      className="min-h-10 rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)] xl:self-end"
                    >
                      {t('management.combo.delete')}
                    </button>
                    {groupCode(group) !== 'COMBO_EGG' ? <label className={comboConfigurationLayoutClasses.defaultRow}>
                      <input
                        type="radio"
                        checked={component.is_default || group.default_component_code === component.component_code}
                        onChange={() => patchGroup(groupIndex, { default_component_code: component.component_code || null, components: group.components.map((current, index) => ({
                          ...current,
                          is_default: index === componentIndex,
                        })) })}
                      />
                      {t('management.combo.defaultComponent')} · {component.component_code || t('management.combo.generatedAfterSave')}
                    </label> : null}
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
