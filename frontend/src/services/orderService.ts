import type {
  BackendFrontdeskOrderBoardItem,
  BackendOrderResponse,
  BackendOrderUpdateResponse,
  ItemCustomizationDraft,
  MenuItem,
  OrderLineItem,
  OrderPrintOption,
} from '../types/ordering'
import type { RealtimeUpdateMessage } from '../types/kds'
import type { PrintJobRecord } from './printingAdminService'
import { apiRequest } from './apiClient'

const READ_RETRY_DELAYS_MS = [0, 250, 750]
const pendingEditableOrderRequests = new Map<string, Promise<BackendOrderResponse>>()

interface EditableOrderContext {
  storeId: number
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
  storeId: number
  statuses?: string[]
  limit?: number
}

export interface IdempotentOrderItemPayload {
  menu_item_id: number
  quantity: number
  combo_group_no: number | null
  combo_role: string
  notes: string | null
  options: { option_id: number; quantity: number }[]
}

export interface IdempotentOrderSubmitPayload {
  client_order_id: string
  idempotency_key: string
  organization_id: number
  store_id: number
  server_order_id: number | null
  order_type: 'dine_in' | 'pickup'
  table_no: string | null
  pickup_no: string | null
  menu_revision: number
  expected_subtotal_amount: number
  items: IdempotentOrderItemPayload[]
}

export interface IdempotentOrderSubmitResult {
  client_order_id: string
  idempotency_key: string
  payload_hash: string
  order_id: number
  replayed: boolean
  order: BackendOrderResponse
}

function buildHeaders() {
  return {
    'Content-Type': 'application/json',
  }
}

const request = apiRequest

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

export function mapOptions(draft: ItemCustomizationDraft, menuItem?: MenuItem) {
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
    draft.comboSideRemoveIds.forEach((optionId) => pushOption(optionId))
  }

  draft.removeIds.forEach((optionId) => pushOption(optionId))

  Object.entries(draft.addOnQuantities).forEach(([optionId, quantity]) => {
    if (quantity > 0) {
      pushOption(optionId, quantity)
    }
  })

  return optionPayloads
}

export async function findDraftOrderByTableSlot(storeId: number, slotLabel: string) {
  const params = new URLSearchParams({
    store_id: String(storeId),
    table_no: slotLabel,
  })

  return request<BackendOrderResponse | null>(`/api/v1/orders/draft-open?${params.toString()}`)
}

export async function findEditableOrderByContext(context: EditableOrderContext) {
  const params = new URLSearchParams({
    store_id: String(context.storeId),
  })
  if (context.tableNo) {
    params.set('table_no', context.tableNo)
  }
  if (context.pickupNo) {
    params.set('pickup_no', context.pickupNo)
  }

  const order = await request<BackendOrderResponse | null>(`/api/v1/orders/open-editable?${params.toString()}`)
  return order
}

export async function fetchActiveOrderBoardForStore(storeId: number) {
  return fetchFrontdeskOrderBoard({
    storeId,
    statuses: ['draft', 'submitted', 'preparing', 'ready'],
  })
}

export async function fetchFrontdeskOrderBoard(input: FrontdeskOrderQueryInput) {
  const params = new URLSearchParams()
  params.set('store_id', String(input.storeId))
  ;(input.statuses ?? ['draft', 'submitted', 'preparing', 'ready']).forEach((status) => params.append('status', status))
  if (input.limit) {
    params.set('limit', String(input.limit))
  }

  return request<BackendFrontdeskOrderBoardItem[]>(`/api/v1/frontdesk/orders?${params.toString()}`)
}

export async function fetchFrontdeskOrderHistory(input: FrontdeskOrderQueryInput) {
  const params = new URLSearchParams()
  params.set('store_id', String(input.storeId))
  ;(input.statuses ?? ['completed']).forEach((status) => params.append('status', status))
  if (input.limit) {
    params.set('limit', String(input.limit))
  }

  return request<BackendFrontdeskOrderBoardItem[]>(`/api/v1/frontdesk/orders/history?${params.toString()}`)
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
      return await request<BackendOrderResponse>(`/api/v1/orders/${orderId}`)
    } catch (error) {
      lastError = error
    }
  }

  throw lastError instanceof Error ? lastError : new Error('Failed to load order detail')
}

export async function ensureEditableOrder(context: EditableOrderContext) {
  const requestKey =
    context.orderType === 'pickup'
      ? `${context.storeId}:pickup:${context.pickupNo ?? ''}`
      : `${context.storeId}:table:${context.tableNo ?? ''}`

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
        store_id: context.storeId,
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

export async function submitIdempotentOrder(payload: IdempotentOrderSubmitPayload) {
  return request<IdempotentOrderSubmitResult>(
    `/api/v1/stores/${payload.store_id}/orders/idempotent-submit`,
    {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(payload),
    },
  )
}

export async function addDraftOrderItem(orderId: number, menuItem: MenuItem, draft: ItemCustomizationDraft, notes = '') {
  const effectiveNotes = notes || draft.notes
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}/items`, {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      menu_item_id: Number(menuItem.id),
      quantity: draft.quantity,
      combo_group_no: null,
      combo_role: 'standalone',
      notes: effectiveNotes.trim() || null,
      options: mapOptions(draft, menuItem),
    }),
  })
}

export async function submitOrderUpdate(
  orderId: number,
  idempotencyKey: string,
  items: OrderLineItem[],
  catalogItems: MenuItem[],
) {
  return request<BackendOrderUpdateResponse>(`/api/v1/orders/${orderId}/updates`, {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      idempotency_key: idempotencyKey,
      items: items.map((item) => {
        const menuItem = catalogItems.find((catalogItem) => catalogItem.id === item.menuItemId)
        return {
          menu_item_id: Number(item.menuItemId),
          quantity: item.quantity,
          combo_group_no: null,
          combo_role: 'standalone',
          notes: item.notes.trim() || null,
          options: mapOptions(item.selection, menuItem),
        }
      }),
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
  const effectiveNotes = notes || draft.notes
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}/items/${itemId}`, {
    method: 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify({
      quantity: draft.quantity,
      combo_group_no: null,
      combo_role: 'standalone',
      notes: effectiveNotes.trim() || null,
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
  })
}

export async function completeOrder(orderId: number) {
  return request<BackendOrderResponse>(`/api/v1/orders/${orderId}/complete`, {
    method: 'POST',
  })
}

export async function reprintOrderReceipt(
  orderId: number,
  receiptType: string,
) {
  return request<PrintJobRecord>(`/api/v1/orders/${orderId}/reprint`, {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      receipt_type: receiptType,
      update_ticket: false,
    }),
  })
}

export async function fetchOrderPrintJobs(orderId: number) {
  return request<PrintJobRecord[]>(`/api/v1/orders/${orderId}/print-jobs`)
}

export async function fetchOrderPrintOptions(orderId: number) {
  return request<OrderPrintOption[]>(`/api/v1/orders/${orderId}/print-options`)
}

export async function fetchTodayOrderHistory(storeId: number, limit = 100) {
  const params = new URLSearchParams({ store_id: String(storeId), limit: String(limit) })
  return request<BackendFrontdeskOrderBoardItem[]>(`/api/v1/frontdesk/orders/today?${params.toString()}`)
}
