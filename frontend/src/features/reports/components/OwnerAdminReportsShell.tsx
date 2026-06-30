import type { ReactNode } from 'react'
import { navigateTo } from '../../frontdesk/navigation'
import { reportsNavItems, type ReportsSection } from './reportsNavigation'
import { useOptionalCurrentStore } from '../../store/StoreContext'
import { buildStorePath } from '../../store/storeRoutes'

interface OwnerAdminReportsShellProps {
  activeReport: ReportsSection
  title: string
  description: string
  topBar: ReactNode
  children: ReactNode
}

export function OwnerAdminReportsShell({
  activeReport,
  title,
  description,
  topBar,
  children,
}: OwnerAdminReportsShellProps) {
  const currentStore = useOptionalCurrentStore()
  return (
    <div className="space-y-5">
      <div className="rounded-[28px] bg-[rgba(255,255,255,0.84)] px-5 py-4 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="text-[1.7rem] font-black tracking-[-0.05em] text-[var(--on-surface)]">{title}</div>
            <div className="mt-1 text-[0.9rem] text-[var(--muted)]">{description}</div>
          </div>
          {topBar}
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          {reportsNavItems.map((item) => {
            const active = item.id === activeReport
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => navigateTo(currentStore ? buildStorePath(currentStore.storeId, item.path) : item.path)}
                className={`rounded-full px-4 py-2 text-[0.9rem] font-semibold transition ${
                  active
                    ? 'bg-[var(--primary)] text-white shadow-[0_14px_24px_rgba(97,0,0,0.16)]'
                    : 'bg-[rgba(26,28,25,0.05)] text-[var(--on-surface)] hover:bg-[rgba(97,0,0,0.08)] hover:text-[var(--primary)]'
                }`}
              >
                {item.label}
              </button>
            )
          })}
        </div>
      </div>
      {children}
    </div>
  )
}
