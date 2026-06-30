import { apiRequest } from './apiClient'

export interface AuditLogRecord {
  id: number
  store_id?: number | null
  actor_user_id?: number | null
  actor_name_snapshot?: string | null
  actor_role_snapshot?: string | null
  action: string
  entity_type?: string | null
  entity_id?: number | null
  summary?: string | null
  metadata_json?: string | null
  created_at: string
  request_ip?: string | null
  user_agent?: string | null
}

export interface AuditLogPage {
  records: AuditLogRecord[]
  page: number
  size: number
  total_count: number
}

export function fetchAuditLogs(input: {
  storeId?: number | null
  date?: string
  actor?: string
  action?: string
  page?: number
  size?: number
}) {
  const params = new URLSearchParams()
  if (input.storeId) {
    params.set('store_id', String(input.storeId))
  }
  if (input.date) {
    params.set('date', input.date)
  }
  if (input.actor) {
    params.set('actor', input.actor)
  }
  if (input.action) {
    params.set('action', input.action)
  }
  params.set('page', String(input.page ?? 0))
  params.set('size', String(input.size ?? 50))
  return apiRequest<AuditLogPage>(`/api/v1/admin/audit-logs?${params.toString()}`)
}
