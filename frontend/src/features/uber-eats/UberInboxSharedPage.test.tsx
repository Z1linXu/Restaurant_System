import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UberInboxPage from './UberInboxPage'
import { UberInboxBadge } from './UberInboxBadge'
import { fetchUberInbox, type UberInboxFeed, type UberOrder } from '../../services/uberEatsService'
import { subscribeToFrontdeskOrders } from '../../services/orderService'

vi.mock('../store/useStoreContext', () => ({ useCurrentStore: () => ({ storeId: 12 }) }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 7, organization_id: 1, role_code: 'FRONTDESK' }, sessionMode: 'ONLINE', permissions: ['ORDER_VIEW_ACTIVE'], features: {} }) }))
vi.mock('../frontdesk/components/FrontdeskTopNav', () => ({ FrontdeskTopNav: () => null }))
vi.mock('../../services/uberEatsService', async original => ({ ...await original<typeof import('../../services/uberEatsService')>(), fetchUberInbox: vi.fn() }))
vi.mock('../../services/orderService', () => ({ subscribeToFrontdeskOrders: vi.fn(() => vi.fn()) }))
const order = (id: number, status = 'PRINTED'): UberOrder => ({ id, status, display_id: `TEST${id}`, uber_order_id: `synthetic-${id}`, mapping_status: 'MAPPED', mapping_errors: [], last_error: null, local_order_id: null, placed_at: '2026-11-03T15:00:00', created_at: '2026-11-03T15:00:00', accepted_at: null, cancelled_at: null, snapshot: null })
const page = (orders: UberOrder[]) => ({ orders, has_more: false, next_before_id: null })
const feed = (): UberInboxFeed => ({ business_date: '2026-11-03', time_zone: 'America/Toronto', today: page([order(3), order(2, 'EDIT_REVIEW_REQUIRED')]), attention: page([order(2, 'EDIT_REVIEW_REQUIRED'), order(1, 'WAITING_FOR_RELEASE')]), history: null })

describe('Inbox Page and Badge real shared composition', () => {
  let renderer: ReactTestRenderer | undefined
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(fetchUberInbox).mockResolvedValue(feed())
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
    vi.stubGlobal('window', Object.assign(new EventTarget(), { setInterval: vi.fn(() => 1), clearInterval: vi.fn() }))
    vi.stubGlobal('document', Object.assign(new EventTarget(), { hidden: false }))
  })
  afterEach(async () => { if (renderer) await act(async () => renderer!.unmount()); vi.unstubAllGlobals() })
  const button = (label: string) => renderer!.root.findAllByType('button').find(node => node.children.join('').includes(label))!
  it('uses one stream, splits current/attention cards and requests history only on demand', async () => {
    await act(async () => { renderer = create(<><UberInboxPage /><UberInboxBadge storeId={12} /></>) })
    expect(fetchUberInbox).toHaveBeenCalledTimes(1)
    expect(subscribeToFrontdeskOrders).toHaveBeenCalledTimes(1)
    const today = renderer!.root.findByProps({ 'aria-label': '今日订单' })
    const attention = renderer!.root.findByProps({ 'aria-label': '待处理订单' })
    expect(today.findAllByType('article')).toHaveLength(1)
    expect(attention.findAllByType('article')).toHaveLength(2)
    expect(renderer!.root.findAllByType('article')).toHaveLength(3)
    await act(async () => renderer!.root.findByProps({ 'aria-label': '历史营业日' }).props.onChange({ target: { value: '2026-11-01' } }))
    expect(fetchUberInbox).toHaveBeenCalledTimes(1)
    vi.mocked(fetchUberInbox).mockResolvedValueOnce({ ...feed(), history: page([order(99)]) })
    await act(async () => button('查询历史').props.onClick())
    expect(fetchUberInbox).toHaveBeenLastCalledWith(12, { limit: 100, history_date: '2026-11-01', history_before_id: undefined }, expect.any(AbortSignal))
    expect(renderer!.root.findByProps({ 'aria-label': '历史订单' }).findAllByType('article')).toHaveLength(1)
  })
  it('exposes both independent pagination controls instead of silently truncating pending work', async () => {
    const data = feed()
    data.today.has_more = true; data.today.next_before_id = 3
    data.attention.has_more = true; data.attention.next_before_id = 1
    vi.mocked(fetchUberInbox).mockResolvedValue(data)
    await act(async () => { renderer = create(<><UberInboxPage /><UberInboxBadge storeId={12} /></>) })
    expect(button('更多待处理订单')).toBeDefined()
    expect(button('更多今日订单')).toBeDefined()
    expect(JSON.stringify(renderer!.toJSON())).toContain('+')
  })
})
