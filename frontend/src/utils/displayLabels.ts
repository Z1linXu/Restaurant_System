type PrintJobLike = {
  module_code?: string | null
  receipt_type?: string | null
  status?: string | null
  error_code?: string | null
  operator_message?: string | null
  error_message?: string | null
}

const MODULE_LABELS: Record<string, string> = {
  GRAB: '厨房总票 GRAB',
  FRONTDESK_RECEIPT: '前台小票 FRONTDESK_RECEIPT',
  HOT_KITCHEN: '热厨票 HOT_KITCHEN',
  COLD_KITCHEN: '冷菜票 COLD_KITCHEN',
  BAR: '饮料票 BAR',
  TAKEOUT_RECEIPT: '外卖小票 TAKEOUT_RECEIPT',
}

const RECEIPT_LABELS: Record<string, string> = {
  GRAB: '厨房总票 GRAB',
  GRAB_UPDATE: '厨房更新票 GRAB_UPDATE',
  FRONTDESK_RECEIPT: '前台小票 FRONTDESK_RECEIPT',
  FRONTDESK_RECEIPT_UPDATE: '前台更新小票 FRONTDESK_RECEIPT_UPDATE',
  HOT_KITCHEN: '热厨票 HOT_KITCHEN',
  HOT_KITCHEN_UPDATE: '热厨更新票 HOT_KITCHEN_UPDATE',
}

const PRINT_STATUS_LABELS: Record<string, string> = {
  PENDING: '待处理 PENDING',
  CLAIMED: '已领取 CLAIMED',
  PRINTING: '打印中 PRINTING',
  PRINTED: '已打印 PRINTED',
  FAILED: '打印失败 FAILED',
  CANCELLED: '已取消 CANCELLED',
}

const ORDER_STATUS_LABELS: Record<string, string> = {
  draft: '草稿',
  submitted: '已提交',
  preparing: '制作中',
  ready: '已完成待取',
  completed: '已完成',
  cancelled: '已取消',
}

const PRINT_ERROR_MESSAGES: Record<string, string> = {
  CLOUD_PRIVATE_PRINTER_BLOCKED: '云端服务器不能直接连接店内局域网打印机。请使用 PAD_DIRECT、MOCK、DISABLED 或本地打印桥。',
  PRINTING_DISABLED: '门店自动打印已关闭。订单已保存，但不会自动出票。',
  ASSIGNMENT_MISSING: '该模块还没有分配打印机。请先设置打印机分配后再重打。',
  ASSIGNMENT_DISABLED: '该模块打印已关闭。请启用分配后再重打。',
  PRINTER_MISSING: '已分配的打印机不存在。请重新选择打印机后再重打。',
  PRINTER_DISABLED: '该打印机已停用。请使用可用打印机后再重打。',
  RENDER_FAILED: '小票内容生成失败。请检查订单内容，必要时联系技术支持。',
  RENDERER_MISSING: '该模块缺少小票模板。请联系技术支持。',
  RENDER_DATA_MISSING: '小票数据缺失。请联系技术支持。',
  RENDERED_CONTENT_BLANK: '小票内容为空。请检查订单内容。',
  CONNECTION_FAILED: '打印机连接失败。请检查电源、IP、网络后立即重打。',
  DISPATCH_ERROR: '打印机连接失败。请检查电源、IP、网络后立即重打。',
  TEST_PRINT_FAILED: '测试打印失败。请检查打印机连接。',
  REPRINT_FAILED: '重打失败。请检查打印机状态后再试。',
  ORDER_REPRINT_FAILED: '订单重打失败。请检查打印机状态后再试。',
  PAD_DIRECT_FAILED: 'Pad Direct 报告打印失败。请检查平板打印 App 后重打。',
  PAD_DIRECT_RELEASED: 'Pad Direct 已释放该任务，尚未打印。可重新领取或重打。',
}

function normalize(value?: string | null) {
  return (value ?? '').trim()
}

function normalizeCode(value?: string | null) {
  return normalize(value).toUpperCase()
}

export function moduleDisplayLabel(moduleCode?: string | null) {
  const code = normalizeCode(moduleCode)
  return MODULE_LABELS[code] ?? (code ? code.replaceAll('_', ' ') : '打印任务')
}

export function receiptDisplayLabel(receiptType?: string | null, moduleCode?: string | null) {
  const receipt = normalizeCode(receiptType)
  if (receipt && RECEIPT_LABELS[receipt]) {
    return RECEIPT_LABELS[receipt]
  }
  return moduleDisplayLabel(moduleCode)
}

export function printStatusDisplayLabel(status?: string | null) {
  const code = normalizeCode(status)
  return PRINT_STATUS_LABELS[code] ?? (code || '未知状态')
}

export function orderStatusDisplayLabel(status?: string | null) {
  const code = normalize(status).toLowerCase()
  return ORDER_STATUS_LABELS[code] ?? (status || '未知状态')
}

export function printJobDisplayLabel(job: PrintJobLike) {
  return receiptDisplayLabel(job.receipt_type, job.module_code)
}

export function printJobOperatorDisplayMessage(job: PrintJobLike) {
  const code = normalizeCode(job.error_code)
  if (code && PRINT_ERROR_MESSAGES[code]) {
    return PRINT_ERROR_MESSAGES[code]
  }
  if (normalizeCode(job.status) === 'PENDING' && normalizeCode(job.error_code) === '') {
    return `${printJobDisplayLabel(job)}正在等待处理。`
  }
  return job.operator_message ?? job.error_message ?? job.error_code ?? ''
}

export function printOptionDisplayLabel(moduleCode?: string | null, fallback?: string | null) {
  const code = normalizeCode(moduleCode)
  if (code) {
    return `重打${moduleDisplayLabel(code)}`
  }
  return fallback ?? '重打小票'
}
