import type { ReactNode } from 'react'
import { navigateTo } from '../../frontdesk/navigation'
import { isFeatureEnabled } from '../../feature-flags/featureConfig'
import { ownerAdminSidebarItems, reportsNavItems, type ReportsSection } from './reportsNavigation'

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
  return (
    <div className="min-h-screen bg-[linear-gradient(180deg,#f6f3ec_0%,#efe9dd_100%)] text-[var(--on-surface)]">
      <div className="grid min-h-screen xl:grid-cols-[260px_minmax(0,1fr)]">
        <aside className="border-r border-[rgba(97,0,0,0.08)] bg-[rgba(255,255,255,0.8)] px-4 py-5 backdrop-blur-sm">
          <div className="rounded-[24px] bg-[rgba(97,0,0,0.04)] px-4 py-4">
            <div className="text-[1.6rem] font-black tracking-[-0.05em] text-[var(--primary)]">Owner Console</div>
            <div className="mt-1 text-[0.84rem] leading-5 text-[var(--muted)]">
              Restaurant management workspace
            </div>
          </div>

          <nav className="mt-5 space-y-2">
            {ownerAdminSidebarItems.filter((item) => item.feature == null || isFeatureEnabled(item.feature)).map((item) => {
              const active = item.id === 'reports'
              const disabled = item.path == null
              return (
                <button
                  key={item.id}
                  type="button"
                  disabled={disabled}
                  onClick={() => {
                    if (item.path) {
                      navigateTo(item.path)
                    }
                  }}
                  className={`w-full rounded-[20px] px-4 py-3 text-left transition ${
                    active
                      ? 'bg-[var(--primary)] text-white shadow-[0_18px_34px_rgba(97,0,0,0.18)]'
                      : disabled
                        ? 'cursor-default bg-transparent text-[rgba(26,28,25,0.45)]'
                        : 'bg-transparent text-[rgba(26,28,25,0.78)] hover:bg-[rgba(97,0,0,0.06)]'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <span className="text-[1.2rem] leading-none">{item.icon}</span>
                    <div>
                      <div className="text-[0.98rem] font-semibold">{item.label}</div>
                      <div className={`mt-0.5 text-[0.76rem] ${active ? 'text-[rgba(255,255,255,0.82)]' : 'text-[var(--muted)]'}`}>
                        {item.description}
                      </div>
                    </div>
                  </div>
                </button>
              )
            })}
          </nav>
        </aside>

        <main className="px-5 py-5 xl:px-6">
          <div className="mx-auto max-w-[1650px] space-y-5">
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
                      onClick={() => navigateTo(item.path)}
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
        </main>
      </div>
    </div>
  )
}
