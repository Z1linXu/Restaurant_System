import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { BackendOrderResponse } from '../types/ordering'
import {
  createOrderOutboxRecord,
  type OrderOutboxRecord,
} from '../offline/orderOutbox'
import type { LocalDraftRecord } from '../offline/localDrafts'
import type { IdempotentOrderSubmitPayload } from './orderService'

const mocks = vi.hoisted(() => ({
  read: vi.fn(),
  save: vi.fn(),
  submit: vi.fn(),
}))

vi.mock('../offline/orderOutbox', async () => {
  const actual = await vi.importActual<typeof import('../offline/orderOutbox')>('../offline/orderOutbox')
  return {
    ...actual,
    readOrderOutboxRecord: mocks.read,
    saveOrderOutboxRecord: mocks.save,
  }
})

vi.mock('./orderService', async () => {
  const actual = await vi.importActual<typeof import('./orderService')>('./orderService')
  return {
    ...actual,
    submitIdempotentOrder: mocks.submit,
  }
})

import { processOrderOutboxRecord } from './orderOutboxProcessor'

const draft = {
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
} satisfies LocalDraftRecord

const payload = {
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
        item_name_snapshot_zh: '牛肉面',
        item_name_snapshot_en: 'Beef Noodle',
        unit_price_snapshot: 12.5,
        category_code_snapshot: 'SOUP_NOODLE',
        station_id_snapshot: 3,
        item_sku_snapshot: 'traditional_beef_noodle',
        item_type_snapshot: 'NOODLE',
    quantity: 1,
    combo_group_no: null,
    combo_role: 'standalone',
    notes: null,
    options: [],
  }],
} satisfies IdempotentOrderSubmitPayload

describe('order outbox processor single-flight', () => {
  let stored: OrderOutboxRecord

  beforeEach(() => {
    vi.clearAllMocks()
    stored = createOrderOutboxRecord(draft, payload)
    mocks.read.mockImplementation(async () => stored)
    mocks.save.mockImplementation(async (record: OrderOutboxRecord) => {
      stored = record
      return stored
    })
    vi.spyOn(console, 'info').mockImplementation(() => undefined)
    vi.spyOn(console, 'warn').mockImplementation(() => undefined)
  })

  it('shares one request between automatic replay and a manual submit click', async () => {
    let resolveSubmit!: (result: unknown) => void
    mocks.submit.mockReturnValue(new Promise((resolve) => {
      resolveSubmit = resolve
    }))

    const automatic = processOrderOutboxRecord(stored)
    const manual = processOrderOutboxRecord(stored)

    await vi.waitFor(() => expect(mocks.submit).toHaveBeenCalledTimes(1))
    const order = { id: 491, status: 'preparing', items: [] } as unknown as BackendOrderResponse
    resolveSubmit({
      client_order_id: 'client-order-1',
      idempotency_key: 'client-order-1',
      payload_hash: 'hash',
      order_id: 491,
      replayed: true,
      order,
    })

    const [automaticResult, manualResult] = await Promise.all([automatic, manual])
    expect(automaticResult.order?.id).toBe(491)
    expect(manualResult.order?.id).toBe(491)
    expect(automaticResult.record?.state).toBe('SUBMITTED')
    expect(automaticResult.record?.serverReplayed).toBe(true)
  })
})
