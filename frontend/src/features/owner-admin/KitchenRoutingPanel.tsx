import { useEffect, useMemo, useState } from 'react'
import { Button } from '../../components/ui/Button'
import { fetchMenuCatalog } from '../../services/menuService'
import { fetchKitchenRouting, fetchTemplatePrinters, previewKitchenRouting, saveKitchenRouting, type KitchenRoutingConfig, type KitchenRoutingPreview, type KitchenTicketGroup, type TemplatePrinter } from '../../services/templatePlatformService'
import type { BackendMenuCatalog, ItemCustomizationDraft } from '../../types/ordering'
import { mapCatalog } from '../../hooks/useMenuCatalog'
import { buildDefaultDraft } from '../../hooks/useOrderSessions'
import { mapOptions, calculateDraftLineSubtotal } from '../../utils/orderPricing'
import { templateSelectionError } from '../../utils/templateCustomization'
import { ItemCustomizationModal } from '../ordering/components/ItemCustomizationModal'
import { ReceiptPreview } from './ReceiptPreview'

const input = 'min-h-11 rounded-xl border border-[rgba(26,28,25,0.15)] bg-white px-3 py-2'
const empty: KitchenRoutingConfig = { enabled: false, groups: [], item_overrides: [] }

export function KitchenRoutingPanel({ storeId, canManage }: { storeId: number; canManage: boolean }) {
  const [config, setConfig] = useState<KitchenRoutingConfig>(empty)
  const [printers, setPrinters] = useState<TemplatePrinter[]>([])
  const [catalog, setCatalog] = useState<BackendMenuCatalog | null>(null)
  const [loading, setLoading] = useState(true)
  const [loaded, setLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [preview, setPreview] = useState<KitchenRoutingPreview | null>(null)
  const [lines, setLines] = useState<Record<string, ItemCustomizationDraft>>({})
  const [editing, setEditing] = useState<string | null>(null)
  const ordering = useMemo(() => catalog ? mapCatalog(catalog) : null, [catalog])

  useEffect(() => {
    let active = true
    setLoading(true); setLoaded(false); setError(null); setNotice(null); setPreview(null); setLines({}); setEditing(null)
    void Promise.all([fetchKitchenRouting(storeId), fetchTemplatePrinters(storeId), fetchMenuCatalog(storeId)])
      .then(([route, devices, menu]) => { if (active) { setConfig(route); setPrinters(devices); setCatalog(menu); setLoaded(true) } })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : 'Unable to load kitchen tickets') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [storeId])

  const change = (index: number, patch: Partial<KitchenTicketGroup>) => setConfig((current) => ({ ...current, groups: current.groups.map((group, i) => i === index ? { ...group, ...patch } : group) }))
  const move = (index: number, offset: number) => {
    const groups = [...config.groups]
    ;[groups[index], groups[index + offset]] = [groups[index + offset], groups[index]]
    setConfig({ ...config, groups: groups.map((group, i) => ({ ...group, sort_order: i * 10 })) })
  }
  const toggleId = (values: number[], id: number) => values.includes(id) ? values.filter((value) => value !== id) : [...values, id]
  const save = async () => {
    if (!canManage || !loaded || !window.confirm('Apply these kitchen ticket rules to future orders? / 将厨房票规则应用到后续订单？')) return
    setBusy(true); setError(null); setNotice(null)
    try { setConfig(await saveKitchenRouting(storeId, config)); setPreview(null); setNotice('Kitchen ticket rules saved / 厨房票规则已保存') }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to save routing') }
    finally { setBusy(false) }
  }
  const runPreview = async () => {
    setBusy(true); setError(null); setPreview(null)
    try {
      const items = Object.entries(lines).map(([id, draft]) => {
        const item = ordering?.items.find((candidate) => candidate.id === id)
        if (!item) throw new Error('Please reload the menu / 请刷新菜单')
        const invalid = templateSelectionError(item, draft)
        if (invalid) throw new Error(`${item.nameZh || item.nameEn}: ${invalid}`)
        return { menu_item_id: Number(id), quantity: draft.quantity, options: mapOptions(draft, item).map(({ option_id, quantity }) => ({ option_id, quantity })) }
      })
      if (!items.length) throw new Error('Choose preview items / 请选择预览菜品')
      setPreview(await previewKitchenRouting(storeId, { items }))
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to preview routing') }
    finally { setBusy(false) }
  }
  const editingItem = ordering?.items.find((item) => item.id === editing)

  return <section className="space-y-4 rounded-[26px] bg-white p-5 shadow-sm" aria-label="Kitchen Ticket Configuration">
    <div><h2 className="text-xl font-bold">Kitchen Tickets / 厨房分单</h2><p className="mt-1 text-sm text-[var(--muted)]">Configure independent kitchen tickets. Frontdesk receipts keep the complete order. / 自由配置厨房票，前台小票保留完整订单。</p></div>
    {loading ? <p>Loading / 加载中…</p> : <>
      <label className="flex min-h-11 items-center gap-2"><input type="checkbox" disabled={!canManage} checked={config.enabled} onChange={(event) => setConfig({ ...config, enabled: event.target.checked })} /> Use these kitchen ticket groups / 使用以下厨房分单组</label>
      <fieldset disabled={!canManage || !loaded || busy} className="space-y-4">
        {config.groups.map((group, index) => <section key={group.group_code} className="space-y-3 rounded-2xl border border-[rgba(26,28,25,0.12)] p-4">
          <div className="flex flex-wrap items-center gap-2"><label className="flex-1">Ticket name / 票组名<input className={`${input} w-full`} aria-label={`Kitchen group name ${index + 1}`} value={group.name} onChange={(event) => change(index, { name: event.target.value })} /></label>
            <Button variant="secondary" aria-label={`Move kitchen group ${index + 1} up`} disabled={index === 0} onClick={() => move(index, -1)}>↑</Button><Button variant="secondary" aria-label={`Move kitchen group ${index + 1} down`} disabled={index === config.groups.length - 1} onClick={() => move(index, 1)}>↓</Button>
            <Button variant="tertiary" onClick={() => setConfig({ ...config, groups: config.groups.filter((_, i) => i !== index), item_overrides: config.item_overrides.map((override) => ({ ...override, group_codes: override.group_codes.filter((value) => value !== group.group_code) })) })}>Remove / 移除</Button></div>
          <div className="flex flex-wrap items-center gap-4"><label><input type="checkbox" checked={group.enabled} onChange={(event) => change(index, { enabled: event.target.checked })} /> Enabled / 启用</label>
            <label>Logical target / 逻辑目标 <input className={input} aria-label={`Logical target ${index + 1}`} value={group.logical_target} onChange={(event) => change(index, { logical_target: event.target.value })} /></label>
            <label>MOCK printer / 模拟打印机 <select className={input} aria-label={`Kitchen printer ${index + 1}`} value={group.printer_id ?? ''} onChange={(event) => change(index, { printer_id: event.target.value ? Number(event.target.value) : null })}><option value="">Unbound / 未绑定</option>{printers.filter((printer) => printer.is_active).map((printer) => <option key={printer.id} value={printer.id}>{printer.name}</option>)}</select></label>
            <label>MOCK fault / 模拟故障 <select className={input} aria-label={`MOCK failure ${index + 1}`} value={group.mock_failure ?? ''} onChange={(event) => change(index, { mock_failure: (event.target.value || null) as KitchenTicketGroup['mock_failure'] })}><option value="">None / 无</option><option value="FAILED">FAILED / 模拟失败</option><option value="UNCERTAIN">UNCERTAIN / 模拟结果不确定</option></select></label></div>
          {!group.enabled || group.printer_id == null ? <p className="rounded-xl bg-amber-50 p-3 text-amber-900">{!group.enabled ? 'This mapped ticket is disabled.' : 'This ticket has no printer.'} Legal orders can still be saved; this ticket will need attention. / 合法订单仍可保存，此票会提示需要处理。</p> : null}
          <div><h3 className="font-semibold">Categories / 分类</h3><div className="flex flex-wrap gap-4">{catalog?.categories.map((category) => <label key={category.id}><input type="checkbox" checked={group.category_ids.includes(category.id)} onChange={() => change(index, { category_ids: toggleId(group.category_ids, category.id) })} /> {category.name_zh || category.name_en}</label>)}</div></div>
          <details><summary className="cursor-pointer font-semibold">Additional items / 单独包含菜品</summary><div className="mt-2 flex flex-wrap gap-4">{ordering?.items.map((item) => <label key={item.id}><input type="checkbox" checked={group.item_ids.includes(Number(item.id))} onChange={() => change(index, { item_ids: toggleId(group.item_ids, Number(item.id)) })} /> {item.nameZh || item.nameEn}</label>)}</div></details>
          {catalog?.combo_configuration?.groups.length ? <details><summary className="cursor-pointer font-semibold">Combo components / 必要套餐子项</summary><div className="mt-2 flex flex-wrap gap-4">{catalog.combo_configuration.groups.flatMap((entry) => entry.components).map((component) => <label key={component.component_code}><input type="checkbox" checked={group.combo_component_codes?.includes(component.component_code) ?? false} onChange={() => change(index, { combo_component_codes: group.combo_component_codes?.includes(component.component_code) ? group.combo_component_codes.filter((value) => value !== component.component_code) : [...(group.combo_component_codes ?? []), component.component_code] })} /> {component.name_zh || component.name_en}</label>)}</div></details> : null}
        </section>)}
        <Button variant="secondary" onClick={() => { const id = crypto.randomUUID(); setConfig({ ...config, groups: [...config.groups, { group_code: id, name: 'New kitchen ticket / 新厨房票', enabled: true, sort_order: config.groups.length * 10, category_ids: [], item_ids: [], combo_component_codes: [], logical_target: `kitchen-${id.slice(0, 8)}`, printer_id: null, mock_failure: null }] }) }}>New kitchen ticket / 新建厨房票</Button>
        <details><summary className="cursor-pointer font-semibold">Item overrides / 单品覆盖分类分配</summary><div className="mt-3 space-y-3">{ordering?.items.map((item) => {
          const override = config.item_overrides.find((entry) => entry.menu_item_id === Number(item.id))
          return <div key={item.id} className="rounded-xl bg-[var(--surface)] p-3"><label><input type="checkbox" checked={Boolean(override)} onChange={(event) => setConfig({ ...config, item_overrides: event.target.checked ? [...config.item_overrides, { menu_item_id: Number(item.id), group_codes: [] }] : config.item_overrides.filter((entry) => entry.menu_item_id !== Number(item.id)) })} /> {item.nameZh || item.nameEn} — override / 覆盖</label>
            {override ? <div className="mt-2 flex flex-wrap gap-4">{config.groups.map((group) => <label key={group.group_code}><input type="checkbox" checked={override.group_codes.includes(group.group_code)} onChange={() => setConfig({ ...config, item_overrides: config.item_overrides.map((entry) => entry === override ? { ...entry, group_codes: entry.group_codes.includes(group.group_code) ? entry.group_codes.filter((value) => value !== group.group_code) : [...entry.group_codes, group.group_code] } : entry) })} /> {group.name}</label>)}</div> : null}</div>
        })}</div></details>
        <Button onClick={() => void save()}>Save kitchen rules / 保存厨房票规则</Button>
      </fieldset>
      <div className="space-y-3 rounded-2xl bg-[var(--surface)] p-4"><h3 className="font-bold">SYNTHETIC Preview / 合成订单预览</h3><p className="text-sm">Uses saved rules. No order or print job is created. / 使用已保存规则，不创建营业订单或打印任务。</p>
        <div className="grid gap-2 md:grid-cols-2">{ordering?.items.map((item) => <div key={item.id} className="flex flex-wrap items-center gap-2"><label><input aria-label={`Preview item ${item.id}`} type="checkbox" checked={Boolean(lines[item.id])} onChange={(event) => setLines((current) => { const next = { ...current }; if (event.target.checked) next[item.id] = buildDefaultDraft(item); else delete next[item.id]; return next })} /> {item.nameZh || item.nameEn}</label>{lines[item.id] ? <><input aria-label={`Preview quantity ${item.id}`} type="number" min="1" className={`${input} w-20`} value={lines[item.id].quantity} onChange={(event) => setLines({ ...lines, [item.id]: { ...lines[item.id], quantity: Math.max(1, Number(event.target.value)) } })} /><Button variant="tertiary" onClick={() => setEditing(item.id)}>Options / 选项</Button></> : null}</div>)}</div>
        <Button variant="secondary" disabled={busy || !canManage} onClick={() => void runPreview()}>Preview saved rules / 预览已保存规则</Button>
        {preview ? <div className="space-y-3"><p className="text-sm text-[var(--muted)]">Menu {preview.menu_revision} · Routing {preview.routing_revision} · saved configuration / 已保存配置</p>
          {preview.diagnostics.map((diagnostic, i) => <p key={`${diagnostic.code}:${i}`} role="alert" className="text-amber-900">{diagnostic.message}</p>)}
          {preview.tickets.map((ticket) => <section key={ticket.group_code} className="rounded-2xl border bg-white p-3"><h4 className="font-bold">{ticket.group_name} · {ticket.logical_target}</h4>{ticket.diagnostics.map((diagnostic, i) => <p key={i} className="text-amber-900">{diagnostic.message}</p>)}<ReceiptPreview content={ticket.rendered_text} /></section>)}
          <details className="rounded-xl bg-white p-3"><summary className="cursor-pointer font-semibold">Item coverage / 商品分单去向</summary><ul className="mt-2 space-y-2">{preview.coverage.map((line, i) => <li key={`${line.source_key}:${i}`} className={line.disposition === 'ROUTED' ? '' : 'text-amber-900'}>
            {line.name || ordering?.items.find((item) => Number(item.id) === line.menu_item_id)?.nameZh || line.source_key}{line.parent_source_key ? ' (Combo component / 套餐子项)' : ''} → {line.group_codes.map((groupCode) => preview.tickets.find((ticket) => ticket.group_code === groupCode)?.group_name ?? groupCode).join(', ') || 'Unassigned / 未分配'} · {line.disposition}
          </li>)}</ul></details>
        </div> : null}
      </div>
    </>}
    {error ? <p role="alert" className="text-red-800">{error}</p> : null}{notice ? <p role="status">{notice}</p> : null}
    {editingItem && editing && lines[editing] ? <ItemCustomizationModal item={editingItem} draft={lines[editing]} mode="edit" subtotal={calculateDraftLineSubtotal(editingItem, lines[editing])} onClose={() => setEditing(null)} onChange={(draft) => setLines({ ...lines, [editing]: draft })} onSubmit={() => setEditing(null)} /> : null}
  </section>
}
