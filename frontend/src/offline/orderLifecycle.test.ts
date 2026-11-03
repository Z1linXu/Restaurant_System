import { describe, expect, it } from 'vitest'
import { createLocalDraftRecord, type LocalDraftRecord } from './localDrafts'
import {
  buildTerminalLocalDraft,
  buildTerminalOrderOutbox,
  collectServerOrderIds,
  terminalLocalStateForServerStatus,
} from './orderLifecycle'
import { createOrderOutboxRecord } from './orderOutbox'
import type { IdempotentOrderSubmitPayload } from '../services/orderService'

const scope = { accountId: 7, organizationId: 9, storeId: 3 }
const context = {
  orderType: 'dine_in' as const,
  slotLabel: 'T1',
  tableLabel: 'T1',
  tableNo: 'T1',
  pickupNo: null,
}

function linkedDraft(): LocalDraftRecord {
  return {
    ...createLocalDraftRecord(scope, context, 12),
    mode: 'SERVER_ORDER_UPDATE',
    serverOrderId: 491,
    items: [{ id: 'old-item' } as never],
  }
}

const payload: IdempotentOrderSubmitPayload = {
  client_order_id: 'unused',
  idempotency_key: 'unused',
  organization_id: 9,
  store_id: 3,
  server_order_id: 491,
  order_type: 'dine_in',
  table_no: 'T1',
  pickup_no: null,
  menu_revision: 12,
  expected_subtotal_amount: 10,
  items: [],
}

describe('offline server order lifecycle', () => {
  it.each([
    ['completed', 'COMPLETED'],
    ['closed', 'COMPLETED'],
    ['cancelled', 'CANCELLED'],
    ['canceled', 'CANCELLED'],
  ] as const)('maps server status %s to %s', (status, expected) => {
    expect(terminalLocalStateForServerStatus(status)).toBe(expected)
  })

  it('clears old displayed items when a linked server order completes', () => {
    const terminal = buildTerminalLocalDraft(linkedDraft(), 'COMPLETED', new Date('2026-07-15T12:00:00Z'))

    expect(terminal.submitState).toBe('COMPLETED')
    expect(terminal.serverOrderId).toBe(491)
    expect(terminal.items).toEqual([])
  })

  it('marks the matching outbox terminal without changing its stable identity', () => {
    const draft = linkedDraft()
    const outbox = {
      ...createOrderOutboxRecord(draft, {
        ...payload,
        client_order_id: draft.clientOrderId,
        idempotency_key: draft.clientOrderId,
      }),
      state: 'SUBMITTED' as const,
      serverOrderId: 491,
    }

    const terminal = buildTerminalOrderOutbox(outbox, 'COMPLETED')
    expect(terminal.state).toBe('COMPLETED')
    expect(terminal.clientOrderId).toBe(outbox.clientOrderId)
    expect(terminal.serverOrderId).toBe(491)
  })

  it('reconciles only non-terminal known server order ids', () => {
    const active = linkedDraft()
    const completed = { ...linkedDraft(), clientOrderId: 'completed-client', submitState: 'COMPLETED' as const }

    expect(collectServerOrderIds([active, completed], [])).toEqual([491])
  })
})

it.each(['QUEUED', 'SUBMITTING', 'CONFLICT', 'FAILED_RETRYABLE'] as const)('preserves %s local draft and frozen outbox payload after another Pad finishes', (state) => {
  const draft = { ...linkedDraft(), submitState: state }
  const terminal = buildTerminalLocalDraft(draft, 'COMPLETED')
  expect(terminal.items).toEqual(draft.items); expect(terminal.submitState).toBe('CONFLICT')
  const outbox = { ...createOrderOutboxRecord(draft, { ...payload, client_order_id: draft.clientOrderId, idempotency_key: draft.clientOrderId }), state, serverOrderId: 491 }
  const retained = buildTerminalOrderOutbox(outbox, 'COMPLETED')
  expect(retained.state).toBe('CONFLICT'); expect(retained.frozenPayload).toEqual(outbox.frozenPayload)
  expect(retained.lastErrorCode).toBe('SERVER_ORDER_TERMINAL_WITH_LOCAL_WORK')
})
it('retains unsaved submitted-order additions when remote completion is observed', () => {
  const draft = { ...linkedDraft(), items: [{ id: 'temp-new-line' } as never] }
  const retained = buildTerminalLocalDraft(draft, 'COMPLETED')
  expect(retained.items).toEqual(draft.items); expect(retained.submitState).toBe('CONFLICT')
})
it('does not repeatedly rewrite retained conflicts when the same terminal state is polled', () => {
  const draft = { ...linkedDraft(), submitState: 'QUEUED' as const }
  const first = buildTerminalLocalDraft(draft, 'COMPLETED')
  // A no-snapshot record has no visible state to reconcile; real linked snapshots are stable.
  const withSnapshot = { ...first, serverOrderSnapshot: { status: 'completed' } as never }
  expect(buildTerminalLocalDraft(withSnapshot, 'COMPLETED')).toBe(withSnapshot)
  const o = createOrderOutboxRecord(draft, { ...payload, client_order_id: draft.clientOrderId, idempotency_key: draft.clientOrderId })
  const conflict = buildTerminalOrderOutbox(o, 'COMPLETED')
  expect(buildTerminalOrderOutbox(conflict, 'COMPLETED')).toBe(conflict)
})
