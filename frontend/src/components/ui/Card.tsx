import type { HTMLAttributes, ReactNode } from 'react'

type CardTone = 'base' | 'well' | 'feature'

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  tone?: CardTone
  children: ReactNode
}

const toneClasses: Record<CardTone, string> = {
  base: 'bg-[var(--surface)]',
  well: 'bg-[var(--surface-container-low)]',
  feature: 'bg-[var(--surface-container-lowest)]',
}

export function Card({ children, className = '', tone = 'feature', ...props }: CardProps) {
  return (
    <div
      className={`rounded-3xl p-4 shadow-[0_12px_32px_rgba(26,28,25,0.06)] ring-1 ring-[rgba(26,28,25,0.04)] ${toneClasses[tone]} ${className}`}
      {...props}
    >
      {children}
    </div>
  )
}
