import type { OwnerDashboardCategorySales, OwnerDashboardRange, ReportingGroup } from '../../services/ownerDashboardService'

const GROUPS: Record<ReportingGroup, { label: string; english: string; color: string }> = {
  SOUP_NOODLE: { label: '汤面', english: 'Soup noodles', color: '#8b2922' },
  DRY_NOODLE: { label: '拌面', english: 'Dry noodles', color: '#b86930' },
  FRIED_NOODLE: { label: '炒面', english: 'Chow mein', color: '#daa64b' },
  DRINK: { label: '饮料', english: 'Drinks', color: '#368f98' },
  ALCOHOL: { label: '酒类', english: 'Alcohol', color: '#7665a0' },
  SIDE: { label: '小菜', english: 'Sides', color: '#568749' },
  FRIED: { label: '炸物', english: 'Fried items', color: '#c77983' },
  OTHER: { label: '其他', english: 'Other', color: '#737872' },
}

function money(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}

export function NoodleSalesPanel({ rows, range }: { rows: OwnerDashboardCategorySales[]; range: OwnerDashboardRange }) {
  return (
    <section aria-label="Noodle sales" className="min-w-0 rounded-[26px] bg-[rgba(255,255,255,0.82)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <h2 className="text-[1.1rem] font-bold">{range === 'today' ? '今日面类销售' : '面类销售'}</h2>
      <p className="mt-1 text-sm text-[var(--muted)]">Noodle sales · {range}</p>
      <div className="mt-4 space-y-3">
        {rows.map((row) => (
          <div key={row.reporting_group} className="flex flex-wrap items-center justify-between gap-3 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-3">
            <div><div className="font-bold">{GROUPS[row.reporting_group].label}</div><div className="text-xs text-[var(--muted)]">{GROUPS[row.reporting_group].english}</div></div>
            <div className="text-right"><div className="font-bold">{money(row.revenue)}</div><div className="text-sm text-[var(--muted)]">{row.quantity_sold} sold</div></div>
          </div>
        ))}
      </div>
    </section>
  )
}

export function RevenueMixPanel({ rows, sales, range }: { rows: OwnerDashboardCategorySales[]; sales: number; range: OwnerDashboardRange }) {
  const positiveRows = rows.filter((row) => row.revenue > 0)
  const segments = positiveRows.map((row, index) => ({
    ...row,
    offset: positiveRows.slice(0, index).reduce((total, preceding) => total + preceding.percentage, 0),
  }))
  return (
    <section aria-label="Revenue mix" className="min-w-0 rounded-[26px] bg-[rgba(255,255,255,0.82)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <h2 className="text-[1.1rem] font-bold">{range === 'today' ? '今日营业额构成' : '营业额构成'}</h2>
      <p className="mt-1 text-sm text-[var(--muted)]">{range === 'today' ? "Today's Revenue Mix" : 'Revenue Mix'} · In-store sales</p>
      <div className="mt-4 flex flex-wrap items-center justify-center gap-5">
        <div className="relative h-44 w-44 shrink-0">
          <svg viewBox="0 0 120 120" role="img" aria-label={`Revenue mix: ${money(sales)}`} className="h-full w-full -rotate-90">
            <circle cx="60" cy="60" r="48" fill="none" stroke="#e8e8e3" strokeWidth="16" />
            {segments.map((row) => (
              <circle key={row.reporting_group} cx="60" cy="60" r="48" pathLength="100" fill="none" stroke={GROUPS[row.reporting_group].color} strokeWidth="16" strokeDasharray={`${row.percentage} ${100 - row.percentage}`} strokeDashoffset={-row.offset} />
            ))}
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
            <span className="text-xs text-[var(--muted)]">{range === 'today' ? 'Today Sales' : 'Sales'}</span>
            <strong className="mt-1 text-lg">{money(sales)}</strong>
          </div>
        </div>
        <ul className="min-w-[210px] flex-1 space-y-2 text-sm">
          {rows.filter((row) => row.reporting_group !== 'OTHER' || row.quantity_sold > 0 || row.revenue !== 0).map((row) => (
            <li key={row.reporting_group} className="flex items-center justify-between gap-2">
              <span className="flex items-center gap-2"><span aria-hidden="true" className="h-2.5 w-2.5 rounded-full" style={{ background: GROUPS[row.reporting_group].color }} />{GROUPS[row.reporting_group].label}</span>
              <span className="text-right tabular-nums">{money(row.revenue)} · {row.percentage.toFixed(2)}%</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}
