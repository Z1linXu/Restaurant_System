import { useEffect } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { StoreContext, mapStoreContext } from './StoreContextCore'
import { RequireStoreModule, RequireStoreModuleManagement } from './StoreContext'
import type { StoreContextResponse } from '../../services/storeWorkspaceService'

vi.mock('react-dom', () => ({ createPortal: (children: React.ReactNode) => children }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { role_code: 'OWNER', store_name: 'User home Store' }, features: {}, permissions: [], signOut: vi.fn() }) }))
vi.mock('./StoreSwitcher', () => ({ StoreSwitcher: () => null }))
let view: ReactTestRenderer | undefined
const network = vi.fn()
function Tool() { useEffect(() => { network() }, []); return <div>Printing tool</div> }
function wrap(children: React.ReactNode) {
  const value = mapStoreContext(1, {
    id: 1, name: 'Selected Store', module_configuration: {
      store_id: 1, modules: [{ module_key: 'PRINTING', enabled: true, persisted: true }],
      validation_issues: [{ module_key: 'PRINTING', code: 'HARDWARE_CAPABILITY_MISSING', target: 'PAD', message: 'Internal detail' }],
    }, safe_summary: { schema_version: 1, config_revision: 1, brand_name: 'Current Brand', locale: 'en-CA', currency: 'CAD', timezone: 'America/Toronto', compatibility_mode: 'NEUTRAL' },
  } as unknown as StoreContextResponse, false, null)
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}
beforeEach(() => {
  network.mockClear()
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', { location: { pathname: '/stores/1/frontdesk' }, addEventListener: vi.fn(), removeEventListener: vi.fn(), requestAnimationFrame: () => 1, cancelAnimationFrame: vi.fn(), setInterval: () => 1, clearInterval: vi.fn() })
  vi.stubGlobal('document', { body: {} })
})
afterEach(async () => { if (view) await act(async () => view!.unmount()); vi.unstubAllGlobals() })

it('keeps the Printing management link accessible with missing hardware and shows current branding', async () => {
  await act(async () => { view = create(wrap(<FrontdeskTopNav />)) })
  expect(JSON.stringify(view!.toJSON())).toContain('Current Brand')
  expect(JSON.stringify(view!.toJSON())).not.toContain('蘭')
  const menu = view!.root.findByProps({ 'aria-label': '用户菜单 / User menu' })
  await act(async () => menu.props.onClick())
  expect(JSON.stringify(view!.toJSON())).toContain('Printing')
  const profile = view!.root.findAllByType('button').find(node => node.findAllByType('span').some(span => span.children.includes('个人信息')))!
  await act(async () => profile.props.onClick())
  expect(JSON.stringify(view!.toJSON())).toContain('Selected Store')
  expect(JSON.stringify(view!.toJSON())).not.toContain('User home Store')
})

it('allows management to mount while runtime stays closed without exposing internal readiness details', async () => {
  await act(async () => { view = create(wrap(<RequireStoreModule moduleKey="PRINTING"><Tool /></RequireStoreModule>)) })
  expect(network).not.toHaveBeenCalled()
  expect(JSON.stringify(view!.toJSON())).not.toMatch(/HARDWARE_CAPABILITY|Internal detail|Legacy|Store module gate/)
  await act(async () => view!.update(wrap(<RequireStoreModuleManagement moduleKey="PRINTING"><Tool /></RequireStoreModuleManagement>)))
  expect(network).toHaveBeenCalledTimes(1)
  expect(JSON.stringify(view!.toJSON())).toContain('Printing tool')
})
