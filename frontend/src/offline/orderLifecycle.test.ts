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
