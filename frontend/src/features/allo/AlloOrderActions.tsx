import { useEffect, useRef, useState } from 'react'
import { useLocale } from '../../i18n/useLocale'
import { withMenuCode } from '../../utils/menuCode'
import { AdjustmentHistory, AlloOrderItems, OrderAmounts } from './AlloOrderDetails'
import {
  actionLabel, money, notifySalesChanged, salesApi,
  type Adjustment, type AdjustmentPreview, type FinancialDetail, type FinancialItem,
  type FinancialOption, type Preset, type PresetDraft,
} from './alloSalesService'

const button = 'min-h-12 rounded-xl border border-stone-200 bg-white px-4 py-3 font-bold disabled:opacity-45'
const primary = 'min-h-12 rounded-xl bg-[var(--primary)] px-4 py-3 font-bold text-white disabled:opacity-45'
type Panel = 'menu' | 'void' | 'discount' | 'coupon' | 'editor' | 'manage'
type Editor = { kind: 'custom' | 'discount' | 'coupon'; draft: PresetDraft; value: string }
type Counts = Record<number, number>

const remaining = (line: { quantity: number; voided_quantity: number }) => line.quantity - line.voided_quantity
const isComboChild = (item: FinancialItem) => item.combo_role === 'combo_side' || item.combo_role === 'combo_egg'

function remainingAddon(item: FinancialItem, option: FinancialOption, items: FinancialItem[], selected: Counts) {
  let removed = selected[item.id] ?? 0
  if (isComboChild(item)) {
    const main = items.find(line => item.combo_group_no != null && line.combo_group_no === item.combo_group_no && line.combo_role === 'main')
    if (main) removed = Math.floor((selected[main.id] ?? 0) * item.quantity / main.quantity)
  }
  return Math.max(0, (remaining(item) - removed) * option.per_serving - option.standalone_voided_quantity)
}

function QuantityChoice({ name, count, max, disabled, onChange }: {
  name: string
  count: number
  max: number
  disabled: boolean
  onChange: (qty: number) => void
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-white px-3 py-2">
      <label className="flex min-h-12 cursor-pointer items-center gap-3 font-semibold">
        <input type="checkbox" aria-label={`选择 ${name}`} className="h-6 w-6 accent-red-800"
          checked={count > 0} disabled={disabled || max === 0} onChange={e => onChange(e.target.checked ? 1 : 0)} />
        <span>{name} <small className="text-stone-600">可选 {max} 份</small></span>
      </label>
      <div className="flex items-center gap-1">
        <button className={button} aria-label={`减少 ${name}`} disabled={disabled || count === 0}
          onClick={() => onChange(count - 1)}>−</button>
        <output aria-label={`${name} 作废数量`} className="w-10 text-center font-bold tabular-nums">{count}</output>
        <button className={button} aria-label={`增加 ${name}`} disabled={disabled || count >= max}
          onClick={() => onChange(count + 1)}>＋</button>
      </div>
    </div>
  )
}

export function AlloOrderActions({ store, id, onClose, onChanged }: {
  store: number
  id: number
  onClose: () => void
  onChanged: () => void
}) {
  const { label } = useLocale()
  const [detail, setDetail] = useState<FinancialDetail | null>(null)
  const [presets, setPresets] = useState<Preset[]>([])
  const [coupons, setCoupons] = useState<Preset[]>([])
  const [panel, setPanel] = useState<Panel>('menu')
  const [selectedItems, setSelectedItems] = useState<Counts>({})
  const [selectedAddons, setSelectedAddons] = useState<Counts>({})
  const [editor, setEditor] = useState<Editor | null>(null)
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState<(AdjustmentPreview & { request: Adjustment }) | null>(null)
  const inFlight = useRef(false)

  useEffect(() => {
    let active = true
    salesApi<FinancialDetail>(store, `/orders/${id}`)
      .then(order => { if (active) setDetail(order) })
      .catch(failure => { if (active) setError(String(failure)) })
    salesApi<Preset[]>(store, '/presets')
      .then(result => { if (active) setPresets(result) })
      .catch(failure => { if (active) setError(String(failure)) })
    salesApi<Preset[]>(store, '/coupons')
      .then(result => { if (active) setCoupons(result) })
      .catch(failure => { if (active) setError(String(failure)) })
    return () => { active = false }
  }, [store, id])

  async function run(task: () => Promise<void>) {
    if (inFlight.current) return
    inFlight.current = true
    setBusy(true)
    setError('')
    try {
      await task()
    } catch (failure) {
      setError(String(failure))
    } finally {
      inFlight.current = false
      setBusy(false)
    }
  }

  function preview(action: string, choice?: PresetDraft) {
    if (!detail || inFlight.current) return
    if (!navigator.onLine) {
      setError('请联网后调整订单。')
      return
    }

    const request: Adjustment = {
      request_key: crypto.randomUUID(),
      expected_revision: detail.order.current_revision ?? 0,
      action,
      reason,
    }
    if (action === 'VOID_ITEMS') {
      request.items = Object.entries(selectedItems).filter(([, quantity]) => quantity > 0)
        .map(([id, quantity]) => ({ item_id: Number(id), quantity }))
      request.addons = Object.entries(selectedAddons).filter(([, quantity]) => quantity > 0)
        .map(([id, quantity]) => ({ option_id: Number(id), quantity }))
    }
    if (action === 'DISCOUNT' && choice) {
      request.discount_type = choice.discount_type
      request.discount_value = choice.value
      request.discount_name = choice.name
    }
    if (action === 'APPLY_COUPON' && choice?.id != null && choice.revision != null) {
      request.coupon = { id: choice.id, revision: choice.revision }
    }

    void run(async () => {
      const result = await salesApi<AdjustmentPreview>(store, `/orders/${id}/preview`, request)
      setPending({ request, ...result })
    })
  }

  function apply() {
    if (!pending) return
    void run(async () => {
      const result = await salesApi<FinancialDetail>(store, `/orders/${id}/adjustments`, pending.request)
      setDetail(result)
      setPending(null)
      setSelectedItems({})
      setSelectedAddons({})
      setReason('')
      setPanel('menu')
      notifySalesChanged()
      onChanged()
    })
  }

  function selectItem(item: FinancialItem, quantity: number) {
    if (!detail) return
    const next = { ...selectedItems, [item.id]: quantity }
    setSelectedItems(next)
    setSelectedAddons(current => {
      const clamped = { ...current }
      for (const line of detail.items) {
        for (const option of line.options) {
          if (clamped[option.id]) {
            clamped[option.id] = Math.min(clamped[option.id], remainingAddon(line, option, detail.items, next))
          }
        }
      }
      return clamped
    })
  }

  function openEditor(kind: Editor['kind'], existing?: Preset, type = 'AMOUNT') {
    const draft = existing ?? { name: '', discount_type: type, value: 0, enabled: true }
    setEditor({ kind, draft, value: existing ? String(existing.value) : '' })
    setPanel('editor')
  }

  function savePreset(preset: PresetDraft, isCoupon: boolean) {
    void run(async () => {
      const result = await salesApi<Preset[]>(store, isCoupon ? '/coupons' : '/presets', preset)
      if (isCoupon) setCoupons(result)
      else setPresets(result)
      setEditor(null)
      setPanel(isCoupon ? 'coupon' : 'manage')
    })
  }

  const selected = Object.values(selectedItems).some(Boolean) || Object.values(selectedAddons).some(Boolean)
  const appliedCoupon = Boolean(detail?.order.discount_coupon_id)
  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4">
      <section role="dialog" aria-modal="true" aria-label="订单操作" className="max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-3xl bg-white p-6">
        <header className="flex items-center justify-between gap-3">
          <h2 className="text-2xl font-black">订单操作 {detail?.business_date}</h2>
          <button className={button} disabled={busy} onClick={onClose}>关闭</button>
        </header>
        {error && <p role="alert" className="my-3 rounded-xl bg-red-50 p-3 text-red-800">{error}</p>}
        {!detail && !error && <p>加载中…</p>}
        {detail && (
          <>
            <OrderAmounts amounts={detail.amounts} />
            <p className="text-sm text-stone-600">按门店规则记账的已收金额，未按现金/银行卡核对</p>
            {detail.order.discount_name && (
              <p className="mt-2 font-semibold">{appliedCoupon ? 'Coupon' : '折扣'} · {detail.order.discount_name}</p>
            )}
            {pending ? (
              <div className="my-5 rounded-2xl bg-red-50 p-4">
                <h3 className="text-xl font-bold">确认{actionLabel(pending.request.action)}</h3>
                <p>含税金额 {money(pending.before.total)} → {money(pending.after.total)}</p>
                <p>账面已收调整 {money(pending.after.collected - pending.before.collected)}</p>
                {!!pending.items.length && <p>{pending.items.map(row => `${row.name} ×${row.quantity}`).join('、')}</p>}
                {pending.discount_name && <p>{pending.discount_name}</p>}
                <p className="my-3">{detail.printing_notice}</p>
                <div className="flex flex-wrap gap-2">
                  <button className={primary} disabled={busy} onClick={apply}>确认执行</button>
                  <button className={button} disabled={busy} onClick={() => setPending(null)}>返回</button>
                </div>
              </div>
            ) : detail.editable ? (
              <>
                <nav className="my-5 flex flex-wrap gap-2">
                  <button className={button} disabled={busy} onClick={() => setPanel('void')}>删除订单 / 菜品</button>
                  <button className={button} disabled={busy} onClick={() => setPanel('discount')}>加折扣</button>
                  <button className={button} disabled={busy} onClick={() => setPanel('coupon')}>Coupon</button>
                  {detail.order.discount_type && (
                    <button className={button} disabled={busy} onClick={() => preview(appliedCoupon ? 'REMOVE_COUPON' : 'REMOVE_DISCOUNT')}>
                      {appliedCoupon ? '移除 Coupon' : '取消折扣'}
                    </button>
                  )}
                </nav>
                {panel === 'void' && (
                  <div className="space-y-3">
                    {detail.items.filter(item => remaining(item) > 0).map(item => {
                      const name = withMenuCode(item.item_sku_snapshot, label({
                        zh: item.item_name_snapshot_zh, en: item.item_name_snapshot_en, fr: item.item_name_snapshot_fr,
                      }))
                      const addons = item.options.filter(option => option.is_addon && remaining(option) > 0)
                      if (isComboChild(item) && !addons.length) return null
                      return (
                        <div key={item.id} className="space-y-2 rounded-xl bg-stone-100 p-2">
                          {isComboChild(item) ? <p className="px-3 py-2 font-semibold">{name}</p> : (
                            <QuantityChoice name={name} count={selectedItems[item.id] ?? 0} max={remaining(item)}
                              disabled={busy} onChange={quantity => selectItem(item, quantity)} />
                          )}
                          {addons.map(option => (
                            <div key={option.id} className="ml-4 border-l-2 border-stone-300 pl-2">
                              <QuantityChoice name={'加料 · ' + label({
                                zh: option.option_name_snapshot_zh, en: option.option_name_snapshot_en, fr: option.option_name_snapshot_fr,
                              })} count={selectedAddons[option.id] ?? 0} max={remainingAddon(item, option, detail.items, selectedItems)}
                                disabled={busy} onChange={quantity => setSelectedAddons(current => ({ ...current, [option.id]: quantity }))} />
                            </div>
                          ))}
                        </div>
                      )
                    })}
                    <input className="w-full rounded-xl border p-3" aria-label="作废原因" placeholder="原因（可选）"
                      maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
                    <div className="flex flex-wrap gap-2">
                      <button className={primary} disabled={busy || !selected} onClick={() => preview('VOID_ITEMS')}>预览选中项</button>
                      <button className={button} disabled={busy} onClick={() => preview('VOID_ORDER')}>作废整单</button>
                    </div>
                  </div>
                )}
                {panel === 'discount' && (
                  <div className="grid grid-cols-2 gap-3">
                    {[10, 20, 50].map(value => (
                      <button className={button} disabled={busy} key={value}
                        onClick={() => preview('DISCOUNT', { name: `${value}% Off`, discount_type: 'PERCENT', value, enabled: true })}>
                        {value}% Off
                      </button>
                    ))}
                    {[1, 5, 10].map(value => (
                      <button className={button} disabled={busy} key={`amount-${value}`}
                        onClick={() => preview('DISCOUNT', { name: `${money(value)} Off`, discount_type: 'AMOUNT', value, enabled: true })}>
                        {money(value)} Off
                      </button>
                    ))}
                    {presets.filter(p => p.enabled).map(p => (
                      <button className={button} key={p.id} disabled={busy} onClick={() => preview('DISCOUNT', p)}>{p.name}</button>
                    ))}
                    <button className={button} disabled={busy} onClick={() => openEditor('custom', undefined, 'PERCENT')}>自定义百分比</button>
                    <button className={button} disabled={busy} onClick={() => openEditor('custom')}>自定义减免金额</button>
                    <button className={button} disabled={busy} onClick={() => openEditor('discount')}>新增折扣预设</button>
                    <button className={button} disabled={busy} onClick={() => setPanel('manage')}>管理折扣预设</button>
                  </div>
                )}
                {(panel === 'coupon' || panel === 'manage') && (
                  <div className="space-y-2">
                    {panel === 'coupon' && <button className={primary} disabled={busy} onClick={() => openEditor('coupon')}>新增 Coupon</button>}
                    {(panel === 'coupon' ? coupons : presets).map(p => (
                      <div className="flex flex-wrap items-center gap-2 rounded-xl bg-stone-50 p-2" key={p.id}>
                        <span className="mr-auto px-2 font-semibold">
                          {p.name} · {p.discount_type === 'PERCENT' ? `${p.value}%` : money(p.value)}
                          {!p.enabled && (panel === 'coupon' ? ' · 已删除' : ' · 停用')}
                        </span>
                        {panel === 'coupon' && p.enabled && (
                          <button className={button} disabled={busy} aria-label={`应用 ${p.name}`} onClick={() => preview('APPLY_COUPON', p)}>应用</button>
                        )}
                        {p.enabled && <button className={button} disabled={busy} aria-label={`修改 ${p.name}`}
                          onClick={() => openEditor(panel === 'coupon' ? 'coupon' : 'discount', p)}>修改</button>}
                        <button className={button} disabled={busy} aria-label={`${p.enabled ? (panel === 'coupon' ? '删除' : '停用') : '恢复'} ${p.name}`}
                          onClick={() => savePreset({ ...p, enabled: !p.enabled }, panel === 'coupon')}>
                          {p.enabled ? (panel === 'coupon' ? '删除' : '停用') : '恢复'}
                        </button>
                      </div>
                    ))}
                  </div>
                )}
                {panel === 'editor' && editor && (
                  <form className="space-y-3" onSubmit={e => {
                    e.preventDefault()
                    const value = Number(editor.value)
                    const suffix = editor.draft.discount_type === 'PERCENT' ? `${value}%` : money(value)
                    const preset = { ...editor.draft, name: editor.draft.name.trim() || `${suffix} Off`, value }
                    if (editor.kind === 'custom') preview('DISCOUNT', preset)
                    else savePreset(preset, editor.kind === 'coupon')
                  }}>
                    <label className="block">名称
                      <input className="ml-3 rounded-xl border p-3" required={editor.kind !== 'custom'} maxLength={120}
                        value={editor.draft.name} onChange={e => setEditor({ ...editor, draft: { ...editor.draft, name: e.target.value } })} />
                    </label>
                    <label className="mr-2 inline-flex items-center gap-2">类型
                      <select className="rounded-xl border p-3" value={editor.draft.discount_type}
                        onChange={e => setEditor({ ...editor, draft: { ...editor.draft, discount_type: e.target.value } })}>
                        <option value="PERCENT">百分比</option>
                        <option value="AMOUNT">固定金额</option>
                      </select>
                    </label>
                    <label className="inline-flex items-center gap-2">优惠值
                      <input className="w-28 rounded-xl border p-3" type="number" required min="0.01"
                        max={editor.draft.discount_type === 'PERCENT' ? 100 : 999999.99} step="0.01"
                        value={editor.value} onChange={e => setEditor({ ...editor, value: e.target.value })} />
                    </label>
                    <div className="flex gap-2">
                      <button className={primary} disabled={busy} type="submit">
                        {editor.kind === 'custom' ? '预览折扣' : editor.kind === 'coupon' ? '保存 Coupon' : '保存预设'}
                      </button>
                      <button className={button} disabled={busy} type="button"
                        onClick={() => setPanel(editor.kind === 'coupon' ? 'coupon' : 'discount')}>返回</button>
                    </div>
                  </form>
                )}
              </>
            ) : <p className="my-4">此订单仅供查看。</p>}
            {panel === 'menu' && !pending && <AlloOrderItems detail={detail} />}
            {!!detail.adjustments.length && (
              <>
                <h3 className="my-4 text-lg font-bold">调整记录</h3>
                <AdjustmentHistory rows={detail.adjustments} />
              </>
            )}
          </>
        )}
      </section>
    </div>
  )
}
