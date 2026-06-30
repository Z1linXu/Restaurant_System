import { useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '../features/auth/useAuth'
import { defaultWorkspacePathForRole } from '../features/store/storeRoutes'
import { fetchWorkspaces } from '../services/storeWorkspaceService'
import { ApiRequestError, getApiUserMessage } from '../services/apiClient'

function navigateTo(path: string) {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export default function Login() {
  const { signIn } = useAuth()
  const [loginIdentifier, setLoginIdentifier] = useState('owner')
  const [password, setPassword] = useState('741xu741')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      const response = await signIn(loginIdentifier, password).catch((exception) => {
        if (exception instanceof ApiRequestError && exception.status === 401) {
          throw new Error('账号或密码错误 / Login ID or password is incorrect.')
        }
        throw exception
      })
      const workspaces = await fetchWorkspaces().catch((exception) => {
        console.error('[Login] workspace loading failed after successful login', exception)
        throw new Error('登录成功，但门店权限加载失败，请联系管理员检查门店权限 / Login succeeded, but workspace loading failed. Please contact manager or check store access.')
      })
      const targetPath = defaultWorkspacePathForRole(response.user.role_code, workspaces)
      if (!targetPath) {
        setError('此账号还没有分配门店权限 / No store access assigned to this account.')
        return
      }
      navigateTo(targetPath)
    } catch (exception) {
      setError(getApiUserMessage(exception, '登录失败，请稍后重试 / Login failed. Please try again.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-[var(--surface)] px-6 py-8 text-[var(--on-surface)]">
      <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-[980px] items-center justify-center">
        <div className="grid w-full overflow-hidden rounded-[32px] bg-white shadow-[0_24px_70px_rgba(26,28,25,0.12)] md:grid-cols-[1fr_1.05fr]">
          <div className="bg-[var(--primary)] px-8 py-10 text-white">
            <div className="text-[2.4rem] font-black leading-none tracking-[-0.07em]">Restaurant POS</div>
            <div className="mt-5 text-[1.05rem] font-semibold opacity-90">
              Owner, frontdesk, and kitchen login foundation for the platform-ready system.
            </div>
            <div className="mt-8 rounded-[22px] bg-white/12 px-5 py-4 text-[0.95rem] font-medium leading-7">
              Dev accounts are enabled for local testing only. Production should replace these passwords before launch.
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5 px-8 py-10">
            <div>
              <div className="text-[1.8rem] font-black tracking-[-0.05em]">Sign in</div>
              <div className="mt-1 text-[0.95rem] font-medium text-[var(--muted)]">Use your restaurant staff account.</div>
            </div>

            <label className="block">
              <span className="text-[0.85rem] font-bold uppercase tracking-[0.12em] text-[var(--muted)]">Login ID</span>
              <input
                value={loginIdentifier}
                onChange={(event) => setLoginIdentifier(event.target.value)}
                className="mt-2 h-14 w-full rounded-[18px] border border-[rgba(26,28,25,0.12)] px-4 text-[1.05rem] font-semibold outline-none focus:border-[var(--primary)]"
                autoComplete="username"
              />
            </label>

            <label className="block">
              <span className="text-[0.85rem] font-bold uppercase tracking-[0.12em] text-[var(--muted)]">Password</span>
              <input
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type="password"
                className="mt-2 h-14 w-full rounded-[18px] border border-[rgba(26,28,25,0.12)] px-4 text-[1.05rem] font-semibold outline-none focus:border-[var(--primary)]"
                autoComplete="current-password"
              />
            </label>

            {error ? (
              <div className="rounded-[18px] bg-red-50 px-4 py-3 text-[0.95rem] font-semibold text-red-700">{error}</div>
            ) : null}

            <button
              type="submit"
              disabled={isSubmitting}
              className="h-14 w-full rounded-[18px] bg-[var(--primary)] text-[1.05rem] font-black text-white shadow-[0_12px_26px_rgba(97,0,0,0.22)] disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isSubmitting ? 'Signing in...' : 'Sign In'}
            </button>

            <button
              type="button"
              onClick={() => navigateTo('/')}
              className="h-12 w-full rounded-[16px] bg-[rgba(26,28,25,0.05)] text-[0.95rem] font-bold text-[var(--on-surface)]"
            >
              Back to Home
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
