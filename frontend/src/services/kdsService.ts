import type { BackendOrderResponse } from '../types/ordering'
import type { BackendKdsHistoryOrder, BackendKdsTaskDisplay } from '../types/kds'
import { apiRequest } from './apiClient'
import { createAuthenticatedRealtimeClient } from './realtimeClient'

const request = apiRequest

export async function fetchNoodleDisplayTasks(storeId: number) {
  const params = new URLSearchParams()
  params.set('store_id', String(storeId))

  return request<BackendKdsTaskDisplay[]>(`/api/v1/kds/noodle-display?${params.toString()}`)
}

export async function fetchHotKitchenDisplayTasks(storeId: number) {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<BackendKdsTaskDisplay[]>(`/api/v1/kds/hot-kitchen?${params.toString()}`)
}

export async function fetchKitchenOrderDetail(orderId: number) {
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}`)
}

export async function markKitchenTaskReadyForPickup(taskId: number) {
  return request(`/api/v1/kitchen-tasks/${taskId}/ready-for-pickup`, {
    method: 'POST',
  })
}

export async function fetchKdsHistory(storeId: number, stationCode?: string) {
  const params = new URLSearchParams()
  params.set('store_id', String(storeId))
  if (stationCode) {
    params.set('station_code', stationCode)
  }

  return request<BackendKdsHistoryOrder[]>(`/api/v1/kds/history?${params.toString()}`)
}

export function subscribeToNoodleDisplay(
  storeId: number,
  onMessage: () => void,
) {
  let disposed = false
  const connection = createAuthenticatedRealtimeClient()
  let subscription: { unsubscribe: () => void } | null = null
  connection.client.onConnect = () => {
    if (disposed) return
    subscription?.unsubscribe()
    subscription = connection.client.subscribe(`/topic/stores/${storeId}/kds/noodle-display`, () => {
      if (!disposed) onMessage()
    })
  }
  connection.client.activate()
  return () => { if (disposed) return; disposed=true; subscription?.unsubscribe(); subscription=null; connection.dispose() }
}

export function subscribeToHotKitchen(storeId: number, onMessage: () => void) {
  let disposed = false
  const connection = createAuthenticatedRealtimeClient()
  let subscription: { unsubscribe: () => void } | null = null
  connection.client.onConnect = () => {
    if (disposed) return
    subscription?.unsubscribe()
    subscription = connection.client.subscribe(`/topic/stores/${storeId}/kds/hot-kitchen`, () => { if (!disposed) onMessage() })
  }
  connection.client.activate()
  return () => { if (disposed) return; disposed=true; subscription?.unsubscribe(); subscription=null; connection.dispose() }
}
