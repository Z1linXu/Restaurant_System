import type { ChannelSalesSummary } from './channelSales'
import { apiRequest } from './apiClient'

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

export type ReportingGroup = 'SOUP_NOODLE' | 'DRY_NOODLE' | 'FRIED_NOODLE' | 'DRINK' | 'ALCOHOL' | 'SIDE' | 'FRIED' | 'OTHER'

export interface OwnerDashboardCategorySales {
  reporting_group: ReportingGroup
  quantity_sold: number
  revenue: number
  percentage: number
}

export interface OwnerDashboardResponse {
  channel_sales?: ChannelSalesSummary
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
  sales_timestamp: string
  noodle_sales: OwnerDashboardCategorySales[]
  revenue_mix: OwnerDashboardCategorySales[]
}

const request = apiRequest

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

  return request<OwnerDashboardResponse>(`/api/v1/admin/dashboard?${params.toString()}`)
}
