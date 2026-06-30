import { apiRequest } from './apiClient'

export type AnalyticsReportRange = 'today' | 'week' | 'month' | 'custom'

export interface SalesDailySummaryRecord {
  id: number
  summary_date: string
  organization_id: number | null
  store_id: number | null
  gross_sales: number
  net_sales: number
  order_count: number
  completed_order_count: number
  cancelled_order_count: number
  average_order_value: number
  total_cost: number
  total_profit: number
  profit_margin: number
  created_at: string
  updated_at: string
}

export interface SalesHourlySummaryRecord {
  id: number
  summary_date: string
  hour_of_day: number
  organization_id: number | null
  store_id: number | null
  sales_amount: number
  order_count: number
  created_at: string
  updated_at: string
}

export interface MenuItemSalesSummaryRecord {
  id: number
  summary_date: string
  organization_id: number | null
  store_id: number | null
  menu_item_id: number | null
  item_name_snapshot_zh: string | null
  item_name_snapshot_en: string | null
  quantity_sold: number
  sales_amount: number
  total_cost: number
  total_profit: number
  order_count: number
  created_at: string
  updated_at: string
}

export interface StorePerformanceSummaryRecord {
  id: number
  summary_date: string
  organization_id: number | null
  store_id: number | null
  sales_amount: number
  order_count: number
  average_order_value: number
  active_order_count: number
  created_at: string
  updated_at: string
}

export interface AnalyticsAlertRecord {
  id: number
  organization_id: number | null
  store_id: number | null
  alert_type: string
  severity: string
  title: string
  message: string
  metric_value: number | null
  comparison_value: number | null
  is_resolved: boolean
  created_at: string
  resolved_at: string | null
}

export interface AnalyticsSummaryResponse {
  organization_id: number | null
  store_id: number | null
  range: string
  start_date: string
  end_date: string
  sales_daily_summaries: SalesDailySummaryRecord[]
  sales_hourly_summaries: SalesHourlySummaryRecord[]
  menu_item_sales_summaries: MenuItemSalesSummaryRecord[]
  store_performance_summaries: StorePerformanceSummaryRecord[]
  analytics_alerts: AnalyticsAlertRecord[]
}

export interface AnalyticsSummaryQuery {
  organizationId?: number | null
  storeId?: number | null
  range: AnalyticsReportRange
  anchorDate?: string | null
  startDate?: string | null
  endDate?: string | null
}

const summaryCache = new Map<string, Promise<AnalyticsSummaryResponse>>()

function buildCacheKey(query: AnalyticsSummaryQuery) {
  return JSON.stringify({
    organizationId: query.organizationId ?? null,
    storeId: query.storeId ?? null,
    range: query.range,
    anchorDate: query.anchorDate ?? null,
    startDate: query.startDate ?? null,
    endDate: query.endDate ?? null,
  })
}

const request = apiRequest

export async function fetchAnalyticsSummaries(query: AnalyticsSummaryQuery) {
  const key = buildCacheKey(query)
  const existing = summaryCache.get(key)
  if (existing) {
    return existing
  }

  const params = new URLSearchParams({
    range: query.range,
  })

  if (query.organizationId != null) {
    params.set('organization_id', String(query.organizationId))
  }
  if (query.storeId != null) {
    params.set('store_id', String(query.storeId))
  }
  if (query.anchorDate) {
    params.set('anchor_date', query.anchorDate)
  }
  if (query.startDate && query.endDate) {
    params.set('start_date', query.startDate)
    params.set('end_date', query.endDate)
  }

  const pending = request<AnalyticsSummaryResponse>(`/api/v1/admin/analytics/summaries?${params.toString()}`)
    .catch((error) => {
      summaryCache.delete(key)
      throw error
    })

  summaryCache.set(key, pending)
  return pending
}

export function clearAnalyticsSummaryCache() {
  summaryCache.clear()
}
