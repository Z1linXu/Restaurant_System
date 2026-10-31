import { useEffect, useState } from 'react'
import { Button } from '../../components/ui/Button'
import { fetchItemOptionGroups, fetchOptionLibrary, saveItemOptionGroups, saveOptionLibrary, type TemplateOption, type TemplateOptionGroup } from '../../services/templatePlatformService'
import { copyTemplateGroup, newTemplateGroup, TEMPLATE_GROUP_LABELS } from '../../utils/templateOptionGroups'
import { menuManagementPath } from './menuManagementSections'

const input = 'min-h-11 w-full rounded-xl border border-[rgba(26,28,25,0.15)] bg-white px-3 py-2'
const kinds: TemplateOptionGroup['kind'][] = ['SPICY_LEVEL', 'ADD_ON', 'REMOVE', 'SIZE', 'CUSTOM', 'COMBO']
const labels = TEMPLATE_GROUP_LABELS
const code = () => crypto.randomUUID()

function move<T>(values: T[], index: number, offset: number): T[] {
  const result = [...values]
  if (index + offset < 0 || index + offset >= values.length) return result
  ;[result[index], result[index + offset]] = [result[index + offset], result[index]]
  return result
}

export function TemplateOptionGroupsPanel({ storeId, itemId, itemName }: { storeId: number; itemId?: number; itemName?: string }) {
  const [groups, setGroups] = useState<TemplateOptionGroup[]>([])
  const [library, setLibrary] = useState<TemplateOptionGroup[]>([])
  const [selectedPreset, setSelectedPreset] = useState('')
  const [kind, setKind] = useState<TemplateOptionGroup['kind']>('CUSTOM')
  const [loading, setLoading] = useState(true)
  const [loaded, setLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setLoading(true); setLoaded(false); setError(null); setNotice(null); setGroups([]); setLibrary([])
    void Promise.all([fetchOptionLibrary(storeId), itemId == null ? Promise.resolve(null) : fetchItemOptionGroups(storeId, itemId)])
      .then(([catalog, item]) => { if (active) { setLibrary(catalog.groups); setGroups(item?.groups ?? catalog.groups); setLoaded(true) } })
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
    if (!window.confirm(itemId == null ? 'Save the option library? Existing item copies stay independent. / 保存选项库？已有菜品副本不受影响。' : 'Apply these option changes to this item? / 应用此菜品的选项更新？')) return
    setSaving(true); setError(null); setNotice(null)
    try {
      const payload = { groups: groups.map((group, i) => ({ ...group, sort_order: i * 10, options: group.options.map((option, j) => ({ ...option, sort_order: j * 10 })) })) }
      const result = itemId == null ? await saveOptionLibrary(storeId, payload) : await saveItemOptionGroups(storeId, itemId, payload)
      setGroups(result.groups)
      if (itemId == null) setLibrary(result.groups)
      setNotice('Saved / 已保存')
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to save option groups') }
    finally { setSaving(false) }
  }

  return <section className="space-y-4 rounded-[26px] bg-white p-5 shadow-sm" aria-label={itemId == null ? 'Option Group Library' : 'Item Option Groups'}>
    <div><h2 className="text-xl font-bold">{itemId == null ? 'Option Group Library / 预设与自定义组选项库' : `Item Options / 菜品选项 · ${itemName ?? ''}`}</h2>
      <p className="mt-1 text-sm text-[var(--muted)]">{itemId == null ? 'Create reusable starting points. Each item receives its own independent copy.' : 'Choose no groups, add a library copy, or create a group for this item.'}</p></div>
    {loading ? <p>Loading / 加载中…</p> : <>
      {itemId != null ? <div className="flex flex-wrap items-center gap-2"><select aria-label="Library group" className={`${input} max-w-sm`} value={selectedPreset} onChange={(event) => setSelectedPreset(event.target.value)}>
        <option value="">Choose a library group / 选择预设或自定义组</option>{library.map((group) => <option key={group.group_instance_code} value={group.group_instance_code}>{group.name}</option>)}
      </select><Button variant="secondary" disabled={!selectedPreset} onClick={() => { const source = library.find((group) => group.group_instance_code === selectedPreset); if (source) setGroups([...groups, copyTemplateGroup(source)]) }}>Add independent copy / 添加独立副本</Button></div> : null}
      <div className="flex flex-wrap items-center gap-2"><select aria-label="New group kind" className={`${input} max-w-sm`} value={kind} onChange={(event) => setKind(event.target.value as TemplateOptionGroup['kind'])}>{kinds.map((value) => <option key={value} value={value}>{labels[value]}</option>)}</select>
        <Button variant="secondary" onClick={() => setGroups([...groups, newTemplateGroup(kind)])}>New group / 新建组选项</Button></div>
      {!groups.length ? <p className="rounded-xl bg-[var(--surface)] p-4">No option groups / 无组选项{itemId != null ? ' — items without interactive groups can be added directly in POS.' : ''}</p> : null}
      {groups.map((group, groupIndex) => <fieldset key={group.group_instance_code} className="space-y-3 rounded-2xl border border-[rgba(26,28,25,0.12)] p-4">
        <div className="flex flex-wrap gap-2"><label className="min-w-48 flex-1">Group name / 名称<input aria-label={`Group name ${groupIndex + 1}`} className={input} value={group.name} onChange={(event) => updateGroup(groupIndex, { name: event.target.value })} /></label>
          <Button variant="secondary" aria-label={`Move group ${groupIndex + 1} up`} disabled={groupIndex === 0} onClick={() => setGroups(move(groups, groupIndex, -1))}>↑</Button>
          <Button variant="secondary" aria-label={`Move group ${groupIndex + 1} down`} disabled={groupIndex === groups.length - 1} onClick={() => setGroups(move(groups, groupIndex, 1))}>↓</Button>
          <Button variant="tertiary" onClick={() => setGroups(groups.filter((_, i) => i !== groupIndex))}>Remove group / 移除组</Button></div>
        <div className="flex flex-wrap items-center gap-5"><span className="text-sm">{labels[group.kind]}</span>
          {group.kind !== 'COMBO' ? <><label>Selection / 选择 <select aria-label={`Selection mode ${groupIndex + 1}`} className={input} value={group.selection_mode} onChange={(event) => updateGroup(groupIndex, { selection_mode: event.target.value as TemplateOptionGroup['selection_mode'] })}><option value="SINGLE">Single / 单选</option><option value="MULTIPLE">Multiple / 多选</option></select></label>
          <label><input aria-label={`Required group ${groupIndex + 1}`} type="checkbox" checked={group.required} onChange={(event) => updateGroup(groupIndex, { required: event.target.checked })} /> Required / 必选</label></> : null}
          <label><input aria-label={`Enabled group ${groupIndex + 1}`} type="checkbox" checked={group.is_active} onChange={(event) => updateGroup(groupIndex, { is_active: event.target.checked })} /> Enabled / 启用</label></div>
        {group.kind === 'COMBO' ? <p className="text-sm text-[var(--muted)]">Combo choices and linked items use the existing Combo configuration. / 套餐内容继续在原Combo配置中管理。 <a className="font-semibold text-[var(--primary)] underline" href={menuManagementPath(storeId, 'combo')} target="_blank" rel="noreferrer">Open Combo configuration / 打开套餐配置</a></p> : <>
          {group.options.map((option, optionIndex) => <div key={option.id ?? `new-${optionIndex}`} className="grid items-end gap-2 rounded-xl bg-[var(--surface)] p-3 md:grid-cols-[1fr_1fr_7rem_auto]">
            <label>中文<input aria-label={`Option Chinese name ${groupIndex + 1}-${optionIndex + 1}`} className={input} value={option.name_zh} onChange={(event) => updateOption(groupIndex, optionIndex, { name_zh: event.target.value })} /></label>
            <label>English<input aria-label={`Option English name ${groupIndex + 1}-${optionIndex + 1}`} className={input} value={option.name_en} onChange={(event) => updateOption(groupIndex, optionIndex, { name_en: event.target.value })} /></label>
            <label>Price / 差价<input aria-label={`Option price ${groupIndex + 1}-${optionIndex + 1}`} className={input} type="number" min="0" step="0.01" disabled={group.kind === 'REMOVE'} value={option.price_delta} onChange={(event) => updateOption(groupIndex, optionIndex, { price_delta: Number(event.target.value) })} /></label>
            <div className="flex flex-wrap items-center gap-3"><label><input aria-label={`Default option ${groupIndex + 1}-${optionIndex + 1}`} type="checkbox" checked={Boolean(option.is_default || (option.id != null && group.default_option_ids.includes(option.id)))} onChange={(event) => setDefault(groupIndex, optionIndex, event.target.checked)} /> Default / 默认</label>
              <label><input aria-label={`Enabled option ${groupIndex + 1}-${optionIndex + 1}`} type="checkbox" checked={option.is_active} onChange={(event) => updateOption(groupIndex, optionIndex, { is_active: event.target.checked })} /> Enabled</label>
              <Button variant="tertiary" aria-label={`Move option ${groupIndex + 1}-${optionIndex + 1} up`} disabled={optionIndex === 0} onClick={() => updateGroup(groupIndex, { options: move(group.options, optionIndex, -1) })}>↑</Button>
              <Button variant="tertiary" aria-label={`Move option ${groupIndex + 1}-${optionIndex + 1} down`} disabled={optionIndex === group.options.length - 1} onClick={() => updateGroup(groupIndex, { options: move(group.options, optionIndex, 1) })}>↓</Button>
              <Button variant="tertiary" onClick={() => { const remaining = group.options.filter((_, i) => i !== optionIndex); updateGroup(groupIndex, { options: remaining, default_option_ids: group.default_option_ids.filter((id) => remaining.some((entry) => entry.id === id)) }) }}>Remove / 移除</Button></div>
          </div>)}
          <Button variant="secondary" onClick={() => updateGroup(groupIndex, { options: [...group.options, { option_type: ({ SPICY_LEVEL: 'spicy_level', REMOVE: 'remove', SIZE: 'size' } as Record<string, string>)[group.kind] ?? 'addon', option_code: code(), option_group: group.kind, parent_option_id: null, sort_order: group.options.length * 10, name_zh: '', name_en: '', price_delta: 0, is_active: true, is_default: false }] })}>Add option / 添加选项</Button>
        </>}
      </fieldset>)}
      <Button disabled={!loaded || saving} onClick={() => void save()}>{saving ? 'Saving…' : 'Save option groups / 保存组选项'}</Button>
    </>}
    {error ? <p role="alert" className="text-red-800">{error}</p> : null}{notice ? <p role="status" className="text-green-800">{notice}</p> : null}
  </section>
}
