import type { TableStatus } from '../../../types/dinein'

interface TableStatusLegendProps {
  counts: Record<TableStatus, number>
  compact?: boolean
}

const items: Array<{ status: TableStatus; label: string; dotClass: string }> = [
  { status: 'available', label: 'AVAILABLE', dotClass: 'bg-[#6a8a6d]' },
  { status: 'occupied', label: 'IN USE', dotClass: 'bg-[#8d8176]' },
  { status: 'alert', label: 'ALERT', dotClass: 'bg-[#c85d22]' },
]

export function TableStatusLegend({ counts, compact = false }: TableStatusLegendProps) {
  return (
    <div className={`flex ${compact ? 'items-center justify-between gap-4' : 'flex-col gap-4 xl:flex-row xl:items-center xl:justify-between'}`}>
      <div className={`flex flex-wrap items-center ${compact ? 'gap-5' : 'gap-10'}`}>
        {items.map((item) => (
          <div key={item.status} className={`flex items-center ${compact ? 'gap-2' : 'gap-3'}`}>
            <span className={`${compact ? 'h-3.5 w-3.5' : 'h-4 w-4'} rounded-full ${item.dotClass}`} />
            <span className={`${compact ? 'text-[0.92rem]' : 'text-[1.05rem]'} font-bold tracking-[0.02em] text-[rgba(53,43,38,0.86)]`}>
              {item.label} ({counts[item.status] ?? 0})
            </span>
          </div>
        ))}
      </div>

      <button
        type="button"
        className={`inline-flex items-center gap-3 rounded-[18px] bg-[rgba(26,28,25,0.05)] font-semibold text-[var(--on-surface)] ${compact ? 'min-h-10 px-4 text-[0.95rem]' : 'min-h-14 px-5 text-[1.1rem]'}`}
      >
        <span>☰</span>
        {!compact ? <span>All Areas</span> : <span>Areas</span>}
      </button>
    </div>
  )
}
