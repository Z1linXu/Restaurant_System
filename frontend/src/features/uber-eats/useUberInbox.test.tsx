import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useUberInbox } from './useUberInbox'
import { fetchUberOrders, type UberOrder } from '../../services/uberEatsService'
import { subscribeToFrontdeskOrders } from '../../services/orderService'
vi.mock('../../services/uberEatsService', () => ({ fetchUberOrders: vi.fn() }))
vi.mock('../../services/orderService', () => ({ subscribeToFrontdeskOrders: vi.fn(() => vi.fn()) }))
describe('durable Uber inbox refresh', () => {
  let renderer: ReactTestRenderer | undefined
  let latest: ReturnType<typeof useUberInbox>
  function Probe({ store }: { store: number }) { const value = useUberInbox(store); useEffect(() => { latest = value }, [value]); return null }
  beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true); vi.stubGlobal('window', { setInterval: vi.fn(() => 1), clearInterval: vi.fn() }); vi.stubGlobal('document', { hidden: false }) })
  afterEach(async () => { if (renderer) await act(async () => renderer!.unmount()); vi.unstubAllGlobals() })
  it('reloads pending orders after remount and on realtime hint', async () => {
    const row = { id: 1, status: 'PENDING' } as UberOrder
    vi.mocked(fetchUberOrders).mockResolvedValue([row])
    await act(async () => { renderer = create(<Probe store={1} />) })
    expect(latest!.orders).toEqual([row])
    await act(async () => renderer!.unmount())
    await act(async () => { renderer = create(<Probe store={1} />) })
    expect(fetchUberOrders).toHaveBeenCalledTimes(2)
    await act(async () => vi.mocked(subscribeToFrontdeskOrders).mock.calls.at(-1)![1]({ event_type: 'uber.inbox.changed' } as never))
    expect(fetchUberOrders).toHaveBeenCalledTimes(3)
  })
  it('never shows previous-store orders while the next store is loading', async () => {
    vi.mocked(fetchUberOrders).mockResolvedValueOnce([{ id: 9 } as UberOrder]).mockReturnValueOnce(new Promise(() => {}))
    await act(async () => { renderer = create(<Probe store={1} />) })
    await act(async () => renderer!.update(<Probe store={2} />))
    expect(latest!.orders).toEqual([]); expect(latest!.loading).toBe(true)
  })
})
