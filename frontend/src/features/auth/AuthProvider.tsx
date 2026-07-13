import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiRequestError, clearAuthTokens, getAccessToken, getRefreshToken } from '../../services/apiClient'
import { fetchCurrentUser, login as loginRequest, logout as logoutRequest, type AuthUser, type LoginResponse } from '../../services/authService'
import { canUseOfflineSnapshot } from '../../offline/offlineFallbackPolicy'
import {
  clearRestrictedAuthSnapshot,
  readRestrictedAuthSnapshot,
  saveRestrictedAuthSnapshot,
} from '../../offline/workspaceSnapshot'
import { AuthContext, type AuthSessionMode } from './useAuth'

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [permissions, setPermissions] = useState<string[]>([])
  const [features, setFeatures] = useState<Record<string, boolean>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [sessionMode, setSessionMode] = useState<AuthSessionMode>('NONE')

  const applyAuthIdentity = useCallback((
    nextUser: AuthUser,
    nextPermissions: string[],
    nextFeatures: Record<string, boolean>,
    nextSessionMode: AuthSessionMode,
  ) => {
    setUser(nextUser)
    setPermissions(nextPermissions)
    setFeatures(nextFeatures)
    setSessionMode(nextSessionMode)
  }, [])

  const clearAuthIdentity = useCallback(() => {
    setUser(null)
    setPermissions([])
    setFeatures({})
    setSessionMode('NONE')
  }, [])

  const applyOnlineAuthResponse = useCallback((response: LoginResponse) => {
    applyAuthIdentity(response.user, response.permissions ?? [], response.features ?? {}, 'ONLINE')
    void saveRestrictedAuthSnapshot(response).catch((snapshotError) => {
      console.warn('[AuthProvider] unable to save restricted offline auth snapshot', snapshotError)
    })
  }, [applyAuthIdentity])

  const refreshMe = useCallback(async () => {
    if (!getAccessToken() && !getRefreshToken()) {
      clearAuthIdentity()
      void clearRestrictedAuthSnapshot().catch(() => undefined)
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    if (!navigator.onLine) {
      const snapshot = await readRestrictedAuthSnapshot().catch(() => null)
      if (snapshot) {
        applyAuthIdentity(
          snapshot.user,
          snapshot.permissions,
          snapshot.features,
          'OFFLINE_RESTRICTED',
        )
        setError('当前离线，仅可使用最近在线验证的本机点单工作区。')
        setLoading(false)
        return
      }
    }
    try {
      applyOnlineAuthResponse(await fetchCurrentUser())
    } catch (exception) {
      if (exception instanceof ApiRequestError && (exception.status === 401 || exception.status === 403)) {
        clearAuthTokens()
        clearAuthIdentity()
        void clearRestrictedAuthSnapshot().catch(() => undefined)
      } else if (canUseOfflineSnapshot(exception)) {
        const snapshot = await readRestrictedAuthSnapshot().catch(() => null)
        if (snapshot) {
          applyAuthIdentity(
            snapshot.user,
            snapshot.permissions,
            snapshot.features,
            'OFFLINE_RESTRICTED',
          )
          setError('当前离线，仅可使用最近在线验证的本机点单工作区。')
          return
        }
        clearAuthIdentity()
      }
      setError(exception instanceof Error ? exception.message : 'Session expired')
    } finally {
      setLoading(false)
    }
  }, [applyAuthIdentity, applyOnlineAuthResponse, clearAuthIdentity])

  useEffect(() => {
    void refreshMe()
  }, [refreshMe])

  useEffect(() => {
    const handleExpired = () => {
      clearAuthIdentity()
      void clearRestrictedAuthSnapshot().catch(() => undefined)
    }
    window.addEventListener('restaurant-auth-expired', handleExpired)
    return () => window.removeEventListener('restaurant-auth-expired', handleExpired)
  }, [clearAuthIdentity])

  useEffect(() => {
    const handleAuthUpdated = () => {
      void refreshMe()
    }
    window.addEventListener('restaurant-auth-updated', handleAuthUpdated)
    return () => window.removeEventListener('restaurant-auth-updated', handleAuthUpdated)
  }, [refreshMe])

  useEffect(() => {
    const recoverSession = () => {
      if ((!user || sessionMode === 'OFFLINE_RESTRICTED') && (getAccessToken() || getRefreshToken())) {
        void refreshMe()
      }
    }
    const handleVisibility = () => {
      if (document.visibilityState === 'visible' && navigator.onLine) recoverSession()
    }
    window.addEventListener('online', recoverSession)
    document.addEventListener('visibilitychange', handleVisibility)
    return () => {
      window.removeEventListener('online', recoverSession)
      document.removeEventListener('visibilitychange', handleVisibility)
    }
  }, [refreshMe, sessionMode, user])

  const signIn = useCallback(
    async (loginIdentifier: string, password: string) => {
      const response = await loginRequest(loginIdentifier, password)
      applyOnlineAuthResponse(response)
      return response
    },
    [applyOnlineAuthResponse],
  )

  const signOut = useCallback(async () => {
    await logoutRequest()
    clearAuthIdentity()
    await clearRestrictedAuthSnapshot().catch(() => undefined)
  }, [clearAuthIdentity])

  const role = user?.role_code?.toUpperCase()
  const value = useMemo(
    () => ({
      user,
      permissions,
      features,
      loading,
      error,
      sessionMode,
      isOfflineRestricted: sessionMode === 'OFFLINE_RESTRICTED',
      signIn,
      signOut,
      refreshMe,
      isOwner: role === 'OWNER' || role === 'ADMIN',
      isManager: role === 'MANAGER',
      isFrontdesk: role === 'FRONTDESK',
    }),
    [error, features, loading, permissions, refreshMe, role, sessionMode, signIn, signOut, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
