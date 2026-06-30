import type { BackendOrderResponse } from '../types/ordering'
import type { BackendKdsHistoryOrder, BackendKdsTaskDisplay } from '../types/kds'
import { apiRequest } from './apiClient'

const request = apiRequest

export async function fetchNoodleDisplayTasks(storeId: number) {
  const params = new URLSearchParams()
  params.set('store_id', String(storeId))

  return request<BackendKdsTaskDisplay[]>(`/api/v1/kds/noodle-display?${params.toString()}`)
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
  let deactivate: (() => void) | null = null

  void Promise.all([import('@stomp/stompjs'), import('sockjs-client')])
    .then(([stompModule, sockJsModule]) => {
      if (disposed) {
        return
      }

      const ClientCtor = stompModule.Client
      const SockJSImport = sockJsModule.default
      const SockJSCtor =
        typeof SockJSImport === 'function'
          ? SockJSImport
          : ((sockJsModule as unknown as { SockJS?: typeof SockJSImport }).SockJS ?? SockJSImport)

      const client = new ClientCtor({
        webSocketFactory: () => new SockJSCtor('/ws'),
        reconnectDelay: 3000,
      })

      client.onConnect = () => {
        client.subscribe(`/topic/stores/${storeId}/kds/noodle-display`, () => {
          onMessage()
        })
      }

      client.activate()
      deactivate = () => {
        void client.deactivate()
      }
    })
    .catch(() => {
      // ignore websocket bootstrap failures; polling remains the fallback
    })

  return () => {
    disposed = true
    deactivate?.()
  }
}
