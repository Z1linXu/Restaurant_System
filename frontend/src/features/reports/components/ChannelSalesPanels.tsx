import { useState } from 'react'
import { CHANNEL_LABELS, channelMoney, type ChannelCategory, type ChannelItem, type ChannelMetric, type ChannelPoint, type ChannelSalesSummary, type ChannelSplit, type SalesChannel } from '../../../services/channelSales'
import type { ReportingGroup } from '../../../services/ownerDashboardService'

const GROUPS: Record<ReportingGroup, { label: string; color: string }> = {
  SOUP_NOODLE: { label: '汤面', color: '#8b2922' }, DRY_NOODLE: { label: '拌面', color: '#b86930' },
  FRIED_NOODLE: { label: '炒面', color: '#daa64b' }, DRINK: { label: '饮料', color: '#368f98' },
  ALCOHOL: { label: '酒类', color: '#7665a0' }, SIDE: { label: '小菜', color: '#568749' },
  FRIED: { label: '炸物', color: '#c77983' }, OTHER: { label: '其他', color: '#737872' },
}
const CHANNELS: SalesChannel[] = ['total', 'in_store', 'uber_eats']
const PANEL = 'min-w-0 rounded-[26px] bg-[rgba(255,255,255,0.82)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]'

function Amount({ metric }: { metric: ChannelMetric }) {
  return <><strong className="tabular-nums">{channelMoney(metric.revenue)}</strong>{metric.unknown_amount_count > 0 ? <span className="block text-xs text-amber-800">Known {channelMoney(metric.known_revenue)} · {metric.unknown_amount_count} pending</span> : null}</>
}

export function ChannelSalesKpis({ summary }: { summary: ChannelSalesSummary }) {
  return <div className="space-y-3">
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {CHANNELS.map((channel) => <div key={channel} className={PANEL}>
        <h2 className="text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">{`${CHANNEL_LABELS[channel]} Sales`}</h2>
        <div className="mt-3 text-2xl"><Amount metric={summary.totals[channel]} /></div>
        <p className="mt-2 text-sm text-[var(--muted)]">{summary.totals[channel].order_count} orders · AOV {channelMoney(summary.totals[channel].average_order_value)}</p>
      </div>)}
      <div className={PANEL}><h2 className="text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">Total Orders</h2>
        <strong className="mt-3 block text-2xl">{summary.totals.total.order_count}</strong>
        <p className="mt-2 text-sm text-[var(--muted)]">In-store {summary.totals.in_store.order_count} · Uber {summary.totals.uber_eats.order_count}</p>
      </div>
    </div>
    <p className="px-1 text-xs text-[var(--muted)]">Merchandise sales including tax · excludes platform fees and tips · not payout or profit. Pending amounts are not estimated.</p>
  </div>
}

export function ChannelNoodleSales({ rows, range }: { rows: ChannelCategory[]; range: string }) {
  return <section aria-label="Noodle sales" className={PANEL}>
    <h2 className="text-lg font-bold">{range === 'today' ? '今日面类销售' : '面类销售'}</h2>
    <p className="mt-1 text-sm text-[var(--muted)]">Noodle sales · {range}</p>
    <div className="mt-4 space-y-4">{rows.map((row) => <div key={row.reporting_group} className="rounded-2xl bg-[rgba(26,28,25,0.04)] p-3">
      <h3 className="font-bold">{GROUPS[row.reporting_group].label}</h3>
      <div className="mt-2 grid grid-cols-3 gap-2 text-sm">{CHANNELS.map((channel) => <div key={channel} className="min-w-0 break-words">
        <p className="text-xs text-[var(--muted)]">{CHANNEL_LABELS[channel]}</p><p className="my-1">{row[channel].quantity} sold</p><Amount metric={row[channel]} />
      </div>)}</div>
    </div>)}</div>
  </section>
}

export function ChannelRevenueMix({ summary, range }: { summary: ChannelSalesSummary; range: string }) {
  const [channel, setChannel] = useState<SalesChannel>('total')
  const rows = summary.categories
  const total = summary.totals[channel]
  const complete = total.revenue != null && rows.every((row) => row[channel].percentage != null)
  const positive = complete ? rows.filter((row) => (row[channel].revenue ?? 0) > 0) : []
  return <section aria-label="Revenue mix" className={PANEL}>
    <h2 className="text-lg font-bold">{range === 'today' ? '今日营业额构成' : '营业额构成'}</h2>
    <p className="mt-1 text-sm text-[var(--muted)]">{range === 'today' ? "Today's Revenue Mix" : 'Revenue Mix'}</p>
    <div className="mt-3 flex flex-wrap gap-2" aria-label="Revenue channel">{CHANNELS.map((value) => <button key={value} type="button" aria-pressed={channel === value} onClick={() => setChannel(value)} className={`rounded-full px-3 py-2 text-sm ${channel === value ? 'bg-[var(--primary)] text-white' : 'bg-[rgba(26,28,25,0.06)]'}`}>{CHANNEL_LABELS[value]}</button>)}</div>
    {!complete ? <p className="mt-3 text-sm text-amber-800">Category amounts pending from Uber; quantities remain included.</p> : null}
    <div className="mt-4 flex flex-wrap items-center justify-center gap-5">
      <div className="relative h-44 w-44 shrink-0"><svg viewBox="0 0 120 120" role="img" aria-label={`${CHANNEL_LABELS[channel]} revenue mix`} className="h-full w-full -rotate-90">
        <circle cx="60" cy="60" r="48" fill="none" stroke="#e8e8e3" strokeWidth="16" />
        {positive.map((row, index) => <circle key={row.reporting_group} cx="60" cy="60" r="48" pathLength="100" fill="none" stroke={GROUPS[row.reporting_group].color} strokeWidth="16" strokeDasharray={`${row[channel].percentage} ${100 - (row[channel].percentage ?? 0)}`} strokeDashoffset={-positive.slice(0, index).reduce((sum, preceding) => sum + (preceding[channel].percentage ?? 0), 0)} />)}
      </svg><div className="absolute inset-0 flex flex-col items-center justify-center text-center"><span className="text-xs text-[var(--muted)]">{`${CHANNEL_LABELS[channel]} Sales`}</span><strong className="mt-1 text-lg">{channelMoney(total.revenue)}</strong></div></div>
      <ul className="min-w-[210px] flex-1 space-y-2 text-sm">{rows.filter((row) => row.reporting_group !== 'OTHER' || row[channel].quantity > 0 || row[channel].revenue !== 0).map((row) => <li key={row.reporting_group} className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-2"><span aria-hidden="true" className="h-2.5 w-2.5 rounded-full" style={{ background: GROUPS[row.reporting_group].color }} />{GROUPS[row.reporting_group].label}</span>
        <span className="text-right tabular-nums">{channelMoney(row[channel].revenue)} · {row[channel].percentage == null ? '—' : `${row[channel].percentage.toFixed(2)}%`}</span>
      </li>)}</ul>
    </div>
  </section>
}

export function ChannelTrend({ points }: { points: ChannelPoint[] }) {
  const max = Math.max(...points.map((row) => row.total.known_revenue), 1)
  return <div className="space-y-3">
    <p className="text-sm text-[var(--muted)]"><span className="text-[#8b2922]">● In-store</span> · <span className="text-[#368f98]">● Uber Eats</span> · labels show Total</p>
    <div className="overflow-x-auto pb-2"><div className="grid gap-2" style={{ minWidth: Math.max(650, points.length * 54), gridTemplateColumns: `repeat(${Math.max(points.length, 1)}, minmax(0, 1fr))` }}>
      {points.map((point) => <div key={point.label} className="min-w-0 text-center text-xs">
        <div className="mb-2 font-bold">{channelMoney(point.total.revenue)}</div>
        <div className="flex h-44 flex-col justify-end overflow-hidden rounded-xl bg-[rgba(26,28,25,0.04)]" title={`${point.label}: In-store ${channelMoney(point.in_store.revenue)}; Uber ${channelMoney(point.uber_eats.revenue)}; Total ${channelMoney(point.total.revenue)}`}>
          <div className="bg-[#368f98]" style={{ height: `${point.uber_eats.known_revenue / max * 100}%` }} />
          <div className="bg-[#8b2922]" style={{ height: `${point.in_store.known_revenue / max * 100}%` }} />
        </div><div className="mt-2 font-semibold">{point.label}</div>
      </div>)}
    </div></div><p className="text-xs text-[var(--muted)]">POS first submission time · Uber placed time. Bars show known amounts; pending is not zero.</p>
  </div>
}

export function ChannelSalesTable({ rows, label, count = 'quantity', showAov = false }: { rows: (ChannelItem | (ChannelSplit & { item_key: string; item_name: string }))[]; label: string; count?: 'quantity' | 'orders'; showAov?: boolean }) {
  return <div className="overflow-x-auto"><table aria-label={label} className="min-w-full text-left text-sm"><thead><tr><th className="p-3">{label}</th>{CHANNELS.map((channel) => <th key={channel} className="p-3">{CHANNEL_LABELS[channel]}<br />{count === 'orders' ? 'Orders' : 'Qty'} · Sales</th>)}</tr></thead><tbody>
    {rows.map((row) => <tr key={row.item_key} className="border-t border-[rgba(26,28,25,0.08)]"><td className="p-3">{row.item_name}</td>{CHANNELS.map((channel) => <td key={channel} className="p-3"><p>{count === 'orders' ? row[channel].order_count : row[channel].quantity}</p><Amount metric={row[channel]} />{showAov ? <p className="text-xs text-[var(--muted)]">AOV {channelMoney(row[channel].average_order_value)}</p> : null}</td>)}</tr>)}
  </tbody></table></div>
}
