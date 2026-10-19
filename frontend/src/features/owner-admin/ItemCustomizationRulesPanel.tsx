import { useState } from 'react'
import type { ItemCustomizationConfiguration } from '../../types/ordering'
import type { MenuItemOptionAdminRecord } from '../../services/ownerMenuOptionService'
import { updateItemCustomization } from '../../services/itemCustomizationService'

interface Props {
  itemId: number
  configuration: ItemCustomizationConfiguration
  options: MenuItemOptionAdminRecord[]
  onSaved: (configuration: ItemCustomizationConfiguration) => void
}

export function ItemCustomizationRulesPanel({ itemId, configuration, options, onSaved }: Props) {
  const [draft, setDraft] = useState(configuration)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const inputClass = 'min-h-11 rounded-[12px] border border-black/10 bg-white px-3 py-2'
  const changeGroup = (index: number, patch: Partial<ItemCustomizationConfiguration['option_groups'][number]>) => {
    setDraft((current) => ({ ...current, option_groups: current.option_groups.map((group, position) => position === index ? { ...group, ...patch } : group) }))
    setSaved(false)
  }
  const save = async () => {
    setSaving(true)
    setError(null)
    setSaved(false)
    try {
      const result = await updateItemCustomization(itemId, draft)
      setDraft(result)
      onSaved(result)
      setSaved(true)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '保存选项设置失败')
    } finally {
      setSaving(false)
    }
  }
  return <section aria-label="菜品选项设置" className="mt-4 space-y-4 rounded-[20px] border border-black/5 bg-white/70 p-4">
    <div className="flex items-center justify-between gap-3">
      <h3 className="font-bold">选项设置</h3>
      <button type="button" disabled={saving} onClick={() => void save()} className="min-h-11 rounded-[14px] bg-[var(--primary)] px-4 font-semibold text-white disabled:opacity-50">{saving ? '保存中…' : '保存选项设置'}</button>
    </div>
    {draft.option_groups.map((group, index) => {
      const choices = options.filter((option) => option.is_active && option.option_group === group.option_group)
      return <div key={group.option_group} className="space-y-3 rounded-[14px] bg-black/[0.025] p-3">
        <div className="font-semibold">{group.option_group === 'NOODLE_TYPE' ? '面型' : group.option_group === 'PROTEIN' ? '蛋白选择' : '选择项目'}</div>
        <div className="flex flex-wrap gap-3">
          <label className="flex items-center gap-2">最少<input className={`${inputClass} w-20`} aria-label={`${group.option_group} 最少选择`} type="number" min={0} value={group.min_select} onChange={(event) => changeGroup(index, { min_select: Number(event.target.value) })} /></label>
          <label className="flex items-center gap-2">最多<input className={`${inputClass} w-20`} aria-label={`${group.option_group} 最多选择`} type="number" min={1} value={group.max_select} onChange={(event) => changeGroup(index, { max_select: Number(event.target.value) })} /></label>
          <label className="flex min-h-11 items-center gap-2"><input type="checkbox" checked={group.distinct} onChange={(event) => changeGroup(index, { distinct: event.target.checked })} />不可重复</label>
        </div>
        <div className="text-sm font-semibold">默认选项</div>
        <div className="flex flex-wrap gap-2">{choices.map((option) => <label key={option.id} className="flex min-h-11 items-center gap-2 rounded-[12px] bg-white px-3">
          <input type={group.max_select === 1 ? 'radio' : 'checkbox'} name={`${itemId}:${group.option_group}:default`} checked={group.default_option_codes.includes(option.option_code ?? '')} onChange={(event) => {
            const code = option.option_code
            if (!code) return
            changeGroup(index, { default_option_codes: group.max_select === 1 ? [code] : event.target.checked ? [...group.default_option_codes, code] : group.default_option_codes.filter((candidate) => candidate !== code) })
          }} />{option.name_zh || option.name_en}
        </label>)}</div>
      </div>
    })}
    {Object.entries(draft.addon_max_quantities).map(([code, maximum]) => <label key={code} className="flex flex-wrap items-center gap-3 text-sm">
      {options.find((option) => option.option_code === code)?.name_zh || code} · 每份最多
      <input className={`${inputClass} w-20`} type="number" aria-label={`${code} 每份最多`} min={0} value={maximum} onChange={(event) => { setDraft({ ...draft, addon_max_quantities: { ...draft.addon_max_quantities, [code]: Number(event.target.value) } }); setSaved(false) }} />
    </label>)}
    {draft.combo ? <label className="flex flex-wrap items-center gap-3 font-semibold">套餐税前总价
      <input className={`${inputClass} w-28`} type="number" min={0} step="0.01" value={draft.combo.fixed_price} onChange={(event) => { setDraft({ ...draft, combo: { ...draft.combo!, fixed_price: Number(event.target.value) } }); setSaved(false) }} />
    </label> : null}
    {error ? <p role="alert" className="text-sm font-semibold text-red-800">{error}</p> : null}
    {saved ? <p role="status" className="text-sm text-green-800">已保存；新点单将使用更新后的设置。</p> : null}
  </section>
}
