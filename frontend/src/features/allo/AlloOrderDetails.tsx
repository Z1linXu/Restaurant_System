import { useLocale } from '../../i18n/useLocale'
import { withMenuCode } from '../../utils/menuCode'
import { actionLabel, money, type Amounts, type FinancialDetail, type Ledger } from './alloSalesService'

const amountLabels: Record<keyof Amounts, string> = {
  gross: '折扣前',
  discount: '折扣',
  net: '税前净额',
  tax: '税',
  total: '含税金额',
  collected: '账面已收',
}

function parseSnapshot(value: string): Partial<Amounts> {
  try {
    return JSON.parse(value) as Amounts
  } catch {
    return {}
  }
}

function changesText(value: string) {
  try {
    const changes = JSON.parse(value) as { name: string; quantity: number }[]
    return changes.map(i => `${i.name} ×${i.quantity}`).join('、')
  } catch {
    return ''
  }
}

export function AdjustmentHistory({ rows }: { rows: Ledger[] }) {
  return (
    <div className="space-y-3">
      {rows.map(row => {
        const before = parseSnapshot(row.before_snapshot)
        const after = parseSnapshot(row.after_snapshot)
        return (
          <div className="rounded-xl bg-stone-50 p-3 text-sm" key={row.id}>
            <p>{[row.display_time, row.actor_name, row.table_no ?? row.pickup_no ?? row.order_type].filter(Boolean).join(' · ')}</p>
            <p className="font-bold">{actionLabel(row.action)} · 账面已收调整 {money(row.collected_delta)}</p>
            <p>{changesText(row.item_changes)} {row.reason}</p>
            <details>
              <summary className="min-h-10 cursor-pointer py-2">前后金额</summary>
              <dl className="grid grid-cols-2 gap-2">
                {(Object.keys(amountLabels) as (keyof Amounts)[]).map(key => (
                  <div key={key}><dt>{amountLabels[key]}</dt><dd>{money(before[key])} → {money(after[key])}</dd></div>
                ))}
              </dl>
            </details>
          </div>
        )
      })}
    </div>
  )
}

export function OrderAmounts({ amounts }: { amounts: Amounts }) {
  return (
    <dl className="my-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
      {(Object.keys(amountLabels) as (keyof Amounts)[]).map(key => (
        <div key={key} className="rounded-xl bg-stone-50 p-3">
          <dt className="text-sm text-stone-600">{amountLabels[key]}</dt>
          <dd className="text-lg font-bold tabular-nums">{money(amounts[key])}</dd>
        </div>
      ))}
    </dl>
  )
}

function DeletedLabel({ removed, original }: { removed: number; original: number }) {
  if (removed <= 0) return null
  return (
    <span className="ml-2 inline-block rounded-full bg-red-100 px-2 py-0.5 text-xs font-bold text-red-900">
      已删除{removed < original ? ` ×${removed}` : ''}
    </span>
  )
}

export function AlloOrderItems({ detail }: { detail: FinancialDetail }) {
  const { label, t } = useLocale()
  return (
    <div className="space-y-3">
      {detail.items.map(item => (
        <div key={item.id} className="rounded-[18px] bg-stone-50 px-4 py-3">
          <div className="flex items-start justify-between gap-3">
            <p className="text-lg font-bold">
              {withMenuCode(item.item_sku_snapshot, label({
                zh: item.item_name_snapshot_zh, en: item.item_name_snapshot_en, fr: item.item_name_snapshot_fr,
              }))}
              <DeletedLabel removed={item.voided_quantity} original={item.quantity} />
            </p>
            <p className="shrink-0 text-right font-semibold">
              ×{item.quantity}<span className="ml-3 font-normal">{money(item.effective_line_amount)}</span>
            </p>
          </div>
          {!!item.options.length && (
            <ul className="mt-1 space-y-1 pl-4 text-sm text-stone-700">
              {item.options.map(option => (
                <li key={option.id}>
                  {label({ zh: option.option_name_snapshot_zh, en: option.option_name_snapshot_en, fr: option.option_name_snapshot_fr })}
                  {' ×'}{option.quantity}
                  <DeletedLabel removed={option.voided_quantity} original={option.quantity} />
                </li>
              ))}
            </ul>
          )}
          {item.notes && <p className="mt-1 text-sm">{t('order.note')}：{item.notes}</p>}
        </div>
      ))}
      {detail.order.order_notes && <p className="whitespace-pre-wrap rounded-xl bg-stone-50 p-3 text-sm">整单备注：{detail.order.order_notes}</p>}
      {detail.order.discount_name && (
        <p className="text-sm font-semibold">
          {detail.order.discount_coupon_id ? 'Coupon' : '折扣'} · {detail.order.discount_name}
        </p>
      )}
    </div>
  )
}
