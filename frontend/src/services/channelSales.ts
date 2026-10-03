import type { ReportingGroup } from './ownerDashboardService'

export type SalesChannel = 'total' | 'in_store' | 'uber_eats'
export interface ChannelMetric {
  revenue: number | null
  known_revenue: number
  unknown_amount_count: number
  order_count: number
  quantity: number
  average_order_value: number | null
  percentage: number | null
}
export interface ChannelSplit { total: ChannelMetric; in_store: ChannelMetric; uber_eats: ChannelMetric }
export interface ChannelCategory extends ChannelSplit { reporting_group: ReportingGroup }
export interface ChannelItem extends ChannelSplit { item_key: string; item_name: string; reporting_group: ReportingGroup }
export interface ChannelPoint extends ChannelSplit { label: string; date: string; hour: number | null }
export interface ChannelStore extends ChannelSplit { store_id: number }
export interface ChannelSalesSummary {
  revenue_basis: string
  currency: string
  revenue_note: string
  totals: ChannelSplit
  categories: ChannelCategory[]
  noodle_sales: ChannelCategory[]
  items: ChannelItem[]
  trend: ChannelPoint[]
  daily: ChannelPoint[]
  stores: ChannelStore[]
}
export const CHANNEL_LABELS: Record<SalesChannel, string> = { total: 'Total', in_store: 'In-store', uber_eats: 'Uber Eats' }
export function channelMoney(value: number | null) {
  return value == null ? 'Pending' : new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}
