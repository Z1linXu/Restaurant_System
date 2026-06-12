import type {
  BackendApiResponse,
  BackendFrontdeskOrderBoardItem,
  BackendOrderResponse,
  ItemCustomizationDraft,
  MenuItem,
} from '../types/ordering'
import type { RealtimeUpdateMessage } from '../types/kds'

const DEFAULT_STORE_ID = 1
const DEFAULT_USER_ID = '1'
const READ_RETRY_DELAYS_MS = [0, 250, 750]
const pendingEditableOrderRequests = new Map<string, Promise<BackendOrderResponse>>()

interface EditableOrderContext {
  orderType: 'dine_in' | 'pickup'
  tableNo?: string | null
  pickupNo?: string | null
}

interface UpdateOrderHeaderInput {
  orderType: 'dine_in' | 'pickup'
  tableNo?: string | null
  pickupNo?: string | null
}

interface FrontdeskOrderQueryInput {
  statuses?: string[]
  limit?: number
}

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

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function mapOptions(draft: ItemCustomizationDraft, menuItem?: MenuItem) {
  const optionPayloads: { option_id: number; quantity: number }[] = []

  const pushOption = (optionId: string | undefined, quantity = 1) => {
    if (!optionId) {
      return
    }
    optionPayloads.push({
      option_id: Number(optionId),
      quantity,
    })
  }

  pushOption(draft.sizeId)
  pushOption(draft.soupBaseId)
  pushOption(draft.noodleTypeId)
  pushOption(draft.spicyLevelId)

  if (draft.comboEnabled) {
    pushOption(menuItem?.customization?.combo?.optionId)
    pushOption(draft.comboEggId ?? menuItem?.customization?.combo?.eggs[0]?.id)
    pushOption(draft.comboSideId ?? menuItem?.customization?.combo?.sides[0]?.id)
  }

  draft.removeIds.forEach((optionId) => pushOption(optionId))

  Object.entries(draft.addOnQuantities).forEach(([optionId, quantity]) => {
    if (quantity > 0) {
      pushOption(optionId, quantity)
    }
  })

  return optionPayloads
}

export async function findDraftOrderByTableSlot(slotLabel: string) {
  const params = new URLSearchParams({
    store_id: String(DEFAULT_STORE_ID),
    table_no: slotLabel,
  })

  return request<BackendOrderResponse | null>(
    `/api/v1/orders/draft-open?${params.toString()}`,
    {
      headers: {
        'X-User-Id': DEFAULT_USER_ID,
      },
    },
  )
}

export async function findEditableOrderByContext(context: EditableOrderContext) {
  const params = new URLSearchParams({
    store_id: String(DEFAULT_STORE_ID),
  })
  if (context.tableNo) {
    params.set('table_no', context.tableNo)
  }
  if (context.pickupNo) {
    params.set('pickup_no', context.pickupNo)
  }

  const order = await request<BackendOrderResponse | null>(
    `/api/v1/orders/open-editable?${params.toString()}`,
    {
      headers: {
        'X-User-Id': DEFAULT_USER_ID,
      },
    },
  )
  return order
}

export async function fetchActiveOrderBoard() {
  return fetchFrontdeskOrderBoard({
    statuses: ['draft', 'submitted', 'preparing', 'ready'],
  })
}

export async function fetchFrontdeskOrderBoard(input: FrontdeskOrderQueryInput = {}) {
  const params = new URLSearchParams()
  params.set('store_id', String(DEFAULT_STORE_ID))
  ;(input.statuses ?? ['draft', 'submitted', 'preparing', 'ready']).forEach((status) => params.append('status', status))
  if (input.limit) {
    params.set('limit', String(input.limit))
  }

  return request<BackendFrontdeskOrderBoardItem[]>(`/api/v1/frontdesk/orders?${params.toString()}`, {
    headers: {
      'X-User-Id': DEFAULT_USER_ID,
    },
  })
}

export async function fetchFrontdeskOrderHistory(input: FrontdeskOrderQueryInput = {}) {
  const params = new URLSearchParams()
  params.set('store_id', String(DEFAULT_STORE_ID))
  ;(input.statuses ?? ['completed']).forEach((status) => params.append('status', status))
  if (input.limit) {
    params.set('limit', String(input.limit))
  }

  return request<BackendFrontdeskOrderBoardItem[]>(`/api/v1/frontdesk/orders/history?${params.toString()}`, {
    headers: {
      'X-User-Id': DEFAULT_USER_ID,
    },
  })
}

export function subscribeToFrontdeskOrders(
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
        client.subscribe(`/topic/stores/${storeId}/frontdesk/orders`, (frame) => {
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
      // ignore websocket bootstrap failures; polling remains the fallback
    })

  return () => {
    disposed = true
    deactivate?.()
  }
}

export async function fetchOrderDetail(orderId: number) {
  let lastError: unknown = null

  for (const delay of READ_RETRY_DELAYS_MS) {
    if (delay > 0) {
      await sleep(delay)
    }

    try {
      return await request<BackendOrderResponse>(`/api/v1/orders/${orderId}`, {
        headers: {
          'X-User-Id': DEFAULT_USER_ID,
        },
      })
    } catch (error) {
      lastError = error
    }
  }

  throw lastError instanceof Error ? lastError : new Error('Failed to load order detail')
}

export async function ensureEditableOrder(context: EditableOrderContext) {
  const requestKey =
    context.orderType === 'pickup'
      ? `pickup:${context.pickupNo ?? ''}`
      : `table:${context.tableNo ?? ''}`

  const existingPendingRequest = pendingEditableOrderRequests.get(requestKey)
  if (existingPendingRequest) {
    return existingPendingRequest
  }

  const requestPromise = (async () => {
    const existingOrder = await findEditableOrderByContext(context)
    if (existingOrder) {
      return existingOrder
    }

    return request<BackendOrderResponse>('/api/v1/orders', {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify({
        store_id: DEFAULT_STORE_ID,
        created_by: 1,
        order_type: context.orderType,
        table_no: context.tableNo ?? null,
        pickup_no: context.pickupNo ?? null,
        items: [],
      }),
    })
  })()

  pendingEditableOrderRequests.set(requestKey, requestPromise)

  try {
    return await requestPromise
  } finally {
    pendingEditableOrderRequests.delete(requestKey)
  }
}

export async function submitDraftOrder(orderId: number) {
  let lastError: unknown = null

  for (const delay of READ_RETRY_DELAYS_MS) {
    if (delay > 0) {
      await sleep(delay)
    }

    try {
      return await request<BackendOrderResponse>(`/api/v1/orders/${orderId}/submit`, {
        method: 'POST',
        headers: {
          'X-User-Id': DEFAULT_USER_ID,
        },
      })
    } catch (error) {
      lastError = error
      if (!(error instanceof Error) || error.message !== 'Draft order must contain at least one item before submission') {
        throw error
      }
    }
  }

  throw lastError instanceof Error ? lastError : new Error('Failed to submit order')
}

export async function addDraftOrderItem(orderId: number, menuItem: MenuItem, draft: ItemCustomizationDraft, notes = '') {
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}/items`, {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      menu_item_id: Number(menuItem.id),
      quantity: draft.quantity,
      combo_group_no: null,
      combo_role: 'standalone',
      notes: notes.trim() || null,
      options: mapOptions(draft, menuItem),
    }),
  })
}

export async function updateDraftOrderItem(orderId: number, itemId: number, draft: ItemCustomizationDraft) {
  return updateDraftOrderItemWithMenuItem(orderId, itemId, undefined, draft)
}

export async function updateDraftOrderItemWithMenuItem(
  orderId: number,
  itemId: number,
  menuItem: MenuItem | undefined,
  draft: ItemCustomizationDraft,
  notes = '',
) {
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}/items/${itemId}`, {
    method: 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify({
      quantity: draft.quantity,
      combo_group_no: null,
      combo_role: 'standalone',
      notes: notes.trim() || null,
      options: mapOptions(draft, menuItem),
    }),
  })
}

export async function updateDraftOrderItemQuantity(orderId: number, itemId: number, quantity: number) {
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}/items/${itemId}/quantity`, {
    method: 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify({
      quantity,
    }),
  })
}

export async function removeDraftOrderItem(orderId: number, itemId: number) {
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}/items/${itemId}`, {
    method: 'DELETE',
    headers: {
      'X-User-Id': DEFAULT_USER_ID,
    },
  })
}

export async function updateEditableOrderHeader(orderId: number, input: UpdateOrderHeaderInput) {
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}/draft-header`, {
    method: 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify({
      order_type: input.orderType,
      table_no: input.tableNo ?? null,
      pickup_no: input.pickupNo ?? null,
    }),
  })
}

export async function cancelDraftOrder(orderId: number) {
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}/cancel`, {
    method: 'POST',
    headers: {
      'X-User-Id': DEFAULT_USER_ID,
    },
  })
}

export async function completeOrder(orderId: number) {
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}/complete`, {
    method: 'POST',
    headers: {
      'X-User-Id': DEFAULT_USER_ID,
    },
  })
}
