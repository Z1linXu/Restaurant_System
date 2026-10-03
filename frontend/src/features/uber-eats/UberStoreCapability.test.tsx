import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { StoreContext, mapStoreContext } from '../store/StoreContextCore'
import type { StoreContextResponse, StoreModuleConfiguration, StoreModuleState } from '../../services/storeWorkspaceService'
import { fetchStoreContext } from '../../services/storeWorkspaceService'

const session = vi.hoisted(() => ({ role: 'OWNER' }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 1, role_code: session.role }, loading: false, isOfflineRestricted: false, permissions: [], features: {}, signOut: vi.fn() }) }))
vi.mock('../dev/DevRoleSwitcher', () => ({ DevRoleSwitcher: () => null }))
vi.mock('../store/StoreSwitcher', () => ({ StoreSwitcher: () => null }))
vi.mock('../owner-admin/OwnerAdminShell', () => ({ OwnerAdminShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }))
vi.mock('./UberInboxPage', () => ({ default: () => <div>Enabled Uber Inbox</div> }))
vi.mock('./UberMappingPage', () => ({ default: () => <div>Enabled Uber Admin</div> }))
vi.mock('./UberInboxBadge', () => ({ UberInboxBadge: ({ storeId }: { storeId: number }) => <button>Uber Eats {storeId}</button> }))
vi.mock('../../services/storeWorkspaceService', async original => ({ ...await original<typeof import('../../services/storeWorkspaceService')>(), fetchStoreContext: vi.fn() }))

function context(id: number, enabled: boolean): StoreContextResponse {
  return { id, name: `Store ${id}`, code: null, status: 'active', organization_id: 1, organization_name: 'Fixture', organization_code: null, role_code: session.role,
    module_configuration: { store_id: id, modules: [{ module_key: 'UBER_EATS', display_name: 'Uber Eats', enabled, persisted: true } as StoreModuleState], validation_issues: [] } as unknown as StoreModuleConfiguration }
}

describe('Uber Store capability on the shared frontend', () => {
  let view: ReactTestRenderer | undefined
  let listener: (() => void) | undefined
  let location: { pathname: string }
  beforeEach(() => {
    session.role = 'OWNER'; vi.clearAllMocks()
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
    location = { pathname: '/stores/51/frontdesk/uber-eats' }
    vi.stubGlobal('window', { location, addEventListener: (name: string, fn: () => void) => { if (name === 'popstate') listener = fn }, removeEventListener: vi.fn() })
    vi.mocked(fetchStoreContext).mockImplementation(async id => context(id, id !== 73))
  })
  afterEach(async () => { if (view) await act(async () => view!.unmount()); vi.unstubAllGlobals() })
  const text = () => JSON.stringify(view!.toJSON())
  const nav = (data: StoreContextResponse) => <StoreContext.Provider value={mapStoreContext(data.id, data, false, null)}><FrontdeskTopNav /></StoreContext.Provider>

  it('shows only enabled Store badges and changes immediately in both switching directions', async () => {
    await act(async () => { view = create(nav(context(51, true))) })
    expect(text()).toContain('Uber Eats ')
    await act(async () => view!.update(nav(context(73, false))))
    expect(text()).not.toContain('Uber Eats')
    await act(async () => view!.update(nav(context(52, true))))
    expect(text()).toContain('Uber Eats ')
  })
  it('never lends an old Store capability to a newly selected Store', () => {
    const value = mapStoreContext(73, context(51, true), false, null)
    expect(value.moduleConfiguration).toBeNull()
    expect(value.loading).toBe(true)
  })
  it.each(['/frontdesk/uber-eats', '/admin/integrations/uber-eats'])('gates the actual App route %s and clears access while the next Store loads', async path => {
    location.pathname = '/stores/51' + path
    await act(async () => { view = create(<App />) })
    expect(text()).toContain(path.startsWith('/admin') ? 'Enabled Uber Admin' : 'Enabled Uber Inbox')
    let resolve!: (data: StoreContextResponse) => void
    vi.mocked(fetchStoreContext).mockImplementationOnce(() => new Promise(done => { resolve = done }))
    await act(async () => { location.pathname = '/stores/73' + path; listener!() })
    expect(text()).not.toContain('Enabled Uber')
    await act(async () => resolve(context(73, false)))
    expect(text()).toContain('Module disabled for this Store: UBER_EATS')
    expect(text()).not.toContain('Enabled Uber')
    await act(async () => { location.pathname = '/stores/52' + path; listener!() })
    expect(text()).toContain(path.startsWith('/admin') ? 'Enabled Uber Admin' : 'Enabled Uber Inbox')
  })
  it.each(['OWNER', 'ADMIN'])('allows an authorized %s into enabled integration settings', async role => {
    session.role = role; location.pathname = '/stores/51/admin/integrations/uber-eats'
    await act(async () => { view = create(<App />) })
    expect(text()).toContain('Enabled Uber Admin')
  })
  it('still rejects frontdesk users from enabled admin settings', async () => {
    session.role = 'FRONTDESK'; location.pathname = '/stores/51/admin/integrations/uber-eats'
    await act(async () => { view = create(<App />) })
    expect(text()).toContain('Access Denied')
    expect(fetchStoreContext).not.toHaveBeenCalled()
  })
})
