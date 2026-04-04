import type { HTMLAttributes } from 'react'

type BadgeVariant = 'neutral' | 'accent' | 'warm' | 'success'

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant
}

const variantClasses: Record<BadgeVariant, string> = {
  neutral: 'bg-[rgba(26,28,25,0.06)] text-[var(--on-surface)]',
  accent: 'bg-[rgba(97,0,0,0.08)] text-[var(--primary)]',
  warm: 'bg-[rgba(144,77,0,0.12)] text-[var(--secondary)]',
  success: 'bg-[rgba(59,111,78,0.12)] text-[var(--success)]',
}

export function Badge({ children, className = '', variant = 'neutral', ...props }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold tracking-[0.06em] uppercase ${variantClasses[variant]} ${className}`}
      {...props}
    >
      {children}
    </span>
  )
}
