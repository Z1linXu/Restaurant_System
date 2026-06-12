const DEFAULT_ADMIN_USER_ID = '2'

interface BackendApiResponse<T> {
  success: boolean
  message?: string
  data: T
}

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
  created_at?: string
  updated_at?: string
}

export interface PrintCenterOverview {
  store_id: number
  printing_enabled: boolean
  printers: PrinterConfigRecord[]
  assignments: PrinterAssignmentRecord[]
}

export interface PrinterTestResponse {
  success: boolean
  message: string
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
    'X-User-Id': DEFAULT_ADMIN_USER_ID,
  }
}

async function request<T>(input: string, init?: RequestInit) {
  const response = await fetch(input, init)
  if (!response.ok) {
    throw new Error(`Request failed (${response.status})`)
  }

  const payload = (await response.json()) as BackendApiResponse<T>
  if (!payload.success) {
    throw new Error(payload.message || 'Request failed')
  }

  return payload.data
}

export async function fetchPrintCenterOverview(storeId: number) {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<PrintCenterOverview>(`/api/v1/admin/printing?${params.toString()}`, {
    headers: {
      'X-User-Id': DEFAULT_ADMIN_USER_ID,
    },
  })
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

export async function disablePrinterConfig(printerId: number, storeId: number) {
  const params = new URLSearchParams({ store_id: String(storeId) })
  return request<PrinterConfigRecord>(`/api/v1/admin/printing/printers/${printerId}?${params.toString()}`, {
    method: 'DELETE',
    headers: {
      'X-User-Id': DEFAULT_ADMIN_USER_ID,
    },
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

export async function updatePrinterAssignment(assignment: PrinterAssignmentRecord) {
  return request<PrinterAssignmentRecord>(`/api/v1/admin/printing/assignments/${assignment.module_code}`, {
    method: 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify({
      store_id: assignment.store_id,
      printer_id: assignment.printer_id,
      enabled: assignment.enabled,
      font_size: assignment.font_size ?? 'MEDIUM',
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
