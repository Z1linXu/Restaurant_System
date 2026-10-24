import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { DiningTableOrderGate } from './DiningTableOrderGate'
import { requireActiveDiningTable } from '../../services/frontdeskConfigService'
vi.mock('../../services/frontdeskConfigService', () => ({ requireActiveDiningTable: vi.fn(), DINING_TABLES_CHANGED_EVENT: 'tables-changed' }))
let view: ReactTestRenderer | undefined
const listeners = new Map<string, () => void>()
const opened = vi.fn()
const closed = vi.fn()
function Workspace() { useEffect(() => { opened(); return closed }, []); return <button type="button">existing draft</button> }
beforeEach(() => {
  vi.resetAllMocks(); listeners.clear()
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', { addEventListener: (type: string, handler: () => void) => listeners.set(type, handler), removeEventListener: (type: string) => listeners.delete(type) })
  vi.stubGlobal('document', { visibilityState: 'visible', addEventListener: vi.fn(), removeEventListener: vi.fn() })
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.unstubAllGlobals() })
const gate = (tableNo = '1-A', enabled = true) => <DiningTableOrderGate storeId={2} tableNo={tableNo} enabled={enabled} onBack={vi.fn()}><Workspace /></DiningTableOrderGate>
it('does not restore a cached draft/menu into an orderable workspace when fresh validation rejects a deleted table', async () => {
  vi.mocked(requireActiveDiningTable).mockRejectedValue(new Error('桌台已删除；本机草稿已保留。'))
  await act(async () => { view = create(gate()) })
  expect(requireActiveDiningTable).toHaveBeenCalledWith(2, '1-A')
  expect(opened).not.toHaveBeenCalled()
  expect(JSON.stringify(view!.toJSON())).toContain('本机草稿已保留')
})
it('blocks an existing menu after a focus refresh while retaining its mounted draft state', async () => {
  vi.mocked(requireActiveDiningTable).mockResolvedValue({} as Awaited<ReturnType<typeof requireActiveDiningTable>>)
  await act(async () => { view = create(gate()) })
  expect(opened).toHaveBeenCalledTimes(1)
  vi.mocked(requireActiveDiningTable).mockRejectedValue(new Error('无法确认桌台状态；草稿已保留。'))
  await act(async () => listeners.get('focus')?.())
  expect(view!.root.findAllByType('div').find((node) => node.props.hidden === true)).toBeDefined()
  expect(closed).not.toHaveBeenCalled()
  expect(opened).toHaveBeenCalledTimes(1)
})
it('keeps takeout available without table validation', async () => {
  await act(async () => { view = create(gate('TO-A', false)) })
  expect(requireActiveDiningTable).not.toHaveBeenCalled()
  expect(opened).toHaveBeenCalledTimes(1)
})
it('cannot reopen an old table through a delayed response after context changes', async () => {
  let old!: (table: Awaited<ReturnType<typeof requireActiveDiningTable>>) => void
  vi.mocked(requireActiveDiningTable).mockImplementationOnce(() => new Promise((resolve) => { old = resolve })).mockRejectedValueOnce(new Error('该桌已删除'))
  await act(async () => { view = create(gate('1-A')) })
  await act(async () => view!.update(gate('2-A')))
  await act(async () => old({} as Awaited<ReturnType<typeof requireActiveDiningTable>>))
  expect(opened).not.toHaveBeenCalled()
  expect(JSON.stringify(view!.toJSON())).toContain('该桌已删除')
})
