import { useSyncExternalStore } from 'react'
import { getConnectionSnapshot, subscribeConnectionStatus } from '../services/networkStatus'

export function useConnectionStatus() {
  return useSyncExternalStore(subscribeConnectionStatus, getConnectionSnapshot, getConnectionSnapshot)
}
