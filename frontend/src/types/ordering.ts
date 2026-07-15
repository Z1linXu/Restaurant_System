export interface LocalizedText {
  en: string
  zh: string
}

export interface ChoiceOption {
  id: string
  labelEn: string
  labelZh: string
  priceDelta?: number
  optionType?: string
  optionCode?: string | null
  optionGroup?: string | null
  parentOptionId?: string | null
  sortOrder?: number | null
  sideItemRemoveOptions?: ChoiceOption[]
}

export interface MenuCategory {
  id: string
  labelEn: string
  labelZh: string
  code?: string
}

export interface MenuItemCustomizationConfig {
  sizes?: {
    required: boolean
    options: ChoiceOption[]
  }
  soupBases?: {
    required: boolean
    options: ChoiceOption[]
  }
  noodleTypes?: ChoiceOption[]
  spicyLevels?: ChoiceOption[]
  combo?: {
    optionId?: string
    upcharge: number
    eggs: ChoiceOption[]
    sides: ChoiceOption[]
    sideRemoveOptions: ChoiceOption[]
  }
  addOns?: ChoiceOption[]
  removeOptions?: ChoiceOption[]
}

export interface MenuItem {
  id: string
  sku?: string
  sortOrder?: number | null
  categoryId: string
  categoryCode?: string
  itemType?: string
  nameEn: string
  nameZh: string
  descriptionEn: string
  descriptionZh: string
  price: number
  isActive?: boolean
  soldOut?: boolean
  badge?: LocalizedText
  customization?: MenuItemCustomizationConfig
}

export interface ItemSelectionState {
  sizeId?: string
  soupBaseId?: string
  noodleTypeId?: string
  spicyLevelId?: string
  comboEnabled: boolean
  comboEggId?: string
  comboSideId?: string
  comboSideRemoveIds: string[]
  addOnQuantities: Record<string, number>
  removeIds: string[]
  quantity: number
  notes: string
}

export interface OrderLineItem {
  id: string
  menuItemId: string
  nameEn: string
  nameZh: string
  quantity: number
  unitPrice: number
  lineSubtotal: number
  selection: ItemSelectionState
  summaryTags: LocalizedText[]
  notes: string
  locked?: boolean
}

export interface OrderSession {
  orderId: string
  slotLabel: string
  tableLabel: string
  status: 'draft' | 'submitted' | 'preparing'
  isModifiedAfterSubmit: boolean
  items: OrderLineItem[]
}

export interface ItemCustomizationDraft extends ItemSelectionState {
  itemId?: string
}

export interface BackendMenuCatalogResponse {
  success: boolean
  message: string
  data: BackendMenuCatalog
}

export interface BackendMenuCatalog {
  store_id: number
  organization_id: number
  menu_revision: number
  generated_at: string
  catalog_version: string
  combo_metadata_version: string
  content_hash: string
  tax_policy: {
    rate: number
    label: string
    version: string
  }
  categories: BackendMenuCategory[]
}

export interface BackendMenuCategory {
  id: number
  code: string
  name_zh: string
  name_en: string
  sort_order: number | null
  is_active: boolean
  items: BackendMenuItem[]
}

export interface BackendMenuItem {
  id: number
  category_id: number
  station_id: number
  name_zh: string
  name_en: string
  sku: string
  item_type: string
  base_price: number
  is_active: boolean
  is_sold_out: boolean
  sort_order: number | null
  options: BackendMenuOption[]
}

export interface BackendMenuOption {
  id: number
  option_type: 'size' | 'soup_base' | 'noodle_type' | 'addon' | 'remove' | 'spicy_level' | string
  option_code: string | null
  option_group: string | null
  parent_option_id: number | null
  sort_order: number | null
  name_zh: string
  name_en: string
  price_delta: number
  is_active: boolean
  side_item_remove_options?: BackendMenuOption[]
}

export interface OrderingCatalog {
  storeId: string
  organizationId: string
  menuRevision: number
  generatedAt: string
  contentHash: string
  taxPolicy: BackendMenuCatalog['tax_policy']
  categories: MenuCategory[]
  items: MenuItem[]
}

export interface BackendApiResponse<T> {
  success: boolean
  message: string
  data: T
  error_code?: string | null
}

export interface BackendFrontdeskOrderBoardItem {
  order_id: number
  order_no: string
  order_type: string
  table_no: string | null
  pickup_no: string | null
  order_status: string
  is_modified_after_submit: boolean
  modified_after_submit_at: string | null
  submitted_at: string | null
  updated_at: string | null
  total_item_count: number
  ready_item_count: number
  beverage_pending_count: number
  kitchen_pending_count: number
  total_amount: number
}

export interface BackendOrderResponse {
  id: number
  order_no: string
  status: string
  store_id: number
  created_by: number
  order_type: string
  table_no: string | null
  pickup_no: string | null
  subtotal_amount: number
  discount_amount: number
  total_amount: number
  submitted_at: string | null
  ready_at: string | null
  completed_at: string | null
  is_modified_after_submit: boolean
  modified_after_submit_at: string | null
  modified_after_submit_by: number | null
  current_revision: number
  created_at: string
  updated_at: string
  items: BackendOrderItemResponse[]
  beverage_items: BackendOrderItemResponse[]
  kitchen_items: BackendOrderItemResponse[]
}

export interface BackendOrderItemResponse {
  id: number
  menu_item_id: number
  category_code_snapshot: string | null
  item_name_snapshot_zh: string
  item_name_snapshot_en: string
  quantity: number
  unit_price: number
  line_amount: number
  combo_group_no: number | null
  combo_role: string | null
  notes: string | null
  is_modified_after_submit: boolean
  modified_after_submit_at: string | null
  added_revision: number | null
  order_update_batch_id: number | null
  requires_kitchen_task: boolean
  is_beverage_item: boolean
  is_kitchen_related_item: boolean
  station_code: string | null
  task_status: string | null
  started_at: string | null
  ready_for_pickup_at: string | null
  served_at: string | null
  beverage_status: string | null
  beverage_special_instructions_snapshot: string | null
  beverage_started_at: string | null
  beverage_ready_at: string | null
  beverage_served_at: string | null
  beverage_cancelled_at: string | null
  options: BackendOrderItemOptionResponse[]
}

export interface BackendOrderUpdateResponse {
  order: BackendOrderResponse
  update_batch_id: number
  revision: number
  already_processed: boolean
}

export interface OrderPrintOption {
  module_code: string
  label: string
  available: boolean
  unavailable_reason: string | null
}

export interface BackendOrderItemOptionResponse {
  id: number
  option_id: number
  option_type_snapshot: string
  option_code_snapshot: string | null
  option_group_snapshot: string | null
  parent_option_id_snapshot: number | null
  option_name_snapshot_zh: string
  option_name_snapshot_en: string
  price_delta: number
  quantity: number
}

export type SplitBillCount = 1 | 2 | 3 | 4
export type SplitMode = 'UNASSIGNED' | 'SINGLE' | 'SHARED'
export type SharedMethod = 'EQUAL' | 'MANUAL'
export type BillId = 'A' | 'B' | 'C' | 'D'

export interface ItemAllocationUnassigned {
  mode: 'UNASSIGNED'
}

export interface ItemAllocationSingle {
  mode: 'SINGLE'
  billId: BillId
}

export interface ItemAllocationSharedParticipant {
  billId: BillId
  amount: number
}

export interface ItemAllocationShared {
  mode: 'SHARED'
  method: SharedMethod
  participants: ItemAllocationSharedParticipant[]
}

export type ItemAllocation = ItemAllocationUnassigned | ItemAllocationSingle | ItemAllocationShared
