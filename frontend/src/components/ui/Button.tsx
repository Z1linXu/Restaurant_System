import type { ButtonHTMLAttributes, ReactNode } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'tertiary'
type ButtonSize = 'md' | 'lg'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  icon?: ReactNode
  fullWidth?: boolean
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-linear-to-b from-[var(--primary)] to-[var(--primary-container)] text-[var(--on-primary)] shadow-[0_14px_36px_rgba(97,0,0,0.18)] hover:brightness-[1.03]',
  secondary:
    'bg-[var(--surface-container-lowest)] text-[var(--on-surface)] shadow-[0_12px_30px_rgba(26,28,25,0.06)] ring-1 ring-[rgba(26,28,25,0.06)] hover:bg-[var(--surface)]',
  tertiary: 'bg-transparent text-[var(--primary)] hover:bg-[rgba(97,0,0,0.05)]',
}

const sizeClasses: Record<ButtonSize, string> = {
  md: 'min-h-12 px-4 text-sm',
  lg: 'min-h-16 px-5 text-base',
}

export function Button({
  children,
  className = '',
  variant = 'primary',
  size = 'md',
  icon,
  fullWidth = false,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`inline-flex items-center justify-center gap-2 rounded-2xl font-semibold tracking-[0.01em] transition duration-200 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[rgba(97,0,0,0.2)] disabled:cursor-not-allowed disabled:opacity-50 ${variantClasses[variant]} ${sizeClasses[size]} ${fullWidth ? 'w-full' : ''} ${className}`}
      {...props}
    >
      {icon}
      <span>{children}</span>
    </button>
  )
}
