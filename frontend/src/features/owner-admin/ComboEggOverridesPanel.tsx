import { useEffect, useRef, useState } from 'react'
import { useLocale } from '../../i18n/useLocale'
import { translate } from '../../i18n/engine'
import {
  fetchOwnerMenuItemOptions,
  fetchStoreComboConfiguration,
  updateOwnerMenuItemComboEggDefault,
  type StoreComboConfigurationRecord,
} from '../../services/ownerMenuOptionService'
import { fetchAdminMenuItems, type MenuItemAdminRecord } from '../../services/platformAdminService'

interface ComboEggOverridesPanelProps {
  storeId: number
  configuration: StoreComboConfigurationRecord
  onChanged: (next: StoreComboConfigurationRecord) => void
}

interface OverrideDraft {
  itemId: string
  componentCode: string
  editing: boolean
}

const fieldClass = 'rounded-[12px] border border-[rgba(26,28,25,0.12)] bg-white px-3 py-2 text-[0.85rem] disabled:opacity-60'
const buttonClass = 'rounded-[12px] bg-white px-3 py-2 text-[0.82rem] font-semibold text-[var(--primary)] disabled:opacity-50'

export function ComboEggOverridesPanel({ storeId, configuration, onChanged }: ComboEggOverridesPanelProps) {
  const { t } = useLocale()
  const [candidates, setCandidates] = useState<MenuItemAdminRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [draft, setDraft] = useState<OverrideDraft | null>(null)
  const scopeRef = useRef<{ storeId: number; active: boolean; saving: boolean } | null>(null)

  useEffect(() => {
    const scope = { storeId, active: true, saving: false }
    scopeRef.current = scope
    setCandidates([])
    setLoading(true)
    setSaving(false)
    setLoadError(null)
    setSaveError(null)
    setDraft(null)

    const loadCandidates = async () => {
      try {
        const items = await fetchAdminMenuItems(storeId)
        if (!scope.active) return
        const eligible = await Promise.all(items
          .filter((item) => item.store_id === storeId && item.is_active && item.id != null)
          .map(async (item) => {
            const options = await fetchOwnerMenuItemOptions(item.id!)
            return options.some((option) => option.is_active
              && option.option_group?.trim().toUpperCase() === 'COMBO') ? item : null
          }))
        if (scope.active) setCandidates(eligible.filter((item): item is MenuItemAdminRecord => item !== null))
      } catch (error) {
        if (scope.active) setLoadError(error instanceof Error ? error.message : translate('management.eggOverrides.loadFailed'))
      } finally {
        if (scope.active) setLoading(false)
      }
    }
    void loadCandidates()
    return () => { scope.active = false }
  }, [storeId])

  const sameStore = configuration.store_id === storeId
  const overrides = sameStore ? (configuration.item_overrides ?? []).filter((item) => item.default_combo_egg_component_code) : []
  const eggGroups = sameStore ? configuration.groups.filter((group) => (group.group_code || group.component_group) === 'COMBO_EGG') : []
  const allEggs = eggGroups.flatMap((group) => group.components)
  const eggChoices = eggGroups.filter((group) => group.enabled !== false)
    .flatMap((group) => group.components.filter((component) => component.enabled))
  const availableItems = candidates.filter((item) => !overrides.some((override) => override.item_id === item.id))
  const editedItem = overrides.find((item) => String(item.item_id) === draft?.itemId)
  const currentEgg = allEggs.find((component) => component.component_code === draft?.componentCode)
  const validChoice = !!draft && eggChoices.some((component) => component.component_code === draft.componentCode)
  const validItem = !!draft && (draft.editing
    ? !!editedItem
    : availableItems.some((item) => String(item.id) === draft.itemId))

  const saveOverride = async (itemId: number, componentCode: string | null) => {
    const scope = scopeRef.current
    if (!scope?.active || scope.storeId !== storeId || scope.saving || !sameStore) return
    scope.saving = true
    setSaving(true)
    setSaveError(null)
    try {
      await updateOwnerMenuItemComboEggDefault(itemId, componentCode)
      if (!scope.active) return
      const next = await fetchStoreComboConfiguration(storeId)
      if (!scope.active) return
      if (next.store_id !== storeId) throw new Error(translate('management.eggOverrides.reloadFailed'))
      onChanged(next)
      setDraft(null)
    } catch (error) {
      if (scope.active) setSaveError(error instanceof Error ? error.message : translate('management.eggOverrides.saveFailed'))
    } finally {
      scope.saving = false
      if (scope.active) setSaving(false)
    }
  }

  return (
    <section className="mt-5 rounded-[20px] bg-[rgba(26,28,25,0.035)] p-4" aria-label={t('management.eggOverrides.section')}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-bold text-[var(--on-surface)]">{t('management.eggOverrides.title')}</h3>
          <p className="mt-1 text-[0.82rem] text-[var(--muted)]">
            {t('management.eggOverrides.description')}
          </p>
        </div>
        <button
          type="button"
          className={buttonClass}
          disabled={loading || saving || !!draft || !sameStore || !availableItems.length || !eggChoices.length}
          onClick={() => {
            setSaveError(null)
            setDraft({ itemId: '', componentCode: eggChoices[0]?.component_code ?? '', editing: false })
          }}
        >{t('management.eggOverrides.add')}</button>
      </div>

      {loadError || saveError ? (
        <div role="alert" className="mt-3 rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.82rem] text-[var(--primary)]">
          {loadError ? <p>{loadError}</p> : null}
          {saveError ? <p>{saveError}</p> : null}
        </div>
      ) : null}

      {overrides.length ? (
        <ul className="mt-3 space-y-2">
          {overrides.map((item) => {
            const egg = allEggs.find((component) => component.component_code === item.default_combo_egg_component_code)
            return (
              <li key={item.item_id} className="flex flex-wrap items-center justify-between gap-3 rounded-[12px] bg-white px-3 py-3">
                <div>
                  <div className="text-[0.88rem] font-semibold">{item.name_zh} / {item.name_en}</div>
                  <div className="text-[0.82rem] text-[var(--muted)]">
                    {egg ? `${egg.name_zh} / ${egg.name_en}` : item.default_combo_egg_component_code}
                  </div>
                </div>
                <div className="flex gap-2">
                  <button type="button" className={buttonClass} disabled={saving || !!draft || !eggChoices.length}
                    aria-label={t('management.eggOverrides.editLabel', { name: item.name_en || item.name_zh })}
                    onClick={() => {
                      setSaveError(null)
                      setDraft({ itemId: String(item.item_id), componentCode: item.default_combo_egg_component_code!, editing: true })
                    }}>{t('common.edit')}</button>
                  <button type="button" className={buttonClass} disabled={saving || !!draft}
                    aria-label={t('management.eggOverrides.removeLabel', { name: item.name_en || item.name_zh })}
                    onClick={() => void saveOverride(item.item_id, null)}>{t('management.eggOverrides.remove')}</button>
                </div>
              </li>
            )
          })}
        </ul>
      ) : <p className="mt-3 text-[0.83rem] text-[var(--muted)]">{t('management.eggOverrides.empty')}</p>}

      {loading ? <p className="mt-3 text-[0.82rem] text-[var(--muted)]">{t('management.eggOverrides.loading')}</p> : null}

      {draft ? (
        <form className="mt-4 flex flex-wrap items-end gap-3" onSubmit={(event) => {
          event.preventDefault()
          if (validItem && validChoice) void saveOverride(Number(draft.itemId), draft.componentCode)
        }}>
          <label className="flex min-w-[180px] flex-col gap-1 text-[0.8rem]">
            {t('management.eggOverrides.item')}
            <select aria-label={t('management.eggOverrides.itemLabel')} value={draft.itemId} disabled={saving || draft.editing}
              className={fieldClass} onChange={(event) => setDraft({ ...draft, itemId: event.target.value })}>
              {draft.editing && editedItem ? (
                <option value={editedItem.item_id}>{editedItem.name_zh} / {editedItem.name_en}</option>
              ) : <>
                <option value="">{t('management.eggOverrides.selectItem')}</option>
                {availableItems.map((item) => <option key={item.id} value={item.id}>{item.name_zh} / {item.name_en}</option>)}
              </>}
            </select>
          </label>
          <label className="flex min-w-[180px] flex-col gap-1 text-[0.8rem]">
            {t('management.eggOverrides.defaultEgg')}
            <select aria-label={t('management.eggOverrides.defaultEggLabel')} className={fieldClass} value={draft.componentCode} disabled={saving}
              onChange={(event) => setDraft({ ...draft, componentCode: event.target.value })}>
              {!validChoice ? <option value={draft.componentCode} disabled>
                {currentEgg ? `${currentEgg.name_zh} / ${currentEgg.name_en}` : draft.componentCode} ({t('management.eggOverrides.unavailable')})
              </option> : null}
              {eggChoices.map((component) => <option key={component.component_code} value={component.component_code}>
                {component.name_zh} / {component.name_en}
              </option>)}
            </select>
          </label>
          <button type="submit" className="rounded-[12px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white disabled:opacity-50"
            disabled={saving || !validItem || !validChoice}>{saving ? t('common.saving') : t('management.eggOverrides.save')}</button>
          <button type="button" className={buttonClass} disabled={saving} onClick={() => setDraft(null)}>{t('common.cancel')}</button>
        </form>
      ) : null}
    </section>
  )
}
