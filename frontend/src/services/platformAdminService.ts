import { apiRequest } from './apiClient'

export interface OrganizationRecord {
  id?: number
  name: string
  code: string
  status?: string
}

export interface TemplateRecord {
  id?: number
  organization_id?: number | null
  name: string
  code: string
  description?: string | null
  source_store_id?: number | null
  default_station_setup_json?: string | null
  default_kds_display_rules_json?: string | null
  default_menu_category_structure_json?: string | null
  default_dining_table_layout_rules_json?: string | null
  default_role_setup_json?: string | null
  is_active?: boolean
}

export interface StoreRecord {
  id?: number
  organization_id?: number | null
  name: string
  code: string
  status?: string
  enable_bar_kitchen_tasks?: boolean
}

export interface DiningTableRecord {
  id?: number
  store_id: number
  table_code: string
  table_name: string
  area_name: string
  table_config: string
  capacity: number
  supports_split: boolean
  sort_order: number
  is_active: boolean
}

export interface StoreKdsDisplayConfigRecord {
  id?: number
  store_id: number
  screen_code: string
  header_layout: string
  density_mode: string
  card_size_mode: string
  config_json: string
}

export interface TemplateCreateStoreInput {
  organization_id: number
  template_id: number
  name: string
  code: string
  status?: string
  copy_menu_items?: boolean
}

export interface PlatformAdminOverview {
  organizations: Record<string, unknown>[]
  templates: Record<string, unknown>[]
  stores: Record<string, unknown>[]
  roles: Record<string, unknown>[]
  users: Record<string, unknown>[]
  stations: Record<string, unknown>[]
  dining_tables: Record<string, unknown>[]
  menu_categories: Record<string, unknown>[]
  menu_items: Record<string, unknown>[]
  menu_item_options: Record<string, unknown>[]
  kds_display_configs: Record<string, unknown>[]
}

export interface MenuItemAdminRecord {
  id?: number
  store_id: number
  category_id: number
  station_id: number
  name_zh: string
  name_en: string
  sku: string
  item_type: string
  base_price: number | null
  cost_per_item: number | null
  is_active: boolean
  is_sold_out: boolean
  created_at?: string
  updated_at?: string
}

const request = apiRequest

function buildHeaders() {
  return {
    'Content-Type': 'application/json',
  }
}

export async function fetchPlatformOverview(storeId: number) {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<PlatformAdminOverview>(`/api/v1/admin/platform/overview?${params.toString()}`)
}

export async function fetchMenuManagementContext(storeId: number) {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<PlatformAdminOverview>(`/api/v1/admin/menu/management-context?${params.toString()}`)
}

export async function fetchAdminMenuItems(storeId: number) {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<MenuItemAdminRecord[]>(`/api/v1/admin/platform/menu/items?${params.toString()}`)
}

export async function savePlatformEntity(path: string, payload: Record<string, unknown>, id?: number) {
  const target = id == null ? `/api/v1/admin/platform/${path}` : `/api/v1/admin/platform/${path}/${id}`
  return request<Record<string, unknown>>(target, {
    method: id == null ? 'POST' : 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify(payload),
  })
}

export async function createStoreFromTemplate(payload: TemplateCreateStoreInput) {
  return request<Record<string, unknown>>('/api/v1/admin/platform/stores/from-template', {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify(payload),
  })
}

export async function rebuildAnalyticsForDate(date: string, storeId: number) {
  const params = new URLSearchParams({
    date,
    store_id: String(storeId),
  })
  return request<string>(`/api/v1/admin/analytics/rebuild?${params.toString()}`, {
    method: 'POST',
  })
}
