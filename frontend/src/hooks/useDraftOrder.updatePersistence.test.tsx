import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useDraftOrder } from './useDraftOrder'
import { buildOrderUpdatePayload, fetchOrderDetail, findEditableOrderByContext, submitOrderUpdate } from '../services/orderService'
import { createLocalDraftRecord, readLocalDraft, saveLocalDraft, saveLocalDraftIfClientMatches, type LocalDraftRecord } from '../offline/localDrafts'
import { readOrderOutboxRecord } from '../offline/orderOutbox'
import type { BackendOrderResponse, MenuItem } from '../types/ordering'

vi.mock('../services/frontdeskConfigService', () => ({ requireActiveDiningTable: vi.fn().mockResolvedValue({}) }))
vi.mock('../services/networkStatus', async () => ({ ...await vi.importActual<typeof import('../services/networkStatus')>('../services/networkStatus'), recordAppOperation: vi.fn() }))
vi.mock('../services/orderService', async () => ({ ...await vi.importActual<typeof import('../services/orderService')>('../services/orderService'), fetchOrderDetail: vi.fn(), findEditableOrderByContext: vi.fn(), submitOrderUpdate: vi.fn() }))
vi.mock('../offline/localDrafts', async () => ({ ...await vi.importActual<typeof import('../offline/localDrafts')>('../offline/localDrafts'), readLocalDraft: vi.fn(), saveLocalDraft: vi.fn(), saveLocalDraftIfClientMatches: vi.fn(), cleanupExpiredLocalDrafts: vi.fn().mockResolvedValue(0) }))
vi.mock('../offline/orderOutbox', async () => ({ ...await vi.importActual<typeof import('../offline/orderOutbox')>('../offline/orderOutbox'), readOrderOutboxRecord: vi.fn() }))
const menu: MenuItem = { id: '21', categoryId: '1', nameZh: '合成菜', nameEn: 'Synthetic item', descriptionZh: '', descriptionEn: '', price: 5 }
const items: MenuItem[] = [menu]
const selection = { comboEnabled: false, comboSelections: {}, comboSideRemoveIds: [], addOnQuantities: {}, removeIds: [], quantity: 1, notes: '' }
let current: ReturnType<typeof useDraftOrder>
let view: ReactTestRenderer | undefined
let stored: LocalDraftRecord
let server: BackendOrderResponse
let notesEnabled = false
function Probe({ slot = 'TEST 3' }: { slot?: string }) {
 const value = useDraftOrder(2, slot, slot, 'dine_in', null, items, { accountId: 5, organizationId: 9, menuRevision: 1, orderNotesEnabled: notesEnabled })
 useEffect(() => { current = value }, [value])
 return null
}
async function mount() { await act(async () => { view = create(<Probe />) }) }
async function unmount() { await act(async () => view!.unmount()); view = undefined }
function confirmedUpdate(): BackendOrderResponse {
 return { ...server, current_revision: 2, is_modified_after_submit: true, items: [...server.items, { id: 11, menu_item_id: 21, item_name_snapshot_zh: '合成菜', item_name_snapshot_en: 'Synthetic item', quantity: 1, unit_price: 5, line_amount: 5, options: [] }] } as BackendOrderResponse
}
beforeEach(() => {
 vi.clearAllMocks()
 notesEnabled = false
 vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
 vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn(), setTimeout, clearTimeout })
 vi.stubGlobal('document', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
 server = { id: 7, order_no: 'SYNTHETIC7', status: 'submitted', store_id: 2, order_type: 'dine_in', table_no: 'TEST 3', current_revision: 1, updated_at: '2026-11-04T10:00:00Z', items: [{ id: 10, menu_item_id: 20, item_name_snapshot_zh: '原菜', item_name_snapshot_en: 'Original item', quantity: 1, unit_price: 10, line_amount: 10, options: [] }] } as unknown as BackendOrderResponse
 stored = { ...createLocalDraftRecord({ accountId: 5, organizationId: 9, storeId: 2 }, { orderType: 'dine_in', slotLabel: 'TEST 3', tableLabel: 'TEST 3', tableNo: 'TEST 3', pickupNo: null }, 1), mode: 'SERVER_ORDER_UPDATE', serverOrderId: 7, serverOrderSnapshot: structuredClone(server) }
 vi.mocked(readLocalDraft).mockImplementation(async () => structuredClone(stored))
 vi.mocked(readOrderOutboxRecord).mockResolvedValue(null)
 vi.mocked(saveLocalDraft).mockImplementation(async (record) => { stored = structuredClone(record); return structuredClone(stored) })
 vi.mocked(saveLocalDraftIfClientMatches).mockImplementation(async (record) => { stored = structuredClone(record); return structuredClone(stored) })
 vi.mocked(findEditableOrderByContext).mockImplementation(async () => structuredClone(server))
 vi.mocked(fetchOrderDetail).mockImplementation(async () => structuredClone(server))
 vi.mocked(submitOrderUpdate).mockImplementation(async () => { server = confirmedUpdate(); return { order: structuredClone(server) } as Awaited<ReturnType<typeof submitOrderUpdate>> })
})
it('restores bilingual notes and submits a revision-checked note-only update and explicit clear', async () => {
 notesEnabled = true
 server.order_notes = '原备注'
 stored.serverOrderSnapshot = structuredClone(server)
 vi.mocked(submitOrderUpdate).mockImplementation(async (_id, _key, added, _menu, notes) => {
  expect(added).toEqual([])
  server = { ...server, current_revision: server.current_revision + 1, order_notes: notes!.orderNotes.trim() || null }
  return { order: structuredClone(server), update_batch_id: 100 + server.current_revision } as Awaited<ReturnType<typeof submitOrderUpdate>>
 })
 await mount()
 expect(current.session!.orderNotes).toBe('原备注')
 await act(async () => { current.updateOrderNotes('一起上菜\nServe together') })
 await act(async () => { await current.saveLocalDraftNow() })
 await unmount()
 await mount()
 expect(current.session!.orderNotes).toBe('一起上菜\nServe together')
 await act(async () => { await current.submitOrder() })
 expect(vi.mocked(submitOrderUpdate).mock.calls[0][4]).toEqual({ orderNotes: '一起上菜\nServe together', expectedRevision: 1 })
 expect(stored.orderNotes).toBe('一起上菜\nServe together')
 expect(current.getConfirmedUpdateBatchId()).toBe(102)
 expect(stored.updateIdempotencyKey).toBeUndefined()
 expect(current.session!.isModifiedAfterSubmit).toBe(false)
 await act(async () => { current.updateOrderNotes('') })
 await act(async () => { await current.submitOrder() })
 expect(vi.mocked(submitOrderUpdate).mock.calls[1][4]).toEqual({ orderNotes: '', expectedRevision: 2 })
 expect(current.session!.items.map((line) => line.id)).toEqual(['10'])
})
it('persists one update identity across an uncertain request and reload before retry', async () => {
 notesEnabled = true
 server.order_notes = null
 stored.serverOrderSnapshot = structuredClone(server)
 vi.mocked(submitOrderUpdate).mockRejectedValue(new Error('synthetic network interruption'))
 await mount()
 await act(async () => { current.updateOrderNotes('先上主食') })
 await act(async () => { await expect(current.submitOrder()).rejects.toThrow('network interruption') })
 const key = stored.updateIdempotencyKey
 expect(key).toBeTruthy()
 const first = vi.mocked(submitOrderUpdate).mock.calls[0]
 await unmount()
 await mount()
 await act(async () => { await expect(current.submitOrder()).rejects.toThrow('network interruption') })
 expect(vi.mocked(submitOrderUpdate).mock.calls[1]).toEqual(first)
 expect(stored.updateIdempotencyKey).toBe(key)
})
it('loads fresh server notes when cached notes have no local changes', async () => {
 notesEnabled = true
 server.order_notes = 'old'
 stored.orderNotes = 'old'
 stored.serverOrderSnapshot = structuredClone(server)
 server = { ...server, current_revision: 2, order_notes: 'Another terminal' }
 await mount()
 expect(current.session!.orderNotes).toBe('Another terminal')
 expect(current.session!.isModifiedAfterSubmit).toBe(false)
 expect(current.draftSubmissionLocked).toBe(false)
})
it('refreshes untouched notes but preserves and locks a concurrent local note change', async () => {
 notesEnabled = true
 server.order_notes = 'old'
 stored.serverOrderSnapshot = structuredClone(server)
 await mount()
 server = { ...server, current_revision: 2, order_notes: 'Fresh server note' }
 await act(async () => { await current.refreshOrder() })
 expect(current.session!.orderNotes).toBe('Fresh server note')
 await act(async () => { current.updateOrderNotes('Local unsent note') })
 server = { ...server, current_revision: 3, order_notes: 'Concurrent server note' }
 await act(async () => { await current.refreshOrder() })
 expect(current.session!.orderNotes).toBe('Local unsent note')
 expect(current.draftSubmissionLocked).toBe(true)
 await act(async () => { await current.submitOrder() })
 expect(submitOrderUpdate).not.toHaveBeenCalled()
 await unmount()
 await mount()
 expect(current.session!.orderNotes).toBe('Local unsent note')
 expect(current.draftSubmissionLocked).toBe(true)
})
afterEach(async () => { if (view) await unmount(); vi.unstubAllGlobals() })
it('persists confirmed locked lines before return and immediate unmount, then submits only a genuinely new item', async () => {
 await mount()
 const identity = stored.clientOrderId
 await act(async () => { await current.addItem(menu, selection) })
 expect(current.session!.items.some((item) => item.id.startsWith('temp-'))).toBe(true)
 await act(async () => { await current.submitOrder(); view!.unmount(); view = undefined })
 expect(stored.clientOrderId).toBe(identity)
 expect(stored.serverOrderSnapshot!.current_revision).toBe(2)
 expect(stored.items.map((item) => item.id)).toEqual(['10', '11'])
 expect(stored.items.every((item) => item.locked)).toBe(true)
 await mount()
 expect(current.session!.items.map((item) => item.id)).toEqual(['10', '11'])
 expect(current.session!.isModifiedAfterSubmit).toBe(false)
 await act(async () => { await current.addItem({ ...menu, id: '22', nameEn: 'New synthetic item' }, selection) })
 await act(async () => { await current.submitOrder() })
 expect(submitOrderUpdate).toHaveBeenCalledTimes(2)
 expect(vi.mocked(submitOrderUpdate).mock.calls[1][2].map((item) => item.menuItemId)).toEqual(['22'])
})
it('retains and locks ambiguous legacy items when the server has advanced, including after reopening again', async () => {
 await mount()
 await act(async () => { await current.addItem(menu, selection); await current.saveLocalDraftNow() })
 // Save explicitly again after the staged-items effect has published the new row.
 await act(async () => { await current.saveLocalDraftNow() })
 const tempId = stored.items.find((item) => item.id.startsWith('temp-'))!.id
 await unmount()
 server = confirmedUpdate()
 await mount()
 expect(current.draftSubmissionLocked).toBe(true)
 expect(current.error).toContain('本机记录已保留')
 expect(current.session!.items.some((item) => item.id === tempId)).toBe(true)
 await act(async () => { await current.submitOrder() })
 expect(submitOrderUpdate).not.toHaveBeenCalled()
 await unmount()
 await mount()
 expect(current.draftSubmissionLocked).toBe(true)
 await act(async () => { await current.submitOrder() })
 expect(submitOrderUpdate).not.toHaveBeenCalled()
})
it('locks a server-confirmed update if the local confirmation write fails, without making a second request', async () => {
 await mount()
 await act(async () => { await current.addItem(menu, selection) })
 const save = vi.mocked(saveLocalDraftIfClientMatches).getMockImplementation()!
 vi.mocked(saveLocalDraftIfClientMatches).mockImplementation(async (record, client) => {
  if (record.serverOrderSnapshot?.current_revision === 2) throw new Error('synthetic storage failure')
  return save(record, client)
 })
 await act(async () => { await expect(current.submitOrder()).rejects.toThrow('服务器已确认更新') })
 expect(current.draftSubmissionLocked).toBe(true)
 expect(current.session!.items.every((item) => item.locked)).toBe(true)
 await act(async () => { await current.submitOrder() })
 expect(submitOrderUpdate).toHaveBeenCalledTimes(1)
 expect(current.persistenceError).toContain('不要重复 Update')
})
it('drains an in-flight old autosave before writing the confirmed snapshot', async () => {
 await mount()
 await act(async () => { await current.addItem(menu, selection) })
 const save = vi.mocked(saveLocalDraftIfClientMatches).getMockImplementation()!
 let release!: () => void
 let active = 0
 let maximumActive = 0
 let blockNext = true
 const revisions: number[] = []
 vi.mocked(saveLocalDraftIfClientMatches).mockImplementation(async (record, client) => {
  active += 1
  maximumActive = Math.max(maximumActive, active)
  if (blockNext) {
   blockNext = false
   await new Promise<void>((resolve) => { release = resolve })
  }
  try {
   revisions.push(record.serverOrderSnapshot!.current_revision)
   return await save(record, client)
  } finally { active -= 1 }
 })
 let oldSave!: Promise<void>
 let update!: ReturnType<typeof current.submitOrder>
 await act(async () => { oldSave = current.saveLocalDraftNow() })
 await act(async () => { update = current.submitOrder() })
 expect(submitOrderUpdate).not.toHaveBeenCalled()
 await act(async () => { release(); await oldSave; await update; view!.unmount(); view = undefined })
 expect(maximumActive).toBe(1)
 expect(revisions.at(-1)).toBe(2)
 expect(stored.items.every((item) => item.locked && !item.id.startsWith('temp-'))).toBe(true)
})
it.each(['refresh', 'reopen'])('confirms a committed update after a lost response via %s, then allows a new update', async (action) => {
 notesEnabled = true
 server.order_notes = null
 stored.serverOrderSnapshot = structuredClone(server)
 let acceptedKey: string | undefined
 vi.mocked(submitOrderUpdate).mockImplementation(async (_id, key, _items, _menu, _notes, frozen) => {
  expect(frozen).toBeDefined()
  if (!acceptedKey) {
   acceptedKey = key
   server = { ...confirmedUpdate(), order_notes: frozen!.order_notes }
   throw new Error('committed but response lost')
  }
  if (key === acceptedKey) {
   expect(frozen!.expected_revision).toBe(1)
   expect(frozen!.order_notes).toBe('Together')
   return { order: structuredClone(server) } as Awaited<ReturnType<typeof submitOrderUpdate>>
  }
  server = { ...server, current_revision: server.current_revision + 1, order_notes: frozen!.order_notes }
  return { order: structuredClone(server) } as Awaited<ReturnType<typeof submitOrderUpdate>>
 })
 await mount()
 await act(async () => { await current.addItem(menu, selection); current.updateOrderNotes('Together') })
 await act(async () => { await expect(current.submitOrder()).rejects.toThrow('response lost') })
 const frozen = structuredClone(stored.updatePayload)
 expect(current.draftEditingLocked).toBe(true)
 if (action === 'refresh') await act(async () => { await current.refreshOrder() })
 else { await unmount(); await mount() }
 expect(vi.mocked(submitOrderUpdate).mock.calls[1][5]).toEqual(frozen)
 expect(current.session!.items.map((line) => line.id)).toEqual(['10', '11'])
 expect(stored.updatePayload).toBeUndefined()
 expect(current.draftEditingLocked).toBe(false)
 expect(current.session!.isModifiedAfterSubmit).toBe(false)
 await act(async () => { current.updateOrderNotes('Next note') })
 await act(async () => { await current.submitOrder() })
 expect(vi.mocked(submitOrderUpdate).mock.calls[2][1]).not.toBe(acceptedKey)
 expect(vi.mocked(submitOrderUpdate).mock.calls[2][5]!.expected_revision).toBe(2)
 expect(current.session!.orderNotes).toBe('Next note')
})
it('does not publish a delayed old update into a different ordering context', async () => {
 notesEnabled = true
 server.order_notes = null
 stored.serverOrderSnapshot = structuredClone(server)
 stored.orderNotes = 'Old pending note'
 stored.updatePayload = buildOrderUpdatePayload('old-intent', [], items, { orderNotes: stored.orderNotes, expectedRevision: 1 })
 const old = structuredClone(stored)
 const otherServer = { ...structuredClone(server), id: 99, table_no: 'TEST 4', order_notes: 'Other table note' }
 const other = { ...createLocalDraftRecord({ accountId: 5, organizationId: 9, storeId: 2 },
   { orderType: 'dine_in' as const, slotLabel: 'TEST 4', tableLabel: 'TEST 4', tableNo: 'TEST 4', pickupNo: null }, 1),
   mode: 'SERVER_ORDER_UPDATE' as const, serverOrderId: 99, serverOrderSnapshot: otherServer }
 const records = new Map([[old.key, old], [other.key, other]])
 vi.mocked(readLocalDraft).mockImplementation(async (_scope, context) => structuredClone(context === 'table:TEST 4' ? other : old))
 vi.mocked(saveLocalDraftIfClientMatches).mockImplementation(async (record) => { records.set(record.key, structuredClone(record)); return structuredClone(record) })
 vi.mocked(findEditableOrderByContext).mockImplementation(async (context) => structuredClone(context.tableNo === 'TEST 4' ? otherServer : server))
 let finish!: (value: Awaited<ReturnType<typeof submitOrderUpdate>>) => void
 vi.mocked(submitOrderUpdate).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
 await mount()
 expect(submitOrderUpdate).toHaveBeenCalledTimes(1)
 await act(async () => { view!.update(<Probe slot="TEST 4" />) })
 expect(current.order!.id).toBe(99)
 expect(current.session!.orderNotes).toBe('Other table note')
 await act(async () => { finish({ order: { ...server, current_revision: 2, order_notes: 'Old pending note' }, update_batch_id: 88 } as Awaited<ReturnType<typeof submitOrderUpdate>>) })
 expect(current.order!.id).toBe(99)
 expect(current.session!.orderNotes).toBe('Other table note')
 expect(current.getConfirmedUpdateBatchId()).toBeNull()
 expect(records.get(other.key)!.serverOrderId).toBe(99)
 expect(records.get(old.key)!.updatePayload).toBeUndefined()
})
