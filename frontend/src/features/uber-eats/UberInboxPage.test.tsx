import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UberInboxPage from './UberInboxPage'
import { useUberInbox } from './useUberInbox'
import { decideUberOrder, type UberOrder } from '../../services/uberEatsService'
vi.mock('../store/useStoreContext', () => ({ useCurrentStore: () => ({ storeId: 12 }) }))
vi.mock('../frontdesk/components/FrontdeskTopNav', () => ({ FrontdeskTopNav: () => null }))
vi.mock('./useUberInbox', () => ({ useUberInbox: vi.fn() }))
vi.mock('../../services/uberEatsService', async original => ({ ...await original<typeof import('../../services/uberEatsService')>(), decideUberOrder: vi.fn() }))
const pending: UberOrder = { id: 1, status: 'PENDING', display_id: 'ABC01', uber_order_id: 'fixture-order', mapping_status: 'MAPPED', mapping_errors: [], last_error: null, local_order_id: null, placed_at: '2026-09-16T16:00:00', created_at: '2026-09-16T16:00:00', accepted_at: null, cancelled_at: null, snapshot: { notes: 'Allergy note', items: [{ id: 'noodle', external_data: 'beef_noodle', title: 'Noodle', quantity: 2, removed: false, notes: '', modifiers: [{ id: 'egg', external_data: 'fried_egg', title: 'Fried Egg', quantity: 1, removed: false, notes: '', modifiers: [], issues: [] }], issues: [] }] } }
describe('Uber inbox staff workflow', () => {
  let view: ReactTestRenderer | undefined
  const refresh = vi.fn()
  beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true); vi.mocked(useUberInbox).mockReturnValue({ orders: [pending], loading: false, error: null, refresh }) })
  afterEach(async () => { if (view) await act(async () => view!.unmount()); vi.unstubAllGlobals() })
  const button = (text: string) => view!.root.findAllByType('button').find(b => b.children.join('').includes(text))!
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
