import { useEffect } from 'react'
import { useAuth } from '../auth/useAuth'
import { startOrderOutboxProcessor } from '../../services/orderOutboxProcessor'

export function OrderOutboxProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth()

  useEffect(() => {
    if (!user?.id) return
    return startOrderOutboxProcessor(user.id)
  }, [user?.id])

  return <>{children}</>
}
