import { apiRequest } from './apiClient'

export interface PrinterConfigRecord {
  id?: number
  store_id: number
  name: string
  ip_address: string
  port: number
  printer_type: string
  text_encoding?: string
  escpos_code_page?: number | null
  font_size?: string
  font_size_mode?: string
  enabled: boolean
  paper_width_mm: number
  timeout_ms: number
  last_successful_print_at?: string | null
  last_failed_print_at?: string | null
  last_error_message?: string | null
  last_connection_success_at?: string | null
  last_connection_failed_at?: string | null
  last_connection_error?: string | null
  created_at?: string
  updated_at?: string
}

export interface PrinterAssignmentRecord {
  id?: number
  store_id: number
  printer_id: number | null
  module_code: string
  enabled: boolean
  font_size?: string | null
  takeout_receipt_copies?: number | null
  created_at?: string
  updated_at?: string
}

export interface PrintCenterOverview {
  store_id: number
  printing_enabled: boolean
  printing_mode?: 'REAL' | 'MOCK' | 'DISABLED' | 'PAD_DIRECT'
  cloud_private_printer_guard_active?: boolean
  cloud_private_printer_warning?: string | null
  printers: PrinterConfigRecord[]
  assignments: PrinterAssignmentRecord[]
}

export interface PrinterTestResponse {
  success: boolean
  message: string
}

export interface PrinterConnectionTestResponse {
  success: boolean
  message: string
  checked_at: string
}

export interface PrintJobRecord {
  id: number
  organization_id?: number | null
  store_id: number
  order_id?: number | null
  order_update_batch_id?: number | null
  printer_id?: number | null
  printer_name?: string | null
  printer_endpoint?: string | null
  module_code: string
  receipt_type: string
  status: 'PENDING' | 'CLAIMED' | 'PRINTING' | 'PRINTED' | 'FAILED' | 'CANCELLED'
  execution_mode?: string | null
  rendered_text_snapshot?: string | null
  escpos_payload_base64?: string | null
  error_message?: string | null
  error_code?: string | null
  operator_message?: string | null
  retry_count?: number | null
  max_retry_count?: number | null
  requested_by_user_id?: number | null
  claimed_by_device_id?: number | null
  claimed_at?: string | null
  claim_expires_at?: string | null
  printed_by_device_id?: number | null
  client_attempt_token?: string | null
  created_at: string
  updated_at?: string | null
  printed_at?: string | null
  failed_at?: string | null
  last_attempt_at?: string | null
}

export interface StoreDeviceRecord {
  id: number
  organization_id?: number | null
  store_id: number
  device_name?: string | null
  device_type?: string | null
  status?: string | null
  last_seen_at?: string | null
  app_version?: string | null
  platform?: string | null
  is_active?: boolean | null
  created_at?: string | null
  updated_at?: string | null
}

export interface DeviceRegisterRequest {
  store_id: number
  device_name: string
  device_type: string
  app_version?: string | null
  platform?: string | null
}

export interface DeviceRegisterResponse {
  device_id: number
  store_id: number
  device_name?: string | null
  device_type?: string | null
  device_token: string
  status?: string | null
  created_at?: string | null
}

export interface PrinterEncodingTestResult {
  encoding: string
  success: boolean
  message: string
}

export interface PrinterEncodingTestResponse {
  success: boolean
  recommendation: string
  code_page_command_sent: boolean
  escpos_code_page: number | null
  results: PrinterEncodingTestResult[]
}

export interface GrabFontTestResult {
  test_mode: string
  command_bytes: string
  success: boolean
  message: string
}

export interface GrabFontTestResponse {
  success: boolean
  results: GrabFontTestResult[]
}

function buildHeaders() {
  return {
    'Content-Type': 'application/json',
  }
}

const request = apiRequest

export async function fetchPrintCenterOverview(storeId: number) {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<PrintCenterOverview>(`/api/v1/admin/printing?${params.toString()}`)
}

export async function savePrinterConfig(printer: PrinterConfigRecord) {
  const target = printer.id == null
    ? '/api/v1/admin/printing/printers'
    : `/api/v1/admin/printing/printers/${printer.id}`
  return request<PrinterConfigRecord>(target, {
    method: printer.id == null ? 'POST' : 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify(printer),
  })
}

export async function deletePrinterConfig(printerId: number, storeId: number) {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<boolean>(`/api/v1/admin/printing/printers/${printerId}?${params.toString()}`, {
    method: 'DELETE',
  })
}

export async function updatePrintingStatus(storeId: number, printingEnabled: boolean) {
  return request<boolean>('/api/v1/admin/printing/status', {
    method: 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify({
      store_id: storeId,
      printing_enabled: printingEnabled,
    }),
  })
}

export async function updatePrintingMode(storeId: number, printingMode: 'REAL' | 'MOCK' | 'DISABLED' | 'PAD_DIRECT') {
  return request<boolean>('/api/v1/admin/printing/status', {
    method: 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify({
      store_id: storeId,
      printing_mode: printingMode,
    }),
  })
}

export async function updatePrinterAssignment(assignment: PrinterAssignmentRecord) {
  return request<PrinterAssignmentRecord>(`/api/v1/admin/printing/assignments/${assignment.module_code}`, {
    method: 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify({
      store_id: assignment.store_id,
      printer_id: assignment.printer_id,
      enabled: assignment.enabled,
      font_size: assignment.font_size ?? 'MEDIUM',
      takeout_receipt_copies: assignment.takeout_receipt_copies ?? 1,
    }),
  })
}

export async function triggerPrinterTest(storeId: number, printerId: number, moduleCode?: string | null) {
  return request<PrinterTestResponse>('/api/v1/admin/printing/printers/test', {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      store_id: storeId,
      printer_id: printerId,
      module_code: moduleCode ?? null,
    }),
  })
}

export async function triggerPrinterConnectionTest(storeId: number, printerId: number) {
  return request<PrinterConnectionTestResponse>('/api/v1/admin/printing/printers/connection-test', {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      store_id: storeId,
      printer_id: printerId,
    }),
  })
}

export async function fetchPrintJobs(input: {
  storeId: number
  status?: string
  orderId?: number
  moduleCode?: string
  printerId?: number
  startDate?: string
  endDate?: string
}): Promise<PrintJobRecord[]> {
  const params = new URLSearchParams({ store_id: String(input.storeId) })
  if (input.status) {
    params.set('status', input.status)
  }
  if (input.orderId) {
    params.set('orderId', String(input.orderId))
  }
  if (input.moduleCode) {
    params.set('moduleCode', input.moduleCode)
  }
  if (input.printerId) {
    params.set('printerId', String(input.printerId))
  }
  if (input.startDate) {
    params.set('startDate', input.startDate)
  }
  if (input.endDate) {
    params.set('endDate', input.endDate)
  }

  return request<PrintJobRecord[]>(`/api/v1/admin/printing/jobs?${params.toString()}`)
}

export async function fetchStoreDevices(storeId: number): Promise<StoreDeviceRecord[]> {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<StoreDeviceRecord[]>(`/api/v1/admin/printing/devices?${params.toString()}`)
}

export async function registerStoreDevice(input: DeviceRegisterRequest): Promise<DeviceRegisterResponse> {
  return request<DeviceRegisterResponse>('/api/v1/devices/register', {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify(input),
  })
}

export async function reprintPrintJob(jobId: number) {
  return request<PrintJobRecord>(`/api/v1/admin/printing/jobs/${jobId}/reprint`, {
    method: 'POST',
    headers: buildHeaders(),
  })
}

export async function triggerCurrentFontSizeTest(storeId: number, printerId: number) {
  return request<PrinterTestResponse>('/api/v1/admin/printing/printers/font-size-test', {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      store_id: storeId,
      printer_id: printerId,
    }),
  })
}

export async function triggerAssignedModulePrintTest(storeId: number, moduleCode: string) {
  return request<PrinterTestResponse>('/api/v1/admin/printing/modules/test', {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      store_id: storeId,
      module_code: moduleCode,
    }),
  })
}

export async function triggerPrinterEncodingTest(
  storeId: number,
  printerId: number,
  sendCodePageCommand = false,
  escposCodePage?: number | null,
) {
  return request<PrinterEncodingTestResponse>('/api/v1/admin/printing/printers/encoding-test', {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      store_id: storeId,
      printer_id: printerId,
      send_code_page_command: sendCodePageCommand,
      escpos_code_page: escposCodePage ?? null,
    }),
  })
}

export async function triggerGrabFontTest(storeId: number, printerId: number) {
  return request<GrabFontTestResponse>('/api/v1/admin/printing/grab-font-test', {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify({
      store_id: storeId,
      printer_id: printerId,
    }),
  })
}
