import { translate } from '../i18n/engine'
import type { MessageKey } from '../i18n/messages'
type PrintJobLike = {
  module_code?: string | null
  receipt_type?: string | null
  status?: string | null
  error_code?: string | null
  operator_message?: string | null
  error_message?: string | null
}

const MODULE_LABELS: Record<string, MessageKey> = {"GRAB": "printing.role.GRAB", "FRONTDESK_RECEIPT": "printing.role.FRONTDESK_RECEIPT", "HOT_KITCHEN": "printing.role.HOT_KITCHEN", "COLD_KITCHEN": "printing.role.COLD_KITCHEN", "BAR": "printing.role.BAR", "TAKEOUT_RECEIPT": "printing.role.TAKEOUT_RECEIPT"}

const PRINT_ERROR_MESSAGES: Record<string, MessageKey> = {"RECEIPT_STATION_NOT_ASSIGNED": "printing.error.RECEIPT_STATION_NOT_ASSIGNED","CLOUD_PRIVATE_PRINTER_BLOCKED": "printing.error.CLOUD_PRIVATE_PRINTER_BLOCKED", "PRINTING_DISABLED": "printing.error.PRINTING_DISABLED", "ASSIGNMENT_MISSING": "printing.error.ASSIGNMENT_MISSING", "ASSIGNMENT_DISABLED": "printing.error.ASSIGNMENT_DISABLED", "PRINTER_MISSING": "printing.error.PRINTER_MISSING", "PRINTER_DISABLED": "printing.error.PRINTER_DISABLED", "RENDER_FAILED": "printing.error.RENDER_FAILED", "RENDERER_MISSING": "printing.error.RENDERER_MISSING", "RENDER_DATA_MISSING": "printing.error.RENDER_DATA_MISSING", "RENDERED_CONTENT_BLANK": "printing.error.RENDERED_CONTENT_BLANK", "CONNECTION_FAILED": "printing.error.CONNECTION_FAILED", "REPRINT_FAILED": "printing.error.REPRINT_FAILED", "PAD_DIRECT_RELEASED": "printing.error.PAD_DIRECT_RELEASED", "ANDROID_PRINTER_WRITE_FAILED": "printing.error.ANDROID_PRINTER_WRITE_FAILED", "ANDROID_PRINTER_FLUSH_FAILED": "printing.error.ANDROID_PRINTER_FLUSH_FAILED", "DISPATCH_ERROR": "printing.error.CONNECTION_FAILED", "TEST_PRINT_FAILED": "printing.error.CONNECTION_FAILED", "ANDROID_PRINTER_CONNECT_TIMEOUT": "printing.error.CONNECTION_FAILED", "ANDROID_PRINTER_CONNECTION_REFUSED": "printing.error.CONNECTION_FAILED", "ANDROID_PRINTER_NETWORK_UNREACHABLE": "printing.error.CONNECTION_FAILED", "ANDROID_ASSIGNED_PRINTER_UNREACHABLE": "printing.error.CONNECTION_FAILED", "ORDER_REPRINT_FAILED": "printing.error.REPRINT_FAILED", "PAD_DIRECT_FAILED": "printing.error.REPRINT_FAILED", "ANDROID_NATIVE_PRINT_FAILED": "printing.error.REPRINT_FAILED"}

function normalize(value?: string | null) {
  return (value ?? '').trim()
}

function normalizeCode(value?: string | null) {
  return normalize(value).toUpperCase()
}

export function moduleDisplayLabel(moduleCode?: string | null) {
  const code = normalizeCode(moduleCode)
  return MODULE_LABELS[code] ? `${translate(MODULE_LABELS[code])} ${code}` : (code ? code.replaceAll('_', ' ') : translate('printing.job'))
}

export function receiptDisplayLabel(receiptType?: string | null, moduleCode?: string | null) {
  const receipt = normalizeCode(receiptType)
  if (receipt.endsWith('_UPDATE') && MODULE_LABELS[receipt.slice(0, -7)]) return translate('printing.updateLabel', { name: moduleDisplayLabel(receipt.slice(0, -7)) })
  if (MODULE_LABELS[receipt]) return moduleDisplayLabel(receipt)
  return moduleDisplayLabel(moduleCode)
}

export function printStatusDisplayLabel(status?: string | null) {
  const code = normalizeCode(status)
  return ['PENDING', 'CLAIMED', 'PRINTING', 'PRINTED', 'FAILED', 'CANCELLED'].includes(code) ? `${translate(`printing.status.${code}` as MessageKey)} ${code}` : (code || translate('printing.unknownStatus'))
}

export function orderStatusDisplayLabel(status?: string | null) {
  const code = normalize(status).toLowerCase()
  return ['draft', 'submitted', 'preparing', 'ready', 'completed', 'cancelled', 'picked_up'].includes(code) ? translate(`order.status.${code}` as MessageKey) : (status || translate('printing.unknownStatus'))
}

export function printJobDisplayLabel(job: PrintJobLike) {
  return receiptDisplayLabel(job.receipt_type, job.module_code)
}

export function printJobOperatorDisplayMessage(job: PrintJobLike) {
  const code = normalizeCode(job.error_code)
  if (code && PRINT_ERROR_MESSAGES[code]) {
    return translate(PRINT_ERROR_MESSAGES[code])
  }
  if (normalizeCode(job.status) === 'PENDING' && normalizeCode(job.error_code) === '') {
    return translate('printing.waiting', { name: printJobDisplayLabel(job) })
  }
  return job.operator_message ?? job.error_message ?? job.error_code ?? ''
}

export function printOptionDisplayLabel(moduleCode?: string | null, fallback?: string | null) {
  const code = normalizeCode(moduleCode)
  if (code) {
    return translate('printing.reprintLabel', { name: moduleDisplayLabel(code) })
  }
  return fallback ?? translate('printing.reprint')
}
