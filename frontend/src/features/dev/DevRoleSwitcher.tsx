import { useEffect, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { fetchDevTestUsers, switchDevUser, type DevTestUser } from '../../services/devRoleSwitcherService'
import { fetchWorkspaces } from '../../services/storeWorkspaceService'
import { defaultWorkspacePathForRole } from '../store/storeRoutes'
import { navigateTo } from '../frontdesk/navigation'
import { getApiUserMessage } from '../../services/apiClient'

const enabled = import.meta.env.VITE_ENABLE_DEV_ROLE_SWITCHER === 'true'

export function DevRoleSwitcher() {
  const { user } = useAuth()
  const [users, setUsers] = useState<DevTestUser[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busyUser, setBusyUser] = useState<string | null>(null)
  const [collapsed, setCollapsed] = useState(false)

  useEffect(() => {
    if (!enabled) {
      return
    }
    let cancelled = false
    fetchDevTestUsers()
      .then((response) => {
        if (!cancelled) {
          setUsers(response)
          setError(null)
        }
      })
      .catch((exception) => {
        if (!cancelled) {
          setError(getApiUserMessage(exception, 'Dev switcher backend is disabled or unavailable'))
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (!enabled) {
    return null
  }

  const handleSwitch = async (loginIdentifier: string) => {
    setBusyUser(loginIdentifier)
    setError(null)
    try {
      const response = await switchDevUser(loginIdentifier)
      const workspaces = await fetchWorkspaces()
      const targetPath = defaultWorkspacePathForRole(response.user.role_code, workspaces)
      if (targetPath) {
        navigateTo(targetPath)
      } else {
        window.location.reload()
      }
    } catch (exception) {
      setError(getApiUserMessage(exception, 'Failed to switch dev user'))
      setBusyUser(null)
    }
  }

  return (
    <div className="fixed bottom-4 right-4 z-[9999] w-[280px] rounded-[22px] border border-amber-300 bg-amber-50/95 p-3 text-[0.82rem] text-stone-900 shadow-[0_18px_44px_rgba(26,28,25,0.22)] backdrop-blur">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="font-black uppercase tracking-[0.12em] text-amber-800">DEV ONLY</div>
          <div className="mt-0.5 font-extrabold">Role Switcher</div>
          <div className="mt-1 text-[0.74rem] font-semibold text-stone-600">
            {user ? `${user.username} · ${user.role_code}` : 'No active user'}
          </div>
        </div>
        <button
          type="button"
          onClick={() => setCollapsed((current) => !current)}
          className="rounded-full bg-white px-2 py-1 text-[0.72rem] font-bold text-amber-800"
        >
          {collapsed ? 'Open' : 'Hide'}
        </button>
      </div>

      {!collapsed ? (
        <>
          {error ? (
            <div className="mt-3 rounded-[14px] bg-red-100 px-3 py-2 text-[0.76rem] font-bold text-red-700">
              {error}
            </div>
          ) : null}
          <div className="mt-3 grid gap-2">
            {users.map((entry) => (
              <button
                key={entry.login_identifier}
                type="button"
                disabled={busyUser != null}
                onClick={() => void handleSwitch(entry.login_identifier)}
                className="rounded-[14px] bg-white px-3 py-2 text-left font-bold text-stone-800 shadow-sm transition hover:bg-amber-100 disabled:cursor-wait disabled:opacity-60"
              >
                <span>{busyUser === entry.login_identifier ? 'Switching...' : entry.label}</span>
                <span className="block text-[0.72rem] font-semibold text-stone-500">
                  {entry.login_identifier} · {entry.role_code}
                </span>
              </button>
            ))}
            {!users.length && !error ? (
              <div className="rounded-[14px] bg-white px-3 py-2 text-[0.76rem] font-semibold text-stone-500">
                Loading dev users...
              </div>
            ) : null}
          </div>
        </>
      ) : null}
    </div>
  )
}
