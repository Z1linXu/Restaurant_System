import { useEffect } from 'react'
import { apiRequest } from '../../services/apiClient'
import { setBrowserOnlineStatus } from '../../services/networkStatus'

const HEALTH_PROBE_INTERVAL_MS = 30_000

export function ConnectionHealthProvider({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    let active = true
    let probeInFlight = false

    const probe = async () => {
      const browserOnline = navigator.onLine
      setBrowserOnlineStatus(browserOnline)
      if (!browserOnline || probeInFlight || !active) return
      probeInFlight = true
      try {
        await apiRequest<{ status: string; timestamp: string }>('/api/v1/system/health')
      } catch {
        // apiClient records the normalized failure for the shared connection state.
      } finally {
        probeInFlight = false
      }
    }

    const handleOnline = () => void probe()
    const handleOffline = () => setBrowserOnlineStatus(false)
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') void probe()
    }

    void probe()
    const intervalId = window.setInterval(() => {
      if (document.visibilityState === 'visible') void probe()
    }, HEALTH_PROBE_INTERVAL_MS)
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    document.addEventListener('visibilitychange', handleVisibility)
    return () => {
      active = false
      window.clearInterval(intervalId)
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
      document.removeEventListener('visibilitychange', handleVisibility)
    }
  }, [])

  return <>{children}</>
}
