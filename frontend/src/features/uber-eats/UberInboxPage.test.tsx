import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UberInboxPage from './UberInboxPage'
import { useUberInbox } from './useUberInbox'
import { decideUberOrder, reprintUberKitchen, type UberItem, type UberOrder } from '../../services/uberEatsService'
vi.mock('../store/useStoreContext', () => ({ useCurrentStore: () => ({ storeId: 12 }) }))
vi.mock('../frontdesk/components/FrontdeskTopNav', () => ({ FrontdeskTopNav: () => null }))
vi.mock('./useUberInbox', () => ({ useUberInbox: vi.fn() }))
vi.mock('../../services/uberEatsService', async original => ({ ...await original<typeof import('../../services/uberEatsService')>(), decideUberOrder: vi.fn(), reprintUberKitchen: vi.fn() }))
const pending: UberOrder = { id: 1, status: 'PENDING', display_id: 'ABC01', uber_order_id: 'fixture-order', mapping_status: 'MAPPED', mapping_errors: [], last_error: null, local_order_id: null, placed_at: '2026-09-16T16:00:00', created_at: '2026-09-16T16:00:00', accepted_at: null, cancelled_at: null, snapshot: { notes: 'Allergy note', items: [{ id: 'noodle', external_data: 'beef_noodle', title: 'Noodle', quantity: 2, removed: false, notes: '', modifiers: [{ id: 'egg', external_data: 'fried_egg', title: 'Fried Egg', quantity: 1, removed: false, notes: '', modifiers: [], issues: [] }], issues: [] }] } }
describe('Uber inbox staff workflow', () => {
  let view: ReactTestRenderer | undefined
  const refresh = vi.fn()
  beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true); vi.mocked(useUberInbox).mockReturnValue({ orders: [pending], loading: false, error: null, refresh }) })
  afterEach(async () => { if (view) await act(async () => view!.unmount()); vi.unstubAllGlobals() })
  const button = (text: string) => view!.root.findAllByType('button').find(b => b.children.join('').includes(text))!
  const item = (title: string, notes: string, modifiers: UberItem[] = []): UberItem => ({ id: title, external_data: title, title, quantity: 1, removed: false, notes, modifiers, issues: [] })
  const renderOrder = async (order: UberOrder) => {
    vi.mocked(useUberInbox).mockReturnValue({ orders: [order], loading: false, error: null, refresh })
    await act(async () => { view = create(<UberInboxPage />) })
  }
  const noteParagraphs = () => view!.root.findAllByType('p').map(p => p.children.join('')).filter(text => text.startsWith('订单备注：') || text.startsWith('商品备注：'))
  const itemNotes = (title: string) => view!.root.findAllByType('span').find(span => span.children.join('') === `${title} ×1`)!.parent!.children.filter(child => typeof child !== 'string' && child.type === 'p').map(child => typeof child === 'string' ? child : child.children.join(''))
  it('shows a cart note once for ten items without fanning it out under the items', async () => {
    const note = 'Please check order contents are correct'
    await renderOrder({ ...pending, order_note_snapshot: note, snapshot: { notes: note, items: Array.from({ length: 10 }, (_, index) => item(`Noodle ${index}`, note)) } })
    expect(noteParagraphs()).toEqual([`订单备注：${note}`])
  })
  it('keeps each distinct item note at its own root or modifier, including the same note on different items', async () => {
    await renderOrder({ ...pending, snapshot: { notes: 'Pack carefully', items: [item('Soup', 'no onion', [item('Egg', 'soft please')]), item('Fried noodle', 'no onion'), item('Drink', '')] } })
    expect(itemNotes('Soup')).toEqual(['商品备注：no onion'])
    expect(itemNotes('Egg')).toEqual(['商品备注：soft please'])
    expect(itemNotes('Fried noodle')).toEqual(['商品备注：no onion'])
    expect(itemNotes('Drink')).toEqual([])
    expect(noteParagraphs()).toEqual(['商品备注：no onion', '商品备注：soft please', '商品备注：no onion', '订单备注：Pack carefully'])
  })
  it('keeps an item-only note when no cart note exists', async () => {
    await renderOrder({ ...pending, snapshot: { notes: '', items: [item('Soup', 'no onion')] } })
    expect(noteParagraphs()).toEqual(['商品备注：no onion'])
  })
  it('deduplicates only exact whitespace-normalized cart and item notes, recursively', async () => {
    await renderOrder({ ...pending, order_note_snapshot: '  no\r\n onion  ', snapshot: { notes: 'stale note', items: [item('Soup', 'no   onion', [item('Egg', ' no\t onion ')]), item('Fried noodle', 'no onion please'), item('Drink', 'No onion')] } })
    expect(noteParagraphs()).toEqual(['商品备注：no onion please', '商品备注：No onion', '订单备注：  no\r\n onion  '])
  })
  it('uses Unicode whitespace including NEL and em spaces for exact note deduplication', async () => {
    await renderOrder({ ...pending, order_note_snapshot: '\u0085no\u2003onion\u00a0', snapshot: { notes: '', items: [item('Soup', 'no onion'), item('Drink', 'no onion please')] } })
    expect(noteParagraphs()).toEqual(['商品备注：no onion please', '订单备注：\u0085no\u2003onion\u00a0'])
  })
  it.each([undefined, null])('falls back to source cart note when the frozen field is %s on an old order', async frozenNote => {
    await renderOrder({ ...pending, order_note_snapshot: frozenNote })
    expect(noteParagraphs()).toEqual(['订单备注：Allergy note'])
  })
  it('uses the frozen order note instead of a newer source cart note', async () => {
    await renderOrder({ ...pending, order_note_snapshot: 'Frozen order note', snapshot: { notes: 'New cart note', items: [item('Soup', 'Frozen order note'), item('Drink', 'Own note')] } })
    expect(noteParagraphs()).toEqual(['商品备注：Own note', '订单备注：Frozen order note'])
  })
  it('treats an empty frozen order note as explicitly absent and keeps the item note', async () => {
    await renderOrder({ ...pending, order_note_snapshot: '', snapshot: { notes: 'Later cart note', items: [item('Soup', 'Later cart note')] } })
    expect(noteParagraphs()).toEqual(['商品备注：Later cart note'])
  })
  it('shows a frozen order note when the source snapshot is unavailable', async () => {
    await renderOrder({ ...pending, order_note_snapshot: 'Frozen order note', snapshot: null })
    expect(noteParagraphs()).toEqual(['订单备注：Frozen order note'])
  })
  it('shows scoped notes once alongside note-free mapped and raw kitchen summaries', async () => {
    await renderOrder({ ...pending, processing_mode: 'KITCHEN_MIRROR', order_note_snapshot: 'Pack together', mapped_items: ['汤面 ×1'], raw_items: ['Unknown noodle ×1', 'Unknown topping ×1'], snapshot: { notes: 'Pack together', items: [item('Soup', 'less spicy'), item('Unknown noodle', 'no onion', [item('Unknown topping', 'extra crispy')])] } })
    expect(noteParagraphs()).toEqual(['商品备注：less spicy', '商品备注：no onion', '商品备注：extra crispy', '订单备注：Pack together'])
    const rendered = JSON.stringify(view!.toJSON())
    expect(rendered).toContain('汤面 ×1')
    expect(rendered).toContain('Unknown noodle ×1')
    expect(rendered).toContain('Unknown topping ×1')
  })
  it('shows notes, quantity, modifier and calls backend exactly once for double tap', async () => {
    let resolve!: (result: UberOrder) => void
    vi.mocked(decideUberOrder).mockReturnValue(new Promise(done => { resolve = done }))
    await act(async () => { view = create(<UberInboxPage />) })
    const rendered = JSON.stringify(view!.toJSON())
    expect(rendered).toContain('Fried Egg'); expect(rendered).toContain('Allergy note'); expect(rendered).toContain('ABC01')
    await act(async () => { const onClick = button('Accept').props.onClick; onClick(); onClick() })
    expect(decideUberOrder).toHaveBeenCalledExactlyOnceWith(12, 1, 'accept', 'CAPACITY')
    expect(button('处理中').props.disabled).toBe(true)
    await act(async () => resolve({ ...pending, status: 'ACCEPTED', local_order_id: 20 }))
    expect(refresh).toHaveBeenCalledOnce()
  })
  it('blocks unmapped acceptance and exposes the precise error', async () => {
    vi.mocked(useUberInbox).mockReturnValue({ orders: [{ ...pending, status: 'MAPPING_REQUIRED', mapping_status: 'MAPPING_REQUIRED', mapping_errors: ['Fried Egg: MODIFIER_MAPPING_MISSING'] }], loading: false, error: null, refresh })
    await act(async () => { view = create(<UberInboxPage />) })
    expect(button('Accept').props.disabled).toBe(true)
    expect(JSON.stringify(view!.toJSON())).toContain('MODIFIER_MAPPING_MISSING')
  })
  it('mirror mode hides remote decisions even if an old PENDING status arrives', async () => {
    vi.mocked(useUberInbox).mockReturnValue({ orders: [{ ...pending, processing_mode: 'KITCHEN_MIRROR', customer_header: 'UBER - Ashton Z', released_at: null }], loading: false, error: null, refresh })
    await act(async () => { view = create(<UberInboxPage />) })
    expect(button('Accept')).toBeUndefined(); expect(button('Deny')).toBeUndefined()
    expect(JSON.stringify(view!.toJSON())).toContain('UBER - Ashton Z')
    expect(decideUberOrder).not.toHaveBeenCalled()
  })
  it('shows the accepted observation time without enabling cashier or remote decision controls', async () => {
    vi.mocked(useUberInbox).mockReturnValue({ orders: [{ ...pending, processing_mode: 'KITCHEN_MIRROR', status: 'WAITING_FOR_ACCEPTANCE', accepted_observed_at: '2026-10-02T17:00:00', released_at: null }], loading: false, error: null, refresh })
    await act(async () => { view = create(<UberInboxPage />) })
    const rendered = JSON.stringify(view!.toJSON())
    expect(rendered).toContain('等待 Uber 接单'); expect(rendered).toContain('接单观察时间')
    expect(rendered).not.toContain('厨房释放通知')
    expect(button('Accept')).toBeUndefined(); expect(button('Deny')).toBeUndefined()
    expect(button('Checkout')).toBeUndefined(); expect(button('Payment')).toBeUndefined()
  })
  it('mirror mode exposes only applicable kitchen reprints through the shared idempotent service', async () => {
    vi.mocked(useUberInbox).mockReturnValue({ orders: [{ ...pending, processing_mode: 'KITCHEN_MIRROR', status: 'PRINTED', local_order_id: 22, customer_header: 'UBER - Ashton Z', grab_status: 'PRINTED', hot_kitchen_status: 'NOT_REQUIRED', mapped_items: ['牛肉面 ×1 · 大碗'] }], loading: false, error: null, refresh })
    vi.mocked(reprintUberKitchen).mockResolvedValue({ id: 10, status: 'PRINTED' } as Awaited<ReturnType<typeof reprintUberKitchen>>)
    await act(async () => { view = create(<UberInboxPage />) })
    expect(button('Accept')).toBeUndefined(); expect(button('HOT KITCHEN')).toBeUndefined()
    await act(async () => button('重打 GRAB').props.onClick())
    expect(reprintUberKitchen).toHaveBeenCalledExactlyOnceWith(22, 'GRAB')
    expect(JSON.stringify(view!.toJSON())).toContain('牛肉面 ×1 · 大碗')
  })
  it('shows mapped and frozen raw content with warnings and keeps both kitchen reprints enabled', async () => {
    vi.mocked(useUberInbox).mockReturnValue({ orders: [{ ...pending, processing_mode: 'KITCHEN_MIRROR', status: 'KITCHEN_SENT_WITH_MAPPING_WARNINGS', mapping_status: 'PARTIALLY_MAPPED', local_order_id: 22, grab_status: 'FAILED', hot_kitchen_status: 'PENDING', mapped_items: ['传统牛肉面 ×1 · 大'], raw_items: ['Unknown Uber Item ×2', 'Unknown Modifier ×1'], mapping_errors: ['UNMAPPED_ROUTE_REVIEW'] }], loading: false, error: null, refresh })
    vi.mocked(reprintUberKitchen).mockResolvedValue({ id: 11, status: 'PENDING' } as Awaited<ReturnType<typeof reprintUberKitchen>>)
    await act(async () => { view = create(<UberInboxPage />) })
    const rendered = JSON.stringify(view!.toJSON())
    expect(rendered).toContain('部分匹配'); expect(rendered).toContain('Unknown Uber Item'); expect(rendered).toContain('传统牛肉面'); expect(rendered).toContain('UNMAPPED_ROUTE_REVIEW')
    expect(button('Accept')).toBeUndefined()
    await act(async () => button('重打 HOT KITCHEN').props.onClick())
    expect(reprintUberKitchen).toHaveBeenCalledWith(22, 'HOT_KITCHEN')
    expect(button('重打 GRAB')).toBeDefined()
  })
  it('requires a denial reason and submits it to the backend', async () => {
    vi.mocked(decideUberOrder).mockResolvedValue({ ...pending, status: 'DENIED' })
    await act(async () => { view = create(<UberInboxPage />) })
    await act(async () => button('Deny').props.onClick())
    await act(async () => button('确认拒单').props.onClick())
    expect(decideUberOrder).toHaveBeenCalledWith(12, 1, 'deny', 'CAPACITY')
  })
  it('shows an error and refreshes authoritative state after a request timeout', async () => {
    vi.mocked(decideUberOrder).mockRejectedValue(new Error('Network timeout'))
    await act(async () => { view = create(<UberInboxPage />) })
    await act(async () => button('Accept').props.onClick())
    expect(JSON.stringify(view!.toJSON())).toContain('Network timeout'); expect(refresh).toHaveBeenCalledOnce()
  })
  it('shows cancellation/edit warnings without new accept controls', async () => {
    vi.mocked(useUberInbox).mockReturnValue({ orders: [{ ...pending, status: 'CANCELLED_REVIEW_REQUIRED', local_order_id: 22 }, { ...pending, id: 2, status: 'EDIT_REVIEW_REQUIRED' }], loading: false, error: null, refresh })
    await act(async () => { view = create(<UberInboxPage />) })
    expect(button('Accept')).toBeUndefined(); expect(JSON.stringify(view!.toJSON())).toContain('厨房内容未自动修改')
  })
})
