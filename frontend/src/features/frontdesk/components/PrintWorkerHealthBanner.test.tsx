import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PrintWorkerHealthBanner } from './PrintWorkerHealthBanner'
import { StoreContext, mapStoreContext } from '../../store/StoreContextCore'
import type { StoreContextResponse } from '../../../services/storeWorkspaceService'
import { getAndroidPadDeviceBridge } from '../../../types/androidPadBridge'

vi.mock('../../../types/androidPadBridge', async (original) => ({
  ...await original<typeof import('../../../types/androidPadBridge')>(),
  getAndroidPadDeviceBridge: vi.fn(),
}))

function context(mode: string, id = 1) {
  return mapStoreContext(id, {
    id, name: `Store ${id}`, module_configuration: {
      store_id: id, modules: [{ module_key: 'PRINTING', persisted: true, enabled: mode !== 'DISABLED', legacy_runtime_mode: mode }], validation_issues: [],
    },
  } as unknown as StoreContextResponse, false, null)
}

describe('printing health presentation uses the existing Store mode', () => {
  let view: ReactTestRenderer | undefined
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
    vi.stubGlobal('window', globalThis)
    vi.stubGlobal('document', { visibilityState: 'visible', addEventListener: vi.fn(), removeEventListener: vi.fn() })
    vi.mocked(getAndroidPadDeviceBridge).mockReturnValue(undefined)
  })
  afterEach(async () => {
    if (view) await act(async () => view?.unmount())
    view = undefined
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })
  const render = (mode: string, id = 1) => <StoreContext.Provider value={context(mode, id)}><PrintWorkerHealthBanner /></StoreContext.Provider>

  it('explains MOCK receipts without a Pad warning, wake control, or bridge polling', async () => {
    await act(async () => { view = create(render('MOCK')) })
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    const text = JSON.stringify(view!.toJSON())
    expect(text).toContain('模拟打印，不连接实体打印机；提交后在打印中心查看小票。')
    expect(text).not.toContain('Android Pad')
    expect(view!.root.findAllByType('button')).toHaveLength(0)
    expect(getAndroidPadDeviceBridge).not.toHaveBeenCalled()
  })

  it('does not show a worker or poll the native bridge when printing is DISABLED', async () => {
    await act(async () => { view = create(render('DISABLED')) })
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(view!.toJSON()).toBeNull()
    expect(getAndroidPadDeviceBridge).not.toHaveBeenCalled()
  })

  it('preserves the existing PAD_DIRECT browser warning and wake action', async () => {
    await act(async () => { view = create(render('PAD_DIRECT')) })
    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    expect(JSON.stringify(view!.toJSON())).toContain('打印执行器状态仅 Android Pad 可见')
    const wake = view!.root.findByType('button')
    await act(async () => wake.props.onClick())
    expect(JSON.stringify(view!.toJSON())).toContain('当前不是 Android Pad App，无法唤醒本地打印执行器。')
    expect(getAndroidPadDeviceBridge).toHaveBeenCalled()
  })

  it('unmounts the Pad worker on switching to a MOCK Store and stops polling', async () => {
    await act(async () => { view = create(render('PAD_DIRECT')) })
    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    expect(getAndroidPadDeviceBridge).toHaveBeenCalled()
    await act(async () => view!.update(render('MOCK', 2)))
    const callsAtSwitch = vi.mocked(getAndroidPadDeviceBridge).mock.calls.length
    await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
    expect(getAndroidPadDeviceBridge).toHaveBeenCalledTimes(callsAtSwitch)
    expect(JSON.stringify(view!.toJSON())).toContain('模拟打印，不连接实体打印机')
  })
})
