import type { BackendOrderResponse } from '../types/ordering'
import {
  isServerTerminalLocalState,
  hasUnsubmittedLocalWork,
  publishLocalDraftUpdate,
  resolveLocalDraftWrite,
  type LocalDraftRecord,
  type LocalDraftScope,
  type LocalDraftSubmitState,
} from './localDrafts'
import {
  OFFLINE_STORES,
  openOfflineDatabase,
  requestResult,
  transactionComplete,
} from './offlineDatabase'
import {
  ORDER_OUTBOX_SCHEMA_VERSION,
  publishOrderOutboxUpdate,
  resolveOrderOutboxWrite,
  type OrderOutboxRecord,
} from './orderOutbox'

export type ServerOrderTerminalState = 'COMPLETED' | 'CANCELLED'
const PENDING_LOCAL_STATES = new Set(['QUEUED', 'SUBMITTING', 'CONFLICT', 'FAILED_RETRYABLE', 'FAILED_VALIDATION'])
const TERMINAL_WITH_LOCAL_WORK = 'Server order finished; local work retained for review / 服务器订单已结束，本地内容已保留，请检查'

export function terminalLocalStateForServerStatus(status: string | null | undefined): ServerOrderTerminalState | null {
  const normalized = status?.trim().toLowerCase()
  if (normalized === 'completed' || normalized === 'closed') return 'COMPLETED'
  if (normalized === 'cancelled' || normalized === 'canceled') return 'CANCELLED'
  return null
}

export function buildTerminalLocalDraft(
  record: LocalDraftRecord,
  terminalState: ServerOrderTerminalState,
  now = new Date(),
): LocalDraftRecord {
  if (record.submitState === 'CONFLICT' && record.lastError === TERMINAL_WITH_LOCAL_WORK
    && record.serverOrderSnapshot?.status === terminalState.toLowerCase()) return record
  const serverOrderSnapshot = record.serverOrderSnapshot
    ? { ...record.serverOrderSnapshot, status: terminalState.toLowerCase() } as BackendOrderResponse
    : null
  return {
    ...record,
    serverOrderSnapshot,
    items: hasUnsubmittedLocalWork(record) || PENDING_LOCAL_STATES.has(record.submitState) ? record.items : [],
    submitState: hasUnsubmittedLocalWork(record) || PENDING_LOCAL_STATES.has(record.submitState) ? 'CONFLICT' : terminalState,
    lastError: hasUnsubmittedLocalWork(record) || PENDING_LOCAL_STATES.has(record.submitState) ? TERMINAL_WITH_LOCAL_WORK : null,
    nextRetryAt: null,
    updatedAt: now.toISOString(),
  }
}

export function buildTerminalOrderOutbox(
  record: OrderOutboxRecord,
  terminalState: ServerOrderTerminalState,
  now = new Date(),
): OrderOutboxRecord {
  if (record.state === 'CONFLICT' && record.lastErrorCode === 'SERVER_ORDER_TERMINAL_WITH_LOCAL_WORK') return record
  return {
    ...record,
    state: PENDING_LOCAL_STATES.has(record.state) ? 'CONFLICT' : terminalState,
    nextRetryAt: null,
    lastErrorCode: PENDING_LOCAL_STATES.has(record.state) ? 'SERVER_ORDER_TERMINAL_WITH_LOCAL_WORK' : null,
    lastErrorMessage: PENDING_LOCAL_STATES.has(record.state) ? TERMINAL_WITH_LOCAL_WORK : null,
    updatedAt: now.toISOString(),
  }
}

function matchesScope(record: LocalDraftScope, scope: LocalDraftScope) {
  return record.accountId === scope.accountId
    && record.organizationId === scope.organizationId
    && record.storeId === scope.storeId
}

export async function finalizeOfflineOrderRecords(
  scope: LocalDraftScope,
  serverOrderId: number,
  serverStatus: string,
) {
  const terminalState = terminalLocalStateForServerStatus(serverStatus)
  if (!terminalState) return { drafts: 0, outbox: 0 }

  const database = await openOfflineDatabase()
  const transaction = database.transaction(
    [OFFLINE_STORES.localDrafts, OFFLINE_STORES.orderOutbox],
    'readwrite',
  )
  const completed = transactionComplete(transaction)
  const draftStore = transaction.objectStore(OFFLINE_STORES.localDrafts)
  const outboxStore = transaction.objectStore(OFFLINE_STORES.orderOutbox)
  const draftRequest = requestResult<LocalDraftRecord[]>(draftStore.getAll())
  const outboxRequest = requestResult<OrderOutboxRecord[]>(outboxStore.index('byAccountId').getAll(scope.accountId))
  const [drafts, outboxRecords] = await Promise.all([draftRequest, outboxRequest])
  const changedDrafts: LocalDraftRecord[] = []
  const changedOutbox: OrderOutboxRecord[] = []

  drafts
    .filter((record) => matchesScope(record, scope) && record.serverOrderId === serverOrderId)
    .forEach((record) => {
      const incoming = buildTerminalLocalDraft(record, terminalState)
      if (incoming === record) return
      const resolved = resolveLocalDraftWrite(record, incoming)
      if (resolved === incoming) {
        draftStore.put(incoming)
        changedDrafts.push(incoming)
      }
    })

  outboxRecords
    .filter((record) => (
      matchesScope(record, scope)
      && record.schemaVersion === ORDER_OUTBOX_SCHEMA_VERSION
      && record.serverOrderId === serverOrderId
    ))
    .forEach((record) => {
      const incoming = buildTerminalOrderOutbox(record, terminalState)
      if (incoming === record) return
      const resolved = resolveOrderOutboxWrite(record, incoming)
      if (resolved === incoming) {
        outboxStore.put(incoming)
        changedOutbox.push(incoming)
      }
    })

  await completed
  if (changedDrafts.length) publishLocalDraftUpdate()
  changedOutbox.forEach(publishOrderOutboxUpdate)
  return { drafts: changedDrafts.length, outbox: changedOutbox.length }
}

export function collectServerOrderIds(
  drafts: LocalDraftRecord[],
  outboxRecords: OrderOutboxRecord[],
) {
  const ids = new Set<number>()
  drafts.forEach((record) => {
    if (record.serverOrderId != null && !isServerTerminalLocalState(record.submitState)) {
      ids.add(record.serverOrderId)
    }
  })
  outboxRecords.forEach((record) => {
    if (record.serverOrderId != null && !isServerTerminalLocalState(record.state as LocalDraftSubmitState)) {
      ids.add(record.serverOrderId)
    }
  })
  return [...ids]
}
