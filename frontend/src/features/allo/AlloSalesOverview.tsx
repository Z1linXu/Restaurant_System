import { useEffect, useRef, useState } from 'react'
import { useCurrentStore } from '../store/useStoreContext'
import { salesApi, money, localTime, type Overview } from './alloSalesService'
import { AdjustmentHistory } from './AlloOrderActions'
import { followReprint, printResultMessage } from '../../services/manualReprintService'
import type { PrintJobRecord } from '../../services/printingAdminService'
export function AlloSalesOverview() {
  const {storeId}=useCurrentStore()
  const [date,setDate]=useState(''),[data,setData]=useState<Overview|null>(null),[snapshot,setSnapshot]=useState<Overview|null>(null)
  const [error,setError]=useState(''),[status,setStatus]=useState(''),[busy,setBusy]=useState(false),[show,setShow]=useState(false)
  const key=useRef<string|null>(null),gate=useRef(false),scope=useRef(0),stop=useRef<(()=>void)|null>(null)
  const load=async()=>{const g=scope.current;try{const d=await salesApi<Overview>(storeId,`/overview${date?'?date='+date:''}`);if(g===scope.current){setData(d);setError('')}}catch(e){if(g===scope.current)setError(String(e))}}
  useEffect(()=>{scope.current++;void load();const timer=window.setInterval(()=>void load(),30000);const refresh=()=>{setSnapshot(null);key.current=null;void load()};window.addEventListener('allo-sales-changed',refresh);window.addEventListener('focus',refresh);return()=>{scope.current++;window.clearInterval(timer);window.removeEventListener('allo-sales-changed',refresh);window.removeEventListener('focus',refresh);stop.current?.()}},[storeId,date])
  async function generate() {
    if(gate.current)return;gate.current=true;setBusy(true);setError('');const g=scope.current
    try{const d=await salesApi<Overview>(storeId,`/reports${date?'?date='+date:''}`,{});if(g===scope.current){setSnapshot(d);setData(d);key.current=crypto.randomUUID();setShow(true);setStatus('')}}catch(e){if(g===scope.current)setError(String(e))}finally{gate.current=false;if(g===scope.current)setBusy(false)}
  }
  async function print() {
    if(!snapshot||!key.current||gate.current)return;gate.current=true;setBusy(true);setError('');const g=scope.current
    try{const job=await salesApi<PrintJobRecord>(storeId,'/reports/print',{report_id:snapshot.report_id,request_key:key.current});if(g!==scope.current)return;setStatus(printResultMessage(job));stop.current?.();stop.current=followReprint(job,j=>{if(g===scope.current){setStatus(printResultMessage(j));if(j.status==='PRINTED')key.current=null}})}catch(e){if(g===scope.current)setError(`${String(e)}；请重试打印。`)}finally{gate.current=false;if(g===scope.current)setBusy(false)}
  }
  const report=show&&snapshot?snapshot:data
  return <section className="space-y-5 p-4"><header className="flex flex-wrap items-center justify-between gap-4"><h1 className="text-3xl font-black">Sales Overview / 销售汇总</h1><label>营业日期 <input aria-label="营业日期" type="date" value={date||data?.business_date||''} className="rounded-xl border p-3" onChange={e=>{setDate(e.target.value);setSnapshot(null);setShow(false);key.current=null}}/></label><button className="rounded-xl bg-red-800 px-5 py-3 font-bold text-white disabled:opacity-50" disabled={busy} onClick={()=>void generate()}>生成销售汇总预览</button></header>
    {error&&<p role="alert" className="rounded-xl bg-red-50 p-4">{error}</p>}{status&&<p role="status">{status}</p>}
    {report&&<><div className="grid grid-cols-2 gap-4 lg:grid-cols-3">{(['gross','discount','net','tax','total','collected'] as const).map(k=><article key={k} className="rounded-2xl bg-white p-5 shadow-sm"><p>{({gross:'Gross Sales / 折扣前销售',discount:'Discounts / 折扣',net:'Net Sales / 税前净销售额',tax:'Taxes / 税',total:'Total Sales incl. Tax / 含税销售额',collected:'Amount Collected / 账面已收'})[k]}</p><strong className="mt-3 block text-3xl">{money(report[k])}</strong></article>)}</div><p>{report.collected_note}</p><p>{report.business_date} · {report.timezone} · 生成于 {localTime(report.generated_at)}</p>
    <div className="flex flex-wrap gap-6 rounded-2xl bg-white p-5"><p>Dine-in / 堂食：{report.dine_in.count} 单 · {money(report.dine_in.total)}</p><p>Takeout / 外带：{report.takeout.count} 单 · {money(report.takeout.total)}</p><p>Uber Eats：未接入</p><p>有效订单：{report.order_count} · 整单作废：{report.voided_orders} · 作废份数：{report.voided_quantity} · 作废税前金额：{money(report.voided_gross)}</p></div>
    <h2 className="text-xl font-bold">作废与折扣明细</h2><p>作废为单独披露信息，已从有效销售排除，不再重复扣减。</p><AdjustmentHistory rows={report.adjustments}/>
    {show&&snapshot&&<div className="rounded-2xl bg-white p-5"><div className="mb-4 flex flex-wrap gap-3"><button disabled={busy||!key.current} className="rounded-xl bg-red-800 px-5 py-3 font-bold text-white disabled:opacity-50" onClick={()=>void print()}>打印此报告</button><button className="rounded-xl border px-5 py-3" onClick={()=>{setShow(false);void load()}}>返回实时汇总</button><p>快照 {snapshot.report_id}；金额变化后请重新生成。</p></div><pre className="overflow-x-auto whitespace-pre-wrap font-mono text-base">{snapshot.rendered_text}</pre></div>}
    </>}
  </section>
}
