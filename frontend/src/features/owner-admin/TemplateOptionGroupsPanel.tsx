import { useEffect, useState } from 'react'
import { useLocale } from '../../i18n/useLocale'
import type { MessageKey } from '../../i18n/messages'
import { Button } from '../../components/ui/Button'
import { fetchItemOptionGroups, fetchOptionLibrary, saveItemOptionGroups, saveOptionLibrary, type TemplateOption, type TemplateOptionGroup } from '../../services/templatePlatformService'
import { copyTemplateGroup, newTemplateGroup } from '../../utils/templateOptionGroups'
import { menuManagementPath } from './menuManagementSections'
import { fetchStoreAddons, type StoreAddon } from '../../services/ownerAddonService'

const input = 'min-h-11 w-full rounded-xl border border-[rgba(26,28,25,0.15)] bg-white px-3 py-2'
const kinds: TemplateOptionGroup['kind'][] = ['SPICY_LEVEL', 'ADD_ON', 'REMOVE', 'SIZE', 'CUSTOM', 'COMBO']
const kindKeys: Record<TemplateOptionGroup['kind'], MessageKey> = { SPICY_LEVEL: 'options.spicy', ADD_ON: 'options.addon', REMOVE: 'options.remove', SIZE: 'options.size', CUSTOM: 'options.custom', COMBO: 'options.combo' }
const code = () => crypto.randomUUID()

function move<T>(values: T[], index: number, offset: number): T[] {
  const result = [...values]
  if (index + offset < 0 || index + offset >= values.length) return result
  ;[result[index], result[index + offset]] = [result[index + offset], result[index]]
  return result
}

export function TemplateOptionGroupsPanel({ storeId, itemId, itemName }: { storeId: number; itemId?: number; itemName?: string }) {
  const { t, label } = useLocale()
  const [groups, setGroups] = useState<TemplateOptionGroup[]>([])
  const [library, setLibrary] = useState<TemplateOptionGroup[]>([])
  const [addons, setAddons] = useState<StoreAddon[]>([])
  const [selectedPreset, setSelectedPreset] = useState('')
  const [kind, setKind] = useState<TemplateOptionGroup['kind']>('CUSTOM')
  const [loading, setLoading] = useState(true)
  const [loaded, setLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setLoading(true); setLoaded(false); setError(null); setNotice(null); setGroups([]); setLibrary([]); setAddons([])
    void Promise.all([fetchOptionLibrary(storeId), itemId == null ? Promise.resolve(null) : fetchItemOptionGroups(storeId, itemId), fetchStoreAddons(storeId)])
      .then(([catalog, item, addonCatalog]) => { if (active) { setLibrary(catalog.groups); setGroups(item?.groups ?? catalog.groups); setAddons(addonCatalog.addons); setLoaded(true) } })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : 'Unable to load option groups') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [storeId, itemId])

  const updateGroup = (index: number, patch: Partial<TemplateOptionGroup>) => setGroups((current) => current.map((group, i) => i === index ? { ...group, ...patch } : group))
  const updateOption = (groupIndex: number, optionIndex: number, patch: Partial<TemplateOption>) => {
    const group = groups[groupIndex]
    updateGroup(groupIndex, { options: group.options.map((option, i) => i === optionIndex ? { ...option, ...patch } : option) })
  }
  const setDefault = (groupIndex: number, optionIndex: number, checked: boolean) => {
    const group = groups[groupIndex]
    const options = group.options.map((option, i) => ({ ...option, is_default: i === optionIndex ? checked : group.selection_mode === 'SINGLE' && checked ? false : Boolean(option.is_default || (option.id != null && group.default_option_ids.includes(option.id))) }))
    updateGroup(groupIndex, { options, default_option_ids: options.filter((option) => option.is_default && option.id != null).map((option) => option.id!) })
  }
  const save = async () => {
    if (!loaded || saving) return
    if (!window.confirm(itemId == null ? t('options.confirmLibrary') : t('options.confirmItem'))) return
    setSaving(true); setError(null); setNotice(null)
    try {
      const payload = { groups: groups.map((group, i) => ({ ...group, sort_order: i * 10, options: group.options.map((option, j) => ({ ...option, sort_order: j * 10 })) })) }
      const result = itemId == null ? await saveOptionLibrary(storeId, payload) : await saveItemOptionGroups(storeId, itemId, payload)
      setGroups(result.groups)
      if (itemId == null) setLibrary(result.groups)
      setNotice(t('common.saved'))
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to save option groups') }
    finally { setSaving(false) }
  }

  return <section className="space-y-4 rounded-[26px] bg-white p-5 shadow-sm" aria-label={itemId == null ? 'Option Group Library' : 'Item Option Groups'}>
    <div><h2 className="text-xl font-bold">{itemId == null ? t('menu.library') : `${t('menu.itemOptions')} · ${itemName ?? ''}`}</h2>
      <p className="mt-1 text-sm text-[var(--muted)]">{itemId == null ? t('options.libraryHint') : t('options.itemHint')}</p></div>
    {loading ? <p>{t('common.loading')}</p> : <>
      {itemId != null ? <div className="flex flex-wrap items-center gap-2"><select aria-label="Library group" className={`${input} max-w-sm`} value={selectedPreset} onChange={(event) => setSelectedPreset(event.target.value)}>
        <option value="">{t('options.chooseLibrary')}</option>{library.map((group) => <option key={group.group_instance_code} value={group.group_instance_code}>{label({ name: group.name, translations: group.name_translations })}</option>)}
      </select><Button variant="secondary" disabled={!selectedPreset} onClick={() => { const source = library.find((group) => group.group_instance_code === selectedPreset); if (source) setGroups([...groups, copyTemplateGroup(source)]) }}>{t('options.copy')}</Button></div> : null}
      <div className="flex flex-wrap items-center gap-2"><select aria-label="New group kind" className={`${input} max-w-sm`} value={kind} onChange={(event) => setKind(event.target.value as TemplateOptionGroup['kind'])}>{kinds.map((value) => <option key={value} value={value}>{t(kindKeys[value])}</option>)}</select>
        <Button variant="secondary" onClick={() => setGroups([...groups, newTemplateGroup(kind)])}>{t('options.newGroup')}</Button></div>
      {!groups.length ? <p className="rounded-xl bg-[var(--surface)] p-4">{t('options.noGroups')}</p> : null}
      {groups.map((group, groupIndex) => <fieldset key={group.group_instance_code} className="space-y-3 rounded-2xl border border-[rgba(26,28,25,0.12)] p-4">
        <div className="flex flex-wrap gap-2"><label className="min-w-48 flex-1">{t('common.name')}<input aria-label={`Group name ${groupIndex + 1}`} className={input} value={group.name} onChange={(event) => updateGroup(groupIndex, { name: event.target.value })} /></label>
          <Button variant="secondary" aria-label={`Move group ${groupIndex + 1} up`} disabled={groupIndex === 0} onClick={() => setGroups(move(groups, groupIndex, -1))}>↑</Button>
          <Button variant="secondary" aria-label={`Move group ${groupIndex + 1} down`} disabled={groupIndex === groups.length - 1} onClick={() => setGroups(move(groups, groupIndex, 1))}>↓</Button>
          <Button variant="tertiary" onClick={() => setGroups(groups.filter((_, i) => i !== groupIndex))}>{t('options.removeGroup')}</Button></div>
        <div className="grid gap-2 md:grid-cols-3">{([['zh-CN', 'common.chinese'], ['en', 'common.english'], ['fr-CA', 'common.french']] as const).map(([language, key]) => <label key={language}>{t(key)}<input aria-label={`Group translation ${language} ${groupIndex + 1}`} className={input} value={group.name_translations?.[language] ?? ''} onChange={(event) => updateGroup(groupIndex, { name_translations: { ...group.name_translations, [language]: event.target.value } })} /></label>)}</div>
        <div className="flex flex-wrap items-center gap-5"><span className="text-sm">{t(kindKeys[group.kind])}</span>
          {group.kind !== 'COMBO' ? <><label>{t('options.title')} <select aria-label={`Selection mode ${groupIndex + 1}`} className={input} value={group.selection_mode} onChange={(event) => updateGroup(groupIndex, { selection_mode: event.target.value as TemplateOptionGroup['selection_mode'] })}><option value="SINGLE">{t('options.single')}</option><option value="MULTIPLE">{t('options.multiple')}</option></select></label>
          <label>{t('options.minimum')}<input aria-label={`Minimum selections ${groupIndex + 1}`} className={input} type="number" min="0" step="1" value={group.min_select ?? ''} onChange={(event) => updateGroup(groupIndex, { min_select: event.target.value === '' ? null : Number(event.target.value) })} /></label>
          <label>{t('options.maximum')}<input aria-label={`Maximum selections ${groupIndex + 1}`} className={input} type="number" min="0" step="1" value={group.max_select ?? ''} onChange={(event) => updateGroup(groupIndex, { max_select: event.target.value === '' ? null : Number(event.target.value) })} /></label>
          <label><input aria-label={`Required group ${groupIndex + 1}`} type="checkbox" checked={group.required} onChange={(event) => updateGroup(groupIndex, { required: event.target.checked })} /> {t('common.required')}</label></> : null}
          <label><input aria-label={`Enabled group ${groupIndex + 1}`} type="checkbox" checked={group.is_active} onChange={(event) => updateGroup(groupIndex, { is_active: event.target.checked })} /> {t('common.enabled')}</label></div>
        {group.kind === 'COMBO' ? <p className="text-sm text-[var(--muted)]">{t('options.comboExisting')} <a className="font-semibold text-[var(--primary)] underline" href={menuManagementPath(storeId, 'combo')} target="_blank" rel="noreferrer">{t('options.openCombo')}</a></p> : <>
          {group.kind === 'ADD_ON' ? <p className="text-sm text-[var(--muted)]">{t('management.addons.description')} <a className="font-semibold text-[var(--primary)] underline" href={menuManagementPath(storeId, 'addons')} target="_blank" rel="noreferrer">{t('management.addons.title')}</a></p> : null}
          {group.options.map((option, optionIndex) => <div key={option.id ?? `new-${optionIndex}`} className="grid items-end gap-2 rounded-xl bg-[var(--surface)] p-3 md:grid-cols-2 xl:grid-cols-[1fr_1fr_1fr_7rem_auto]">
            <label>{t('common.chinese')}<input aria-label={`Option Chinese name ${groupIndex + 1}-${optionIndex + 1}`} className={input} value={option.name_zh} readOnly={group.kind === 'ADD_ON'} onChange={(event) => updateOption(groupIndex, optionIndex, { name_zh: event.target.value })} /></label>
            <label>{t('common.english')}<input aria-label={`Option English name ${groupIndex + 1}-${optionIndex + 1}`} className={input} value={option.name_en} readOnly={group.kind === 'ADD_ON'} onChange={(event) => updateOption(groupIndex, optionIndex, { name_en: event.target.value })} /></label>
            <label>{t('common.french')}<input aria-label={`Option French name ${groupIndex + 1}-${optionIndex + 1}`} className={input} value={option.name_fr ?? ''} onChange={(event) => updateOption(groupIndex, optionIndex, { name_fr: event.target.value })} /></label>
            <label>{t('options.surcharge')}<input aria-label={`Option price ${groupIndex + 1}-${optionIndex + 1}`} className={input} type="number" min="0" step="0.01" readOnly={group.kind === 'ADD_ON'} disabled={group.kind === 'REMOVE'} value={option.price_delta} onChange={(event) => updateOption(groupIndex, optionIndex, { price_delta: Number(event.target.value) })} /></label>
            <div className="flex flex-wrap items-center gap-3"><label><input aria-label={`Default option ${groupIndex + 1}-${optionIndex + 1}`} type="checkbox" checked={Boolean(option.is_default || (option.id != null && group.default_option_ids.includes(option.id)))} onChange={(event) => setDefault(groupIndex, optionIndex, event.target.checked)} /> {t('common.default')}</label>
              <label><input aria-label={`Enabled option ${groupIndex + 1}-${optionIndex + 1}`} type="checkbox" checked={option.addon_eligible ?? option.is_active} onChange={(event) => updateOption(groupIndex, optionIndex, { is_active: event.target.checked, ...(option.store_addon_id != null ? { addon_eligible: event.target.checked } : {}) })} /> {t('common.enabled')}</label>
              <Button variant="tertiary" aria-label={`Move option ${groupIndex + 1}-${optionIndex + 1} up`} disabled={optionIndex === 0} onClick={() => updateGroup(groupIndex, { options: move(group.options, optionIndex, -1) })}>↑</Button>
              <Button variant="tertiary" aria-label={`Move option ${groupIndex + 1}-${optionIndex + 1} down`} disabled={optionIndex === group.options.length - 1} onClick={() => updateGroup(groupIndex, { options: move(group.options, optionIndex, 1) })}>↓</Button>
              <Button variant="tertiary" onClick={() => { const remaining = group.options.filter((_, i) => i !== optionIndex); updateGroup(groupIndex, { options: remaining, default_option_ids: group.default_option_ids.filter((id) => remaining.some((entry) => entry.id === id)) }) }}>{t('common.remove')}</Button></div>
          </div>)}
          {group.kind === 'ADD_ON' ? <select aria-label={`Add-on catalog ${groupIndex + 1}`} className={input} value="" onChange={(event) => {
            const addon = addons.find((entry) => entry.id === Number(event.target.value))
            if (addon) updateGroup(groupIndex, { options: [...group.options, { option_type: 'addon', option_code: addon.code, option_group: 'ADD_ON', parent_option_id: null, sort_order: group.options.length * 10, name_zh: addon.name_zh, name_en: addon.name_en ?? '', price_delta: addon.price, is_active: addon.active, is_default: false, store_addon_id: addon.id, addon_eligible: true }] })
          }}><option value="">{t('options.addOption')}</option>{addons.filter((addon) => addon.active && !group.options.some((option) => option.store_addon_id === addon.id || option.option_code === addon.code)).map((addon) => <option key={addon.id} value={addon.id}>{label({ en: addon.name_en, zh: addon.name_zh })} · {Number(addon.price).toFixed(2)}</option>)}</select>
            : <Button variant="secondary" onClick={() => updateGroup(groupIndex, { options: [...group.options, { option_type: ({ SPICY_LEVEL: 'spicy_level', REMOVE: 'remove', SIZE: 'size' } as Record<string, string>)[group.kind] ?? 'addon', option_code: code(), option_group: group.kind, parent_option_id: null, sort_order: group.options.length * 10, name_zh: '', name_en: '', price_delta: 0, is_active: true, is_default: false }] })}>{t('options.addOption')}</Button>}
        </>}
      </fieldset>)}
      <Button disabled={!loaded || saving} onClick={() => void save()}>{saving ? t('common.saving') : t('options.save')}</Button>
    </>}
    {error ? <p role="alert" className="text-red-800">{error}</p> : null}{notice ? <p role="status" className="text-green-800">{notice}</p> : null}
  </section>
}
