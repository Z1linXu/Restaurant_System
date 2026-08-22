import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchOwnerDashboard } from './ownerDashboardService'
import { fetchWorkspaces, type WorkspaceResponse } from './storeWorkspaceService'

const apiRequestMock = vi.hoisted(() => vi.fn())

vi.mock('./apiClient', () => ({
  apiRequest: apiRequestMock,
}))

describe('admin store scope request contracts', () => {
  beforeEach(() => {
    apiRequestMock.mockReset()
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
})
