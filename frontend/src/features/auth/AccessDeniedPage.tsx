import { navigateTo } from '../frontdesk/navigation'
import { buildStorePath } from '../store/storeRoutes'
import { useOptionalCurrentStore } from '../store/StoreContext'
import { useAuth } from './useAuth'

export function AccessDeniedPage({ message = 'You do not have access to this page.' }: { message?: string }) {
  const { user } = useAuth()
  const currentStore = useOptionalCurrentStore()
  return (
    <div className="min-h-screen bg-[var(--surface)] px-6 py-8 text-[var(--on-surface)]">
      <div className="mx-auto max-w-[760px] rounded-[30px] bg-white px-7 py-8 shadow-[0_22px_54px_rgba(26,28,25,0.1)]">
        <div className="text-[2rem] font-black tracking-[-0.05em]">Access Denied</div>
        <div className="mt-3 text-[1rem] font-medium text-[var(--muted)]">{message}</div>
        {user ? (
          <div className="mt-4 rounded-[18px] bg-[rgba(26,28,25,0.05)] px-4 py-3 text-[0.92rem] font-semibold">
            Signed in as {user.full_name || user.username} ({user.role_code})
          </div>
        ) : null}
        <button
          type="button"
          onClick={() => navigateTo(currentStore ? buildStorePath(currentStore.storeId, '/frontdesk') : '/frontdesk')}
          className="mt-6 h-12 rounded-[16px] bg-[var(--primary)] px-5 font-black text-white"
        >
          Back to Frontdesk
        </button>
      </div>
    </div>
  )
}
