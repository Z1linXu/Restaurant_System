import type { AnalyticsReportRange } from '../../../services/analyticsReportService'

export interface ReportsStoreOption {
  id: string
  label: string
}

interface ReportsTopBarProps {
  stores: ReportsStoreOption[]
  selectedStoreId: string
  selectedRange: AnalyticsReportRange
  compareEnabled: boolean
  customStartDate: string
  customEndDate: string
  onStoreChange: (storeId: string) => void
  onRangeChange: (range: AnalyticsReportRange) => void
  onCompareToggle: (enabled: boolean) => void
  onCustomStartDateChange: (value: string) => void
  onCustomEndDateChange: (value: string) => void
}

const RANGE_OPTIONS: { value: AnalyticsReportRange; label: string }[] = [
  { value: 'today', label: 'Today' },
  { value: 'week', label: 'This Week' },
  { value: 'month', label: 'This Month' },
  { value: 'custom', label: 'Custom' },
]

export function ReportsTopBar({
  stores,
  selectedStoreId,
  selectedRange,
  compareEnabled,
  customStartDate,
  customEndDate,
  onStoreChange,
  onRangeChange,
  onCompareToggle,
  onCustomStartDateChange,
  onCustomEndDateChange,
}: ReportsTopBarProps) {
  return (
    <div className="flex flex-wrap items-center justify-end gap-3">
      <label className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-3 py-2.5">
        <div className="text-[0.72rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">Store</div>
        <select
          value={selectedStoreId}
          onChange={(event) => onStoreChange(event.target.value)}
          className="mt-1 min-w-[180px] bg-transparent text-[0.95rem] font-semibold text-[var(--on-surface)] outline-none"
        >
          {stores.map((store) => (
            <option key={store.id} value={store.id}>
              {store.label}
            </option>
          ))}
        </select>
      </label>

      <div className="flex flex-wrap gap-2 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-3 py-2.5">
        {RANGE_OPTIONS.map((option) => {
          const active = option.value === selectedRange
          return (
            <button
              key={option.value}
              type="button"
              onClick={() => onRangeChange(option.value)}
              className={`rounded-full px-3 py-1.5 text-[0.82rem] font-semibold transition ${
                active
                  ? 'bg-[var(--primary)] text-white'
                  : 'bg-[rgba(255,255,255,0.7)] text-[var(--on-surface)] hover:bg-[rgba(97,0,0,0.08)] hover:text-[var(--primary)]'
              }`}
            >
              {option.label}
            </button>
          )
        })}
      </div>

      {selectedRange === 'custom' ? (
        <div className="flex flex-wrap gap-2 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-3 py-2.5">
          <label className="text-[0.74rem] font-semibold uppercase tracking-[0.16em] text-[var(--muted)]">
            Start
            <input
              type="date"
              value={customStartDate}
              onChange={(event) => onCustomStartDateChange(event.target.value)}
              className="mt-1 block rounded-[12px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] font-medium text-[var(--on-surface)] outline-none"
            />
          </label>
          <label className="text-[0.74rem] font-semibold uppercase tracking-[0.16em] text-[var(--muted)]">
            End
            <input
              type="date"
              value={customEndDate}
              onChange={(event) => onCustomEndDateChange(event.target.value)}
              className="mt-1 block rounded-[12px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.88rem] font-medium text-[var(--on-surface)] outline-none"
            />
          </label>
        </div>
      ) : null}

      <button
        type="button"
        onClick={() => onCompareToggle(!compareEnabled)}
        className={`rounded-[18px] px-4 py-3 text-[0.84rem] font-semibold transition ${
          compareEnabled
            ? 'bg-[rgba(97,0,0,0.92)] text-white shadow-[0_12px_24px_rgba(97,0,0,0.16)]'
            : 'bg-[rgba(26,28,25,0.04)] text-[var(--on-surface)] hover:bg-[rgba(97,0,0,0.08)] hover:text-[var(--primary)]'
        }`}
      >
        Compare previous period
      </button>
    </div>
  )
}
