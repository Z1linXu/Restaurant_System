import { useEffect } from 'react'
import { navigateTo } from '../frontdesk/navigation'
import { AccessDeniedPage } from './AccessDeniedPage'
import { useAuth } from './useAuth'

export type AppRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'FRONTDESK' | 'HOT_KITCHEN' | 'NOODLE_VIEW' | 'PASS'

export function defaultPathForRole(roleCode?: string | null) {
  const role = roleCode?.toUpperCase()
  if (role === 'FRONTDESK') {
    return '/frontdesk'
  }
  return '/admin/dashboard'
}

export function RequireAuth({
  children,
  allowedRoles,
}: {
  children: React.ReactNode
  allowedRoles: AppRole[]
}) {
  const { user, loading } = useAuth()

  useEffect(() => {
    if (!loading && !user) {
      navigateTo('/login')
    }
  }, [loading, user])

  if (loading) {
    return (
      <div className="min-h-screen bg-[var(--surface)] px-6 py-8 text-[var(--on-surface)]">
        <div className="mx-auto max-w-[760px] rounded-[30px] bg-white px-7 py-8 shadow-[0_22px_54px_rgba(26,28,25,0.1)]">
          <div className="text-[1rem] font-bold text-[var(--muted)]">Checking session...</div>
        </div>
      </div>
    )
  }

  if (!user) {
    return null
  }

  const role = user.role_code?.toUpperCase() as AppRole
  if (!allowedRoles.includes(role)) {
    return <AccessDeniedPage />
  }

  return <>{children}</>
}
