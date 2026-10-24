import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useDraftOrder } from './useDraftOrder'
import { requireActiveDiningTable } from '../services/frontdeskConfigService'
import { findEditableOrderByContext, submitOrderUpdate } from '../services/orderService'
import { saveOrderOutboxRecord } from '../offline/orderOutbox'
import type { BackendOrderResponse, MenuItem } from '../types/ordering'

vi.mock('../services/frontdeskConfigService', () => ({ requireActiveDiningTable: vi.fn() }))
vi.mock('../services/networkStatus', async () => ({ ...await vi.importActual<typeof import('../services/networkStatus')>('../services/networkStatus'), recordAppOperation: vi.fn() }))
vi.mock('../services/orderService', async () => ({ ...await vi.importActual<typeof import('../services/orderService')>('../services/orderService'), findEditableOrderByContext: vi.fn(), submitOrderUpdate: vi.fn() }))
vi.mock('../offline/orderOutbox', async () => ({ ...await vi.importActual<typeof import('../offline/orderOutbox')>('../offline/orderOutbox'), saveOrderOutboxRecord: vi.fn() }))
const items: MenuItem[] = []
let current: ReturnType<typeof useDraftOrder>
let view: ReactTestRenderer | undefined
function Probe() {
  const value = useDraftOrder(2, '1-左-A', '1-左', 'dine_in', null, items, { accountId: null, organizationId: null, menuRevision: 1 })
  useEffect(() => { current = value }, [value])
  return null
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn(), setTimeout, clearTimeout })
  vi.stubGlobal('document', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  vi.mocked(findEditableOrderByContext).mockResolvedValue({ id: 7, order_no: 'ORDER7', status: 'draft', store_id: 2, order_type: 'dine_in', table_no: '1-左-A', items: [{ id: 10, menu_item_id: 20, item_name_snapshot_zh: '面', item_name_snapshot_en: 'Noodle', quantity: 1, unit_price: 10, line_amount: 10, options: [] }] } as unknown as BackendOrderResponse)
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.unstubAllGlobals() })
it.each(['该桌台已删除；本机草稿已保留。', '无法确认桌台状态，请联网后重试。'])('revalidates before queuing/submitting and preserves the draft when %s', async (message) => {
  await act(async () => { view = create(<Probe />) })
  expect(current.session?.items).toHaveLength(1)
  const before = JSON.stringify(current.session)
  vi.mocked(requireActiveDiningTable).mockRejectedValue(new Error(message))
  let result: BackendOrderResponse | null | undefined
  await act(async () => { result = await current.submitOrder() })
  expect(result).toBeNull()
  expect(requireActiveDiningTable).toHaveBeenCalledWith(2, '1-左-A')
  expect(current.error).toBe(message)
  expect(JSON.stringify(current.session)).toBe(before)
  expect(saveOrderOutboxRecord).not.toHaveBeenCalled()
  expect(submitOrderUpdate).not.toHaveBeenCalled()
})

it('serializes concurrent submissions after delayed table validation without changing idempotency', async () => {
  const order = await findEditableOrderByContext({ storeId: 2, orderType: 'dine_in', tableNo: '1-左-A' })
  vi.mocked(findEditableOrderByContext).mockResolvedValue({ ...order, status: 'submitted' } as BackendOrderResponse)
  await act(async () => { view = create(<Probe />) })
  await act(async () => { await current.addItem({ id: '21', categoryId: '1', nameZh: '新菜', nameEn: 'New item', descriptionZh: '', descriptionEn: '', price: 10 }, { comboEnabled: false, comboSelections: {}, comboSideRemoveIds: [], addOnQuantities: {}, removeIds: [], quantity: 1, notes: '' }) })
  const validations: Array<(table: Awaited<ReturnType<typeof requireActiveDiningTable>>) => void> = []
  vi.mocked(requireActiveDiningTable).mockImplementation(() => new Promise((resolve) => { validations.push(resolve) }))
  let finish!: (result: Awaited<ReturnType<typeof submitOrderUpdate>>) => void
  vi.mocked(submitOrderUpdate).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
  let first!: ReturnType<typeof current.submitOrder>
  let second!: ReturnType<typeof current.submitOrder>
  await act(async () => { first = current.submitOrder(); second = current.submitOrder() })
  expect(validations).toHaveLength(2)
  await act(async () => { validations.forEach((resolve) => resolve({} as Awaited<ReturnType<typeof requireActiveDiningTable>>)) })
  expect(submitOrderUpdate).toHaveBeenCalledTimes(1)
  expect(await second).toBeNull()
  await act(async () => { finish({ order: { ...order, status: 'submitted' } } as Awaited<ReturnType<typeof submitOrderUpdate>>); await first })
  expect(saveOrderOutboxRecord).not.toHaveBeenCalled()
  expect(submitOrderUpdate).toHaveBeenCalledTimes(1)
})
