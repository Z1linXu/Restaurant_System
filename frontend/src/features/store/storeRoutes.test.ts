import { describe, expect, it } from 'vitest'
import { defaultWorkspacePathForRole } from './storeRoutes'
import type { WorkspaceResponse } from '../../services/storeWorkspaceService'

function workspace(overrides: Partial<WorkspaceResponse> = {}): WorkspaceResponse {
  return {
    default_store_id: null,
    organizations: [],
    stores: [],
    ...overrides,
  }
}

describe('store route defaults', () => {
  it('routes an org-only Owner workspace to Owner Home', () => {
    expect(defaultWorkspacePathForRole('OWNER', workspace({
      organizations: [{
        id: 100,
        name: 'Lanzhou Group',
        code: 'LANZHOU',
        status: 'active',
        role_code: 'OWNER',
      }],
    }))).toBe('/owner/dashboard')
  })

  it('does not route non-owner org-only workspaces into Owner Home', () => {
    expect(defaultWorkspacePathForRole('MANAGER', workspace({
      organizations: [{
        id: 100,
        name: 'Lanzhou Group',
        code: 'LANZHOU',
        status: 'active',
        role_code: 'MANAGER',
      }],
    }))).toBeNull()
  })

  it('preserves store-scoped defaults when a Store exists', () => {
    expect(defaultWorkspacePathForRole('OWNER', workspace({
      default_store_id: 10,
      stores: [{
        id: 10,
        name: 'St-Denis',
        code: 'ST_DENIS',
        status: 'active',
        organization_id: 100,
        role_code: 'OWNER',
      }],
    }))).toBe('/stores/10/admin/dashboard')
  })
})
