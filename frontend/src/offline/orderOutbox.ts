import type { IdempotentOrderSubmitPayload } from '../services/orderService'
import type { LocalDraftRecord, LocalDraftSubmitState } from './localDrafts'
import {
  OFFLINE_STORES,
  openOfflineDatabase,
  requestResult,
  transactionComplete,
} from './offlineDatabase'
import { stablePayloadHash } from './offlineHash'

export const ORDER_OUTBOX_SCHEMA_VERSION = 1
export const ORDER_OUTBOX_UPDATED_EVENT = 'restaurant-order-outbox-updated'

export const ORDER_OUTBOX_RETRY_DELAYS_MS = [2_000, 5_000, 15_000, 30_000, 60_000] as const
const LATER_RETRY_DELAY_MS = 5 * 60_000

export interface OrderOutboxRecord {
  key: string
  localDraftId: string
  clientOrderId: string
  accountId: number
  organizationId: number
  storeId: number
  frozenPayload: IdempotentOrderSubmitPayload
  payloadHash: string
  menuRevision: number
  state: LocalDraftSubmitState
  attemptCount: number
  lastAttemptAt: string | null
  nextRetryAt: string | null
  serverOrderId: number | null
  serverReplayed?: boolean
  lastErrorCode: string | null
  lastErrorMessage: string | null
  createdAt: string
  updatedAt: string
  schemaVersion: number
}

const PROCESSABLE_STATES = new Set<LocalDraftSubmitState>([
  'QUEUED',
  'SUBMITTING',
  'FAILED_RETRYABLE',
])

export function orderOutboxKey(accountId: number, organizationId: number, storeId: number, clientOrderId: string) {
  return `account:${accountId}|organization:${organizationId}|store:${storeId}|client:${clientOrderId}`
}

export function createOrderOutboxRecord(
  draft: LocalDraftRecord,
  frozenPayload: IdempotentOrderSubmitPayload,
  now = new Date(),
): OrderOutboxRecord {
  const timestamp = now.toISOString()
  return {
    key: orderOutboxKey(draft.accountId, draft.organizationId, draft.storeId, draft.clientOrderId),
    localDraftId: draft.localDraftId,
    clientOrderId: draft.clientOrderId,
    accountId: draft.accountId,
    organizationId: draft.organizationId,
    storeId: draft.storeId,
    frozenPayload,
    payloadHash: stablePayloadHash(frozenPayload),
    menuRevision: draft.menuRevision,
    state: 'QUEUED',
    attemptCount: 0,
    lastAttemptAt: null,
    nextRetryAt: timestamp,
    serverOrderId: null,
    lastErrorCode: null,
    lastErrorMessage: null,
    createdAt: timestamp,
    updatedAt: timestamp,
    schemaVersion: ORDER_OUTBOX_SCHEMA_VERSION,
  }
}

function normalizeRecord(record: OrderOutboxRecord): OrderOutboxRecord {
  const expectedKey = orderOutboxKey(
    record.accountId,
    record.organizationId,
    record.storeId,
    record.clientOrderId,
  )
  if (record.key !== expectedKey || record.frozenPayload.store_id !== record.storeId) {
    throw new Error('ORDER_OUTBOX_SCOPE_MISMATCH')
  }
  if (record.frozenPayload.client_order_id !== record.clientOrderId
    || record.frozenPayload.idempotency_key !== record.clientOrderId) {
    throw new Error('ORDER_OUTBOX_IDEMPOTENCY_KEY_MISMATCH')
  }
  return {
    ...record,
    updatedAt: new Date().toISOString(),
    schemaVersion: ORDER_OUTBOX_SCHEMA_VERSION,
  }
}

export function resolveOrderOutboxWrite(
  current: OrderOutboxRecord | null,
  incoming: OrderOutboxRecord,
) {
  if (!current) return incoming
  if (current.key !== incoming.key) throw new Error('ORDER_OUTBOX_KEY_MISMATCH')
  if (incoming.state === 'SUBMITTED') return incoming
  if (current.state === 'SUBMITTED') return current
  if (incoming.attemptCount < current.attemptCount) return current
  if (current.state === 'SUBMITTING' && incoming.state === 'QUEUED') return current
  if (
    (current.state === 'CONFLICT' || current.state === 'FAILED_VALIDATION')
    && incoming.state !== 'CANCELLED_LOCAL'
    && incoming.attemptCount <= current.attemptCount
  ) {
    return current
  }
  return incoming
}

export async function saveOrderOutboxRecord(record: OrderOutboxRecord) {
  const incoming = normalizeRecord(record)
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.orderOutbox, 'readwrite')
  const completed = transactionComplete(transaction)
  const records = transaction.objectStore(OFFLINE_STORES.orderOutbox)
  const current = await requestResult<OrderOutboxRecord | undefined>(records.get(incoming.key))
  const resolved = resolveOrderOutboxWrite(current ?? null, incoming)
  if (resolved === incoming) {
    records.put(incoming)
  }
  await completed
  if (resolved === incoming) publishOrderOutboxUpdate(incoming)
  return resolved
}

export async function readOrderOutboxRecord(
  accountId: number,
  organizationId: number,
  storeId: number,
  clientOrderId: string,
) {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.orderOutbox, 'readonly')
  const completed = transactionComplete(transaction)
  const record = await requestResult<OrderOutboxRecord | undefined>(
    transaction.objectStore(OFFLINE_STORES.orderOutbox).get(
      orderOutboxKey(accountId, organizationId, storeId, clientOrderId),
    ),
  )
  await completed
  if (!record || record.schemaVersion !== ORDER_OUTBOX_SCHEMA_VERSION) return null
  return record
}

export async function listOrderOutboxForAccount(accountId: number) {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.orderOutbox, 'readonly')
  const completed = transactionComplete(transaction)
  const records = await requestResult<OrderOutboxRecord[]>(
    transaction.objectStore(OFFLINE_STORES.orderOutbox).index('byAccountId').getAll(accountId),
  )
  await completed
  return records.filter((record) => record.schemaVersion === ORDER_OUTBOX_SCHEMA_VERSION)
}

export function isOrderOutboxRecordDue(record: OrderOutboxRecord, nowMs = Date.now()) {
  if (!PROCESSABLE_STATES.has(record.state)) return false
  if (record.state === 'SUBMITTING') return true
  if (!record.nextRetryAt) return true
  const dueAt = new Date(record.nextRetryAt).getTime()
  return !Number.isFinite(dueAt) || dueAt <= nowMs
}

export async function listDueOrderOutboxRecords(accountId: number, nowMs = Date.now()) {
  return (await listOrderOutboxForAccount(accountId))
    .filter((record) => isOrderOutboxRecordDue(record, nowMs))
    .sort((left, right) => left.createdAt.localeCompare(right.createdAt))
}

export function retryDelayMs(attemptCount: number) {
  if (attemptCount <= 0) return ORDER_OUTBOX_RETRY_DELAYS_MS[0]
  return ORDER_OUTBOX_RETRY_DELAYS_MS[attemptCount - 1] ?? LATER_RETRY_DELAY_MS
}

export function nextRetryAt(attemptCount: number, nowMs = Date.now()) {
  return new Date(nowMs + retryDelayMs(attemptCount)).toISOString()
}

export function beginOrderOutboxAttempt(record: OrderOutboxRecord, now = new Date()): OrderOutboxRecord {
  return {
    ...record,
    state: 'SUBMITTING',
    attemptCount: record.attemptCount + 1,
    lastAttemptAt: now.toISOString(),
    nextRetryAt: null,
    lastErrorCode: null,
    lastErrorMessage: null,
  }
}

export function completeOrderOutboxAttempt(
  record: OrderOutboxRecord,
  serverOrderId: number,
  serverReplayed = false,
): OrderOutboxRecord {
  return {
    ...record,
    state: 'SUBMITTED',
    serverOrderId,
    serverReplayed,
    nextRetryAt: null,
    lastErrorCode: null,
    lastErrorMessage: null,
  }
}

export function failOrderOutboxAttempt(
  record: OrderOutboxRecord,
  status: number,
  errorCode: string,
  errorMessage: string,
  nowMs = Date.now(),
): OrderOutboxRecord {
  const state = classifySubmissionFailure(status, errorCode)
  return {
    ...record,
    state,
    nextRetryAt: state === 'FAILED_RETRYABLE' ? nextRetryAt(record.attemptCount, nowMs) : null,
    lastErrorCode: errorCode,
    lastErrorMessage: errorMessage,
  }
}

export function classifySubmissionFailure(status: number, errorCode?: string | null) {
  if (status === 0 || status >= 500 || errorCode === 'REQUEST_TIMEOUT' || errorCode === 'NETWORK_ERROR') {
    return 'FAILED_RETRYABLE' as const
  }
  if (status === 400) return 'FAILED_VALIDATION' as const
  return 'CONFLICT' as const
}

export function publishOrderOutboxUpdate(record: OrderOutboxRecord) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent<OrderOutboxRecord>(ORDER_OUTBOX_UPDATED_EVENT, { detail: record }))
}
