import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiRequestError, clearAuthTokens, getAccessToken, getRefreshToken } from '../../services/apiClient'
import { fetchCurrentUser, login as loginRequest, logout as logoutRequest, type AuthUser, type LoginResponse } from '../../services/authService'
import { AuthContext } from './useAuth'

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [permissions, setPermissions] = useState<string[]>([])
  const [features, setFeatures] = useState<Record<string, boolean>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const applyAuthResponse = useCallback((response: LoginResponse) => {
    setUser(response.user)
    setPermissions(response.permissions ?? [])
    setFeatures(response.features ?? {})
  }, [])

  const refreshMe = useCallback(async () => {
    if (!getAccessToken() && !getRefreshToken()) {
      setUser(null)
      setPermissions([])
      setFeatures({})
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      applyAuthResponse(await fetchCurrentUser())
    } catch (exception) {
      if (exception instanceof ApiRequestError && exception.status === 401) {
        clearAuthTokens()
        setUser(null)
        setPermissions([])
        setFeatures({})
      }
      setError(exception instanceof Error ? exception.message : 'Session expired')
    } finally {
      setLoading(false)
    }
  }, [applyAuthResponse])

  useEffect(() => {
    void refreshMe()
  }, [refreshMe])

  useEffect(() => {
    const handleExpired = () => {
      setUser(null)
      setPermissions([])
      setFeatures({})
    }
    window.addEventListener('restaurant-auth-expired', handleExpired)
    return () => window.removeEventListener('restaurant-auth-expired', handleExpired)
  }, [])

  useEffect(() => {
    const handleAuthUpdated = () => {
      void refreshMe()
    }
    window.addEventListener('restaurant-auth-updated', handleAuthUpdated)
    return () => window.removeEventListener('restaurant-auth-updated', handleAuthUpdated)
  }, [refreshMe])

  useEffect(() => {
    const recoverSession = () => {
      if (!user && (getAccessToken() || getRefreshToken())) {
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
  }, [refreshMe, user])

  const signIn = useCallback(
    async (loginIdentifier: string, password: string) => {
      const response = await loginRequest(loginIdentifier, password)
      applyAuthResponse(response)
      return response
    },
    [applyAuthResponse],
  )

  const signOut = useCallback(async () => {
    await logoutRequest()
    setUser(null)
    setPermissions([])
    setFeatures({})
  }, [])

  const role = user?.role_code?.toUpperCase()
  const value = useMemo(
    () => ({
      user,
      permissions,
      features,
      loading,
      error,
      signIn,
      signOut,
      refreshMe,
      isOwner: role === 'OWNER' || role === 'ADMIN',
      isManager: role === 'MANAGER',
      isFrontdesk: role === 'FRONTDESK',
    }),
    [error, features, loading, permissions, refreshMe, role, signIn, signOut, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
