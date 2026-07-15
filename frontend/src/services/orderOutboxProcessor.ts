import { ApiRequestError, getApiUserMessage } from './apiClient'
import { submitIdempotentOrder } from './orderService'
import type { BackendOrderResponse } from '../types/ordering'
import {
  getConnectionSnapshot,
  subscribeConnectionStatus,
  type ConnectionState,
} from './networkStatus'
import {
  beginOrderOutboxAttempt,
  completeOrderOutboxAttempt,
  failOrderOutboxAttempt,
  listDueOrderOutboxRecords,
  listOrderOutboxForAccount,
  readOrderOutboxRecord,
  saveOrderOutboxRecord,
  type OrderOutboxRecord,
} from '../offline/orderOutbox'

const IDLE_CHECK_MS = 30_000
const MAX_BATCH_SIZE = 10
type OutboxProcessResult = {
  record: OrderOutboxRecord | null
  order: BackendOrderResponse | null
}

const inFlightRequests = new Map<string, Promise<OutboxProcessResult>>()

let activeAccountId: number | null = null
let activeGeneration = 0
let scheduledTimer: number | null = null
let processorRunning = false
let pendingKick = false

function browserCanSubmit() {
  return typeof navigator === 'undefined' || navigator.onLine
}

function safeErrorMessage(error: unknown) {
  return getApiUserMessage(error, '订单提交失败，将在网络恢复后重试。').slice(0, 500)
}

function submissionTrace(record: OrderOutboxRecord) {
  return {
    localDraftId: record.localDraftId,
    outboxKey: record.key.slice(-16),
    idempotencyKey: record.clientOrderId.slice(-12),
    storeId: record.storeId,
    attempt: record.attemptCount,
  }
}

async function executeOrderOutboxRecord(record: OrderOutboxRecord): Promise<OutboxProcessResult> {
  if (!browserCanSubmit()) {
    return { record, order: null }
  }

  const current = await readOrderOutboxRecord(
    record.accountId,
    record.organizationId,
    record.storeId,
    record.clientOrderId,
  )
  if (!current || current.state === 'SUBMITTED' || current.state === 'CONFLICT'
    || current.state === 'FAILED_VALIDATION' || current.state === 'CANCELLED_LOCAL') {
    return { record: current, order: null }
  }

  const attemptStartedAt = new Date().toISOString()
  let submitting = await saveOrderOutboxRecord(beginOrderOutboxAttempt(current, new Date(attemptStartedAt)))
  console.info('[order-outbox] submission started', submissionTrace(submitting))
  try {
    const result = await submitIdempotentOrder(submitting.frozenPayload)
    submitting = await saveOrderOutboxRecord(
      completeOrderOutboxAttempt(submitting, result.order_id, result.replayed),
    )
    console.info('[order-outbox] submission confirmed', {
      ...submissionTrace(submitting),
      orderId: result.order_id,
      replayed: result.replayed,
    })
    return { record: submitting, order: result.order as BackendOrderResponse }
  } catch (error) {
    const status = error instanceof ApiRequestError ? error.status : 0
    const errorCode = error instanceof ApiRequestError ? (error.code ?? `HTTP_${error.status}`) : 'NETWORK_ERROR'
    submitting = await saveOrderOutboxRecord(failOrderOutboxAttempt(
      submitting,
      status,
      errorCode,
      safeErrorMessage(error),
    ))
    console.warn('[order-outbox] submission not confirmed', {
      ...submissionTrace(submitting),
      status,
      errorCode,
      resolution: submitting.state,
    })
    return { record: submitting, order: null }
  }
}

export function processOrderOutboxRecord(record: OrderOutboxRecord): Promise<OutboxProcessResult> {
  const existing = inFlightRequests.get(record.key)
  if (existing) return existing

  const request = executeOrderOutboxRecord(record).finally(() => {
    if (inFlightRequests.get(record.key) === request) inFlightRequests.delete(record.key)
  })
  inFlightRequests.set(record.key, request)
  return request
}

function clearScheduledTimer() {
  if (scheduledTimer != null) {
    window.clearTimeout(scheduledTimer)
    scheduledTimer = null
  }
}

function scheduleProcessor(delayMs: number, generation = activeGeneration) {
  if (activeAccountId == null || generation !== activeGeneration) return
  clearScheduledTimer()
  scheduledTimer = window.setTimeout(() => {
    scheduledTimer = null
    void runProcessor(generation)
  }, Math.max(0, delayMs))
}

async function nextProcessorDelay(accountId: number) {
  const records = (await listOrderOutboxForAccount(accountId)).filter((record) => (
    !inFlightRequests.has(record.key)
    && (record.state === 'QUEUED'
      || record.state === 'SUBMITTING'
      || record.state === 'FAILED_RETRYABLE')
  ))
  if (!records.length) return IDLE_CHECK_MS
  const now = Date.now()
  const earliest = records.reduce((minimum, record) => {
    if (record.state === 'SUBMITTING' || !record.nextRetryAt) return now
    const dueAt = new Date(record.nextRetryAt).getTime()
    return Math.min(minimum, Number.isFinite(dueAt) ? dueAt : now)
  }, Number.POSITIVE_INFINITY)
  return Math.min(IDLE_CHECK_MS, Math.max(250, earliest - now))
}

async function runProcessor(generation: number) {
  const accountId = activeAccountId
  if (accountId == null || generation !== activeGeneration) return
  if (processorRunning) {
    pendingKick = true
    scheduleProcessor(250, generation)
    return
  }
  if (!browserCanSubmit() || document.visibilityState === 'hidden') {
    scheduleProcessor(IDLE_CHECK_MS, generation)
    return
  }

  processorRunning = true
  try {
    const records = (await listDueOrderOutboxRecords(accountId))
      .filter((record) => !inFlightRequests.has(record.key))
      .slice(0, MAX_BATCH_SIZE)
    for (const record of records) {
      if (generation !== activeGeneration || !browserCanSubmit()) break
      await processOrderOutboxRecord(record)
    }
  } catch (error) {
    console.error('[order-outbox] processor failed', error)
  } finally {
    processorRunning = false
    if (generation !== activeGeneration) {
      if (activeAccountId != null) scheduleProcessor(0, activeGeneration)
    } else if (pendingKick) {
      pendingKick = false
      scheduleProcessor(0, generation)
    } else {
      try {
        scheduleProcessor(await nextProcessorDelay(accountId), generation)
      } catch {
        scheduleProcessor(IDLE_CHECK_MS, generation)
      }
    }
  }
}

export function kickOrderOutboxProcessor() {
  if (activeAccountId == null) return
  if (processorRunning) {
    pendingKick = true
    return
  }
  scheduleProcessor(0)
}

function isUnavailable(state: ConnectionState) {
  return state === 'BROWSER_OFFLINE' || state === 'BACKEND_UNREACHABLE'
}

export function startOrderOutboxProcessor(accountId: number) {
  activeGeneration += 1
  const generation = activeGeneration
  activeAccountId = accountId
  pendingKick = false
  let previousConnectionState = getConnectionSnapshot().state

  const handleOnline = () => kickOrderOutboxProcessor()
  const handleVisibility = () => {
    if (document.visibilityState === 'visible') kickOrderOutboxProcessor()
  }
  const unsubscribeConnection = subscribeConnectionStatus(() => {
    const nextState = getConnectionSnapshot().state
    if (isUnavailable(previousConnectionState) && !isUnavailable(nextState)) {
      kickOrderOutboxProcessor()
    }
    previousConnectionState = nextState
  })

  window.addEventListener('online', handleOnline)
  document.addEventListener('visibilitychange', handleVisibility)
  scheduleProcessor(0, generation)

  return () => {
    if (generation !== activeGeneration) return
    activeGeneration += 1
    activeAccountId = null
    pendingKick = false
    clearScheduledTimer()
    window.removeEventListener('online', handleOnline)
    document.removeEventListener('visibilitychange', handleVisibility)
    unsubscribeConnection()
  }
}
