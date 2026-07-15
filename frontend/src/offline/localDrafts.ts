import type { BackendOrderResponse, OrderLineItem } from '../types/ordering'
import { createClientId } from '../utils/randomId'
import {
  OFFLINE_STORES,
  openOfflineDatabase,
  requestResult,
  transactionComplete,
} from './offlineDatabase'
import { stablePayloadHash } from './offlineHash'

export const LOCAL_DRAFT_SCHEMA_VERSION = 1
export const LOCAL_DRAFT_UPDATED_EVENT = 'restaurant-local-draft-updated'
export const NON_EMPTY_DRAFT_RETENTION_MS = 7 * 24 * 60 * 60 * 1000
export const EMPTY_DRAFT_RETENTION_MS = 24 * 60 * 60 * 1000

export type LocalDraftMode = 'LOCAL_NEW_ORDER' | 'SERVER_ORDER_UPDATE'
export type LocalDraftSubmitState =
  | 'LOCAL_DRAFT'
  | 'QUEUED'
  | 'SUBMITTING'
  | 'SUBMITTED'
  | 'FAILED_RETRYABLE'
  | 'FAILED_VALIDATION'
  | 'CONFLICT'
  | 'CANCELLED_LOCAL'
  | 'COMPLETED'
  | 'CANCELLED'

export interface LocalDraftScope {
  accountId: number
  organizationId: number
  storeId: number
}

export interface LocalDraftContext {
  orderType: 'dine_in' | 'pickup'
  slotLabel: string
  tableLabel: string
  tableNo: string | null
  pickupNo: string | null
}

export interface LocalDraftRecord extends LocalDraftScope {
  key: string
  localDraftId: string
  clientOrderId: string
  contextKey: string
  context: LocalDraftContext
  mode: LocalDraftMode
  serverOrderId: number | null
  serverOrderSnapshot: BackendOrderResponse | null
  items: OrderLineItem[]
  menuRevision: number
  createdAt: string
  updatedAt: string
  submitState: LocalDraftSubmitState
  payloadHash: string
  lastError: string | null
  nextRetryAt: string | null
  schemaVersion: number
}

const PROTECTED_STATES = new Set<LocalDraftSubmitState>([
  'QUEUED',
  'SUBMITTING',
  'FAILED_RETRYABLE',
  'FAILED_VALIDATION',
  'CONFLICT',
])
const SERVER_TERMINAL_STATES = new Set<LocalDraftSubmitState>(['COMPLETED', 'CANCELLED'])
const TERMINAL_STATES = new Set<LocalDraftSubmitState>([
  'CANCELLED_LOCAL',
  ...SERVER_TERMINAL_STATES,
])

export function isServerTerminalLocalState(state: LocalDraftSubmitState) {
  return SERVER_TERMINAL_STATES.has(state)
}

export function buildDraftContextKey(context: LocalDraftContext) {
  return context.orderType === 'pickup'
    ? `takeout:${context.pickupNo ?? context.slotLabel}`
    : `table:${context.tableNo ?? context.slotLabel}`
}

export function localDraftKey(scope: LocalDraftScope, contextKey: string) {
  return `account:${scope.accountId}|organization:${scope.organizationId}|store:${scope.storeId}|${contextKey}`
}

export function localDraftPayload(record: Pick<
  LocalDraftRecord,
  'clientOrderId' | 'organizationId' | 'storeId' | 'context' | 'items' | 'menuRevision' | 'mode' | 'serverOrderId'
>) {
  return {
    clientOrderId: record.clientOrderId,
    organizationId: record.organizationId,
    storeId: record.storeId,
    context: record.context,
    items: record.items,
    menuRevision: record.menuRevision,
    mode: record.mode,
    serverOrderId: record.serverOrderId,
  }
}

export function withDraftPayloadHash(record: LocalDraftRecord): LocalDraftRecord {
  return {
    ...record,
    payloadHash: stablePayloadHash(localDraftPayload(record)),
  }
}

export function createLocalDraftRecord(
  scope: LocalDraftScope,
  context: LocalDraftContext,
  menuRevision: number,
  now = new Date(),
): LocalDraftRecord {
  const contextKey = buildDraftContextKey(context)
  const timestamp = now.toISOString()
  return withDraftPayloadHash({
    ...scope,
    key: localDraftKey(scope, contextKey),
    localDraftId: createClientId('local-draft'),
    clientOrderId: createClientId('client-order'),
    contextKey,
    context,
    mode: 'LOCAL_NEW_ORDER',
    serverOrderId: null,
    serverOrderSnapshot: null,
    items: [],
    menuRevision,
    createdAt: timestamp,
    updatedAt: timestamp,
    submitState: 'LOCAL_DRAFT',
    payloadHash: '',
    lastError: null,
    nextRetryAt: null,
    schemaVersion: LOCAL_DRAFT_SCHEMA_VERSION,
  })
}

export function resolveLocalDraftForOpen(
  scope: LocalDraftScope,
  context: LocalDraftContext,
  menuRevision: number,
  existing: LocalDraftRecord | null,
  relatedOutboxState: LocalDraftSubmitState | null,
  now = new Date(),
) {
  if (!existing) {
    return createLocalDraftRecord(scope, context, menuRevision, now)
  }

  // A confirmed server terminal state wins over stale queued/submitting snapshots.
  if (isServerTerminalLocalState(existing.submitState)
    || (relatedOutboxState != null && isServerTerminalLocalState(relatedOutboxState))) {
    return createLocalDraftRecord(scope, context, menuRevision, now)
  }

  // The outbox is authoritative while non-terminal work is queued or needs operator action.
  if (relatedOutboxState && PROTECTED_STATES.has(relatedOutboxState)) {
    return existing
  }

  const lifecycleFinished = TERMINAL_STATES.has(relatedOutboxState ?? existing.submitState)
    || TERMINAL_STATES.has(existing.submitState)
  if (!lifecycleFinished) {
    return existing
  }

  return createLocalDraftRecord(scope, context, menuRevision, now)
}

export function resolveLocalDraftWrite(
  current: LocalDraftRecord | null,
  incoming: LocalDraftRecord,
) {
  if (!current) return incoming
  if (current.key !== incoming.key) throw new Error('LOCAL_DRAFT_KEY_MISMATCH')
  if (current.clientOrderId !== incoming.clientOrderId) return incoming
  if (isServerTerminalLocalState(incoming.submitState)) return incoming
  if (isServerTerminalLocalState(current.submitState)) return current
  if (incoming.submitState === 'SUBMITTED' || incoming.submitState === 'CANCELLED_LOCAL') return incoming
  if (current.submitState === 'SUBMITTED' || current.submitState === 'CANCELLED_LOCAL') return current
  return incoming
}

export function reopenRejectedLocalDraft(record: LocalDraftRecord, now = new Date()) {
  const next = createLocalDraftRecord(
    {
      accountId: record.accountId,
      organizationId: record.organizationId,
      storeId: record.storeId,
    },
    record.context,
    record.menuRevision,
    now,
  )
  return withDraftPayloadHash({
    ...next,
    items: record.items,
  })
}

function matchesScope(record: LocalDraftRecord, scope: LocalDraftScope, contextKey: string) {
  return record.accountId === scope.accountId
    && record.organizationId === scope.organizationId
    && record.storeId === scope.storeId
    && record.contextKey === contextKey
    && record.schemaVersion === LOCAL_DRAFT_SCHEMA_VERSION
}

export async function readLocalDraft(scope: LocalDraftScope, contextKey: string) {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.localDrafts, 'readonly')
  const completed = transactionComplete(transaction)
  const record = await requestResult<LocalDraftRecord | undefined>(
    transaction.objectStore(OFFLINE_STORES.localDrafts).get(localDraftKey(scope, contextKey)),
  )
  await completed
  if (!record) return null
  if (!matchesScope(record, scope, contextKey)) {
    await deleteLocalDraft(scope, contextKey)
    return null
  }
  return record
}

export async function saveLocalDraft(record: LocalDraftRecord) {
  const normalized = withDraftPayloadHash({
    ...record,
    updatedAt: new Date().toISOString(),
    schemaVersion: LOCAL_DRAFT_SCHEMA_VERSION,
  })
  const expectedKey = localDraftKey(normalized, normalized.contextKey)
  if (normalized.key !== expectedKey) {
    throw new Error('LOCAL_DRAFT_SCOPE_MISMATCH')
  }
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.localDrafts, 'readwrite')
  const completed = transactionComplete(transaction)
  const drafts = transaction.objectStore(OFFLINE_STORES.localDrafts)
  const current = await requestResult<LocalDraftRecord | undefined>(drafts.get(expectedKey))
  const resolved = resolveLocalDraftWrite(current ?? null, normalized)
  if (resolved === normalized) drafts.put(normalized)
  await completed
  if (resolved === normalized) publishLocalDraftUpdate()
  return resolved
}

export async function saveLocalDraftIfClientMatches(
  record: LocalDraftRecord,
  expectedClientOrderId: string,
) {
  const normalized = withDraftPayloadHash({
    ...record,
    updatedAt: new Date().toISOString(),
    schemaVersion: LOCAL_DRAFT_SCHEMA_VERSION,
  })
  const expectedKey = localDraftKey(normalized, normalized.contextKey)
  if (normalized.key !== expectedKey || normalized.clientOrderId !== expectedClientOrderId) {
    throw new Error('LOCAL_DRAFT_SCOPE_MISMATCH')
  }
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.localDrafts, 'readwrite')
  const completed = transactionComplete(transaction)
  const drafts = transaction.objectStore(OFFLINE_STORES.localDrafts)
  const current = await requestResult<LocalDraftRecord | undefined>(drafts.get(expectedKey))
  const shouldSave = Boolean(current && current.clientOrderId === expectedClientOrderId)
  const resolved = shouldSave ? resolveLocalDraftWrite(current ?? null, normalized) : null
  if (resolved === normalized) drafts.put(normalized)
  await completed
  if (resolved === normalized) publishLocalDraftUpdate()
  return resolved
}

export async function listLocalDraftsForScope(scope: LocalDraftScope) {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.localDrafts, 'readonly')
  const completed = transactionComplete(transaction)
  const records = await requestResult<LocalDraftRecord[]>(
    transaction.objectStore(OFFLINE_STORES.localDrafts).getAll(),
  )
  await completed
  return records.filter((record) => (
    record.accountId === scope.accountId
    && record.organizationId === scope.organizationId
    && record.storeId === scope.storeId
    && record.schemaVersion === LOCAL_DRAFT_SCHEMA_VERSION
  ))
}

export async function deleteLocalDraft(scope: LocalDraftScope, contextKey: string) {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.localDrafts, 'readwrite')
  const completed = transactionComplete(transaction)
  transaction.objectStore(OFFLINE_STORES.localDrafts).delete(localDraftKey(scope, contextKey))
  await completed
  publishLocalDraftUpdate()
}

export async function deleteLocalDraftIfClientMatches(
  scope: LocalDraftScope,
  contextKey: string,
  clientOrderId: string,
) {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.localDrafts, 'readwrite')
  const completed = transactionComplete(transaction)
  const drafts = transaction.objectStore(OFFLINE_STORES.localDrafts)
  const key = localDraftKey(scope, contextKey)
  const record = await requestResult<LocalDraftRecord | undefined>(drafts.get(key))
  const shouldDelete = Boolean(
    record
    && matchesScope(record, scope, contextKey)
    && record.clientOrderId === clientOrderId,
  )
  if (shouldDelete) {
    drafts.delete(key)
  }
  await completed
  if (shouldDelete) {
    publishLocalDraftUpdate()
  }
  return shouldDelete
}

export function publishLocalDraftUpdate() {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new Event(LOCAL_DRAFT_UPDATED_EVENT))
}

export function isLocalDraftExpired(record: LocalDraftRecord, nowMs = Date.now()) {
  if (PROTECTED_STATES.has(record.submitState)) return false
  const updatedAtMs = new Date(record.updatedAt).getTime()
  if (!Number.isFinite(updatedAtMs)) return true
  const retention = record.items.length > 0 ? NON_EMPTY_DRAFT_RETENTION_MS : EMPTY_DRAFT_RETENTION_MS
  return nowMs - updatedAtMs > retention
}

export async function cleanupExpiredLocalDrafts(nowMs = Date.now()) {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.localDrafts, 'readwrite')
  const completed = transactionComplete(transaction)
  const cursorRequest = transaction.objectStore(OFFLINE_STORES.localDrafts).openCursor()
  cursorRequest.onsuccess = () => {
    const cursor = cursorRequest.result
    if (!cursor) return
    if (isLocalDraftExpired(cursor.value as LocalDraftRecord, nowMs)) {
      cursor.delete()
    }
    cursor.continue()
  }
  await completed
}
