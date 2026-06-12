export function ReportEmptyState({ message }: { message: string }) {
  return (
    <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-6 text-[0.9rem] text-[var(--muted)]">
      {message}
    </div>
  )
}
