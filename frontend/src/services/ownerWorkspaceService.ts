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
  stores: OwnerOverviewStore[]
}

export interface OwnerOverviewResponse {
  organizations: OwnerOverviewOrganization[]
  generated_at: string
}

export function fetchOwnerOverview() {
  return apiRequest<OwnerOverviewResponse>('/api/v1/owner/overview')
}
