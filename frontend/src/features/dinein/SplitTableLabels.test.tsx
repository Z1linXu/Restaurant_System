import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, expect, it, vi } from 'vitest'
import { TableCard } from './components/TableCard'
import { OrderingTopBar } from '../ordering/components/OrderingTopBar'
import { OrderSummaryPanel } from '../ordering/components/OrderSummaryPanel'
import { OrderMiniCard } from '../orders/components/OrderMiniCard'
import { OrderHistoryDetail } from '../orders/components/OrderHistoryDetail'
import type { BackendFrontdeskOrderBoardItem, BackendOrderResponse, OrderSession } from '../../types/ordering'
import { buildMenuPath, parseMenuRoute } from '../frontdesk/navigation'
import { buildDraftContextKey } from '../../offline/localDrafts'

vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
let view: ReactTestRenderer | undefined
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })
const noop = () => undefined

it.each([false, true])('shows inside/outside entry buttons while preserving callback identity (compact=%s)', async (compact) => {
  const entry = vi.fn()
  await act(async () => { view = create(<TableCard slot={{ id: '1-full', label: '1', baseTableLabel: '1', zone: 'Main', status: 'available', action: 'entry', mode: 'full', splitLabelMode: 'INSIDE_OUTSIDE' }} onEntrySelect={entry} onStart={noop} onEdit={noop} onPrint={noop} onFinish={noop} compact={compact} />) })
  const button = (name: string) => view!.root.findAllByType('button').find((node) => node.findAllByType('span').some((span) => span.children.join('') === name))!
  await act(async () => button('里').props.onClick())
  await act(async () => button('外').props.onClick())
  expect(entry.mock.calls).toEqual([['1-full', 'left'], ['1-full', 'right']])
})

it('uses display props for initial order, edit and Update summary while raw routes and offline keys remain A/B', async () => {
  const session: OrderSession = { orderId: 'ORDER1', slotLabel: '1-A', tableLabel: '1', status: 'submitted', isModifiedAfterSubmit: true, items: [] }
  const before = JSON.stringify(session)
  await act(async () => { view = create(<><OrderingTopBar tableLabel="1" slotLabel="1-A" tableDisplayName="1" slotDisplayName="1里" onBack={noop} searchValue="" onSearchChange={noop} /><OrderSummaryPanel session={session} slotDisplayName="1里" subtotal={0} tax={0} total={0} localSubmitState="LOCAL_DRAFT" lastBackendSuccessAt={null} onIncrementItem={noop} onDecrementItem={noop} onEditItem={noop} onRemoveItem={noop} onUpdateItemNote={noop} onSaveDraft={noop} onCancelOrder={noop} onSubmitOrder={noop} /></>) })
  const output = JSON.stringify(view!.toJSON())
  expect(output).toContain('1里')
  expect(output).toContain('更新订单')
  expect(output).not.toContain('1-A')
  const context = { slotLabel: session.slotLabel, tableLabel: session.tableLabel, orderType: 'dine_in' as const, pickupLabel: null, workstation: null }
  const path = buildMenuPath(context, 2)
  expect(parseMenuRoute('/stores/2/frontdesk/menu', path.substring(path.indexOf('?')))?.slotLabel).toBe('1-A')
  expect(buildDraftContextKey({ orderType: 'dine_in', slotLabel: session.slotLabel, tableLabel: '1', tableNo: session.slotLabel, pickupNo: null })).toBe('table:1-A')
  expect(JSON.stringify(session)).toBe(before)
})

it('shows the server projection consistently in Order Center and history without rewriting order data', async () => {
  const order = { id: 7, order_no: 'ORDER7', table_no: '1-B', table_display_name: '1外', pickup_no: null, order_type: 'dine_in', status: 'completed', items: [], total_amount: 0 } as unknown as BackendOrderResponse
  const board = { order_id: 7, table_no: '1-B', table_display_name: '1外', pickup_no: null, order_type: 'dine_in', order_status: 'completed' } as BackendFrontdeskOrderBoardItem
  const before = JSON.stringify(order)
  await act(async () => { view = create(<><OrderMiniCard order={board} selected={false} onClick={noop} /><OrderHistoryDetail order={order} loading={false} printOptions={[]} printJobs={[]} printBusy={null} printStatusMessage={null} onReprint={noop} /></>) })
  expect(JSON.stringify(view!.toJSON()).match(/1外/g)).toHaveLength(2)
  expect(JSON.stringify(view!.toJSON())).not.toContain('1-B')
  expect(JSON.stringify(order)).toBe(before)
})

it('keeps a takeout label ending in A untouched in ordering', async () => {
  await act(async () => { view = create(<OrderingTopBar tableLabel="Takeout" slotLabel="TO-A" orderType="pickup" onBack={noop} searchValue="" onSearchChange={noop} />) })
  expect(JSON.stringify(view!.toJSON())).toContain('TO-A')
  expect(JSON.stringify(view!.toJSON())).not.toContain('TO-左')
})
