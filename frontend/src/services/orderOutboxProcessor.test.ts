import { activateOfflineAccount, activateOfflineStore, invalidateOfflineSession } from '../offline/environmentScope'
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
  key: 'environment:https%3A%2F%2Fsynthetic.invalid|account:7|organization:9|store:3|table:T1',
  localDraftId: 'local-draft-1',
  clientOrderId: 'client-order-1',
  environmentId: 'https://synthetic.invalid',
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
    vi.stubGlobal('window', Object.assign(new EventTarget(), { location: { origin: 'https://synthetic.invalid' } }))
    invalidateOfflineSession()
    activateOfflineAccount(7)
    activateOfflineStore(draft)
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
  it('does not transmit foreign environment/account manual records', async () => {
    const foreign = { ...stored, environmentId: 'https://other.synthetic.invalid' }
    expect((await processOrderOutboxRecord(foreign)).order).toBeNull()
    expect((await processOrderOutboxRecord({ ...stored, accountId: 8 })).order).toBeNull()
    expect((await processOrderOutboxRecord({ ...stored, organizationId: 10 })).order).toBeNull()
    expect((await processOrderOutboxRecord({ ...stored, storeId: 4 })).order).toBeNull()
    expect(mocks.read).not.toHaveBeenCalled(); expect(mocks.submit).not.toHaveBeenCalled()
  })
  it('does not submit when logout happens while IndexedDB lookup is pending', async () => {
    let finish!: (record: OrderOutboxRecord) => void
    mocks.read.mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const operation = processOrderOutboxRecord(stored)
    invalidateOfflineSession(); finish(stored)
    expect((await operation).order).toBeNull(); expect(mocks.submit).not.toHaveBeenCalled()
    expect(stored.state).toBe('QUEUED'); expect(stored.attemptCount).toBe(0)
  })

  it('pauses an old Store during an IndexedDB lookup and resumes with its stable retry identity on return', async () => {
    let finish!: (record: OrderOutboxRecord) => void
    mocks.read.mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const operation = processOrderOutboxRecord(stored)
    activateOfflineStore({ ...draft, organizationId: 10, storeId: 4 }); finish(stored)
    await operation; expect(mocks.submit).not.toHaveBeenCalled(); expect(stored.state).toBe('QUEUED')
    activateOfflineStore(draft)
    mocks.submit.mockResolvedValueOnce({ order_id: 491, replayed: true, order: { id: 491 } })
    await processOrderOutboxRecord(stored)
    expect(mocks.submit).toHaveBeenCalledWith(payload)
  })

})
