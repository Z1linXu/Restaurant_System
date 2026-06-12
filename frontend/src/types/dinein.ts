export type ServiceMode = 'dine_in' | 'takeout'

export type TableStatus = 'available' | 'occupied' | 'alert'

export type TableSeatCode = 'A' | 'B'

export type TableConfigMode = 'split_supported' | 'single_only'

export type TableOccupancyMode = 'empty' | 'full' | 'split'

export interface SlotOrder {
  orderId: string
  orderDbId?: number
  orderStatus?: string
  backendTableNo?: string
}

export interface DiningTable {
  id: number
  label: string
  seats: number
  zone: string
  tableConfig: TableConfigMode
  occupancyMode: TableOccupancyMode
  alertMessage?: string
  fullOrder?: SlotOrder
  splitOrders?: Partial<Record<TableSeatCode, SlotOrder>>
}

export interface TableSlot {
  id: string
  label: string
  baseTableLabel: string
  zone: string
  status: TableStatus
  action: 'entry' | 'start' | 'edit'
  mode: 'full' | 'split'
  orderId?: string
  orderDbId?: number
  orderStatus?: string
  backendTableNo?: string
  seatCode?: TableSeatCode
  alertMessage?: string
}

export interface DineInMockData {
  tables: DiningTable[]
}

export interface BackendDiningTableConfig {
  id: number
  store_id: number
  table_code: string
  table_name: string
  area_name: string
  table_config: TableConfigMode
  capacity: number
  supports_split: boolean
  sort_order: number
  is_active: boolean
}
