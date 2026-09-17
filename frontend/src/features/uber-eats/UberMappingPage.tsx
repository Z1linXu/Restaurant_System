import { useEffect, useState } from 'react'
import { useCurrentStore } from '../store/useStoreContext'
import { useAuth } from '../auth/useAuth'
import { apiRequest } from '../../services/apiClient'
import type { BackendMenuCatalog } from '../../types/ordering'
import { bindUberStore, fetchUberChoices, fetchUberConnection, saveUberMapping, type UberChoice, type UberConnection } from '../../services/uberEatsService'
import type { BackendMenuItem } from '../../types/ordering'
export default function UberMappingPage() {
  const { storeId } = useCurrentStore(); const { user } = useAuth()
  const [connection, setConnection] = useState<UberConnection | null>(null)
  const [items, setItems] = useState<BackendMenuItem[]>([])
  const [choices, setChoices] = useState<UberChoice[]>([])
  const [itemId, setItemId] = useState(0); const [choice, setChoice] = useState('')
  const [kind, setKind] = useState('ITEM'); const [identifierType, setIdentifierType] = useState('ID')
  const [identifier, setIdentifier] = useState(''); const [parent, setParent] = useState(''); const [uberStore, setUberStore] = useState('')
  const [error, setError] = useState<string | null>(null); const [message, setMessage] = useState(''); const [busy, setBusy] = useState(false)
  useEffect(() => { let active = true; Promise.all([fetchUberConnection(storeId), apiRequest<BackendMenuCatalog>(`/api/v1/stores/${storeId}/integrations/uber-eats/mapping-catalog`)]).then(([status, catalog]) => { if (active) { setConnection(status); setItems(catalog.categories.flatMap(category => category.items)) } }).catch(e => { if (active) setError(String(e.message)) }); return () => { active = false } }, [storeId])
  useEffect(() => { if (!itemId) return; let active = true; fetchUberChoices(storeId, itemId).then(result => { if (active) setChoices(result) }).catch(e => { if (active) setError(String(e.message)) }); return () => { active = false } }, [storeId, itemId])
  const run = async (action: () => Promise<unknown>) => { if (busy) return; setBusy(true); setError(null); setMessage(''); try { await action(); setConnection(await fetchUberConnection(storeId)); setMessage('已保存。收件箱接单时将重新核对菜单。') } catch (e) { setError(e instanceof Error ? e.message : '保存失败') } finally { setBusy(false) } }
  const selected = choices.find(c => `${c.id}:${c.parentCode || ''}` === choice)
  const field = 'min-h-12 w-full rounded-xl border border-stone-300 bg-white px-3'
  return <section className="space-y-5"><p>使用 Uber 稳定 ID 关联本地菜单。中文名称与厨房格式由现有菜单和打印规则生成。</p>{error && <p role="alert" className="rounded-xl bg-red-100 p-4">{error}</p>}{message && <p role="status" className="rounded-xl bg-emerald-50 p-4">{message}</p>}
    <div className="rounded-2xl bg-stone-100 p-4"><p>环境：{connection?.environment || '加载中'} · 接入：{connection?.enabled ? '已启用' : '未启用'}</p><p>待补全映射的订单：{connection?.unmapped_orders ?? 0}</p><p>Uber Store：{connection?.store?.uberStoreId || '未关联'}</p><p>Webhook：{connection?.webhook_status === 'RECEIVED' ? '已收到事件' : '尚未收到事件'} {connection?.last_event_at}</p></div>
    {!connection?.store && user?.role_code === 'ADMIN' && <form className="flex gap-3" onSubmit={e => { e.preventDefault(); void run(() => bindUberStore(storeId, uberStore)) }}><input aria-label="Uber Store ID" className={field} required value={uberStore} onChange={e => setUberStore(e.target.value)} placeholder="已确认归属此门店的 Uber Store UUID" /><button className="min-h-12 shrink-0 rounded-xl bg-emerald-900 px-5 text-white" disabled={busy}>关联门店</button></form>}
    {!connection?.store && user?.role_code !== 'ADMIN' && <p>请联系平台管理员关联此门店的 Uber Store。</p>}
    {connection?.store && <form className="grid gap-4 rounded-2xl border p-5 md:grid-cols-2" onSubmit={e => { e.preventDefault(); void run(() => saveUberMapping(storeId, { kind, identifierType, uberIdentifier: identifier, uberItemId: parent, localMenuItemId: itemId, localOptionCode: selected?.code, localOptionGroup: selected?.group, parentOptionCode: selected?.parentCode })) }}>
      <label>映射类型<select className={field} value={kind} onChange={e => { setKind(e.target.value); setChoice('') }}><option value="ITEM">菜品</option><option value="MODIFIER">选项</option><option value="REMOVED_MODIFIER">取消默认配料（removed_items）</option></select></label>
      <label>标识类型<select className={field} value={identifierType} onChange={e => setIdentifierType(e.target.value)}><option value="ID">Uber item / modifier ID</option><option value="EXTERNAL_DATA">external_data</option></select></label>
      <label>Uber 稳定标识<input className={field} required maxLength={255} value={identifier} onChange={e => setIdentifier(e.target.value)} /></label>
      {kind !== 'ITEM' && <label>所属 Uber 菜品 ID<input className={field} required maxLength={255} value={parent} onChange={e => setParent(e.target.value)} /></label>}
      <label>本地菜品<select className={field} required value={itemId || ''} onChange={e => { setItemId(Number(e.target.value)); setChoice('') }}><option value="">选择菜品</option>{items.map(item => <option key={item.id} value={item.id}>{item.name_zh} · {item.sku}</option>)}</select></label>
      {kind !== 'ITEM' && <label>本地选项<select className={field} required value={choice} onChange={e => setChoice(e.target.value)}><option value="">选择选项</option>{choices.map(c => <option key={`${c.id}:${c.parentCode || ''}`} value={`${c.id}:${c.parentCode || ''}`}>{c.zh} · {c.group} · {c.code}{c.parentCode ? ` (${c.parentCode})` : ''}</option>)}</select></label>}
      <button disabled={busy || !itemId || (kind !== 'ITEM' && !selected)} className="min-h-14 rounded-xl bg-emerald-900 px-5 font-bold text-white disabled:opacity-40">{busy ? '保存中…' : '保存映射'}</button></form>}
    <h2 className="text-lg font-bold">已保存的映射</h2><div className="overflow-x-auto"><table className="w-full text-left"><thead><tr><th className="p-3">Uber 稳定标识</th><th>类型</th><th>本地菜品 / 选项</th></tr></thead><tbody>{connection?.mappings.map(m => <tr key={m.id} className="border-t"><td className="break-all p-3">{m.uberIdentifier}</td><td>{m.kind}</td><td>{items.find(i => i.id === m.localMenuItemId)?.name_zh || m.localMenuItemId} {m.localOptionCode}</td></tr>)}</tbody></table></div>
  </section>
}
