import { describe, expect, it } from 'vitest'
import { canCreateStoreInOrganization, type OwnerOverviewOrganization } from './ownerWorkspaceService'

function organization(canCreateStore: boolean): OwnerOverviewOrganization {
  return {
    id: 1,
    name: 'Lanzhou Noodles',
    code: 'LANZHOU',
    status: 'active',
    role_code: 'OWNER',
    can_create_store: canCreateStore,
    stores: [],
  }
}

describe('Owner business Store create capability', () => {
  it('shows Create Store only from the canonical backend capability', () => {
    expect(canCreateStoreInOrganization(organization(true))).toBe(true)
    expect(canCreateStoreInOrganization(organization(false))).toBe(false)
  })
})
