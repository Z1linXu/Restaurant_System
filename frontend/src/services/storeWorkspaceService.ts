import { apiRequest } from './apiClient'
import { canUseOfflineSnapshot } from '../offline/offlineFallbackPolicy'
import {
  readRestrictedStoreContextSnapshot,
  readRestrictedWorkspaceSnapshot,
  saveStoreContextSnapshot,
  saveWorkspaceSnapshot,
} from '../offline/workspaceSnapshot'

export interface WorkspaceStore {
  id: number
  name: string
  code: string | null
  status: string | null
  organization_id: number | null
  role_code: string | null
}

export interface WorkspaceOrganization {
  id: number
  name: string
  code: string | null
  status: string | null
  role_code: string | null
}

export interface WorkspaceResponse {
  default_store_id: number | null
  organizations: WorkspaceOrganization[]
  stores: WorkspaceStore[]
}

export interface StoreContextResponse {
  id: number
  name: string
  code: string | null
  status: string | null
  organization_id: number | null
  organization_name: string | null
  organization_code: string | null
  role_code: string | null
}

interface OfflineSnapshotOptions {
  preferOfflineSnapshot?: boolean
}

export async function fetchWorkspaces(accountId?: number | null, options: OfflineSnapshotOptions = {}) {
  if (accountId != null && options.preferOfflineSnapshot) {
    const cached = await readRestrictedWorkspaceSnapshot(accountId).catch(() => null)
    if (cached) {
      return cached
    }
  }
  try {
    const response = await apiRequest<WorkspaceResponse>('/api/v1/me/workspaces')
    if (accountId != null) {
      await saveWorkspaceSnapshot(accountId, response).catch((snapshotError) => {
        console.warn('[storeWorkspaceService] unable to save workspace snapshot', snapshotError)
      })
    }
    return response
  } catch (error) {
    if (accountId != null && canUseOfflineSnapshot(error)) {
      const cached = await readRestrictedWorkspaceSnapshot(accountId).catch(() => null)
      if (cached) {
        return cached
      }
    }
    throw error
  }
}

export async function fetchStoreContext(
  storeId: number,
  accountId?: number | null,
  options: OfflineSnapshotOptions = {},
) {
  if (accountId != null && options.preferOfflineSnapshot) {
    const cached = await readRestrictedStoreContextSnapshot(accountId, storeId).catch(() => null)
    if (cached) {
      return cached
    }
  }
  try {
    const response = await apiRequest<StoreContextResponse>(`/api/v1/stores/${storeId}/context`)
    if (accountId != null) {
      await saveStoreContextSnapshot(accountId, response).catch((snapshotError) => {
        console.warn('[storeWorkspaceService] unable to save store context snapshot', snapshotError)
      })
    }
    return response
  } catch (error) {
    if (accountId != null && canUseOfflineSnapshot(error)) {
      const cached = await readRestrictedStoreContextSnapshot(accountId, storeId).catch(() => null)
      if (cached) {
        return cached
      }
    }
    throw error
  }
}
