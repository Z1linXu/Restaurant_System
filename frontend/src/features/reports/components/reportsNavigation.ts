import type { FeaturePackage } from '../../feature-flags/featureConfig'

export type ReportsSection = 'sales' | 'items' | 'stores' | 'profit'

export interface ReportsNavItem {
  id: ReportsSection
  label: string
  path: string
  description: string
}

export const ownerAdminSidebarItems = [
  { id: 'home', label: 'Home', icon: '⌂', description: 'Daily operating overview', path: '/admin/dashboard', feature: 'ADMIN' },
  { id: 'stores', label: 'Stores', icon: '▣', description: 'Store portfolio and health', path: '/admin/settings/tables', feature: 'ADMIN' },
  { id: 'menu', label: 'Menu Management', icon: '☰', description: 'Menu maintenance workspace', path: '/admin/menu/items', feature: 'ADMIN' },
  { id: 'reports', label: 'Reports', icon: '◫', description: 'Sales and performance reports', path: '/admin/reports/sales', feature: 'ANALYTICS' },
  { id: 'integrations', label: 'Integrations', icon: '◎', description: 'Delivery and platform links', path: null, feature: null },
  { id: 'settings', label: 'Settings', icon: '⚙', description: 'Organization-level settings', path: '/admin/settings/printing', feature: 'PRINTING' },
] as const satisfies Array<{
  id: string
  label: string
  icon: string
  description: string
  path: string | null
  feature: FeaturePackage | null
}>

export const reportsNavItems: ReportsNavItem[] = [
  { id: 'sales', label: 'Sales Report', path: '/admin/reports/sales', description: 'Sales and trend reporting' },
  { id: 'items', label: 'Item Sales Report', path: '/admin/reports/items', description: 'Best and worst item performance' },
  { id: 'profit', label: 'Profit Report', path: '/admin/reports/profit', description: 'Sales, cost, and margin analysis' },
  { id: 'stores', label: 'Store Comparison Report', path: '/admin/reports/stores', description: 'Cross-store benchmarking' },
]
