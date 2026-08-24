import { apiRequest } from './apiClient'

export interface OwnerStoreSummary {
  today_orders: number
  today_sales: number | null
  active_orders: number
  occupied_tables: number
  open_tables: number
  failed_print_jobs: number
  printing_mode: string | null
  last_failed_print_at: string | null
  kds_active_count: number | null
  last_updated_at: string | null
}

export interface OwnerOverviewStore {
  id: number
  name: string
  code: string | null
  status: string | null
  store_kind?: string | null
  lifecycle_status?: string | null
  operational_state?: string | null
  is_live?: boolean | null
  provisioning_source?: string | null
  provisioned_profile_code?: string | null
  provisioned_profile_version?: string | null
  provisioned_master_menu_key?: string | null
  provisioned_master_menu_version?: string | null
  role_code: string | null
  features: Record<string, boolean>
  summary: OwnerStoreSummary
}

export interface OwnerOverviewOrganization {
  id: number
  name: string
  code: string | null
  status: string | null
  role_code: string | null
  can_create_store: boolean
  stores: OwnerOverviewStore[]
}

export interface OwnerOverviewResponse {
  organizations: OwnerOverviewOrganization[]
  generated_at: string
}

export function canCreateStoreInOrganization(organization: OwnerOverviewOrganization) {
  return organization.can_create_store === true
}

export function fetchOwnerOverview() {
  return apiRequest<OwnerOverviewResponse>('/api/v1/owner/overview')
}
