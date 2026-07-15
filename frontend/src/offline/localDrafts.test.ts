import { describe, expect, it } from 'vitest'
import {
  NON_EMPTY_DRAFT_RETENTION_MS,
  buildDraftContextKey,
  createLocalDraftRecord,
  isLocalDraftExpired,
  localDraftKey,
  resolveLocalDraftForOpen,
  resolveLocalDraftWrite,
  reopenRejectedLocalDraft,
  withDraftPayloadHash,
} from './localDrafts'

const scope = { accountId: 7, organizationId: 9, storeId: 3 }
const tableContext = {
  orderType: 'dine_in' as const,
  slotLabel: 'T1-A',
  tableLabel: 'T1',
  tableNo: 'T1-A',
  pickupNo: null,
}

describe('local draft records', () => {
  it('creates stable ids and a store/account/context-scoped key', () => {
    const record = createLocalDraftRecord(scope, tableContext, 12, new Date('2026-07-13T10:00:00Z'))

    expect(record.localDraftId).toMatch(/^local-draft-/)
    expect(record.clientOrderId).toMatch(/^client-order-/)
    expect(record.contextKey).toBe('table:T1-A')
    expect(record.key).toBe('account:7|organization:9|store:3|table:T1-A')
    expect(record.submitState).toBe('LOCAL_DRAFT')
    expect(record.payloadHash).toMatch(/^fnv1a32:/)
  })

  it('keeps table, takeout, store, and account drafts isolated', () => {
    expect(buildDraftContextKey(tableContext)).toBe('table:T1-A')
    expect(buildDraftContextKey({ ...tableContext, orderType: 'pickup', pickupNo: 'TO-77' })).toBe('takeout:TO-77')
    expect(localDraftKey(scope, 'table:T1-A')).not.toBe(localDraftKey({ ...scope, storeId: 4 }, 'table:T1-A'))
    expect(localDraftKey(scope, 'table:T1-A')).not.toBe(localDraftKey({ ...scope, accountId: 8 }, 'table:T1-A'))
  })

  it('changes payload hash when options or notes change without replacing clientOrderId', () => {
    const record = createLocalDraftRecord(scope, tableContext, 12)
    const next = withDraftPayloadHash({
      ...record,
      items: [{
        id: 'temp-1',
        menuItemId: '10',
        nameEn: 'Noodle',
        nameZh: '面',
        quantity: 1,
        unitPrice: 10,
        lineSubtotal: 10,
        selection: {
          comboEnabled: false,
          comboSideRemoveIds: [],
          addOnQuantities: { '22': 2 },
          removeIds: [],
          quantity: 1,
          notes: '少汤',
        },
        summaryTags: [],
        notes: '少汤',
      }],
    })

    expect(next.clientOrderId).toBe(record.clientOrderId)
    expect(next.payloadHash).not.toBe(record.payloadHash)
  })

  it('retains non-empty drafts for seven days and never expires queued/conflict work', () => {
    const now = Date.parse('2026-07-13T10:00:00Z')
    const old = createLocalDraftRecord(scope, tableContext, 12, new Date(now - NON_EMPTY_DRAFT_RETENTION_MS - 1))
    const nonEmpty = { ...old, items: [{ id: 'x' } as never] }
    expect(isLocalDraftExpired(nonEmpty, now)).toBe(true)
    expect(isLocalDraftExpired({ ...nonEmpty, submitState: 'QUEUED' }, now)).toBe(false)
    expect(isLocalDraftExpired({ ...nonEmpty, submitState: 'CONFLICT' }, now)).toBe(false)
  })

  it('keeps a confirmed submitted lifecycle until the server reports a terminal status', () => {
    const first = createLocalDraftRecord(scope, tableContext, 12, new Date('2026-07-13T10:00:00Z'))
    const submitted = {
      ...first,
      submitState: 'SUBMITTED' as const,
      serverOrderId: 491,
      items: [{ id: 'submitted-item' } as never],
    }

    const reopened = resolveLocalDraftForOpen(
      scope,
      tableContext,
      13,
      submitted,
      'SUBMITTED',
      new Date('2026-07-13T11:00:00Z'),
    )

    expect(reopened).toBe(submitted)
    expect(reopened.serverOrderId).toBe(491)
    expect(reopened.submitState).toBe('SUBMITTED')
  })

  it('does not downgrade a locally cached order when the related outbox is submitted', () => {
    const first = createLocalDraftRecord(scope, tableContext, 12)
    const reopened = resolveLocalDraftForOpen(scope, tableContext, 12, first, 'SUBMITTED')

    expect(reopened).toBe(first)
  })

  it.each(['COMPLETED', 'CANCELLED'] as const)(
    'starts a blank lifecycle when the server order is %s even if an old outbox write is retryable',
    (terminalState) => {
      const terminal = {
        ...createLocalDraftRecord(scope, tableContext, 12),
        submitState: terminalState,
        serverOrderId: 491,
        items: [{ id: 'old-server-item' } as never],
      }

      const next = resolveLocalDraftForOpen(scope, tableContext, 13, terminal, 'FAILED_RETRYABLE')

      expect(next.clientOrderId).not.toBe(terminal.clientOrderId)
      expect(next.serverOrderId).toBeNull()
      expect(next.items).toEqual([])
    },
  )

  it('does not let a stale queued write overwrite a completed lifecycle with the same client id', () => {
    const queued = {
      ...createLocalDraftRecord(scope, tableContext, 12),
      submitState: 'QUEUED' as const,
      items: [{ id: 'queued-item' } as never],
    }
    const completed = { ...queued, submitState: 'COMPLETED' as const, items: [] }

    expect(resolveLocalDraftWrite(completed, queued)).toBe(completed)
  })

  it('allows a new same-table client identity to replace a prior completed lifecycle', () => {
    const completed = {
      ...createLocalDraftRecord(scope, tableContext, 12),
      submitState: 'COMPLETED' as const,
      serverOrderId: 491,
    }
    const next = createLocalDraftRecord(scope, tableContext, 13)

    expect(resolveLocalDraftWrite(completed, next)).toBe(next)
  })

  it.each(['QUEUED', 'SUBMITTING', 'FAILED_RETRYABLE', 'CONFLICT'] as const)(
    'preserves genuine %s offline work instead of rotating it',
    (outboxState) => {
      const existing = {
        ...createLocalDraftRecord(scope, tableContext, 12),
        submitState: outboxState,
        items: [{ id: 'offline-item' } as never],
      }

      expect(resolveLocalDraftForOpen(scope, tableContext, 12, existing, outboxState)).toBe(existing)
    },
  )

  it('gives a protected outbox state priority over a stale terminal draft snapshot', () => {
    const retryable = {
      ...createLocalDraftRecord(scope, tableContext, 12),
      submitState: 'SUBMITTED' as const,
      items: [{ id: 'retryable-item' } as never],
    }

    expect(resolveLocalDraftForOpen(scope, tableContext, 12, retryable, 'FAILED_RETRYABLE')).toBe(retryable)
  })

  it('does not rotate another table lifecycle', () => {
    const otherTable = { ...tableContext, slotLabel: 'T2', tableLabel: 'T2', tableNo: 'T2' }
    const firstTableDraft = createLocalDraftRecord(scope, tableContext, 12)
    const otherTableDraft = createLocalDraftRecord(scope, otherTable, 12)

    expect(resolveLocalDraftForOpen(scope, otherTable, 12, otherTableDraft, null)).toBe(otherTableDraft)
    expect(otherTableDraft.contextKey).not.toBe(firstTableDraft.contextKey)
  })

  it('preserves items but rotates submission identity after a rejected payload is returned for editing', () => {
    const rejected = {
      ...createLocalDraftRecord(scope, tableContext, 12),
      submitState: 'CONFLICT' as const,
      items: [{ id: 'offline-item' } as never],
    }

    const reopened = reopenRejectedLocalDraft(rejected, new Date('2026-07-13T12:00:00Z'))

    expect(reopened.items).toEqual(rejected.items)
    expect(reopened.contextKey).toBe(rejected.contextKey)
    expect(reopened.localDraftId).not.toBe(rejected.localDraftId)
    expect(reopened.clientOrderId).not.toBe(rejected.clientOrderId)
    expect(reopened.submitState).toBe('LOCAL_DRAFT')
  })
})
