import { useEffect, useRef, useState } from 'react'
import { salesApi, money, actionLabel, localTime, notifySalesChanged, type FinancialDetail, type Adjustment, type Preset, type Amounts, type Ledger } from './alloSalesService'
const button = 'min-h-12 rounded-xl border border-stone-200 bg-white px-4 py-3 font-bold disabled:opacity-50'
const amountLabels:Record<keyof Amounts,string>={gross:'折扣前',discount:'折扣',net:'税前净额',tax:'税',total:'含税金额',collected:'账面已收'}
function readSnapshot(value:string):Partial<Amounts> {try{return JSON.parse(value) as Amounts}catch{return {}}}
function changesText(value:string) {try{return (JSON.parse(value) as {name:string;quantity:number}[]).map(i=>`${i.name} × ${i.quantity} 份`).join('、')}catch{return '菜品调整（详见原订单）'}}
export function AdjustmentHistory({ rows }: {rows: Ledger[]}) {
  return <div className="space-y-3">{rows.map(r => {const before=readSnapshot(r.before_snapshot),after=readSnapshot(r.after_snapshot);return <div className="rounded-xl bg-stone-50 p-3 text-sm" key={r.id}>
    <p>{localTime(r.created_at)} · {r.actor_name} · {r.order_no} · {r.table_no ?? r.pickup_no ?? r.order_type}</p>
    <p className="font-bold">{actionLabel(r.action)} · 账面已收调整 {money(r.collected_delta)}</p>
    <p>{changesText(r.item_changes)} {r.reason}</p><details><summary className="min-h-10 cursor-pointer py-2">前后金额</summary><dl className="grid grid-cols-2 gap-2">{(Object.keys(amountLabels) as (keyof Amounts)[]).map(k=><div key={k}><dt>{amountLabels[k]}</dt><dd>{money(before[k])} → {money(after[k])}</dd></div>)}</dl></details>
  </div>})}</div>
}
export function AlloOrderActions({ store, id, onClose, onChanged }: {store:number; id:number; onClose:()=>void; onChanged:()=>void}) {
  const [detail,setDetail]=useState<FinancialDetail|null>(null)
  const [presets,setPresets]=useState<Preset[]>([])
  const [tab,setTab]=useState('menu')
  const [selected,setSelected]=useState<Record<number,number>>({})
  const [type,setType]=useState('PERCENT'),[value,setValue]=useState('10'),[name,setName]=useState('')
  const [editing,setEditing]=useState<Preset|null>(null)
  const [reason,setReason]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false)
  const [pending,setPending]=useState<{request:Adjustment;before:Amounts;after:Amounts}|null>(null)
  const inFlight=useRef(false)
  const generation=useRef(0)
  const reload=async()=>{const d=await salesApi<FinancialDetail>(store,`/orders/${id}`);setDetail(d);setPresets(await salesApi<Preset[]>(store,'/presets'))}
  useEffect(()=>{let alive=true;generation.current++;salesApi<FinancialDetail>(store,`/orders/${id}`).then(d=>{if(alive)setDetail(d)}).catch(e=>{if(alive)setError(String(e))});salesApi<Preset[]>(store,'/presets').then(p=>{if(alive)setPresets(p)}).catch(()=>{});return()=>{alive=false;generation.current++}},[store,id])
  async function preview(action:string, preset?:Preset) {
    if(!detail || inFlight.current)return
    if(!navigator.onLine){setError('当前离线，请联网后调整订单。');return}
    inFlight.current=true;setBusy(true);setError('');const g=generation.current
    try {
      const r:Adjustment={request_key:crypto.randomUUID(),expected_revision:detail.order.current_revision??0,action,reason}
      if(action==='VOID_ITEMS')r.items=Object.entries(selected).filter(([,q])=>q>0).map(([i,q])=>({item_id:Number(i),quantity:q}))
      if(action==='DISCOUNT')Object.assign(r,{discount_type:preset?.discount_type??type,discount_value:preset?.value??Number(value),discount_name:preset?.name??(name||`Custom ${type==='PERCENT'?value+'%':money(value)} Off`)})
      const p=await salesApi<{before:Amounts;after:Amounts}>(store,`/orders/${id}/preview`,r)
      if(g===generation.current)setPending({request:r,...p})
    }catch(e){if(g===generation.current)setError(String(e))}finally{inFlight.current=false;if(g===generation.current)setBusy(false)}
  }
  async function apply() {
    if(!pending||inFlight.current)return
    inFlight.current=true;setBusy(true);setError('');const g=generation.current
    try {
      await salesApi(store,`/orders/${id}/adjustments`,pending.request)
      if(g!==generation.current)return
      setPending(null);setSelected({});setTab('menu');await reload();notifySalesChanged();onChanged()
    }catch(e){if(g===generation.current)setError(`${String(e)}。若结果未确认，请点击确认执行重试。`)}finally{inFlight.current=false;if(g===generation.current)setBusy(false)}
  }
  async function savePreset(p:Preset) {
    if(inFlight.current)return;inFlight.current=true;setBusy(true);setError('')
    try{setPresets(await salesApi<Preset[]>(store,'/presets',p));setTab('discount');setEditing(null)}catch(e){setError(String(e))}finally{inFlight.current=false;setBusy(false)}
  }
  return <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4"><section role="dialog" aria-modal="true" aria-label="订单操作" className="max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-3xl bg-white p-6">
    <header className="flex justify-between"><h2 className="text-2xl font-black">订单 #{id} · {detail?.business_date}</h2><button className={button} disabled={busy} onClick={onClose}>关闭</button></header>
    {error&&<p role="alert" className="my-3 rounded-xl bg-red-50 p-3 text-red-800">{error}</p>}
    {!detail?<p>加载中…</p>:<>
      <div className="my-4 grid grid-cols-3 gap-3">{(['gross','discount','net','tax','total','collected'] as const).map(k=><p key={k}>{({gross:'Gross / 原价',discount:'Discount / 折扣',net:'Net / 税前净额',tax:'Tax / 税',total:'含税金额',collected:'账面已收'})[k]}<strong className="block text-xl">{money(detail.amounts[k])}</strong></p>)}</div>
      <p className="text-sm text-stone-600">按门店规则记账的已收金额，未按现金/银行卡核对</p>
      {pending?<div className="my-5 rounded-2xl bg-red-50 p-4"><h3 className="text-xl font-bold">确认{actionLabel(pending.request.action)}</h3>
        <p>含税金额 {money(pending.before.total)} → {money(pending.after.total)}</p><p>账面已收调整 {money(Number(pending.after.collected)-Number(pending.before.collected))}</p>
        <p>{pending.request.items?.map(i=>`${detail.items.find(x=>x.id===i.item_id)?.item_name_snapshot_zh} ×${i.quantity}`).join('、')}</p>
        {pending.request.action==='VOID_ORDER'&&<p>作废所有剩余有效菜品。</p>}
        <p>{pending.request.discount_name}</p><p className="my-3">{detail.printing_notice}</p>
        <button className={button} disabled={busy} onClick={()=>void apply()}>确认执行</button> <button className={button} disabled={busy} onClick={()=>setPending(null)}>返回</button>
      </div>:<>
      {detail.editable&&<nav className="my-5 flex flex-wrap gap-3"><button className={button} onClick={()=>setTab('void')}>删除订单 / 菜品</button><button className={button} onClick={()=>setTab('discount')}>加折扣</button><button className={button} disabled={busy} onClick={()=>void preview('REMOVE_DISCOUNT')}>取消折扣</button></nav>}
      {!detail.editable&&<p className="my-4">仅可调整本门店当前营业日的有效订单。</p>}
      {detail.editable&&tab==='void'&&<div className="space-y-3">{detail.items.filter(i=>i.status!=='cancelled'&&i.quantity>i.voided_quantity&&i.combo_role!=='combo_side'&&i.combo_role!=='combo_egg').map(i=><label key={i.id} className="flex items-center justify-between gap-4 rounded-xl bg-stone-50 p-3"><span>{i.item_name_snapshot_zh||i.item_name_snapshot_en} · 有效 {i.quantity-i.voided_quantity} 份</span><input aria-label={`${i.item_name_snapshot_zh} 作废数量`} type="number" className="w-24 rounded-xl border p-3" min="0" max={i.quantity-i.voided_quantity} value={selected[i.id]??0} onChange={e=>setSelected({...selected,[i.id]:Number(e.target.value)})}/></label>)}
        <input className="w-full rounded-xl border p-3" placeholder="原因（可选）" maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/>
        <button className={button} disabled={busy} onClick={()=>void preview('VOID_ITEMS')}>确认选中数量</button> <button className={button} disabled={busy} onClick={()=>void preview('VOID_ORDER')}>作废整单</button></div>}
      {detail.editable&&tab==='discount'&&<div className="grid grid-cols-2 gap-3">{[10,25,50].map(v=><button className={button} disabled={busy} key={v} onClick={()=>void preview('DISCOUNT',{name:`${v}% Off`,discount_type:'PERCENT',value:v,enabled:true})}>{v}% Off</button>)}{[1,5,10].map(v=><button className={button} disabled={busy} key={'a'+v} onClick={()=>void preview('DISCOUNT',{name:`${money(v)} Off`,discount_type:'AMOUNT',value:v,enabled:true})}>{money(v)} Off</button>)}
        {presets.filter(p=>p.enabled).map(p=><button className={button} key={p.id} disabled={busy} onClick={()=>void preview('DISCOUNT',p)}>{p.name}</button>)}
        <button className={button} onClick={()=>{setType('PERCENT');setTab('custom')}}>自定义百分比</button><button className={button} onClick={()=>{setType('AMOUNT');setTab('custom')}}>自定义减免金额</button>
        <button className={button} onClick={()=>{setName('');setEditing(null);setTab('preset')}}>新增折扣预设</button><button className={button} onClick={()=>setTab('manage')}>管理自建预设</button></div>}
      {detail.editable&&(tab==='custom'||tab==='preset')&&<div className="space-y-3"><label className="block">名称<input className="ml-3 rounded-xl border p-3" value={name} maxLength={120} onChange={e=>setName(e.target.value)}/></label><select aria-label="折扣类型" className="rounded-xl border p-3" value={type} onChange={e=>setType(e.target.value)}><option value="PERCENT">百分比 Off</option><option value="AMOUNT">固定金额 Off</option></select> <input aria-label="折扣值" className="w-28 rounded-xl border p-3" type="number" min="0" max={type==='PERCENT'?100:999999.99} step="0.01" value={value} onChange={e=>setValue(e.target.value)}/><p>固定减免超过有效小计时封顶；百分比可到 100%。</p><button className={button} disabled={busy} onClick={()=>tab==='custom'?void preview('DISCOUNT'):void savePreset({...editing,name,discount_type:type,value:Number(value),enabled:editing?.enabled??true})}>{tab==='custom'?'查看金额并确认':'保存预设'}</button></div>}
      {tab==='manage'&&<div className="space-y-3">{presets.map(p=><div key={p.id} className="flex flex-wrap gap-3"><span>{p.name} · {p.enabled?'启用':'停用'}</span><button className={button} onClick={()=>{setEditing(p);setName(p.name);setType(p.discount_type);setValue(String(p.value));setTab('preset')}}>修改</button><button className={button} disabled={busy} onClick={()=>void savePreset({...p,enabled:!p.enabled})}>{p.enabled?'停用':'启用'}</button></div>)}</div>}
      </>}
      <h3 className="my-4 text-lg font-bold">调整记录</h3><AdjustmentHistory rows={detail.adjustments}/>
    </>}
  </section></div>
}
