import type {
  AnalyticsReportRange,
  AnalyticsSummaryQuery,
  MenuItemSalesSummaryRecord,
  SalesDailySummaryRecord,
  SalesHourlySummaryRecord,
  StorePerformanceSummaryRecord,
} from '../../services/analyticsReportService'

export function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency: 'CAD',
    minimumFractionDigits: 2,
  }).format(value)
}

export function formatPercent(value: number) {
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(1)}%`
}

export function sumNumber(values: number[]) {
  return values.reduce((total, value) => total + value, 0)
}

export function percentageChange(current: number, previous: number) {
  if (previous === 0) {
    return current === 0 ? 0 : 100
  }
  return ((current - previous) / previous) * 100
}

export function computeAverageOrderValue(totalSales: number, orderCount: number) {
  return orderCount > 0 ? totalSales / orderCount : 0
}

export function toReportQuery(input: {
  organizationId: number | null
  storeId: string
  range: AnalyticsReportRange
  customStartDate: string
  customEndDate: string
}) {
  const query: AnalyticsSummaryQuery = {
    organizationId: input.organizationId,
    storeId: input.storeId === 'ALL' ? null : Number(input.storeId),
    range: input.range,
  }

  if (input.range === 'custom' && input.customStartDate && input.customEndDate) {
    query.startDate = input.customStartDate
    query.endDate = input.customEndDate
  }

  return query
}

export function toPreviousReportQuery(input: {
  organizationId: number | null
  storeId: string
  range: AnalyticsReportRange
  customStartDate: string
  customEndDate: string
}) {
  const base = toReportQuery(input)
  if (input.range === 'today') {
    return { ...base, anchorDate: shiftDate(daysAgo(1), 0) }
  }
  if (input.range === 'week') {
    return { ...base, anchorDate: shiftDate(getCurrentWeekStart(), -1) }
  }
  if (input.range === 'month') {
    const firstOfMonth = getCurrentMonthStart()
    const previousMonthAnchor = new Date(firstOfMonth)
    previousMonthAnchor.setDate(previousMonthAnchor.getDate() - 1)
    return { ...base, anchorDate: toLocalIsoDate(previousMonthAnchor) }
  }
  if (input.customStartDate && input.customEndDate) {
    const start = new Date(`${input.customStartDate}T00:00:00`)
    const end = new Date(`${input.customEndDate}T00:00:00`)
    const days = Math.max(Math.round((end.getTime() - start.getTime()) / 86400000) + 1, 1)
    const previousEnd = new Date(start)
    previousEnd.setDate(previousEnd.getDate() - 1)
    const previousStart = new Date(previousEnd)
    previousStart.setDate(previousStart.getDate() - (days - 1))
    return {
      ...base,
      startDate: toLocalIsoDate(previousStart),
      endDate: toLocalIsoDate(previousEnd),
    }
  }
  return base
}

export function buildSalesTrendPoints(
  range: AnalyticsReportRange,
  daily: SalesDailySummaryRecord[],
  hourly: SalesHourlySummaryRecord[],
  startDate?: string,
  endDate?: string,
) {
  if (range === 'today') {
    return hourly.map((entry) => ({
      label: `${String(entry.hour_of_day).padStart(2, '0')}:00`,
      value: Number(entry.sales_amount ?? 0),
    }))
  }

  return fillMissingDailySummaries(daily, startDate, endDate).map((entry) => ({
    label: entry.summary_date.slice(5),
    value: Number(entry.net_sales ?? 0),
  }))
}

export function fillMissingDailySummaries(
  rows: SalesDailySummaryRecord[],
  startDate?: string,
  endDate?: string,
) {
  if (!startDate || !endDate) {
    return rows
  }

  const byDate = new Map(rows.map((row) => [normalizeDateKey(row.summary_date), row]))
  const filled: SalesDailySummaryRecord[] = []
  let cursor = new Date(`${startDate}T00:00:00`)
  const end = new Date(`${endDate}T00:00:00`)

  while (cursor.getTime() <= end.getTime()) {
    const dateKey = toLocalIsoDate(cursor)
    const existing = byDate.get(dateKey)
    if (existing) {
      filled.push(existing)
    } else {
      filled.push({
        id: Number(`0${dateKey.replaceAll('-', '')}`),
        summary_date: dateKey,
        organization_id: rows[0]?.organization_id ?? null,
        store_id: rows[0]?.store_id ?? null,
        gross_sales: 0,
        net_sales: 0,
        order_count: 0,
        completed_order_count: 0,
        cancelled_order_count: 0,
        average_order_value: 0,
        total_cost: 0,
        total_profit: 0,
        profit_margin: 0,
        created_at: `${dateKey}T00:00:00`,
        updated_at: `${dateKey}T00:00:00`,
      })
    }
    cursor.setDate(cursor.getDate() + 1)
  }

  return filled
}

export function aggregateItemSales(rows: MenuItemSalesSummaryRecord[]) {
  const byItem = new Map<string, { itemName: string; quantity: number; revenue: number; totalCost: number; totalProfit: number; orderCount: number }>()
  for (const row of rows) {
    const itemName = row.item_name_snapshot_zh || row.item_name_snapshot_en || `Item ${row.menu_item_id ?? 'Unknown'}`
    const key = String(row.menu_item_id ?? itemName)
    const current = byItem.get(key) ?? { itemName, quantity: 0, revenue: 0, totalCost: 0, totalProfit: 0, orderCount: 0 }
    current.quantity += Number(row.quantity_sold ?? 0)
    current.revenue += Number(row.sales_amount ?? 0)
    current.totalCost += Number(row.total_cost ?? 0)
    current.totalProfit += Number(row.total_profit ?? 0)
    current.orderCount += Number(row.order_count ?? 0)
    byItem.set(key, current)
  }
  return [...byItem.entries()].map(([key, value]) => ({
    key,
    itemName: value.itemName,
    quantity: value.quantity,
    revenue: value.revenue,
    totalCost: value.totalCost,
    totalProfit: value.totalProfit,
    marginPct: value.revenue > 0 ? (value.totalProfit / value.revenue) * 100 : 0,
    orderCount: value.orderCount,
  }))
}

export function aggregateStorePerformance(
  rows: StorePerformanceSummaryRecord[],
  storeNames: Map<number, string>,
) {
  const byStore = new Map<number, { storeId: number; storeName: string; sales: number; orderCount: number; activeOrders: number }>()
  for (const row of rows) {
    const storeId = Number(row.store_id ?? 0)
    const current = byStore.get(storeId) ?? {
      storeId,
      storeName: storeNames.get(storeId) ?? `Store ${storeId}`,
      sales: 0,
      orderCount: 0,
      activeOrders: 0,
    }
    current.sales += Number(row.sales_amount ?? 0)
    current.orderCount += Number(row.order_count ?? 0)
    current.activeOrders = Number(row.active_order_count ?? current.activeOrders)
    byStore.set(storeId, current)
  }

  return [...byStore.values()].map((row) => ({
    ...row,
    averageOrderValue: computeAverageOrderValue(row.sales, row.orderCount),
  }))
}

export function aggregateDailyTotals(rows: SalesDailySummaryRecord[]) {
  const totalSales = sumNumber(rows.map((row) => Number(row.net_sales ?? 0)))
  const totalCost = sumNumber(rows.map((row) => Number(row.total_cost ?? 0)))
  const totalProfit = sumNumber(rows.map((row) => Number(row.total_profit ?? 0)))
  const completedOrders = sumNumber(rows.map((row) => Number(row.completed_order_count ?? row.order_count ?? 0)))
  const allOrders = sumNumber(rows.map((row) => Number(row.order_count ?? 0)))
  return {
    totalSales,
    totalCost,
    totalProfit,
    profitMargin: totalSales > 0 ? (totalProfit / totalSales) * 100 : 0,
    completedOrders,
    allOrders,
    averageOrderValue: computeAverageOrderValue(totalSales, completedOrders),
  }
}

function daysAgo(days: number) {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return date
}

function getCurrentWeekStart() {
  const date = new Date()
  const day = date.getDay()
  const offset = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + offset)
  return date
}

function getCurrentMonthStart() {
  const date = new Date()
  date.setDate(1)
  return date
}

function shiftDate(source: Date, deltaDays: number) {
  const next = new Date(source)
  next.setDate(next.getDate() + deltaDays)
  return toLocalIsoDate(next)
}

function toLocalIsoDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function normalizeDateKey(value: string) {
  return value.slice(0, 10)
}
