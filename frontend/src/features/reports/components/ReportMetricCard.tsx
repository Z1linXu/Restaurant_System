function formatPercent(value: number) {
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(1)}%`
}

function getTone(value: number) {
  if (value > 0) {
    return 'text-emerald-700 bg-[rgba(18,141,77,0.1)]'
  }
  if (value < 0) {
    return 'text-[var(--primary)] bg-[rgba(97,0,0,0.08)]'
  }
  return 'text-[var(--muted)] bg-[rgba(26,28,25,0.06)]'
}

export function ReportMetricCard({
  label,
  value,
  compareValue,
}: {
  label: string
  value: string
  compareValue: number
}) {
  return (
    <div className="rounded-[24px] bg-[rgba(255,255,255,0.82)] px-5 py-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="text-[0.78rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">{label}</div>
      <div className="mt-3 text-[1.9rem] font-black tracking-[-0.05em] text-[var(--on-surface)]">{value}</div>
      <div className={`mt-3 inline-flex rounded-full px-2.5 py-1 text-[0.78rem] font-semibold ${getTone(compareValue)}`}>
        {formatPercent(compareValue)} vs previous period
      </div>
    </div>
  )
}
