export interface ReportTrendPoint {
  label: string
  value: number
  secondaryValue?: number
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency: 'CAD',
    minimumFractionDigits: 2,
  }).format(value)
}

function formatCompactCurrency(value: number) {
  if (value >= 1000) {
    const compactValue = value >= 10000 ? Math.round(value / 1000) : Math.round((value / 1000) * 10) / 10
    return `$${compactValue}k`
  }

  return `$${Math.round(value)}`
}

export function ReportTrendChart({
  points,
  caption,
  compactXAxis = false,
  dailyXAxisStep = 3,
}: {
  points: ReportTrendPoint[]
  caption: string
  compactXAxis?: boolean
  dailyXAxisStep?: number
}) {
  const maxValue = Math.max(...points.map((point) => point.value), 1)
  const sampledHourlyTickIndexes = new Set([0, 6, 12, 18, points.length - 1])

  return (
    <div className="space-y-3">
      {points.length ? (
        <div className="grid gap-2.5" style={{ gridTemplateColumns: `repeat(${Math.max(points.length, 1)}, minmax(0, 1fr))` }}>
          {points.map((point, index) => {
            const showTickLabel = compactXAxis
              ? sampledHourlyTickIndexes.has(index)
              : index % dailyXAxisStep === 0 || index === points.length - 1
            const showValueLabel = point.value > 0

            return (
              <div key={point.label} className="flex min-w-0 flex-col items-center gap-1.5">
                <div className="flex h-[18px] items-end justify-center text-center text-[0.62rem] font-bold leading-none text-[var(--on-surface)]">
                  {showValueLabel ? formatCompactCurrency(point.value) : ''}
                </div>
                <div
                  className="flex h-[180px] w-full items-end rounded-[16px] bg-[rgba(26,28,25,0.04)] px-1.5 pb-2"
                  title={`${point.label} · ${formatCurrency(point.value)}${point.secondaryValue != null ? ` · ${point.secondaryValue} orders` : ''}`}
                >
                  <div
                    className="w-full rounded-[12px] bg-[linear-gradient(180deg,rgba(138,40,20,0.78)_0%,rgba(97,0,0,0.92)_100%)]"
                    style={{ height: `${Math.max((point.value / maxValue) * 100, point.value > 0 ? 8 : 0)}%` }}
                  />
                </div>
                <div className="min-h-[18px] text-center text-[0.72rem] font-semibold text-[var(--muted)]">
                  {showTickLabel ? point.label : ''}
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-6 text-[0.9rem] text-[var(--muted)]">
          No summary data available for this period.
        </div>
      )}
      <div className="text-[0.8rem] text-[var(--muted)]">{caption}</div>
    </div>
  )
}
