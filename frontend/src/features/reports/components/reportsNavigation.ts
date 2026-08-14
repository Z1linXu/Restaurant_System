import type { StoreModuleKey } from '../../store/storeModuleAccess'

export type ReportsSection = 'sales' | 'items' | 'stores' | 'profit'

export interface ReportsNavItem {
  id: ReportsSection
  label: string
  path: string
  description: string
}

export const ownerAdminSidebarItems = [
  { id: 'home', label: 'Home', icon: '⌂', description: 'Daily operating overview', path: '/admin/dashboard', moduleKey: 'STORE_ADMINISTRATION' },
  { id: 'stores', label: 'Stores', icon: '▣', description: 'Store portfolio and health', path: '/admin/settings/tables', moduleKey: 'TABLE_MANAGEMENT' },
  { id: 'menu', label: 'Menu Management', icon: '☰', description: 'Menu maintenance workspace', path: '/admin/menu/items', moduleKey: 'MENU_MANAGEMENT' },
  { id: 'reports', label: 'Reports', icon: '◫', description: 'Sales and performance reports', path: '/admin/reports/sales', moduleKey: 'REPORTING_CORE' },
  { id: 'integrations', label: 'Integrations', icon: '◎', description: 'Delivery and platform links', path: null, moduleKey: null },
  { id: 'settings', label: 'Settings', icon: '⚙', description: 'Organization-level settings', path: '/admin/settings/printing', moduleKey: 'PRINTING' },
] as const satisfies Array<{
  id: string
  label: string
  icon: string
  description: string
  path: string | null
  moduleKey: StoreModuleKey | null
}>

export const reportsNavItems: ReportsNavItem[] = [
  { id: 'sales', label: 'Sales Report', path: '/admin/reports/sales', description: 'Sales and trend reporting' },
  { id: 'items', label: 'Item Sales Report', path: '/admin/reports/items', description: 'Best and worst item performance' },
  { id: 'profit', label: 'Profit Report', path: '/admin/reports/profit', description: 'Sales, cost, and margin analysis' },
  { id: 'stores', label: 'Store Comparison Report', path: '/admin/reports/stores', description: 'Cross-store benchmarking' },
]
