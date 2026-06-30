import { createContext, useContext } from 'react'
import type { AuthUser, LoginResponse } from '../../services/authService'

export interface AuthContextValue {
  user: AuthUser | null
  permissions: string[]
  features: Record<string, boolean>
  loading: boolean
  error: string | null
  signIn: (loginIdentifier: string, password: string) => Promise<LoginResponse>
  signOut: () => Promise<void>
  refreshMe: () => Promise<void>
  isOwner: boolean
  isManager: boolean
  isFrontdesk: boolean
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return value
}
