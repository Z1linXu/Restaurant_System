import { apiRequest } from './apiClient'

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

export function fetchWorkspaces() {
  return apiRequest<WorkspaceResponse>('/api/v1/me/workspaces')
}

export function fetchStoreContext(storeId: number) {
  return apiRequest<StoreContextResponse>(`/api/v1/stores/${storeId}/context`)
}
