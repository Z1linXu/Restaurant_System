import { useState } from 'react'
import type { FormEvent } from 'react'
import { useLocale } from '../i18n/useLocale'
import { LanguageSelector } from '../components/LanguageSelector'
import { RestaurantBrand } from '../components/RestaurantBrand'
import type { MessageKey } from '../i18n/messages'
import { useAuth } from '../features/auth/useAuth'
import { defaultWorkspacePathForRole } from '../features/store/storeRoutes'
import { fetchWorkspaces } from '../services/storeWorkspaceService'
import { ApiRequestError, getApiUserMessage } from '../services/apiClient'

function navigateTo(path: string) {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export default function Login() {
  const { t } = useLocale()
  const { signIn } = useAuth()
  const [loginIdentifier, setLoginIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [errorKey, setErrorKey] = useState<MessageKey | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setErrorKey(null)
    setIsSubmitting(true)
    try {
      const response = await signIn(loginIdentifier, password).catch((exception) => {
        if (exception instanceof ApiRequestError && exception.status === 401) {
          throw new Error('LOGIN_INVALID')
        }
        throw exception
      })
      const workspaces = await fetchWorkspaces(response.user.id).catch((exception) => {
        console.error('[Login] workspace loading failed after successful login', exception)
        throw new Error('WORKSPACE_LOAD_FAILED')
      })
      const targetPath = defaultWorkspacePathForRole(response.user.role_code, workspaces)
      if (!targetPath) {
        setErrorKey('auth.noStore')
        return
      }
      navigateTo(targetPath)
    } catch (exception) {
      if (exception instanceof Error && exception.message === 'LOGIN_INVALID') setErrorKey('auth.invalid')
      else if (exception instanceof Error && exception.message === 'WORKSPACE_LOAD_FAILED') setErrorKey('auth.workspaceFailed')
      else setError(getApiUserMessage(exception, t('auth.failed')))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-[var(--surface)] px-6 py-8 text-[var(--on-surface)]">
      <div className="mx-auto flex max-w-[980px] justify-end"><LanguageSelector /></div>
      <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-[980px] items-center justify-center">
        <div className="grid w-full overflow-hidden rounded-[32px] bg-white shadow-[0_24px_70px_rgba(26,28,25,0.12)] md:grid-cols-[1fr_1.05fr]">
          <div className="bg-[var(--primary)] px-8 py-10 text-white">
            <div className="rounded-2xl bg-white px-4 py-3"><RestaurantBrand /></div>
            <div className="mt-5 text-[1.05rem] font-semibold opacity-90">
              {t('auth.welcome')}
            </div>
            <div className="mt-8 rounded-[22px] bg-white/12 px-5 py-4 text-[0.95rem] font-medium leading-7">
              {t('auth.description')}
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5 px-8 py-10">
            <div>
              <div className="text-[1.8rem] font-black tracking-[-0.05em]">{t('auth.title')}</div>
              <div className="mt-1 text-[0.95rem] font-medium text-[var(--muted)]">{t('auth.description')}</div>
            </div>

            <label className="block">
              <span className="text-[0.85rem] font-bold uppercase tracking-[0.12em] text-[var(--muted)]">{t('auth.account')}</span>
              <input
                value={loginIdentifier}
                onChange={(event) => setLoginIdentifier(event.target.value)}
                className="mt-2 h-14 w-full rounded-[18px] border border-[rgba(26,28,25,0.12)] px-4 text-[1.05rem] font-semibold outline-none focus:border-[var(--primary)]"
                autoComplete="username"
              />
            </label>

            <label className="block">
              <span className="text-[0.85rem] font-bold uppercase tracking-[0.12em] text-[var(--muted)]">{t('auth.password')}</span>
              <input
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type="password"
                className="mt-2 h-14 w-full rounded-[18px] border border-[rgba(26,28,25,0.12)] px-4 text-[1.05rem] font-semibold outline-none focus:border-[var(--primary)]"
                autoComplete="current-password"
              />
            </label>

            {error || errorKey ? (
              <div className="rounded-[18px] bg-red-50 px-4 py-3 text-[0.95rem] font-semibold text-red-700">{errorKey ? t(errorKey) : error}</div>
            ) : null}

            <button
              type="submit"
              disabled={isSubmitting}
              className="h-14 w-full rounded-[18px] bg-[var(--primary)] text-[1.05rem] font-black text-white shadow-[0_12px_26px_rgba(97,0,0,0.22)] disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isSubmitting ? t('auth.signingIn') : t('auth.title')}
            </button>

            <button
              type="button"
              onClick={() => navigateTo('/')}
              className="h-12 w-full rounded-[16px] bg-[rgba(26,28,25,0.05)] text-[0.95rem] font-bold text-[var(--on-surface)]"
            >
              {t('common.back')}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
