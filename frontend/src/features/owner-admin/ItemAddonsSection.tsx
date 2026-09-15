import { useEffect, useRef, useState } from 'react'
import { fetchItemAddons, fetchStoreAddons, updateItemAddon, type ItemAddon, type AddonConflict } from '../../services/ownerAddonService'
import type { MenuItemOptionAdminRecord } from '../../services/ownerMenuOptionService'

export function isOrdinaryAddon(option: MenuItemOptionAdminRecord) {
  const group = option.option_group?.trim().toUpperCase()
  if (group) return group === 'ADD_ON'
  if (option.option_type?.trim().toLowerCase() !== 'addon') return false
  const code = option.option_code?.trim().toLowerCase()
  if (code) return code !== 'combo'
  return option.name_zh?.trim() !== '套餐' && option.name_en?.trim().toLowerCase() !== 'combo'
}

interface Props {
  storeId: number
  itemId: number
  options: MenuItemOptionAdminRecord[]
  onChanged: () => Promise<void>
}

export function ItemAddonsSection({ storeId, itemId, options, onChanged }: Props) {
  const [addons, setAddons] = useState<ItemAddon[]>([])
  const [conflicts, setConflicts] = useState<AddonConflict[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const generation = useRef(0)
  useEffect(() => {
    const current = ++generation.current
    setLoading(true)
    setError(null)
    setAddons([])
    setConflicts([])
    void Promise.all([fetchItemAddons(itemId), fetchStoreAddons(storeId)])
      .then(([items, catalog]) => {
        if (current !== generation.current) return
        setAddons(items)
        setConflicts(catalog.conflicts)
      })
      .catch((failure: unknown) => {
        if (current === generation.current) setError(failure instanceof Error ? failure.message : 'Failed to load Add-ons / 加料加载失败')
      })
      .finally(() => { if (current === generation.current) setLoading(false) })
    return () => { generation.current++ }
  }, [storeId, itemId])

  const changeEligibility = async (addon: ItemAddon, enabled: boolean) => {
    const current = generation.current
    setSaving(true)
    setError(null)
    try {
      await updateItemAddon(itemId, addon.id, enabled)
      const next = await fetchItemAddons(itemId)
      if (current !== generation.current) return
      setAddons(next)
      await onChanged()
    } catch (failure) {
      if (current === generation.current) setError(failure instanceof Error ? failure.message : 'Failed to save Add-on / 加料保存失败')
    } finally {
      if (current === generation.current) setSaving(false)
    }
  }

  const conflictIds = new Set(conflicts.flatMap((conflict) => conflict.option_ids))
  const legacy = options.filter((option) => isOrdinaryAddon(option)
    && (option.store_addon_id == null || conflictIds.has(option.id)))
  return (
    <section aria-label="Item Add-ons" className="mt-4 rounded-[20px] border border-[rgba(26,28,25,0.06)] bg-white/70 p-4">
      <h3 className="font-bold text-[var(--primary)]">Add-ons / 加料</h3>
      <p className="mt-1 text-sm text-[var(--muted)]">Choose which Add-ons this item offers. Edit names, prices and availability in the Add-ons tab. / 勾选此菜品可加的配料；名称、价格和启停在 Add-ons 页统一维护。</p>
      {error ? <p role="alert" className="mt-3 text-[var(--primary)]">{error}</p> : null}
      {loading ? <p className="mt-3 text-sm">Loading Add-ons / 正在加载加料…</p> : (
        <div className="mt-3 grid gap-2">
          {addons.map((addon) => {
            const conflict = conflicts.some((entry) => entry.code === addon.code)
            return (
              <label key={addon.id} className="flex min-h-11 items-center gap-3 rounded-[14px] bg-[rgba(26,28,25,0.035)] px-3 py-2">
                <input type="checkbox" aria-label={`Enable ${addon.name_en || addon.name_zh}`} checked={addon.enabled}
                  disabled={saving || conflict} onChange={(event) => void changeEligibility(addon, event.target.checked)} />
                <span className="flex-1">{addon.name_zh} / {addon.name_en} <span className="font-semibold">${Number(addon.price).toFixed(2)}</span>
                  {!addon.active ? <span className="block text-sm text-[var(--muted)]">Inactive in Add-ons / 加料已停用</span> : null}
                  {conflict ? <span className="block text-sm text-[var(--primary)]">Names or prices need an Owner decision / 名称或价格待店主确认</span> : null}
                </span>
              </label>
            )
          })}
          {!addons.length ? <p className="text-sm text-[var(--muted)]">No Add-ons yet. Create them in the Add-ons tab. / 请先在 Add-ons 页添加加料。</p> : null}
        </div>
      )}
      {legacy.length ? (
        <div className="mt-3 rounded-[14px] bg-amber-50 p-3" aria-label="Existing Add-ons awaiting agreement">
          <p className="text-sm">Existing Add-ons are shown below until their names and prices are agreed. / 以下原有加料保留显示，名称和价格待统一确认。</p>
          {legacy.map((option) => (
            <div key={option.id} className="mt-2 text-sm">{option.name_zh} / {option.name_en} · ${Number(option.price_delta).toFixed(2)} · {option.is_active ? 'Active / 启用' : 'Inactive / 停用'}</div>
          ))}
        </div>
      ) : null}
    </section>
  )
}
