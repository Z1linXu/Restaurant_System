import { describe, expect, it } from 'vitest'
import type { LocalDraftRecord } from './localDrafts'
import {
  ORDER_OUTBOX_RETRY_DELAYS_MS,
  beginOrderOutboxAttempt,
  classifySubmissionFailure,
  completeOrderOutboxAttempt,
  createOrderOutboxRecord,
  failOrderOutboxAttempt,
  isOrderOutboxRecordDue,
  retryDelayMs,
  resolveOrderOutboxWrite,
} from './orderOutbox'
import type { IdempotentOrderSubmitPayload } from '../services/orderService'

const draft: LocalDraftRecord = {
  key: 'account:7|organization:9|store:3|table:T1',
  localDraftId: 'local-draft-1',
  clientOrderId: 'client-order-1',
  accountId: 7,
  organizationId: 9,
  storeId: 3,
  contextKey: 'table:T1',
  context: {
    orderType: 'dine_in',
    slotLabel: 'T1',
    tableLabel: 'T1',
    tableNo: 'T1',
    pickupNo: null,
  },
  mode: 'LOCAL_NEW_ORDER',
  serverOrderId: null,
  serverOrderSnapshot: null,
  items: [],
  menuRevision: 12,
  createdAt: '2026-07-13T10:00:00.000Z',
  updatedAt: '2026-07-13T10:00:00.000Z',
  submitState: 'LOCAL_DRAFT',
  payloadHash: 'draft-hash',
  lastError: null,
  nextRetryAt: null,
  schemaVersion: 1,
}

const payload: IdempotentOrderSubmitPayload = {
  client_order_id: 'client-order-1',
  idempotency_key: 'client-order-1',
  organization_id: 9,
  store_id: 3,
  server_order_id: null,
  order_type: 'dine_in',
  table_no: 'T1',
  pickup_no: null,
  menu_revision: 12,
  expected_subtotal_amount: 12.5,
  items: [{
    menu_item_id: 20,
    quantity: 1,
    combo_group_no: null,
    combo_role: 'standalone',
    notes: null,
    options: [{ option_id: 30, quantity: 1 }],
  }],
}

describe('order outbox state machine', () => {
  it('freezes one store/account-scoped payload with the stable client idempotency key', () => {
    const record = createOrderOutboxRecord(draft, payload, new Date('2026-07-13T10:00:00Z'))

    expect(record.key).toBe('account:7|organization:9|store:3|client:client-order-1')
    expect(record.clientOrderId).toBe('client-order-1')
    expect(record.frozenPayload).toEqual(payload)
    expect(record.state).toBe('QUEUED')
    expect(record.payloadHash).toMatch(/^fnv1a32:/)
  })

  it('persists SUBMITTING as immediately recoverable after an app restart', () => {
    const queued = createOrderOutboxRecord(draft, payload)
    const submitting = beginOrderOutboxAttempt(queued, new Date('2026-07-13T10:00:01Z'))

    expect(submitting.state).toBe('SUBMITTING')
    expect(submitting.attemptCount).toBe(1)
    expect(isOrderOutboxRecordDue(submitting, Date.parse('2026-07-13T10:00:02Z'))).toBe(true)
  })

  it('uses bounded retry backoff for timeout, network, and 5xx failures', () => {
    expect(ORDER_OUTBOX_RETRY_DELAYS_MS).toEqual([2_000, 5_000, 15_000, 30_000, 60_000])
    expect(retryDelayMs(1)).toBe(2_000)
    expect(retryDelayMs(5)).toBe(60_000)
    expect(retryDelayMs(6)).toBe(300_000)
    expect(classifySubmissionFailure(0, 'REQUEST_TIMEOUT')).toBe('FAILED_RETRYABLE')
    expect(classifySubmissionFailure(503, 'HTTP_503')).toBe('FAILED_RETRYABLE')

    const submitting = beginOrderOutboxAttempt(createOrderOutboxRecord(draft, payload))
    const failed = failOrderOutboxAttempt(
      submitting,
      0,
      'REQUEST_TIMEOUT',
      'timed out',
      Date.parse('2026-07-13T10:00:00Z'),
    )
    expect(failed.state).toBe('FAILED_RETRYABLE')
    expect(failed.nextRetryAt).toBe('2026-07-13T10:00:02.000Z')
  })

  it('stops automatic retries for 409 menu and idempotency conflicts', () => {
    const submitting = beginOrderOutboxAttempt(createOrderOutboxRecord(draft, payload))
    const conflict = failOrderOutboxAttempt(
      submitting,
      409,
      'IDEMPOTENCY_CONFLICT',
      'payload changed',
    )

    expect(conflict.state).toBe('CONFLICT')
    expect(conflict.nextRetryAt).toBeNull()
    expect(isOrderOutboxRecordDue(conflict)).toBe(false)
    expect(classifySubmissionFailure(409, 'MENU_REVISION_STALE')).toBe('CONFLICT')
    expect(classifySubmissionFailure(409, 'PRICE_CHANGED')).toBe('CONFLICT')
  })

  it('separates non-retryable 400 validation failures from 409 state conflicts', () => {
    expect(classifySubmissionFailure(400, 'ORDER_EMPTY')).toBe('FAILED_VALIDATION')
    expect(classifySubmissionFailure(409, 'ORDER_CONTEXT_CONFLICT')).toBe('CONFLICT')
  })

  it('does not let a stale queued or failed write replace a confirmed server result', () => {
    const submitting = beginOrderOutboxAttempt(createOrderOutboxRecord(draft, payload))
    const submitted = completeOrderOutboxAttempt(submitting, 491, true)

    expect(resolveOrderOutboxWrite(submitted, createOrderOutboxRecord(draft, payload))).toBe(submitted)
    expect(resolveOrderOutboxWrite(submitted, failOrderOutboxAttempt(
      submitting,
      0,
      'NETWORK_ERROR',
      'response lost',
    ))).toBe(submitted)
    expect(submitted.serverReplayed).toBe(true)
  })

  it('marks success only after receiving the server order id', () => {
    const submitting = beginOrderOutboxAttempt(createOrderOutboxRecord(draft, payload))
    const submitted = completeOrderOutboxAttempt(submitting, 491)

    expect(submitted.state).toBe('SUBMITTED')
    expect(submitted.serverOrderId).toBe(491)
    expect(submitted.nextRetryAt).toBeNull()
    expect(isOrderOutboxRecordDue(submitted)).toBe(false)
  })
})
