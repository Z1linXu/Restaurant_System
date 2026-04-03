import type { BackendOrderItemResponse, BackendOrderResponse } from './ordering'

export interface BackendKdsTaskDisplay {
  task_id: number
  order_id: number
  order_no: string
  table_no: string | null
  pickup_no: string | null
  station_code: string
  item_name_snapshot_zh: string
  item_name_snapshot_en: string
  quantity: number
  order_modified_after_submit: boolean
  order_modified_after_submit_at: string | null
  item_modified_after_submit: boolean
  item_modified_after_submit_at: string | null
  status: string
  special_instructions_snapshot: string | null
  size_label: string | null
  noodle_type_label: string | null
  extra_flags: string[]
  created_at: string
  started_at: string | null
  completed_at: string | null
  served_at: string | null
}

export interface BackendKdsHistoryItem {
  order_item_id: number
  category_code_snapshot: string | null
  item_name_snapshot_zh: string
  item_name_snapshot_en: string
  quantity: number
  is_modified_after_submit: boolean
  modified_after_submit_at: string | null
  station_code: string | null
  requires_kitchen_task: boolean
  task_status: string | null
  special_instructions_snapshot: string | null
  started_at: string | null
  completed_at: string | null
  served_at: string | null
}

export interface BackendKdsHistoryOrder {
  order_id: number
  order_no: string
  table_no: string | null
  pickup_no: string | null
  order_status: string
  is_modified_after_submit: boolean
  modified_after_submit_at: string | null
  created_at: string
  ready_at: string | null
  completed_at: string | null
  items: BackendKdsHistoryItem[]
}

export interface NoodleStationOrder {
  detail: BackendOrderResponse
  tasks: BackendKdsTaskDisplay[]
}

export interface KdsOrderGroup {
  key: string
  label: string
  items: BackendOrderItemResponse[]
}

export interface BackendServingShelfItem {
  task_id: number
  order_id: number
  order_item_id: number
  order_no: string
  order_type: string
  table_no: string | null
  pickup_no: string | null
  category_code_snapshot: string | null
  item_name_snapshot_zh: string
  item_name_snapshot_en: string
  quantity: number
  special_instructions_snapshot: string | null
  size_label: string | null
  created_at: string
  ready_for_pickup_at: string | null
}

export interface RealtimeUpdateMessage {
  event_type: string
  store_id: number
  order_id: number | null
  order_item_id: number | null
  order_status: string | null
  task_status: string | null
  beverage_status: string | null
  is_modified_after_submit: boolean | null
  happened_at: string
  suggested_topics: string[]
}
