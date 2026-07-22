import {
  listLocalDraftsForScope,
  saveLocalDraft,
  type LocalDraftScope,
  type LocalDraftSubmitState,
} from './localDrafts'
import {
  readOrderOutboxRecord,
  saveOrderOutboxRecord,
  type OrderOutboxRecord,
} from './orderOutbox'
import { processOrderOutboxRecord } from '../services/orderOutboxProcessor'

export async function retryOfflineOrder(
  scope: LocalDraftScope,
  clientOrderId: string,
) {
  const record = await readOrderOutboxRecord(
    scope.accountId,
    scope.organizationId,
    scope.storeId,
    clientOrderId,
  )
  if (!record) return null
  if (record.state === 'SUBMITTING') {
    throw new Error('订单正在确认服务器结果，请等待完成后再操作。')
  }
  if (record.state !== 'QUEUED' && record.state !== 'FAILED_RETRYABLE') {
    return record
  }
  const queued = await saveOrderOutboxRecord({
    ...record,
    state: 'QUEUED',
    nextRetryAt: new Date().toISOString(),
    lastErrorCode: null,
    lastErrorMessage: null,
  })
  const result = await processOrderOutboxRecord(queued)
  return result.record ?? queued
}

export async function readOfflineOrderState(
  scope: LocalDraftScope,
  clientOrderId: string,
) {
  const record = await readOrderOutboxRecord(
    scope.accountId,
    scope.organizationId,
    scope.storeId,
    clientOrderId,
  )
  if (record) return record.state
  const drafts = await listLocalDraftsForScope(scope)
  return drafts.find((draft) => draft.clientOrderId === clientOrderId)?.submitState ?? null
}

export async function cancelOfflineOrder(
  scope: LocalDraftScope,
  clientOrderId: string,
) {
  const record = await readOrderOutboxRecord(
    scope.accountId,
    scope.organizationId,
    scope.storeId,
    clientOrderId,
  )
  if (record?.state === 'SUBMITTING') {
    throw new Error('订单正在确认服务器结果，暂时不能取消。')
  }
  if (record && !isLocalCancellationAllowed(record.state)) {
    throw new Error('该订单已经进入服务器处理流程，不能取消本机记录。')
  }
  if (record) {
    await saveOrderOutboxRecord({
      ...record,
      state: 'CANCELLED_LOCAL',
      nextRetryAt: null,
    })
  }

  const drafts = await listLocalDraftsForScope(scope)
  const draft = drafts.find((candidate) => candidate.clientOrderId === clientOrderId)
  if (draft && isLocalCancellationAllowed(draft.submitState)) {
    await saveLocalDraft({
      ...draft,
      submitState: 'CANCELLED_LOCAL',
      nextRetryAt: null,
      lastError: null,
    })
  }
}

function isLocalCancellationAllowed(state: LocalDraftSubmitState) {
  return state === 'LOCAL_DRAFT'
    || state === 'QUEUED'
    || state === 'FAILED_VALIDATION'
    || state === 'CONFLICT'
}

export function hasQueuedOutboxRecord(record: OrderOutboxRecord | null) {
  return record?.state === 'QUEUED' || record?.state === 'FAILED_RETRYABLE'
}

export async function readOfflineOrderOutbox(
  scope: LocalDraftScope,
  clientOrderId: string,
) {
  return readOrderOutboxRecord(scope.accountId, scope.organizationId, scope.storeId, clientOrderId)
}
