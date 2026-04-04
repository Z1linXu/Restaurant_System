import type { InputHTMLAttributes } from 'react'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
}

export function Input({ label, className = '', id, ...props }: InputProps) {
  const inputId = id ?? label.toLowerCase().replace(/\s+/g, '-')

  return (
    <label htmlFor={inputId} className="flex flex-col gap-2">
      <span className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">
        {label}
      </span>
      <input
        id={inputId}
        className={`min-h-14 rounded-2xl bg-[var(--surface-container-lowest)] px-4 text-sm text-[var(--on-surface)] shadow-[inset_0_0_0_1px_rgba(26,28,25,0.06)] outline-none transition placeholder:text-[var(--muted)] focus:shadow-[inset_0_0_0_2px_rgba(97,0,0,0.22)] ${className}`}
        {...props}
      />
    </label>
  )
}
