import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchDiningTables } from '../services/frontdeskConfigService'
import { fetchActiveOrderBoardForStore, subscribeToFrontdeskOrders } from '../services/orderService'
import { useTableBoard } from './useTableBoard'

vi.mock('../services/frontdeskConfigService', () => ({
  fetchDiningTables: vi.fn(),
}))

vi.mock('../services/orderService', () => ({
  fetchActiveOrderBoardForStore: vi.fn(),
  subscribeToFrontdeskOrders: vi.fn(() => vi.fn()),
}))

const mockedFetchDiningTables = vi.mocked(fetchDiningTables)
const mockedFetchActiveOrders = vi.mocked(fetchActiveOrderBoardForStore)
const mockedSubscribe = vi.mocked(subscribeToFrontdeskOrders)

type Board = ReturnType<typeof useTableBoard>
let latestBoard: Board | null = null

function Probe({ storeId, onBoard }: { storeId: number; onBoard: (board: Board) => void }) {
  const board = useTableBoard({ storeId })
  useEffect(() => onBoard(board), [board, onBoard])
  return null
}

const captureBoard = (board: Board) => { latestBoard = board }

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function backendTable(storeId: number, id = storeId) {
  return {
    id,
    store_id: storeId,
    table_code: `S${storeId}`,
    table_name: `Store ${storeId}`,
    capacity: 4,
    area_name: 'MAIN',
    table_config: 'single_only' as const,
    supports_split: false,
    sort_order: 1,
    is_active: true,
  }
}

describe('useTableBoard mounted synchronization', () => {
  let renderer: ReactTestRenderer | null = null
  let visibilityState = 'visible'
  const documentListeners = new Map<string, EventListener>()
  const windowListeners = new Map<string, EventListener>()

  beforeEach(() => {
    latestBoard = null
    visibilityState = 'visible'
    documentListeners.clear()
    windowListeners.clear()
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
    vi.stubGlobal('navigator', { onLine: true })
    vi.stubGlobal('document', {
      get visibilityState() { return visibilityState },
      addEventListener: vi.fn((name: string, listener: EventListener) => documentListeners.set(name, listener)),
      removeEventListener: vi.fn((name: string) => documentListeners.delete(name)),
    })
    vi.stubGlobal('window', {
      setTimeout,
      clearTimeout,
      addEventListener: vi.fn((name: string, listener: EventListener) => windowListeners.set(name, listener)),
      removeEventListener: vi.fn((name: string) => windowListeners.delete(name)),
    })
    mockedFetchDiningTables.mockReset()
    mockedFetchActiveOrders.mockReset()
    mockedSubscribe.mockReset()
    mockedSubscribe.mockImplementation(() => vi.fn())
  })

  afterEach(async () => {
    if (renderer) {
      await act(async () => renderer?.unmount())
      renderer = null
    }
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('shows loading, then represents a legitimate zero-table Store as empty', async () => {
    const tables = deferred<Awaited<ReturnType<typeof fetchDiningTables>>>()
    mockedFetchDiningTables.mockReturnValue(tables.promise)
    mockedFetchActiveOrders.mockResolvedValue([])

    await act(async () => { renderer = create(<Probe storeId={21} onBoard={captureBoard} />) })
    expect(latestBoard?.isLoading).toBe(true)
    expect(latestBoard?.tableSlots).toEqual([])

    await act(async () => { tables.resolve([]); await tables.promise })
    expect(latestBoard?.isLoading).toBe(false)
    expect(latestBoard?.isEmpty).toBe(true)
    expect(latestBoard?.syncError).toBeNull()
  })

  it('keeps a failed load empty and supports an explicit successful retry', async () => {
    mockedFetchDiningTables
      .mockRejectedValueOnce(new Error('table service unavailable'))
      .mockResolvedValueOnce([backendTable(21)])
    mockedFetchActiveOrders.mockResolvedValue([])

    await act(async () => { renderer = create(<Probe storeId={21} onBoard={captureBoard} />) })
    expect(latestBoard?.syncError).toBe('table service unavailable')
    expect(latestBoard?.tableSlots).toEqual([])

    await act(async () => { await latestBoard?.refreshFromBackend({ force: true }) })
    expect(latestBoard?.syncError).toBeNull()
    expect(latestBoard?.tableSlots.map((slot) => slot.label)).toEqual(['Store 21'])
  })

  it('rejects a delayed old-Store response after a rapid Store switch', async () => {
    const oldTables = deferred<Awaited<ReturnType<typeof fetchDiningTables>>>()
    mockedFetchDiningTables.mockImplementation((storeId) => (
      storeId === 18 ? oldTables.promise : Promise.resolve([backendTable(21)])
    ))
    mockedFetchActiveOrders.mockResolvedValue([])

    await act(async () => { renderer = create(<Probe storeId={18} onBoard={captureBoard} />) })
    await act(async () => { renderer?.update(<Probe storeId={21} onBoard={captureBoard} />) })
    expect(latestBoard?.tableSlots.map((slot) => slot.label)).toEqual(['Store 21'])

    await act(async () => { oldTables.resolve([backendTable(18)]); await oldTables.promise })
    expect(latestBoard?.tableSlots.map((slot) => slot.label)).toEqual(['Store 21'])
  })

  it('cancels the old topic and fallback polling when Store scope changes', async () => {
    vi.useFakeTimers()
    window.setTimeout = setTimeout
    window.clearTimeout = clearTimeout
    const unsubscribe18 = vi.fn()
    const unsubscribe21 = vi.fn()
    mockedSubscribe.mockImplementation((storeId) => storeId === 18 ? unsubscribe18 : unsubscribe21)
    mockedFetchDiningTables.mockImplementation(async (storeId) => [backendTable(storeId)])
    mockedFetchActiveOrders.mockResolvedValue([])

    await act(async () => { renderer = create(<Probe storeId={18} onBoard={captureBoard} />) })
    await act(async () => { renderer?.update(<Probe storeId={21} onBoard={captureBoard} />) })
    expect(unsubscribe18).toHaveBeenCalledTimes(1)

    mockedFetchDiningTables.mockClear()
    await act(async () => { await vi.advanceTimersByTimeAsync(29_999) })
    expect(mockedFetchDiningTables).not.toHaveBeenCalled()
    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    expect(mockedFetchDiningTables).toHaveBeenCalledTimes(1)

    await act(async () => renderer?.unmount())
    renderer = null
    expect(unsubscribe21).toHaveBeenCalledTimes(1)
    mockedFetchDiningTables.mockClear()
    await vi.advanceTimersByTimeAsync(120_000)
    expect(mockedFetchDiningTables).not.toHaveBeenCalled()
  })
})
