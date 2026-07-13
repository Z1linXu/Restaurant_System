import { describe, expect, it } from 'vitest'
import { ApiRequestError } from '../services/apiClient'
import type { WorkspaceResponse } from '../services/storeWorkspaceService'
import { canUseOfflineSnapshot } from './offlineFallbackPolicy'
import {
  isWorkspaceSnapshotFresh,
  restrictWorkspaceToActiveStore,
  WORKSPACE_SNAPSHOT_MAX_AGE_MS,
} from './workspaceSnapshot'

const workspace: WorkspaceResponse = {
  default_store_id: 1,
  organizations: [
    { id: 10, name: 'One', code: 'ONE', status: 'ACTIVE', role_code: 'FRONTDESK' },
    { id: 20, name: 'Two', code: 'TWO', status: 'ACTIVE', role_code: 'FRONTDESK' },
  ],
  stores: [
    { id: 1, name: 'Store One', code: 'S1', status: 'ACTIVE', organization_id: 10, role_code: 'FRONTDESK' },
    { id: 2, name: 'Store Two', code: 'S2', status: 'ACTIVE', organization_id: 20, role_code: 'FRONTDESK' },
  ],
}

describe('restricted offline workspace snapshots', () => {
  it('exposes only the last validated store and its organization', () => {
    expect(restrictWorkspaceToActiveStore(workspace, 2)).toEqual({
      default_store_id: 2,
      organizations: [workspace.organizations[1]],
      stores: [workspace.stores[1]],
    })
    expect(restrictWorkspaceToActiveStore(workspace, 99)).toBeNull()
  })

  it('expires snapshots after the bounded offline window', () => {
    const now = Date.parse('2026-07-13T12:00:00Z')
    expect(isWorkspaceSnapshotFresh(new Date(now - WORKSPACE_SNAPSHOT_MAX_AGE_MS).toISOString(), now)).toBe(true)
    expect(isWorkspaceSnapshotFresh(new Date(now - WORKSPACE_SNAPSHOT_MAX_AGE_MS - 1).toISOString(), now)).toBe(false)
    expect(isWorkspaceSnapshotFresh('invalid', now)).toBe(false)
  })

  it('never falls back for authentication or authorization failures', () => {
    expect(canUseOfflineSnapshot(new ApiRequestError(0, 'offline'))).toBe(true)
    expect(canUseOfflineSnapshot(new ApiRequestError(503, 'unavailable'))).toBe(true)
    expect(canUseOfflineSnapshot(new ApiRequestError(401, 'expired'))).toBe(false)
    expect(canUseOfflineSnapshot(new ApiRequestError(403, 'forbidden'))).toBe(false)
  })
})
