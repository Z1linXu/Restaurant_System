import type { BackendApiResponse, BackendOrderResponse } from '../types/ordering'
import type { BackendKdsHistoryOrder, BackendKdsTaskDisplay } from '../types/kds'

const DEFAULT_STORE_ID = 1
const DEFAULT_USER_ID = '2'

function buildHeaders() {
  return {
    'Content-Type': 'application/json',
    'X-User-Id': DEFAULT_USER_ID,
  }
}

async function request<T>(input: string, init?: RequestInit) {
  const response = await fetch(input, init)
  if (!response.ok) {
    throw new Error(`Request failed (${response.status})`)
  }

  const payload = (await response.json()) as BackendApiResponse<T>
  if (!payload.success) {
    throw new Error(payload.message || 'Request failed')
  }

  return payload.data
}

export async function fetchNoodleDisplayTasks(storeId = DEFAULT_STORE_ID) {
  const params = new URLSearchParams()
  params.set('store_id', String(storeId))

  return request<BackendKdsTaskDisplay[]>(`/api/v1/kds/noodle-display?${params.toString()}`, {
    headers: {
      'X-User-Id': DEFAULT_USER_ID,
    },
  })
}

export async function fetchKitchenOrderDetail(orderId: number) {
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}`, {
    headers: buildHeaders(),
  })
}

export async function markKitchenTaskReadyForPickup(taskId: number) {
  return request(`/api/v1/kitchen-tasks/${taskId}/ready-for-pickup`, {
    method: 'POST',
    headers: buildHeaders(),
  })
}

export async function fetchKdsHistory(storeId = DEFAULT_STORE_ID, stationCode?: string) {
  const params = new URLSearchParams()
  params.set('store_id', String(storeId))
  if (stationCode) {
    params.set('station_code', stationCode)
  }

  return request<BackendKdsHistoryOrder[]>(`/api/v1/kds/history?${params.toString()}`, {
    headers: {
      'X-User-Id': DEFAULT_USER_ID,
    },
  })
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
