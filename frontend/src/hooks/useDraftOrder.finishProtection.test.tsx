import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useDraftOrder } from './useDraftOrder'
import { createLocalDraftRecord, readLocalDraft, saveLocalDraft, saveLocalDraftIfClientMatches, type LocalDraftRecord } from '../offline/localDrafts'
import { finalizeOfflineOrderRecords, buildTerminalLocalDraft } from '../offline/orderLifecycle'
import { findEditableOrderByContext, fetchOrderDetail } from '../services/orderService'
import type { BackendOrderResponse, MenuItem, OrderLineItem } from '../types/ordering'
vi.mock('../offline/localDrafts', async () => ({ ...await vi.importActual('../offline/localDrafts'), cleanupExpiredLocalDrafts: vi.fn(async () => {}), readLocalDraft: vi.fn(), saveLocalDraft: vi.fn(), saveLocalDraftIfClientMatches: vi.fn() }))
vi.mock('../offline/orderOutbox', async () => ({ ...await vi.importActual('../offline/orderOutbox'), readOrderOutboxRecord: vi.fn(async () => null) }))
vi.mock('../offline/orderLifecycle', async () => ({ ...await vi.importActual('../offline/orderLifecycle'), finalizeOfflineOrderRecords: vi.fn() }))
vi.mock('../services/orderService', async () => ({ ...await vi.importActual('../services/orderService'), findEditableOrderByContext: vi.fn(), fetchOrderDetail: vi.fn() }))
vi.mock('../services/orderOutboxProcessor', () => ({ kickOrderOutboxProcessor: vi.fn(), processOrderOutboxRecord: vi.fn() }))
const scope = { accountId: 7, organizationId: 1, storeId: 18 }
const context = { orderType: 'dine_in' as const, slotLabel: '1-A', tableLabel: '1', tableNo: '1-A', pickupNo: null }
const items: MenuItem[] = []
const line = { id: 'temp-retained', menuItemId: '1', quantity: 1, unitPrice: 10, lineSubtotal: 10, nameEn: 'Retain', nameZh: '保留', notes: '', selection: { comboEnabled: false, comboSelections: {}, comboSideRemoveIds: [], addOnQuantities: {}, removeIds: [], quantity: 1, notes: '' }, summaryTags: [] } as OrderLineItem
const old = { id: 91, store_id: 18, order_type: 'dine_in', table_no: '1-A', status: 'preparing', items: [], updated_at: '2026-11-03T10:00:00' } as unknown as BackendOrderResponse
let saved: LocalDraftRecord
let current: ReturnType<typeof useDraftOrder>
let view: ReactTestRenderer | undefined
function Probe() { const value = useDraftOrder(18, '1-A', '1', 'dine_in', null, items, { accountId: 7, organizationId: 1, menuRevision: 1 }); useEffect(() => { current = value }, [value]); return null }
beforeEach(() => {
  vi.resetAllMocks(); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn(), setTimeout, clearTimeout })
  vi.stubGlobal('document', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  saved = { ...createLocalDraftRecord(scope, context, 1), mode: 'SERVER_ORDER_UPDATE', serverOrderId: 91, serverOrderSnapshot: old, items: [line] }
  vi.mocked(readLocalDraft).mockImplementation(async () => saved)
  vi.mocked(saveLocalDraft).mockImplementation(async d => { saved = d; return d })
  vi.mocked(saveLocalDraftIfClientMatches).mockImplementation(async d => { saved = d; return d })
  vi.mocked(finalizeOfflineOrderRecords).mockImplementation(async (_scope, _id, status) => { saved = buildTerminalLocalDraft(saved, status === 'completed' ? 'COMPLETED' : 'CANCELLED'); return { drafts: 1, outbox: 0 } })
  vi.mocked(findEditableOrderByContext).mockResolvedValue(old); vi.mocked(fetchOrderDetail).mockResolvedValue({ ...old, status: 'completed' })
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.unstubAllGlobals() })
it('reopening a remotely completed order retains dirty items as a visible conflict', async () => {
  vi.mocked(findEditableOrderByContext).mockResolvedValue(null)
  await act(async () => { view = create(<Probe />) })
  expect(current.order?.id).toBe(91); expect(current.session?.items).toEqual([line]); expect(current.localSubmitState).toBe('CONFLICT')
  expect(saved.items).toEqual([line]); expect(saved.clientOrderId).toBe(current.clientOrderId)
})
it('cannot attach old pending work to a new order on the same table', async () => {
  vi.mocked(findEditableOrderByContext).mockResolvedValue({ ...old, id: 92 })
  await act(async () => { view = create(<Probe />) })
  expect(current.order?.id).toBe(91); expect(current.session?.items).toEqual([line]); expect(saved.serverOrderId).toBe(91)
  expect(current.error).toContain('上一订单仍有本地修改')
})
it('focus/read refresh preserves staged items after another Pad completes', async () => {
  await act(async () => { view = create(<Probe />) })
  expect(current.session?.items).toEqual([line])
  await act(async () => { await current.refreshOrder() })
  expect(current.order?.status).toBe('completed'); expect(current.session?.items).toEqual([line]); expect(saved.submitState).toBe('CONFLICT')
})
it('a non-terminal read refresh also cannot silently discard local additions', async () => {
  vi.mocked(fetchOrderDetail).mockResolvedValue(old)
  await act(async () => { view = create(<Probe />) })
  await act(async () => { await current.refreshOrder() })
  expect(current.session?.items).toEqual([line]); expect(finalizeOfflineOrderRecords).not.toHaveBeenCalled()
})
