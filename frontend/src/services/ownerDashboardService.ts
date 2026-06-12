const DEFAULT_ADMIN_USER_ID = '2'

interface BackendApiResponse<T> {
  success: boolean
  message?: string
  data: T
}

export type OwnerDashboardRange = 'today' | 'week' | 'month'

export interface OwnerDashboardStoreSummary {
  id: number
  name: string
  code: string
}

export interface OwnerDashboardMetricWithChange {
  value: number
  previous_value: number
  change_pct: number
}

export interface OwnerDashboardKpis {
  sales: OwnerDashboardMetricWithChange
  orders: OwnerDashboardMetricWithChange
  average_order_value: OwnerDashboardMetricWithChange
  active_orders: OwnerDashboardMetricWithChange
}

export interface OwnerDashboardInsight {
  type: string
  title: string
  message: string
  severity: string
}

export interface OwnerDashboardTrendPoint {
  label: string
  value: number
}

export interface OwnerDashboardTrend {
  granularity: 'hourly' | 'daily' | 'weekly' | string
  points: OwnerDashboardTrendPoint[]
}

export interface OwnerDashboardItemPerformance {
  item_name: string
  quantity: number
  revenue: number
  previous_quantity: number
  quantity_change: number
}

export interface OwnerDashboardOrderStatus {
  pending: number
  preparing: number
  ready: number
}

export interface OwnerDashboardStoreComparisonRow {
  store_id: number
  store_name: string
  sales: number
  previous_sales: number
  change_pct: number
  active_orders: number
}

export interface OwnerDashboardRecentOrder {
  order_id: number
  order_no: string
  label: string
  order_type: string
  status: string
  total_amount: number
  occurred_at_label: string
}

export interface OwnerDashboardResponse {
  organization_id: number | null
  organization_name: string
  range: OwnerDashboardRange | string
  compare_enabled: boolean
  stores: OwnerDashboardStoreSummary[]
  kpis: OwnerDashboardKpis
  insights: OwnerDashboardInsight[]
  trend: OwnerDashboardTrend
  top_items: OwnerDashboardItemPerformance[]
  worst_items: OwnerDashboardItemPerformance[]
  order_status: OwnerDashboardOrderStatus
  store_comparison: OwnerDashboardStoreComparisonRow[]
  recent_orders: OwnerDashboardRecentOrder[]
}

async function request<T>(input: string, init?: RequestInit) {
  const response = await fetch(input, init)
  if (!response.ok) {
    throw new Error(`Request failed (${response.status})`)
  }

  const payload = (await response.json()) as BackendApiResponse<T>
  if (!payload.success) {
    throw new Error(payload.message || 'Request failed')
  }

  return payload.data
}

export async function fetchOwnerDashboard(input: {
  organizationId?: number | null
  storeId?: number | null
  range: OwnerDashboardRange
  compare: boolean
}) {
  const params = new URLSearchParams({
    range: input.range,
    compare: String(input.compare),
  })

  if (input.organizationId != null) {
    params.set('organization_id', String(input.organizationId))
  }
  if (input.storeId != null) {
    params.set('store_id', String(input.storeId))
  }

  return request<OwnerDashboardResponse>(`/api/v1/admin/dashboard?${params.toString()}`, {
    headers: {
      'X-User-Id': DEFAULT_ADMIN_USER_ID,
    },
  })
}
