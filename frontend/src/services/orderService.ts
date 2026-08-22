import type {
  BackendFrontdeskOrderBoardItem,
  BackendOrderResponse,
  BackendOrderUpdateResponse,
  ChoiceOption,
  ItemCustomizationDraft,
  MenuItem,
  OrderLineItem,
  OrderPrintOption,
} from '../types/ordering'
import type { RealtimeUpdateMessage } from '../types/kds'
import type { PrintJobRecord } from './printingAdminService'
import { ApiRequestError, apiRequest } from './apiClient'
import { resolveComboGroupOptionId } from '../utils/comboSelection'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { networkDiagnosticsDisplayEnabled } from './networkStatus'

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

export type FrontdeskRealtimeLifecyclePhase =
  | 'BOOTSTRAP_STARTED'
  | 'CLIENT_CREATED'
  | 'SOCKET_FACTORY_CALLED'
  | 'CONNECTED'
  | 'STOMP_ERROR'
  | 'SOCKET_ERROR'
  | 'SOCKET_CLOSED'
  | 'DISCONNECTED'
  | 'BOOTSTRAP_ERROR'
  | 'CANCELLED'

export interface FrontdeskRealtimeLifecycleEvent {
  storeId: number
  phase: FrontdeskRealtimeLifecyclePhase
  transport: 'sockjs-stomp'
  closeCode?: number
}

export interface FrontdeskRealtimeSubscriptionOptions {
  onLifecycle?: (event: FrontdeskRealtimeLifecycleEvent) => void
}

export interface OrderPrintJobCoordinatorOptions {
  storeId: number
  orderId: number
  updateBatchId?: number | null
  delaysMs?: readonly number[]
  onAttention: (jobs: PrintJobRecord[]) => void
  onUnavailable?: (error: unknown) => void
}

export interface OrderPrintJobCoordinator {
  key: string
  cancel: () => void
}

export interface IdempotentOrderItemPayload {
  menu_item_id: number
  item_name_snapshot_zh: string
  item_name_snapshot_en: string
  unit_price_snapshot: number
  category_code_snapshot: string | null
  station_id_snapshot: number | null
  item_sku_snapshot: string | null
  item_type_snapshot: string | null
  quantity: number
  combo_group_no: number | null
  combo_role: string
  notes: string | null
  options: {
    option_id: number
    option_type_snapshot: string | null
    option_code_snapshot: string | null
    option_group_snapshot: string | null
    parent_option_id_snapshot: number | null
    option_name_snapshot_zh: string
    option_name_snapshot_en: string
    option_price_snapshot: number
    quantity: number
  }[]
}

interface OrderOptionPayload {
  option_id: number
  quantity: number
  option_type_snapshot?: string | null
  option_code_snapshot?: string | null
  option_group_snapshot?: string | null
  parent_option_id_snapshot?: number | null
  option_name_snapshot_zh?: string
  option_name_snapshot_en?: string
  option_price_snapshot?: number
}

export interface IdempotentOrderSubmitPayload {
  client_order_id: string
  idempotency_key: string
  local_draft_id?: string | null
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

function sleep(ms: number, signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Operation cancelled', 'AbortError'))
      return
    }

    function abort() {
      globalThis.clearTimeout(timeoutId)
      signal?.removeEventListener('abort', abort)
      reject(new DOMException('Operation cancelled', 'AbortError'))
    }
    const timeoutId = globalThis.setTimeout(() => {
      signal?.removeEventListener('abort', abort)
      resolve()
    }, ms)
    signal?.addEventListener('abort', abort, { once: true })
  })
}

export function mapOptions(draft: ItemCustomizationDraft, menuItem?: MenuItem) {
  const optionPayloads: OrderOptionPayload[] = []
  const optionsById = menuItem ? collectMenuItemOptions(menuItem) : new Map<string, ChoiceOption>()
  const pushedOptionIds = new Set<string>()

  const pushOption = (optionId: string | undefined, quantity = 1) => {
    if (!optionId) {
      return
    }
    if (pushedOptionIds.has(optionId)) {
      return
    }
    pushedOptionIds.add(optionId)
    const option = optionsById.get(optionId)
    const payload: OrderOptionPayload = {
      option_id: Number(optionId),
      quantity,
    }
    if (option) {
      payload.option_type_snapshot = option.optionType ?? null
      payload.option_code_snapshot = option.optionCode ?? null
      payload.option_group_snapshot = option.optionGroup ?? null
      payload.parent_option_id_snapshot = option.parentOptionId == null ? null : Number(option.parentOptionId)
      payload.option_name_snapshot_zh = option.labelZh
      payload.option_name_snapshot_en = option.labelEn
      payload.option_price_snapshot = option.priceDelta ?? 0
    }
    optionPayloads.push(payload)
  }

  pushOption(draft.sizeId)
  pushOption(draft.soupBaseId)
  pushOption(draft.noodleTypeId)
  pushOption(draft.spicyLevelId)

  if (draft.comboEnabled) {
    pushOption(menuItem?.customization?.combo?.optionId)
    const comboGroups = menuItem?.customization?.combo?.groups ?? []
    if (comboGroups.length) {
      comboGroups.forEach((group) => {
        pushOption(resolveComboGroupOptionId(draft, group))
      })
    } else {
      pushOption(draft.comboEggId ?? menuItem?.customization?.combo?.eggs[0]?.id)
      pushOption(draft.comboSideId ?? menuItem?.customization?.combo?.sides[0]?.id)
    }
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

function collectMenuItemOptions(menuItem: MenuItem) {
  const options = [
    ...(menuItem.customization?.sizes?.options ?? []),
    ...(menuItem.customization?.soupBases?.options ?? []),
    ...(menuItem.customization?.noodleTypes ?? []),
    ...(menuItem.customization?.spicyLevels ?? []),
    ...(menuItem.customization?.combo?.option ? [menuItem.customization.combo.option] : []),
    ...(menuItem.customization?.combo?.groups.flatMap((group) => group.options) ?? []),
    ...(menuItem.customization?.combo?.eggs ?? []),
    ...(menuItem.customization?.combo?.sides ?? []),
    ...(menuItem.customization?.combo?.sideRemoveOptions ?? []),
    ...(menuItem.customization?.addOns ?? []),
    ...(menuItem.customization?.removeOptions ?? []),
  ]
  return new Map(options.map((option) => [option.id, option]))
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

function observeFrontdeskRealtime(event: FrontdeskRealtimeLifecycleEvent, onLifecycle?: (event: FrontdeskRealtimeLifecycleEvent) => void) {
  try {
    onLifecycle?.(event)
  } catch {
    // Lifecycle observers are diagnostics only and must not stop realtime delivery.
  }
  if (networkDiagnosticsDisplayEnabled && typeof console !== 'undefined') {
    const details: Record<string, number | string> = {
      store_id: event.storeId,
      phase: event.phase,
      transport: event.transport,
    }
    if (event.closeCode != null) {
      details.close_code = event.closeCode
    }
    console.info('[frontdesk-realtime]', details)
  }
}

function frontdeskRealtimeEndpoint() {
  if (typeof window === 'undefined') {
    return '/ws'
  }
  return new URL('/ws', window.location.href).toString()
}

export function subscribeToFrontdeskOrders(
  storeId: number,
  onMessage: (message: RealtimeUpdateMessage) => void,
  options: FrontdeskRealtimeSubscriptionOptions = {},
) {
  let disposed = false
  let client: Client | null = null
  let unsubscribeTopic: (() => void) | null = null
  const lifecycle = (phase: FrontdeskRealtimeLifecyclePhase, closeCode?: number) => {
    if (disposed && phase !== 'CANCELLED') return
    observeFrontdeskRealtime({ storeId, phase, transport: 'sockjs-stomp', closeCode }, options.onLifecycle)
  }

  lifecycle('BOOTSTRAP_STARTED')
  try {
    client = new Client({
      webSocketFactory: () => {
        lifecycle('SOCKET_FACTORY_CALLED')
        return new SockJS(frontdeskRealtimeEndpoint())
      },
      reconnectDelay: 3_000,
      connectionTimeout: 10_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
    })

    lifecycle('CLIENT_CREATED')
    client.onConnect = () => {
      if (disposed || !client) return
      lifecycle('CONNECTED')
      try {
        unsubscribeTopic?.()
        const subscription = client.subscribe(`/topic/stores/${storeId}/frontdesk/orders`, (frame) => {
          if (disposed) return
          try {
            const message = JSON.parse(frame.body) as RealtimeUpdateMessage
            if (message.store_id === storeId) {
              onMessage(message)
            }
          } catch {
            // Ignore malformed messages without exposing frame contents in diagnostics.
          }
        })
        unsubscribeTopic = () => subscription.unsubscribe()
      } catch {
        lifecycle('STOMP_ERROR')
      }
    }
    client.onStompError = () => {
      lifecycle('STOMP_ERROR')
    }
    client.onWebSocketError = () => {
      lifecycle('SOCKET_ERROR')
    }
    client.onWebSocketClose = (event) => {
      lifecycle('SOCKET_CLOSED', Number.isInteger(event.code) ? event.code : undefined)
    }
    client.onDisconnect = () => {
      lifecycle('DISCONNECTED')
    }
    client.activate()
  } catch {
    lifecycle('BOOTSTRAP_ERROR')
  }

  return () => {
    if (disposed) return
    disposed = true
    unsubscribeTopic?.()
    lifecycle('CANCELLED')
    if (client) {
      void client.deactivate()
    }
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

export async function fetchOrderPrintJobs(orderId: number, options: { signal?: AbortSignal } = {}) {
  return request<PrintJobRecord[]>(`/api/v1/orders/${orderId}/print-jobs`, {
    signal: options.signal,
  })
}

const DEFAULT_PRINT_JOB_COORDINATOR_DELAYS_MS = [1_000, 3_000, 6_000, 10_000] as const
const activePrintJobCoordinators = new Map<string, OrderPrintJobCoordinator>()

function printJobCoordinatorKey(storeId: number, orderId: number) {
  return `${storeId}:${orderId}`
}

function relevantPrintJobs(jobs: PrintJobRecord[], expectedModules: ReadonlySet<string>, updateBatchId?: number | null) {
  return jobs.filter((job) => {
    if (!expectedModules.has(job.module_code)) {
      return false
    }
    return updateBatchId != null
      ? job.order_update_batch_id === updateBatchId
      : job.order_update_batch_id == null
  })
}

const PRINT_ATTENTION_STATUSES = new Set(['FAILED', 'CANCELLED'])

function printJobNeedsAttention(job: PrintJobRecord) {
  return PRINT_ATTENTION_STATUSES.has(job.status)
}

export function isTerminalPrintJobPollingError(error: unknown) {
  if (!(error instanceof ApiRequestError)) {
    return false
  }
  if (error.status === 403 || error.status === 409) {
    return true
  }
  const diagnostic = [error.code, error.message, error.userMessage]
    .filter(Boolean)
    .join(' ')
    .toUpperCase()
  return diagnostic.includes('CAPABILITY')
    || diagnostic.includes('MODULE_')
    || diagnostic.includes('PRINTING_DISABLED')
    || diagnostic.includes('PRINTING_FEATURE')
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

export function startOrderPrintJobCoordinator(options: OrderPrintJobCoordinatorOptions): OrderPrintJobCoordinator {
  const key = printJobCoordinatorKey(options.storeId, options.orderId)
  activePrintJobCoordinators.get(key)?.cancel()

  const controller = new AbortController()
  let cancelled = false
  const coordinator: OrderPrintJobCoordinator = {
    key,
    cancel: () => {
      if (cancelled) return
      cancelled = true
      controller.abort()
      if (activePrintJobCoordinators.get(key) === coordinator) {
        activePrintJobCoordinators.delete(key)
      }
    },
  }
  activePrintJobCoordinators.set(key, coordinator)

  const delays = options.delaysMs ?? DEFAULT_PRINT_JOB_COORDINATOR_DELAYS_MS
  let expectedModules: Set<string> | null = null
  void (async () => {
    try {
      for (const delayMs of delays) {
        await sleep(delayMs, controller.signal)
        if (cancelled) return

        try {
          if (expectedModules == null) {
            const printOptions = await fetchOrderPrintOptions(options.orderId, { signal: controller.signal })
            expectedModules = new Set(printOptions.filter((option) => option.available).map((option) => option.module_code))
            if (expectedModules.size === 0) return
          }
          const jobs = await fetchOrderPrintJobs(options.orderId, { signal: controller.signal })
          if (cancelled) return
          const relevantJobs = relevantPrintJobs(jobs, expectedModules, options.updateBatchId)
          const attentionJobs = relevantJobs.filter(printJobNeedsAttention)
          if (attentionJobs.length) {
            options.onAttention(attentionJobs)
            return
          }

          const healthyModules = new Set(
            relevantJobs
              .filter((job) => job.status === 'PRINTED' || (job.execution_mode === 'PAD_DIRECT' && job.status === 'PENDING'))
              .map((job) => job.module_code),
          )
          if ([...expectedModules].every((moduleCode) => healthyModules.has(moduleCode))) {
            return
          }
        } catch (error) {
          if (cancelled || isAbortError(error)) return
          if (isTerminalPrintJobPollingError(error)) {
            options.onUnavailable?.(error)
            return
          }
          // A transient status read failure should not block order submission.
        }
      }
    } finally {
      if (activePrintJobCoordinators.get(key) === coordinator) {
        activePrintJobCoordinators.delete(key)
      }
    }
  })().catch(() => {
    // Cancellation and observer failures must never become unhandled rejections.
  })

  return coordinator
}

export async function fetchOrderPrintOptions(orderId: number, options: { signal?: AbortSignal } = {}) {
  return request<OrderPrintOption[]>(`/api/v1/orders/${orderId}/print-options`, {
    signal: options.signal,
  })
}

export async function fetchTodayOrderHistory(storeId: number, limit = 100) {
  const params = new URLSearchParams({ store_id: String(storeId), limit: String(limit) })
  return request<BackendFrontdeskOrderBoardItem[]>(`/api/v1/frontdesk/orders/today?${params.toString()}`)
}
