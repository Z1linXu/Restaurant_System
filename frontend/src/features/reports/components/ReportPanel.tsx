import type { ReactNode } from 'react'

export function ReportPanel({
  title,
  description,
  children,
}: {
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <section className="rounded-[26px] bg-[rgba(255,255,255,0.82)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">{title}</div>
      {description ? <div className="mt-1 text-[0.85rem] text-[var(--muted)]">{description}</div> : null}
      <div className="mt-4">{children}</div>
    </section>
  )
}
