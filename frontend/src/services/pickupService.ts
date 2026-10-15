import type { BackendServingShelfItem, RealtimeUpdateMessage } from '../types/kds'
import { apiRequest } from './apiClient'
import { createAuthenticatedRealtimeClient } from './realtimeClient'

const request = apiRequest

export async function fetchServingShelf(storeId: number) {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<BackendServingShelfItem[]>(`/api/v1/kds/serving-shelf?${params.toString()}`)
}

export async function markShelfItemServed(taskId: number) {
  return request(`/api/v1/kitchen-tasks/${taskId}/served`, {
    method: 'POST',
  })
}

export function subscribeToServingShelf(
  storeId: number,
  onMessage: (message: RealtimeUpdateMessage) => void,
) {
  let disposed = false
  const connection = createAuthenticatedRealtimeClient()
  let subscription: { unsubscribe: () => void } | null = null
  connection.client.onConnect = () => {
    if (disposed) return
    subscription?.unsubscribe()
    subscription = connection.client.subscribe(`/topic/stores/${storeId}/kds/serving-shelf`, (frame) => {
      try {
        const message = JSON.parse(frame.body) as RealtimeUpdateMessage
        if (!disposed && message.store_id === storeId) onMessage(message)
      } catch { /* malformed refresh events contain no usable update */ }
    })
  }
  connection.client.activate()
  return () => { if (disposed) return; disposed=true; subscription?.unsubscribe(); subscription=null; connection.dispose() }
}
