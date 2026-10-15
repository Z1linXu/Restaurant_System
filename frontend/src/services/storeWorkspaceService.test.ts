import { ApiRequestError } from './apiClient'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchOwnerDashboard } from './ownerDashboardService'
import { fetchStoreContext, fetchWorkspaces, type WorkspaceResponse } from './storeWorkspaceService'

const apiRequestMock = vi.hoisted(() => vi.fn())
const snapshots = vi.hoisted(() => ({ workspace: vi.fn(), context: vi.fn(), saveContext: vi.fn() }))

vi.mock('./apiClient', async () => ({
  ...await vi.importActual<typeof import('./apiClient')>('./apiClient'),
  apiRequest: apiRequestMock,
}))
vi.mock('../offline/workspaceSnapshot', async () => ({
  ...await vi.importActual<typeof import('../offline/workspaceSnapshot')>('../offline/workspaceSnapshot'),
  readRestrictedWorkspaceSnapshot: snapshots.workspace, readRestrictedStoreContextSnapshot: snapshots.context,
  saveStoreContextSnapshot: snapshots.saveContext,
}))

describe('admin store scope request contracts', () => {
  beforeEach(() => {
    apiRequestMock.mockReset()
    snapshots.workspace.mockReset(); snapshots.context.mockReset()
    snapshots.saveContext.mockReset().mockResolvedValue(undefined)
    vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
  })

  it('loads selector data with one authorized workspace request instead of platform overview payload', async () => {
    const response: WorkspaceResponse = {
      default_store_id: 2,
      organizations: [{ id: 10, name: 'North', code: 'NORTH', status: 'ACTIVE', role_code: 'OWNER' }],
      stores: [{ id: 2, name: 'North Store', code: 'NORTH-1', status: 'ACTIVE', organization_id: 10, role_code: 'OWNER' }],
    }
    apiRequestMock.mockResolvedValue(response)

    const workspaces = await fetchWorkspaces()

    expect(apiRequestMock).toHaveBeenCalledTimes(1)
    expect(apiRequestMock).toHaveBeenCalledWith('/api/v1/me/workspaces')
    expect(workspaces.stores).toHaveLength(1)
    expect(JSON.stringify(workspaces)).not.toContain('menu_items')
    expect(JSON.stringify(workspaces)).not.toContain('menu_item_options')
  })

  it('sends the selected organization and store once for one dashboard read', async () => {
    apiRequestMock.mockResolvedValue({ organization_id: 10, stores: [] })

    await fetchOwnerDashboard({
      organizationId: 10,
      storeId: 2,
      range: 'today',
      compare: true,
    })

    expect(apiRequestMock).toHaveBeenCalledTimes(1)
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/api/v1/admin/dashboard?range=today&compare=true&organization_id=10&store_id=2',
    )
  })
  it.each([401, 403])('never restores cached merchant data after actual service HTTP %s', async (status) => {
    snapshots.workspace.mockResolvedValue({ stores: [{ id: 999 }] }); snapshots.context.mockResolvedValue({ id: 999 })
    apiRequestMock.mockRejectedValue(new ApiRequestError(status, 'denied'))
    await expect(fetchWorkspaces(7)).rejects.toMatchObject({ status })
    await expect(fetchStoreContext(3, 7)).rejects.toMatchObject({ status })
    expect(snapshots.workspace).not.toHaveBeenCalled(); expect(snapshots.context).not.toHaveBeenCalled()
  })

  it('rejects a foreign Store response before any snapshot write or offline fallback', async () => {
    apiRequestMock.mockResolvedValue({ id: 99, safe_summary: { brand_name: 'Foreign Store' } })
    await expect(fetchStoreContext(3, 7)).rejects.toThrow('STORE_CONTEXT_SCOPE_MISMATCH')
    expect(snapshots.saveContext).not.toHaveBeenCalled()
    expect(snapshots.context).not.toHaveBeenCalled()
  })

})
