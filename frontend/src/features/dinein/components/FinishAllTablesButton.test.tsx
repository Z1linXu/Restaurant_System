import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { FinishAllTablesButton } from './FinishAllTablesButton'
import { finishAllTables, previewFinishAllTables, type FinishAllPreview, type FinishAllResult } from '../../../services/finishAllTablesService'
import { createLocalDraftRecord, listLocalDraftsForScope } from '../../../offline/localDrafts'
import { createOrderOutboxRecord, listOrderOutboxForAccount } from '../../../offline/orderOutbox'
const capabilityState = vi.hoisted(() => ({ permissions: ['ORDER_COMPLETE'] }))
vi.mock('react-dom', () => ({ createPortal: (node: unknown) => node }))
vi.mock('../../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 7 }, permissions: capabilityState.permissions }) }))
vi.mock('../../store/useStoreContext', () => ({ useCurrentStore: () => ({ storeId: 18, organizationId: 1 }) }))
vi.mock('../../../services/finishAllTablesService', () => ({ finishAllTables: vi.fn(), previewFinishAllTables: vi.fn() }))
vi.mock('../../../offline/localDrafts', async () => ({ ...await vi.importActual('../../../offline/localDrafts'), listLocalDraftsForScope: vi.fn() }))
vi.mock('../../../offline/orderOutbox', async () => ({ ...await vi.importActual('../../../offline/orderOutbox'), listOrderOutboxForAccount: vi.fn() }))
const preview: FinishAllPreview = { batch_id: 'batch1', store_id: 18, store_name: 'Chinatown fixture', expires_at: 9999999999, order_count: 2, table_slot_count: 2, orders: [{ order_id: 11, table_no: '1-A', table_display_name: '1里' }, { order_id: 12, table_no: '1-B', table_display_name: '1外' }], blocked_tables: ['2'], preview_token: 'signed-frozen-set' }
const result: FinishAllResult = { batch_id: 'batch1', completed: 2, skipped: 0, failed: 0, released_slot_count: 2, results: [], remaining_tables: ['2'] }
let view: ReactTestRenderer | undefined
const finished = vi.fn()
const button = (label: string) => view!.root.findAllByType('button').find(n => n.children.join('') === label)!
const text = () => JSON.stringify(view!.toJSON())
const click = async (label: string) => { await act(async () => { await button(label).props.onClick() }) }
beforeEach(() => {
  vi.resetAllMocks(); capabilityState.permissions = ['ORDER_COMPLETE']; vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  const data = new Map<string, string>()
  vi.stubGlobal('sessionStorage', { getItem: (k: string) => data.get(k) ?? null, setItem: (k: string, v: string) => data.set(k, v), removeItem: (k: string) => data.delete(k) })
  vi.stubGlobal('document', { body: {} })
  vi.mocked(previewFinishAllTables).mockResolvedValue(preview); vi.mocked(finishAllTables).mockResolvedValue(result)
  vi.mocked(listLocalDraftsForScope).mockResolvedValue([]); vi.mocked(listOrderOutboxForAccount).mockResolvedValue([])
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.unstubAllGlobals() })
async function mount(unsaved = false) { await act(async () => { view = create(<FinishAllTablesButton unsaved={unsaved} onFinished={finished} />) }) }
it('loads the current Store preview, shows split names/counts and Cancel never posts', async () => {
  await mount(); await click('Finish All Tables / 结束所有桌台')
  expect(previewFinishAllTables).toHaveBeenCalledWith(18)
  expect(text()).toContain('1里'); expect(text()).toContain('1外'); expect(text()).toContain('Chinatown fixture')
  expect(text()).toContain('草稿'); expect(finishAllTables).not.toHaveBeenCalled()
  await click('Cancel / 取消'); expect(finishAllTables).not.toHaveBeenCalled()
})
it('requires explicit Confirm and shows accurate partial outcome before returning', async () => {
  vi.mocked(finishAllTables).mockResolvedValue({ ...result, completed: 1, failed: 1, released_slot_count: 1, results: [{ order_id: 12, table_no: '1-B', outcome: 'failed', reason: 'ORDER_FINISH_FAILED' }] })
  await mount(); await click('Finish All Tables / 结束所有桌台'); await click('Confirm Finish All / 确认全部结束')
  expect(finishAllTables).toHaveBeenCalledWith(18, 'signed-frozen-set'); expect(text()).toContain('ORDER_FINISH_FAILED')
  expect(finished).not.toHaveBeenCalled(); await click('Back to Tables / 返回桌台'); expect(finished).toHaveBeenCalledWith(expect.objectContaining({ completed: 1, failed: 1 }))
})
it('disables confirmation for idle tables and blocks unsaved current changes', async () => {
  vi.mocked(previewFinishAllTables).mockResolvedValue({ ...preview, order_count: 0, orders: [] })
  await mount(true); expect(button('Finish All Tables / 结束所有桌台').props.disabled).toBe(true)
  await act(async () => view!.update(<FinishAllTablesButton onFinished={finished} />))
  await click('Finish All Tables / 结束所有桌台'); expect(button('Confirm Finish All / 确认全部结束').props.disabled).toBe(true); expect(text()).toContain('没有可结束')
})
it.each(['QUEUED', 'SUBMITTING', 'CONFLICT', 'FAILED_RETRYABLE'] as const)('retains and warns about %s outbox; prevents completing behind it', async (state) => {
  const d = createLocalDraftRecord({ accountId: 7, organizationId: 1, storeId: 18 }, { orderType: 'dine_in', slotLabel: '1-A', tableLabel: '1', tableNo: '1-A', pickupNo: null }, 1)
  const o = createOrderOutboxRecord(d, { order_type: 'dine_in' } as never); o.state = state
  vi.mocked(listOrderOutboxForAccount).mockResolvedValue([o])
  await mount(); await click('Finish All Tables / 结束所有桌台')
  expect(button('Confirm Finish All / 确认全部结束').props.disabled).toBe(true); expect(text()).toContain('内容不会清除'); expect(finishAllTables).not.toHaveBeenCalled()
})
it('warns for unsubmitted local new-order drafts but does not delete or include them', async () => {
  const d = createLocalDraftRecord({ accountId: 7, organizationId: 1, storeId: 18 }, { orderType: 'dine_in', slotLabel: '3', tableLabel: '3', tableNo: '3', pickupNo: null }, 1)
  d.items = [{ id: 'temp-new' } as never]; vi.mocked(listLocalDraftsForScope).mockResolvedValue([d])
  await mount(); await click('Finish All Tables / 结束所有桌台'); expect(text()).toContain('未提交草稿不会被结束')
  await click('Confirm Finish All / 确认全部结束'); expect(d.items).toHaveLength(1)
})
it('rechecks local work after preview and fails closed if a new queued order appears', async () => {
  await mount(); await click('Finish All Tables / 结束所有桌台')
  vi.mocked(listOrderOutboxForAccount).mockResolvedValue([{ storeId: 18, organizationId: 1, state: 'SUBMITTING', frozenPayload: { order_type: 'dine_in' } } as never])
  await click('Confirm Finish All / 确认全部结束'); expect(finishAllTables).not.toHaveBeenCalled(); expect(text()).toContain('内容均已保留')
})
it('serializes clicks while pending and retries the same token after timeout/reload', async () => {
  let reject!: (e: Error) => void
  vi.mocked(finishAllTables).mockImplementationOnce(() => new Promise((_, r) => { reject = r }))
  await mount(); await click('Finish All Tables / 结束所有桌台')
  await click('Confirm Finish All / 确认全部结束'); expect(button('Processing / 处理中…').props.disabled).toBe(true)
  await act(async () => reject(new Error('timeout'))); expect(text()).toContain('重试仅处理已确认')
  await act(async () => view!.unmount()); view = undefined; await mount()
  vi.mocked(previewFinishAllTables).mockClear(); await click('Finish All Tables / 结束所有桌台'); expect(previewFinishAllTables).not.toHaveBeenCalled()
  await click('Confirm Finish All / 确认全部结束'); expect(finishAllTables).toHaveBeenCalledTimes(2)
  expect(vi.mocked(finishAllTables).mock.calls.every(c => c[1] === 'signed-frozen-set')).toBe(true)
})
it('does not execute if the backend preview or local storage inspection fails', async () => {
  vi.mocked(listLocalDraftsForScope).mockRejectedValue(new Error('IndexedDB unavailable'))
  await mount(); await click('Finish All Tables / 结束所有桌台'); expect(text()).toContain('IndexedDB unavailable'); expect(finishAllTables).not.toHaveBeenCalled()
})

it('uses actual auth/me enum-name permissions and hides the action without ORDER_COMPLETE', async () => {
  capabilityState.permissions = ['ORDER_VIEW_ACTIVE']
  await mount(); expect(view!.root.findAllByType('button')).toHaveLength(0)
})
