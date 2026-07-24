import {
  listLocalDraftsForScope,
  reopenRejectedLocalDraft,
  saveLocalDraft,
  type LocalDraftScope,
  type LocalDraftRecord,
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

export function canReturnOfflineOrderToDraft(state: LocalDraftSubmitState) {
  return state === 'QUEUED'
    || state === 'CONFLICT'
    || state === 'FAILED_VALIDATION'
}

export async function returnOfflineOrderToDraft(
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
  if (record.state === 'SUBMITTING' || record.state === 'FAILED_RETRYABLE') {
    throw new Error('服务器是否已接单尚未确认，不能修改或取消。请先立即重试以确认原订单。')
  }
  if (!canReturnOfflineOrderToDraft(record.state)) return null

  const drafts = await listLocalDraftsForScope(scope)
  const draft = drafts.find((candidate) => candidate.clientOrderId === clientOrderId)
  if (!draft) throw new Error('本机草稿不存在，请先检查本机订单记录。')

  await saveOrderOutboxRecord({
    ...record,
    state: 'CANCELLED_LOCAL',
    nextRetryAt: null,
    lastErrorCode: null,
    lastErrorMessage: null,
  })
  const editableDraft: LocalDraftRecord = record.state === 'CONFLICT' || record.state === 'FAILED_VALIDATION'
    ? reopenRejectedLocalDraft(draft)
    : {
        ...draft,
        submitState: 'LOCAL_DRAFT',
        lastError: null,
        nextRetryAt: null,
      }
  return saveLocalDraft(editableDraft)
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
