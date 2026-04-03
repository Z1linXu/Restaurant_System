import type { BackendApiResponse } from '../types/ordering'
import type { BackendServingShelfItem, RealtimeUpdateMessage } from '../types/kds'

const DEFAULT_STORE_ID = 1
const DEFAULT_USER_ID = '1'

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

export async function fetchServingShelf(storeId = DEFAULT_STORE_ID) {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<BackendServingShelfItem[]>(`/api/v1/kds/serving-shelf?${params.toString()}`, {
    headers: {
      'X-User-Id': DEFAULT_USER_ID,
    },
  })
}

export async function markShelfItemServed(taskId: number) {
  return request(`/api/v1/kitchen-tasks/${taskId}/served`, {
    method: 'POST',
    headers: buildHeaders(),
  })
}

export function subscribeToServingShelf(
  storeId: number,
  onMessage: (message: RealtimeUpdateMessage) => void,
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
        client.subscribe(`/topic/stores/${storeId}/kds/serving-shelf`, (frame) => {
          try {
            const message = JSON.parse(frame.body) as RealtimeUpdateMessage
            onMessage(message)
          } catch {
            // ignore malformed message
          }
        })
      }

      client.activate()
      deactivate = () => {
        void client.deactivate()
      }
    })
    .catch(() => {
      // Ignore websocket bootstrap failure for MVP; the board can stay idle instead of crashing the whole app.
    })

  return () => {
    disposed = true
    deactivate?.()
  }
}
