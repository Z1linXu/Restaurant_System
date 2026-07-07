import { useEffect, useMemo, useState } from 'react'
import { isFeatureEnabled } from '../feature-flags/featureConfig'
import { fetchPlatformOverview, type PlatformAdminOverview } from '../../services/platformAdminService'
import { useCurrentStore } from '../store/StoreContext'
import { ApiRequestError } from '../../services/apiClient'
import {
  deletePrinterConfig,
  fetchPrintJobs,
  fetchPrintCenterOverview,
  fetchStoreDevices,
  registerStoreDevice,
  reprintPrintJob,
  savePrinterConfig,
  triggerPrinterConnectionTest,
  triggerCurrentFontSizeTest,
  triggerAssignedModulePrintTest,
  triggerGrabFontTest,
  triggerPrinterEncodingTest,
  triggerPrinterTest,
  updatePrinterAssignment,
  updatePrintingMode,
  updatePrintingStatus,
  type PrintCenterOverview,
  type PrintJobRecord,
  type PrinterAssignmentRecord,
  type PrinterConfigRecord,
  type StoreDeviceRecord,
} from '../../services/printingAdminService'
import {
  moduleDisplayLabel,
  printJobDisplayLabel,
  printJobOperatorDisplayMessage as displayPrintJobOperatorMessage,
  printStatusDisplayLabel,
} from '../../utils/displayLabels'

type ToastState = { kind: 'success' | 'error'; message: string } | null

type PrinterEditorState = PrinterConfigRecord
type PrintingMode = 'REAL' | 'MOCK' | 'DISABLED' | 'PAD_DIRECT'
type AndroidPadDeviceStatus = {
  paired?: boolean
  device_id?: number | null
  store_id?: number | null
  device_name?: string | null
  registered_at?: string | null
  token_last4?: string | null
  app_version?: string | null
  platform?: string | null
  success?: boolean
  message?: string | null
}

type AndroidPadDeviceBridge = {
  saveDeviceCredentials: (json: string) => string
  getDeviceStatus: () => string
  clearDeviceCredentials: () => string
  kickPrintWorker?: (json: string) => string
}

declare global {
  interface Window {
    RestaurantPadDevice?: AndroidPadDeviceBridge
  }
}

const MODULE_OPTIONS = [
  { code: 'GRAB', label: moduleDisplayLabel('GRAB'), future: false },
  { code: 'FRONTDESK_RECEIPT', label: moduleDisplayLabel('FRONTDESK_RECEIPT'), future: false },
  { code: 'HOT_KITCHEN', label: moduleDisplayLabel('HOT_KITCHEN'), future: false },
  { code: 'COLD_KITCHEN', label: moduleDisplayLabel('COLD_KITCHEN'), future: true },
  { code: 'BAR', label: moduleDisplayLabel('BAR'), future: true },
  { code: 'TAKEOUT_RECEIPT', label: moduleDisplayLabel('TAKEOUT_RECEIPT'), future: true },
] as const

const FONT_SIZE_OPTIONS = [
  { value: 'XS', label: '极小 XS' },
  { value: 'SMALL', label: '小号 SMALL' },
  { value: 'MEDIUM', label: '中号 MEDIUM' },
  { value: 'LARGE', label: '大号 LARGE' },
  { value: 'XL', label: '超大 XL' },
] as const

const CLOUD_PRIVATE_PRINTER_BLOCKED = 'CLOUD_PRIVATE_PRINTER_BLOCKED'
const CLOUD_PRIVATE_PRINTER_WARNING =
  '云端服务器不能直接连接店内局域网打印机。请切换到 PAD_DIRECT、MOCK、DISABLED，或使用本地打印桥。'

const PRINTING_MODE_OPTIONS: Array<{ value: PrintingMode; label: string; description: string }> = [
  { value: 'REAL', label: '真实打印机 REAL', description: '通过 TCP 连接已配置的 ESC/POS 打印机。' },
  { value: 'MOCK', label: '无打印机测试 MOCK', description: '只生成小票预览并模拟打印成功，不连接实体打印机。' },
  { value: 'PAD_DIRECT', label: '平板本地打印 PAD_DIRECT', description: '后端排队打印任务，由 Android Pad 本地领取并打印。' },
  { value: 'DISABLED', label: '关闭打印 DISABLED', description: '订单照常提交，但不自动打印。' },
]

function asString(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback
}

function buildStoreOnlyOverview(storeId: number, storeName: string): PlatformAdminOverview {
  return {
    organizations: [],
    templates: [],
    stores: [{ id: storeId, name: storeName }],
    roles: [],
    users: [],
    stations: [],
    dining_tables: [],
    menu_categories: [],
    menu_items: [],
    menu_item_options: [],
    kds_display_configs: [],
  }
}

function defaultPrinter(storeId: number): PrinterEditorState {
  return {
    store_id: storeId,
    name: '',
    ip_address: '192.168.2.200',
    port: 9100,
    printer_type: 'ESC_POS_TCP',
    text_encoding: 'GBK',
    escpos_code_page: null,
    font_size: 'MEDIUM',
    enabled: true,
    paper_width_mm: 80,
    timeout_ms: 3000,
  }
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '从未'
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return parsed.toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function timestampMs(value?: string | null) {
  if (!value) {
    return null
  }
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? null : parsed.getTime()
}

function durationLabel(ms: number | null) {
  if (ms == null || ms < 0) {
    return '-'
  }
  if (ms < 1000) {
    return `${ms}ms`
  }
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) {
    return `${seconds}s`
  }
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  if (minutes < 60) {
    return remainingSeconds ? `${minutes}m ${remainingSeconds}s` : `${minutes}m`
  }
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  return remainingMinutes ? `${hours}h ${remainingMinutes}m` : `${hours}h`
}

function jobAgeMs(job: PrintJobRecord) {
  const createdAt = timestampMs(job.created_at)
  return createdAt == null ? null : Math.max(0, Date.now() - createdAt)
}

function queueDelayMs(job: PrintJobRecord) {
  const createdAt = timestampMs(job.created_at)
  const claimedAt = timestampMs(job.claimed_at)
  return createdAt == null || claimedAt == null ? null : Math.max(0, claimedAt - createdAt)
}

function totalJobTimeMs(job: PrintJobRecord) {
  const createdAt = timestampMs(job.created_at)
  const finishedAt = timestampMs(job.printed_at ?? job.failed_at ?? null)
  return createdAt == null || finishedAt == null ? null : Math.max(0, finishedAt - createdAt)
}

function pendingAgeNotice(job: PrintJobRecord): { message: string; tone: 'warning' | 'danger' } | null {
  if (job.execution_mode !== 'PAD_DIRECT' || job.status !== 'PENDING') {
    return null
  }
  const ageMs = jobAgeMs(job)
  if (ageMs == null) {
    return null
  }
  if (ageMs > 120000) {
    return {
      message: 'Pad 可能未运行或自动处理已停止。',
      tone: 'danger',
    }
  }
  if (ageMs > 30000) {
    return {
      message: '等待 Pad 处理。',
      tone: 'warning',
    }
  }
  return null
}

function connectionBadge(printer: PrinterConfigRecord) {
  if (printer.last_connection_success_at) {
    return {
      label: `连接成功：${formatDateTime(printer.last_connection_success_at)}`,
      className: 'bg-[rgba(18,141,77,0.12)] text-[rgb(25,112,69)]',
    }
  }
  if (printer.last_connection_failed_at) {
    return {
      label: `连接失败：${formatDateTime(printer.last_connection_failed_at)}`,
      className: 'bg-[rgba(97,0,0,0.08)] text-[var(--primary)]',
    }
  }
  return {
    label: '未测试',
    className: 'bg-white text-[var(--muted)]',
  }
}

function statusTone(status: PrintJobRecord['status']) {
  if (status === 'PRINTED') {
    return 'bg-[rgba(18,141,77,0.12)] text-[rgb(25,112,69)]'
  }
  if (status === 'FAILED') {
    return 'bg-[rgba(97,0,0,0.1)] text-[var(--primary)]'
  }
  if (status === 'PRINTING') {
    return 'bg-[rgba(38,86,160,0.12)] text-[rgb(38,86,160)]'
  }
  if (status === 'CLAIMED') {
    return 'bg-[rgba(118,77,21,0.14)] text-[rgb(118,77,21)]'
  }
  if (status === 'CANCELLED') {
    return 'bg-[rgba(118,77,21,0.14)] text-[rgb(118,77,21)]'
  }
  return 'bg-[rgba(26,28,25,0.08)] text-[var(--muted)]'
}

function isPastDateTime(value?: string | null) {
  if (!value) {
    return false
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return false
  }
  return parsed.getTime() < Date.now()
}

function padDirectClaimNotice(job: PrintJobRecord): { message: string; tone: 'warning' | 'danger' | 'info' } | null {
  if (job.execution_mode !== 'PAD_DIRECT') {
    return null
  }
  if (job.status === 'PRINTING' && isPastDateTime(job.claim_expires_at)) {
    return {
      message: 'Pad 正在打印但领取已过期：可能已经出纸，请确认后再重打，避免重复打印。',
      tone: 'danger',
    }
  }
  if (job.status === 'PRINTING') {
    return {
      message: 'Pad 正在本地打印，请等待完成回报。',
      tone: 'info',
    }
  }
  if (job.status === 'CLAIMED' && isPastDateTime(job.claim_expires_at)) {
    return {
      message: 'Pad 已领取但未开始打印且已过期：其他 Pad 可以重新领取。',
      tone: 'warning',
    }
  }
  if (job.status === 'CLAIMED') {
    return {
      message: 'Pad 已领取，等待开始打印。',
      tone: 'info',
    }
  }
  return null
}

function padDirectNoticeToneClass(tone: 'warning' | 'danger' | 'info') {
  if (tone === 'danger') {
    return 'bg-[rgba(151,34,34,0.1)] text-[rgb(116,22,22)]'
  }
  if (tone === 'warning') {
    return 'bg-[rgba(180,120,20,0.16)] text-[rgb(130,82,14)]'
  }
  return 'bg-[rgba(38,86,160,0.1)] text-[rgb(38,86,160)]'
}

function printJobNeedsAttention(job: PrintJobRecord) {
  const padNotice = padDirectClaimNotice(job)
  const pendingNotice = pendingAgeNotice(job)
  return job.status === 'FAILED'
    || job.status === 'CANCELLED'
    || padNotice?.tone === 'danger'
    || padNotice?.tone === 'warning'
    || pendingNotice?.tone === 'danger'
    || pendingNotice?.tone === 'warning'
}

function printJobOperatorMessage(job: PrintJobRecord) {
  return displayPrintJobOperatorMessage(job)
}

function printJobNativeDiagnostics(job: PrintJobRecord) {
  const source = `${job.error_message ?? ''}\n${job.operator_message ?? ''}`
  const read = (key: string) => {
    const match = source.match(new RegExp(`${key}=([^;\\n]+)`))
    return match?.[1]?.trim() ?? ''
  }
  const nativeErrorCode = read('native_error_code')
  const phase = read('phase')
  const endpoint = read('endpoint')
  const bytesWritten = read('bytes_written')
  const exceptionClass = read('exception_class')
  const deviceId = read('device_id')
  if (!nativeErrorCode && !phase && !endpoint && !bytesWritten && !exceptionClass && !deviceId) {
    return null
  }
  return {
    nativeErrorCode,
    phase,
    endpoint,
    bytesWritten,
    exceptionClass,
    deviceId,
  }
}

function parseAndroidPadStatus(rawStatus: string): AndroidPadDeviceStatus | null {
  try {
    return JSON.parse(rawStatus) as AndroidPadDeviceStatus
  } catch {
    return null
  }
}

export function PrintingSettingsPage() {
  const currentStore = useCurrentStore()
  const { storeId } = currentStore
  const [overview, setOverview] = useState<PlatformAdminOverview | null>(null)
  const [printCenter, setPrintCenter] = useState<PrintCenterOverview | null>(null)
  const [selectedStoreId, setSelectedStoreId] = useState(String(storeId))
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState<ToastState>(null)
  const [error, setError] = useState<string | null>(null)
  const [printerEditor, setPrinterEditor] = useState<PrinterEditorState | null>(null)
  const [printJobs, setPrintJobs] = useState<PrintJobRecord[]>([])
  const [storeDevices, setStoreDevices] = useState<StoreDeviceRecord[]>([])
  const [jobsLoading, setJobsLoading] = useState(false)
  const [jobsError, setJobsError] = useState<string | null>(null)
  const [devicesLoading, setDevicesLoading] = useState(false)
  const [devicesError, setDevicesError] = useState<string | null>(null)
  const [testingAllConnections, setTestingAllConnections] = useState(false)
  const [previewJob, setPreviewJob] = useState<PrintJobRecord | null>(null)
  const [padBridgeAvailable, setPadBridgeAvailable] = useState(false)
  const [padDeviceStatus, setPadDeviceStatus] = useState<AndroidPadDeviceStatus | null>(null)
  const [pairingPad, setPairingPad] = useState(false)

  const refreshPadDeviceStatus = () => {
    const bridge = typeof window === 'undefined' ? undefined : window.RestaurantPadDevice
    setPadBridgeAvailable(Boolean(bridge))
    if (!bridge) {
      setPadDeviceStatus(null)
      return
    }
    setPadDeviceStatus(parseAndroidPadStatus(bridge.getDeviceStatus()))
  }

  const loadData = async (storeId: number) => {
    setLoading(true)
    setJobsLoading(true)
    setDevicesLoading(true)
    setError(null)
    setJobsError(null)
    setDevicesError(null)
    try {
      const loadPlatformOverview = async () => {
        try {
          return await fetchPlatformOverview(storeId)
        } catch (overviewError) {
          if (overviewError instanceof ApiRequestError && overviewError.status === 403) {
            return buildStoreOnlyOverview(storeId, currentStore.storeName)
          }
          throw overviewError
        }
      }
      const [platformOverview, printOverview] = await Promise.all([
        loadPlatformOverview(),
        fetchPrintCenterOverview(storeId),
      ])
      setOverview(platformOverview)
      setPrintCenter(printOverview)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '打印中心设置加载失败')
    } finally {
      setLoading(false)
    }

    try {
      setPrintJobs(await fetchPrintJobs({ storeId }))
    } catch (jobError) {
      setPrintJobs([])
      setJobsError(jobError instanceof Error ? jobError.message : '打印任务加载失败')
    } finally {
      setJobsLoading(false)
    }

    try {
      setStoreDevices(await fetchStoreDevices(storeId))
    } catch (deviceError) {
      setStoreDevices([])
      setDevicesError(deviceError instanceof Error ? deviceError.message : 'Pad 设备加载失败')
    } finally {
      setDevicesLoading(false)
    }
  }

  useEffect(() => {
    setSelectedStoreId(String(storeId))
  }, [storeId])

  useEffect(() => {
    void loadData(Number(selectedStoreId))
  }, [selectedStoreId])

  useEffect(() => {
    refreshPadDeviceStatus()
  }, [selectedStoreId])

  const stores = useMemo(
    () => (overview?.stores ?? []).map((store) => ({
      id: String(store.id),
      label: asString(store.name, `Store ${store.id}`),
    })),
    [overview],
  )

  const assignmentsByModule = useMemo(() => {
    const mapping = new Map<string, PrinterAssignmentRecord>()
    ;(printCenter?.assignments ?? []).forEach((assignment) => mapping.set(assignment.module_code, assignment))
    return mapping
  }, [printCenter])

  const assignmentsByPrinter = useMemo(() => {
    const mapping = new Map<number, string[]>()
    ;(printCenter?.assignments ?? []).forEach((assignment) => {
      if (assignment.printer_id == null || !assignment.enabled) {
        return
      }
      mapping.set(assignment.printer_id, [...(mapping.get(assignment.printer_id) ?? []), assignment.module_code])
    })
    return mapping
  }, [printCenter])

  const failedPrintJobs = useMemo(() => printJobs.filter((job) => job.status === 'FAILED'), [printJobs])
  const attentionPrintJobs = useMemo(() => printJobs.filter(printJobNeedsAttention), [printJobs])
  const padDirectQueueStats = useMemo(() => {
    const pendingJobs = printJobs.filter((job) => job.execution_mode === 'PAD_DIRECT' && job.status === 'PENDING')
    const oldestPendingAgeMs = pendingJobs.reduce<number | null>((oldest, job) => {
      const ageMs = jobAgeMs(job)
      if (ageMs == null) {
        return oldest
      }
      return oldest == null ? ageMs : Math.max(oldest, ageMs)
    }, null)
    const stalePrintingCount = printJobs.filter(
      (job) => job.execution_mode === 'PAD_DIRECT'
        && job.status === 'PRINTING'
        && isPastDateTime(job.claim_expires_at),
    ).length
    return {
      pendingCount: pendingJobs.length,
      oldestPendingAgeMs,
      stalePrintingCount,
    }
  }, [printJobs])
  const printingMode = (printCenter?.printing_mode ?? (printCenter?.printing_enabled ? 'REAL' : 'DISABLED')) as PrintingMode
  const cloudPrivatePrinterWarning = useMemo(() => {
    if (printCenter?.cloud_private_printer_warning) {
      return CLOUD_PRIVATE_PRINTER_WARNING
    }
    return printJobs.some((job) => job.error_code === CLOUD_PRIVATE_PRINTER_BLOCKED)
      ? CLOUD_PRIVATE_PRINTER_WARNING
      : null
  }, [printCenter, printJobs])

  const startCreatePrinter = () => {
    setToast(null)
    setPrinterEditor(defaultPrinter(Number(selectedStoreId)))
  }

  const startEditPrinter = (printer: PrinterConfigRecord) => {
    setToast(null)
    setPrinterEditor({ ...printer })
  }

  const handleSavePrinter = async () => {
    if (!printerEditor) {
      return
    }
    try {
      setSaving(true)
      setToast(null)
      await savePrinterConfig(printerEditor)
      const reloaded = await fetchPrintCenterOverview(Number(selectedStoreId))
      setPrintCenter(reloaded)
      setPrinterEditor(null)
      setToast({ kind: 'success', message: '打印机设置已保存。' })
    } catch (saveError) {
      setToast({ kind: 'error', message: saveError instanceof Error ? saveError.message : '打印机保存失败' })
    } finally {
      setSaving(false)
    }
  }

  const handleDeletePrinter = async (printer: PrinterConfigRecord) => {
    if (!printer.id) {
      return
    }
    const assignedModules = assignmentsByPrinter.get(printer.id) ?? []
    if (assignedModules.length) {
      setToast({
        kind: 'error',
        message: `该打印机仍分配给模块：${assignedModules.map(moduleDisplayLabel).join('、')}。请先移除分配再删除。`,
      })
      return
    }
    if (!window.confirm(`确定要删除打印机「${printer.name}」吗？这不会影响历史打印记录。`)) {
      return
    }
    try {
      setToast(null)
      await deletePrinterConfig(printer.id, Number(selectedStoreId))
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: 'success', message: '打印机已删除。' })
    } catch (actionError) {
      setToast({ kind: 'error', message: actionError instanceof Error ? actionError.message : '删除打印机失败' })
    }
  }

  const handleTestPrint = async (printerId: number, moduleCode?: string) => {
    try {
      setToast(null)
      const result = await triggerPrinterTest(Number(selectedStoreId), printerId, moduleCode)
      setToast({ kind: result.success ? 'success' : 'error', message: result.message })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : '测试打印失败' })
    }
  }

  const handleConnectionTest = async (printerId: number) => {
    try {
      setToast(null)
      const result = await triggerPrinterConnectionTest(Number(selectedStoreId), printerId)
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: result.success ? 'success' : 'error', message: result.success ? '连接测试成功。' : `连接测试失败：${result.message}` })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : '连接测试失败' })
    }
  }

  const handleAllConnectionTests = async () => {
    const printers = printCenter?.printers ?? []
    if (!printers.length) {
      setToast({ kind: 'error', message: '还没有配置打印机。' })
      return
    }
    try {
      setTestingAllConnections(true)
      setToast(null)
      const results = await Promise.all(
        printers
          .filter((printer) => printer.id != null)
          .map((printer) => triggerPrinterConnectionTest(Number(selectedStoreId), printer.id!)),
      )
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      const failedCount = results.filter((result) => !result.success).length
      setToast({
        kind: failedCount ? 'error' : 'success',
        message: failedCount
          ? `${failedCount} 台打印机连接测试失败，请查看对应打印机卡片。`
          : '所有打印机连接测试成功。',
      })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : '批量连接测试失败' })
    } finally {
      setTestingAllConnections(false)
    }
  }

  const handleCurrentFontSizeTest = async (printerId: number) => {
    try {
      setToast(null)
      const result = await triggerCurrentFontSizeTest(Number(selectedStoreId), printerId)
      setToast({ kind: result.success ? 'success' : 'error', message: result.message })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : '当前字体测试失败' })
    }
  }

  const handleEncodingTest = async (printer: PrinterConfigRecord) => {
    try {
      setToast(null)
      const result = await triggerPrinterEncodingTest(
        Number(selectedStoreId),
        printer.id!,
        printer.escpos_code_page != null,
        printer.escpos_code_page ?? null,
      )
      const failures = result.results.filter((entry) => !entry.success)
      setToast({
        kind: failures.length ? 'error' : 'success',
        message: failures.length
          ? `编码测试已发送，但以下编码失败：${failures.map((entry) => entry.encoding).join(', ')}。`
          : `编码测试票已发送。推荐默认编码：${printer.text_encoding ?? 'GBK'}。`,
      })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : '编码测试失败' })
    }
  }

  const handleGrabFontTest = async (printer: PrinterConfigRecord) => {
    try {
      setToast(null)
      const result = await triggerGrabFontTest(Number(selectedStoreId), printer.id!)
      const failures = result.results.filter((entry) => !entry.success)
      setToast({
        kind: failures.length ? 'error' : 'success',
        message: failures.length
          ? `GRAB 字体测试已发送，但以下模式失败：${failures.map((entry) => entry.test_mode).join(', ')}。`
          : `GRAB 字体测试票已发送：${result.results.map((entry) => `${entry.test_mode} (${entry.command_bytes})`).join(' · ')}。`,
      })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : 'GRAB 字体测试失败' })
    }
  }

  const handleStatusToggle = async (nextEnabled: boolean) => {
    try {
      setToast(null)
      await updatePrintingStatus(Number(selectedStoreId), nextEnabled)
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: 'success', message: `门店自动打印已${nextEnabled ? '启用' : '关闭'}。` })
    } catch (statusError) {
      setToast({ kind: 'error', message: statusError instanceof Error ? statusError.message : '打印状态更新失败' })
    }
  }

  const handleModeChange = async (nextMode: PrintingMode) => {
    try {
      setToast(null)
      await updatePrintingMode(Number(selectedStoreId), nextMode)
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: 'success', message: `打印模式已切换为 ${nextMode}。` })
    } catch (statusError) {
      setToast({ kind: 'error', message: statusError instanceof Error ? statusError.message : '打印模式更新失败' })
    }
  }

  const handleAssignmentSave = async (assignment: PrinterAssignmentRecord) => {
    try {
      setToast(null)
      await updatePrinterAssignment(assignment)
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: 'success', message: `${moduleDisplayLabel(assignment.module_code)} 分配已保存。` })
    } catch (assignmentError) {
      setToast({ kind: 'error', message: assignmentError instanceof Error ? assignmentError.message : '打印机分配保存失败' })
    }
  }

  const handleModuleTestPrint = async (moduleCode: string) => {
    try {
      setToast(null)
      const result = await triggerAssignedModulePrintTest(Number(selectedStoreId), moduleCode)
      setToast({ kind: result.success ? 'success' : 'error', message: result.message })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : '模块测试打印失败' })
    }
  }

  const handleReprintJob = async (jobId: number) => {
    try {
      setToast(null)
      const result = await reprintPrintJob(jobId)
      setPrintJobs(await fetchPrintJobs({ storeId: Number(selectedStoreId) }))
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({
        kind: result.status === 'PRINTED' ? 'success' : 'error',
        message: result.status === 'PRINTED' ? `打印任务 #${jobId} 已重新打印。` : `重打失败：${printJobOperatorMessage(result) || '未知错误'}`,
      })
    } catch (reprintError) {
      setToast({ kind: 'error', message: reprintError instanceof Error ? reprintError.message : '重打失败' })
    }
  }

  const refreshJobs = async () => {
    try {
      setJobsLoading(true)
      setJobsError(null)
      setPrintJobs(await fetchPrintJobs({ storeId: Number(selectedStoreId) }))
    } catch (jobError) {
      setJobsError(jobError instanceof Error ? jobError.message : '刷新打印任务失败')
    } finally {
      setJobsLoading(false)
    }
  }

  const refreshDevices = async () => {
    try {
      setDevicesLoading(true)
      setDevicesError(null)
      setStoreDevices(await fetchStoreDevices(Number(selectedStoreId)))
    } catch (deviceError) {
      setDevicesError(deviceError instanceof Error ? deviceError.message : '刷新 Pad 设备失败')
    } finally {
      setDevicesLoading(false)
    }
  }

  const handlePairThisPad = async () => {
    const bridge = typeof window === 'undefined' ? undefined : window.RestaurantPadDevice
    if (!bridge) {
      setToast({ kind: 'error', message: '请在 Android Pad App 内打开打印中心进行配对。' })
      return
    }

    const currentStatus = parseAndroidPadStatus(bridge.getDeviceStatus())
    if (currentStatus?.paired) {
      const currentStore = currentStatus.store_id ? `门店 ${currentStatus.store_id}` : '未知门店'
      const nextStore = `门店 ${selectedStoreId}`
      const message = currentStatus.store_id && Number(currentStatus.store_id) !== Number(selectedStoreId)
        ? `本机已经配对到${currentStore}。继续会为${nextStore}注册新的设备并覆盖本机凭证，确定继续吗？`
        : `本机已经配对到${currentStore}。继续会注册新的设备并覆盖本机凭证，确定继续吗？`
      if (!window.confirm(message)) {
        return
      }
    }

    try {
      setPairingPad(true)
      const registered = await registerStoreDevice({
        store_id: Number(selectedStoreId),
        device_name: 'Restaurant Pad',
        device_type: 'ANDROID_PAD',
        app_version: currentStatus?.app_version ?? 'unknown',
        platform: 'ANDROID',
      })
      const saveResult = parseAndroidPadStatus(bridge.saveDeviceCredentials(JSON.stringify({
        device_id: registered.device_id,
        device_token: registered.device_token,
        store_id: registered.store_id,
        device_name: registered.device_name ?? 'Restaurant Pad',
        app_version: currentStatus?.app_version ?? 'unknown',
        platform: 'ANDROID',
        registered_at: registered.created_at ?? new Date().toISOString(),
      })))
      if (!saveResult?.success) {
        throw new Error(saveResult?.message ?? 'Android Pad 保存配对凭证失败')
      }
      setToast({ kind: 'success', message: `本机 Pad 已配对：设备 #${registered.device_id}` })
      refreshPadDeviceStatus()
      await refreshDevices()
    } catch (pairError) {
      setToast({ kind: 'error', message: pairError instanceof Error ? pairError.message : 'Pad 配对失败' })
    } finally {
      setPairingPad(false)
    }
  }

  return (
    <>
      <div className="space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-4 rounded-[28px] bg-[rgba(255,255,255,0.84)] px-5 py-4 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div>
                <div className="text-[1.7rem] font-black tracking-[-0.05em] text-[var(--on-surface)]">打印中心</div>
                <div className="mt-1 text-[0.9rem] text-[var(--muted)]">
                  管理厨房票、前台小票和热厨票的打印机、分配、测试与打印任务。
                </div>
              </div>

              <div className="rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-2.5">
                <div className="text-[0.72rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">门店范围</div>
                <select
                  value={selectedStoreId}
                  onChange={(event) => setSelectedStoreId(event.target.value)}
                  className="mt-1 min-w-[220px] bg-transparent text-[0.95rem] font-semibold text-[var(--on-surface)] outline-none"
                >
                  {stores.map((store) => (
                    <option key={store.id} value={store.id}>
                      {store.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {toast ? (
              <div
                className={`rounded-[18px] px-4 py-3 text-[0.92rem] font-medium ${
                  toast.kind === 'success'
                    ? 'bg-[rgba(18,141,77,0.1)] text-[rgb(25,112,69)]'
                    : 'bg-[rgba(97,0,0,0.08)] text-[var(--primary)]'
                }`}
              >
                {toast.message}
              </div>
            ) : null}

            {error ? (
              <div className="rounded-[18px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.92rem] font-medium text-[var(--primary)]">
                {error}
              </div>
            ) : null}

            <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">打印模式</div>
                  <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                    选择真实打印机、无打印机测试、平板本地打印或关闭自动打印。
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  {PRINTING_MODE_OPTIONS.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => handleModeChange(option.value)}
                      disabled={loading || printingMode === option.value}
                      title={option.description}
                      className={`rounded-full px-4 py-2 text-[0.84rem] font-semibold transition ${
                        printingMode === option.value
                          ? option.value === 'MOCK'
                            ? 'bg-[rgba(38,86,160,0.16)] text-[rgb(38,86,160)]'
                            : option.value === 'DISABLED'
                              ? 'bg-[rgba(97,0,0,0.1)] text-[var(--primary)]'
                              : option.value === 'PAD_DIRECT'
                                ? 'bg-[rgba(118,77,21,0.14)] text-[rgb(118,77,21)]'
                                : 'bg-[rgba(18,141,77,0.12)] text-[rgb(25,112,69)]'
                          : 'bg-[rgba(26,28,25,0.06)] text-[var(--muted)] hover:bg-[rgba(26,28,25,0.1)]'
                      }`}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>
              {printingMode === 'MOCK' ? (
                <div className="mt-4 rounded-[18px] bg-[rgba(38,86,160,0.1)] px-4 py-3 text-[0.9rem] font-semibold text-[rgb(38,86,160)]">
                  当前为无打印机测试模式，系统不会连接任何实体打印机。订单会生成 print jobs，并保存可预览的小票内容。
                </div>
              ) : null}
              {printingMode === 'PAD_DIRECT' ? (
                <div className="mt-4 rounded-[18px] bg-[rgba(118,77,21,0.12)] px-4 py-3 text-[0.9rem] font-semibold text-[rgb(118,77,21)]">
                  当前为平板本地打印模式。后端只排队打印任务，由 Android Pad 在店内局域网领取并打印。
                </div>
              ) : null}
              {printingMode === 'DISABLED' ? (
                <div className="mt-4 rounded-[18px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.9rem] font-semibold text-[var(--primary)]">
                  当前已关闭自动打印。订单仍会正常提交，但自动打印任务会取消。
                </div>
              ) : null}
              {cloudPrivatePrinterWarning ? (
                <div className="mt-4 rounded-[18px] bg-[rgba(151,34,34,0.12)] px-4 py-3 text-[0.9rem] font-semibold text-[rgb(116,22,22)]">
                  {cloudPrivatePrinterWarning}
                </div>
              ) : null}
              <button
                type="button"
                onClick={() => handleStatusToggle(!printCenter?.printing_enabled)}
                disabled={loading}
                className="mt-4 rounded-full bg-[rgba(26,28,25,0.06)] px-4 py-2 text-[0.82rem] font-semibold text-[var(--muted)]"
              >
                旧版总开关：{printCenter?.printing_enabled ? '已启用' : '已关闭'}
              </button>
            </section>

            <button
              type="button"
              onClick={() => document.getElementById('failed-print-jobs')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
              className={`w-full rounded-[26px] px-5 py-4 text-left shadow-[0_18px_34px_rgba(26,28,25,0.05)] ${
                attentionPrintJobs.length
                  ? 'bg-[rgba(151,34,34,0.12)] text-[rgb(116,22,22)]'
                  : 'bg-[rgba(18,141,77,0.1)] text-[rgb(25,112,69)]'
              }`}
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-[1.1rem] font-black">需要处理的打印任务：{attentionPrintJobs.length}</div>
                  <div className="mt-1 text-[0.86rem] font-medium">
                    {attentionPrintJobs.length
                      ? `${failedPrintJobs.length} 个失败任务。PAD_DIRECT 等待 ${padDirectQueueStats.pendingCount} 个，最老等待 ${durationLabel(padDirectQueueStats.oldestPendingAgeMs)}，过期 PRINTING ${padDirectQueueStats.stalePrintingCount} 个。`
                      : '今天没有失败、取消或 Pad Direct 过期任务。'}
                  </div>
                  {padDirectQueueStats.oldestPendingAgeMs != null && padDirectQueueStats.oldestPendingAgeMs > 120000 ? (
                    <div className="mt-2 rounded-[14px] bg-[rgba(151,34,34,0.1)] px-3 py-2 text-[0.82rem] font-bold text-[rgb(116,22,22)]">
                      队列最前方有旧任务未处理，可能阻塞后续打印。请确认 Android Pad 自动处理是否开启。
                    </div>
                  ) : null}
                </div>
                <span className="rounded-full bg-white/70 px-4 py-2 text-[0.84rem] font-bold">
                  查看打印问题
                </span>
              </div>
            </button>

            <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">Pad Direct 设备</div>
                  <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                    已注册的 Android Pad 本地打印设备。设备 token 注册后不会再次显示。
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => void handlePairThisPad()}
                    disabled={!padBridgeAvailable || pairingPad}
                    title={padBridgeAvailable ? '通过 Android 原生桥保存本机 device token' : '请在 Android Pad App 内打开打印中心进行配对'}
                    className={`rounded-full px-4 py-2 text-[0.84rem] font-semibold ${
                      padBridgeAvailable
                        ? 'bg-[rgba(118,77,21,0.14)] text-[rgb(118,77,21)]'
                        : 'bg-[rgba(26,28,25,0.06)] text-[var(--muted)]'
                    }`}
                  >
                    {pairingPad ? '配对中...' : '配对本机 Pad'}
                  </button>
                  <button
                    type="button"
                    onClick={() => void refreshDevices()}
                    className="rounded-full bg-[rgba(26,28,25,0.06)] px-4 py-2 text-[0.84rem] font-semibold text-[var(--on-surface)]"
                  >
                    {devicesLoading ? '刷新中...' : '刷新设备'}
                  </button>
                </div>
              </div>
              <div className={`mt-4 rounded-[18px] px-4 py-3 text-[0.86rem] font-medium ${
                padBridgeAvailable
                  ? padDeviceStatus?.paired && padDeviceStatus.store_id && Number(padDeviceStatus.store_id) !== Number(selectedStoreId)
                    ? 'bg-[rgba(151,34,34,0.1)] text-[rgb(116,22,22)]'
                    : 'bg-[rgba(18,141,77,0.1)] text-[rgb(25,112,69)]'
                  : 'bg-[rgba(26,28,25,0.05)] text-[var(--muted)]'
              }`}
              >
                {!padBridgeAvailable ? (
                  <span>普通浏览器无法保存 Pad 凭证。请在 Android Pad App 内打开打印中心进行配对。</span>
                ) : padDeviceStatus?.paired ? (
                  <span>
                    本机已保存设备 #{padDeviceStatus.device_id}，门店 #{padDeviceStatus.store_id}
                    {padDeviceStatus.device_name ? `，${padDeviceStatus.device_name}` : ''}
                    {padDeviceStatus.token_last4 ? `，token ****${padDeviceStatus.token_last4}` : ''}
                    {padDeviceStatus.store_id && Number(padDeviceStatus.store_id) !== Number(selectedStoreId)
                      ? '。当前页面门店与本机配对门店不一致，请重新配对后再启用自动领取。'
                      : '。'}
                  </span>
                ) : (
                  <span>当前 Android Pad App 尚未配对。点击“配对本机 Pad”后，device token 只会保存到本机原生层。</span>
                )}
              </div>
              <PadDevicesTable devices={storeDevices} />
              {devicesError ? (
                <div className="mt-3 rounded-[16px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.86rem] font-medium text-[var(--primary)]">
                  Pad 设备加载失败：{devicesError}
                </div>
              ) : null}
            </section>

            <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">打印机列表</div>
                  <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                    添加和维护各打印模块使用的 TCP 打印机。RP820 本地测试建议默认使用 `GBK` 编码。
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={handleAllConnectionTests}
                    disabled={testingAllConnections || !(printCenter?.printers ?? []).length}
                    className="rounded-full bg-[rgba(38,86,160,0.12)] px-4 py-2 text-[0.88rem] font-semibold text-[rgb(38,86,160)] disabled:opacity-60"
                  >
                    {testingAllConnections ? '测试中...' : '测试全部连接'}
                  </button>
                  <button
                    type="button"
                    onClick={startCreatePrinter}
                    className="rounded-full bg-[var(--primary)] px-4 py-2 text-[0.88rem] font-semibold text-white"
                  >
                    添加打印机
                  </button>
                </div>
              </div>

              {printerEditor ? (
                <div className="mt-4 rounded-[20px] bg-[rgba(26,28,25,0.04)] p-4">
                  <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      名称
                      <input
                        value={printerEditor.name}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, name: event.target.value } : current)}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      打印机 IP
                      <input
                        value={printerEditor.ip_address}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, ip_address: event.target.value } : current)}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      端口
                      <input
                        type="number"
                        value={printerEditor.port}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, port: Number(event.target.value) } : current)}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      超时 (ms)
                      <input
                        type="number"
                        value={printerEditor.timeout_ms}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, timeout_ms: Number(event.target.value) } : current)}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      文本编码
                      <select
                        value={printerEditor.text_encoding ?? 'GBK'}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, text_encoding: event.target.value } : current)}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      >
                        <option value="GBK">GBK</option>
                        <option value="GB2312">GB2312</option>
                        <option value="UTF-8">UTF-8</option>
                      </select>
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      ESC/POS 代码页
                      <input
                        type="number"
                        value={printerEditor.escpos_code_page ?? ''}
                        onChange={(event) =>
                          setPrinterEditor((current) =>
                            current
                              ? {
                                  ...current,
                                  escpos_code_page: event.target.value === '' ? null : Number(event.target.value),
                                }
                              : current,
                          )
                        }
                        placeholder="可选"
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      字体大小
                      <select
                        value={printerEditor.font_size ?? 'MEDIUM'}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, font_size: event.target.value } : current)}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      >
                        {FONT_SIZE_OPTIONS.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>

                  <div className="mt-3 flex flex-wrap items-center gap-3">
                    <span className="rounded-full bg-[rgba(18,141,77,0.1)] px-3 py-1.5 text-[0.78rem] font-semibold text-[rgb(25,112,69)]">
                      打印机配置默认有效。请通过「分配状态」控制某个模块是否打印。
                    </span>
                    <button
                      type="button"
                      onClick={handleSavePrinter}
                      disabled={saving}
                      className="rounded-full bg-[var(--primary)] px-4 py-2 text-[0.88rem] font-semibold text-white disabled:opacity-60"
                    >
                      {saving ? '保存中...' : '保存打印机'}
                    </button>
                    <button
                      type="button"
                      onClick={() => setPrinterEditor(null)}
                      className="rounded-full bg-[rgba(26,28,25,0.06)] px-4 py-2 text-[0.88rem] font-semibold text-[var(--on-surface)]"
                    >
                      取消
                    </button>
                  </div>
                </div>
              ) : null}

              <div className="mt-4 space-y-3">
                {(printCenter?.printers ?? []).map((printer) => {
                  const connection = connectionBadge(printer)
                  return (
                  <div key={printer.id} className="rounded-[20px] bg-[rgba(26,28,25,0.04)] px-4 py-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <div className="text-[1rem] font-bold text-[var(--on-surface)]">{printer.name}</div>
                        <div className="mt-1 text-[0.84rem] text-[var(--muted)]">
                          {printer.ip_address}:{printer.port} · {printer.printer_type} · {printer.paper_width_mm}mm · {printer.text_encoding ?? 'GBK'}
                          {printer.escpos_code_page != null ? ` · CP ${printer.escpos_code_page}` : ''}
                          {` · ${FONT_SIZE_OPTIONS.find((option) => option.value === (printer.font_size ?? 'MEDIUM'))?.label ?? (printer.font_size ?? 'MEDIUM')}`}
                        </div>
                        <div className="mt-2 flex flex-wrap gap-2 text-[0.78rem] font-medium text-[var(--muted)]">
                          <span className="rounded-full bg-white px-2.5 py-1">模块：{assignmentsByPrinter.get(printer.id!)?.map(moduleDisplayLabel).join('、') || '无'}</span>
                          <span className="rounded-full bg-white px-2.5 py-1">上次成功：{formatDateTime(printer.last_successful_print_at)}</span>
                          <span className="rounded-full bg-white px-2.5 py-1">上次失败：{formatDateTime(printer.last_failed_print_at)}</span>
                          <span className={`rounded-full px-2.5 py-1 ${connection.className}`}>连接：{connection.label}</span>
                        </div>
                        {printer.last_error_message || printer.last_connection_error ? (
                          <div className="mt-2 rounded-[14px] bg-[rgba(97,0,0,0.07)] px-3 py-2 text-[0.78rem] font-medium text-[var(--primary)]">
                            {printer.last_error_message ?? printer.last_connection_error}
                          </div>
                        ) : null}
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => startEditPrinter(printer)}
                          className="rounded-full bg-[rgba(26,28,25,0.06)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--on-surface)]"
                        >
                          编辑
                        </button>
                        <button
                          type="button"
                          onClick={() => handleTestPrint(printer.id!, 'FRONTDESK_RECEIPT')}
                          className="rounded-full bg-[rgba(18,141,77,0.12)] px-3 py-1.5 text-[0.82rem] font-semibold text-[rgb(25,112,69)]"
                        >
                          直接测试打印
                        </button>
                        <button
                          type="button"
                          onClick={() => handleConnectionTest(printer.id!)}
                          className="rounded-full bg-[rgba(38,86,160,0.12)] px-3 py-1.5 text-[0.82rem] font-semibold text-[rgb(38,86,160)]"
                        >
                          连接测试
                        </button>
                        {isFeatureEnabled('DEVELOPER_TOOLS') ? (
                          <>
                            <button
                              type="button"
                              onClick={() => handleEncodingTest(printer)}
                              className="rounded-full bg-[rgba(38,86,160,0.12)] px-3 py-1.5 text-[0.82rem] font-semibold text-[rgb(38,86,160)]"
                            >
                              编码测试
                            </button>
                            <button
                              type="button"
                              onClick={() => handleCurrentFontSizeTest(printer.id!)}
                              className="rounded-full bg-[rgba(38,86,160,0.12)] px-3 py-1.5 text-[0.82rem] font-semibold text-[rgb(38,86,160)]"
                            >
                              测试当前字体
                            </button>
                            <button
                              type="button"
                              onClick={() => handleGrabFontTest(printer)}
                              className="rounded-full bg-[rgba(97,0,0,0.08)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--primary)]"
                            >
                              GRAB 字体测试
                            </button>
                          </>
                        ) : null}
                        <button
                          type="button"
                          onClick={() => handleDeletePrinter(printer)}
                          className="rounded-full bg-[rgba(97,0,0,0.08)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--primary)]"
                        >
                          删除
                        </button>
                      </div>
                    </div>
                  </div>
                  )
                })}
                {!loading && !(printCenter?.printers?.length ?? 0) ? (
                  <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
                    还没有配置打印机。
                  </div>
                ) : null}
              </div>
            </section>

            <section id="failed-print-jobs" className="scroll-mt-4 rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">打印失败任务</div>
                  <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                    营业前请处理失败任务；如看到 PRINTING_DISABLED，通常表示门店关闭了自动打印。
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => void refreshJobs()}
                  className="rounded-full bg-[rgba(26,28,25,0.06)] px-4 py-2 text-[0.84rem] font-semibold text-[var(--on-surface)]"
                >
                  {jobsLoading ? '刷新中...' : '刷新任务'}
                </button>
              </div>
              <PrintJobsTable jobs={attentionPrintJobs} emptyText="今天没有失败、取消或 Pad Direct 过期任务。" onReprint={handleReprintJob} onPreview={setPreviewJob} />
              {jobsError ? (
                <div className="mt-3 rounded-[16px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.86rem] font-medium text-[var(--primary)]">
                  打印任务加载失败：{jobsError}
                </div>
              ) : null}
            </section>

            <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">最近打印任务</div>
              <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                查看今天厨房总票、前台小票和热厨票的打印状态。
              </div>
              <PrintJobsTable jobs={printJobs.slice(0, 12)} emptyText="今天还没有打印任务。" onReprint={handleReprintJob} onPreview={setPreviewJob} />
              {jobsError ? (
                <div className="mt-3 rounded-[16px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.86rem] font-medium text-[var(--primary)]">
                  最近打印任务暂时不可用。上方打印机设置仍可编辑。
                </div>
              ) : null}
            </section>

            <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">打印机分配</div>
              <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                当前启用模块：厨房总票 GRAB、前台小票 FRONTDESK_RECEIPT、热厨票 HOT_KITCHEN。其他模块暂时保留。
              </div>

              <div className="mt-4 space-y-3">
                {isFeatureEnabled('DEVELOPER_TOOLS') ? (
                  <div className="rounded-[18px] bg-[rgba(38,86,160,0.08)] px-4 py-3 text-[0.86rem] text-[rgb(38,86,160)]">
                    编码测试是临时调试工具，用于验证中文打印兼容性。RP820 默认建议使用 <span className="font-semibold">GBK</span>；GB2312 仅作为备用。
                  </div>
                ) : null}
                <div className="rounded-[18px] bg-[rgba(97,0,0,0.06)] px-4 py-3 text-[0.86rem] text-[rgba(97,0,0,0.84)]">
                  字体大小可以按模块分配单独设置；如果模块未设置，则使用打印机默认字体。
                </div>
                {MODULE_OPTIONS.map((moduleOption) => {
                  const currentAssignment = assignmentsByModule.get(moduleOption.code) ?? {
                    store_id: Number(selectedStoreId),
                    printer_id: null,
                    module_code: moduleOption.code,
                    enabled: false,
                    font_size: 'MEDIUM',
                  }
                  return (
                    <AssignmentRow
                      key={moduleOption.code}
                      moduleCode={moduleOption.code}
                      label={moduleOption.label}
                      future={moduleOption.future}
                      printers={printCenter?.printers ?? []}
                      assignment={currentAssignment}
                      onSave={handleAssignmentSave}
                      onTest={handleModuleTestPrint}
                    />
                  )
                })}
              </div>
            </section>
      </div>
      {previewJob ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 px-4 py-6">
          <div className="max-h-[88vh] w-full max-w-[760px] overflow-hidden rounded-[26px] bg-[#fbfaf5] shadow-[0_24px_80px_rgba(0,0,0,0.24)]">
            <div className="flex items-start justify-between gap-4 border-b border-[rgba(26,28,25,0.08)] px-5 py-4">
              <div>
                <div className="text-[1.1rem] font-black text-[var(--on-surface)]">小票预览</div>
                <div className="mt-1 text-[0.82rem] text-[var(--muted)]">
                  任务 #{previewJob.id} · {moduleDisplayLabel(previewJob.module_code)} · {printStatusDisplayLabel(previewJob.status)}
                </div>
                {previewJob.execution_mode === 'PAD_DIRECT' ? (
                  <div className="mt-1 text-[0.78rem] font-semibold text-[rgb(118,77,21)]">
                    Pad Direct 载荷：{previewJob.escpos_payload_base64 ? `${previewJob.escpos_payload_base64.length} 个 base64 字符` : '未生成'}
                  </div>
                ) : null}
              </div>
              <button
                type="button"
                onClick={() => setPreviewJob(null)}
                className="rounded-full bg-[rgba(26,28,25,0.06)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--on-surface)]"
              >
                关闭
              </button>
            </div>
            <pre className="max-h-[70vh] overflow-auto whitespace-pre-wrap px-5 py-4 font-mono text-[0.92rem] leading-6 text-[var(--on-surface)]">
              {previewJob.rendered_text_snapshot || '该打印任务没有保存小票预览。'}
            </pre>
          </div>
        </div>
      ) : null}
    </>
  )
}

function PadDevicesTable({ devices }: { devices: StoreDeviceRecord[] }) {
  if (!devices.length) {
    return (
      <div className="mt-4 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
        这家店还没有注册 Pad Direct 设备。
      </div>
    )
  }

  return (
    <div className="mt-4 overflow-x-auto rounded-[18px] bg-[rgba(26,28,25,0.04)]">
      <table className="min-w-full text-left text-[0.84rem]">
        <thead className="text-[0.72rem] uppercase tracking-[0.14em] text-[var(--muted)]">
          <tr>
            <th className="px-3 py-3">设备</th>
            <th className="px-3 py-3">类型</th>
            <th className="px-3 py-3">状态</th>
            <th className="px-3 py-3">最后在线</th>
            <th className="px-3 py-3">App</th>
            <th className="px-3 py-3">平台</th>
          </tr>
        </thead>
        <tbody>
          {devices.map((device) => (
            <tr key={device.id} className="border-t border-[rgba(26,28,25,0.06)]">
              <td className="px-3 py-3">
                <div className="font-semibold text-[var(--on-surface)]">{device.device_name ?? `设备 #${device.id}`}</div>
                <div className="text-[0.76rem] text-[var(--muted)]">ID {device.id}</div>
              </td>
              <td className="px-3 py-3 text-[var(--muted)]">{device.device_type ?? '-'}</td>
              <td className="px-3 py-3">
                <span className={`rounded-full px-2.5 py-1 text-[0.72rem] font-bold ${
                  device.is_active === false || device.status !== 'ACTIVE'
                    ? 'bg-[rgba(97,0,0,0.1)] text-[var(--primary)]'
                    : 'bg-[rgba(18,141,77,0.12)] text-[rgb(25,112,69)]'
                }`}
                >
                  {device.is_active === false ? '未启用 INACTIVE' : device.status ?? '未知 UNKNOWN'}
                </span>
              </td>
              <td className="px-3 py-3 text-[var(--muted)]">{formatDateTime(device.last_seen_at)}</td>
              <td className="px-3 py-3 text-[var(--muted)]">{device.app_version ?? '-'}</td>
              <td className="px-3 py-3 text-[var(--muted)]">{device.platform ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function PrintJobsTable({
  jobs,
  emptyText,
  onReprint,
  onPreview,
}: {
  jobs: PrintJobRecord[]
  emptyText: string
  onReprint: (jobId: number) => void
  onPreview: (job: PrintJobRecord) => void
}) {
  if (!jobs.length) {
    return (
      <div className="mt-4 rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
        {emptyText}
      </div>
    )
  }

  return (
    <div className="mt-4 overflow-x-auto rounded-[18px] bg-[rgba(26,28,25,0.04)]">
      <table className="min-w-full text-left text-[0.84rem]">
        <thead className="text-[0.72rem] uppercase tracking-[0.14em] text-[var(--muted)]">
          <tr>
            <th className="px-3 py-3">创建时间</th>
            <th className="px-3 py-3">订单</th>
            <th className="px-3 py-3">模块</th>
            <th className="px-3 py-3">打印机</th>
            <th className="px-3 py-3">状态</th>
            <th className="px-3 py-3">耗时</th>
            <th className="px-3 py-3">Pad 领取</th>
            <th className="px-3 py-3">重试</th>
            <th className="px-3 py-3">错误</th>
            <th className="px-3 py-3 text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          {jobs.map((job) => {
            const padNotice = padDirectClaimNotice(job)
            const pendingNotice = pendingAgeNotice(job)
            const nativeDiagnostics = printJobNativeDiagnostics(job)
            return (
              <tr key={job.id} className="border-t border-[rgba(26,28,25,0.06)]">
                <td className="px-3 py-3 font-medium text-[var(--on-surface)]">{formatDateTime(job.created_at)}</td>
                <td className="px-3 py-3 text-[var(--muted)]">{job.order_id ? `#${job.order_id}` : '-'}</td>
                <td className="px-3 py-3 font-semibold text-[var(--on-surface)]">{printJobDisplayLabel(job)}</td>
                <td className="px-3 py-3 text-[var(--muted)]">
                  <div>{job.printer_name ?? job.printer_endpoint ?? '-'}</div>
                  {job.printer_id ? (
                    <div className="mt-1 text-[0.72rem] font-semibold">
                      ID {job.printer_id}{job.printer_endpoint ? ` · ${job.printer_endpoint}` : ''}
                    </div>
                  ) : null}
                </td>
                <td className="px-3 py-3">
                  <span className={`rounded-full px-2.5 py-1 text-[0.72rem] font-bold ${statusTone(job.status)}`}>{printStatusDisplayLabel(job.status)}</span>
                  {job.execution_mode ? (
                    <div className="mt-1 text-[0.72rem] font-semibold text-[var(--muted)]">{job.execution_mode}</div>
                  ) : null}
                  {pendingNotice ? (
                    <div className={`mt-2 rounded-[12px] px-2.5 py-1.5 text-[0.72rem] font-semibold ${padDirectNoticeToneClass(pendingNotice.tone)}`}>
                      {pendingNotice.message}
                    </div>
                  ) : null}
                </td>
                <td className="px-3 py-3 text-[0.74rem] text-[var(--muted)]">
                  <div>年龄：{durationLabel(jobAgeMs(job))}</div>
                  <div>排队：{durationLabel(queueDelayMs(job))}</div>
                  <div>总计：{durationLabel(totalJobTimeMs(job))}</div>
                  {job.claimed_at ? <div>领取：{formatDateTime(job.claimed_at)}</div> : null}
                  {job.printed_at ? <div>打印：{formatDateTime(job.printed_at)}</div> : null}
                  {job.failed_at ? <div>失败：{formatDateTime(job.failed_at)}</div> : null}
                </td>
                <td className="px-3 py-3 text-[var(--muted)]">
                  {job.claimed_by_device_id ? (
                    <div>
                      <div className="font-semibold text-[var(--on-surface)]">设备 #{job.claimed_by_device_id}</div>
                      <div className="text-[0.74rem]">到 {formatDateTime(job.claim_expires_at)}</div>
                      {padNotice ? (
                        <div className={`mt-2 rounded-[12px] px-2.5 py-1.5 text-[0.72rem] font-semibold ${padDirectNoticeToneClass(padNotice.tone)}`}>
                          {padNotice.message}
                        </div>
                      ) : null}
                    </div>
                  ) : job.printed_by_device_id ? (
                    <div>
                      <div className="font-semibold text-[var(--on-surface)]">由设备 #{job.printed_by_device_id} 打印</div>
                      <div className="text-[0.74rem]">Pad Direct</div>
                    </div>
                  ) : job.execution_mode === 'PAD_DIRECT' ? (
                    <span>等待 Pad</span>
                  ) : (
                    <span>-</span>
                  )}
                </td>
                <td className="px-3 py-3 text-[var(--muted)]">{job.retry_count ?? 0}/{job.max_retry_count ?? 0}</td>
                <td className="max-w-[20rem] px-3 py-3 text-[var(--muted)]" title={job.error_message ?? ''}>
                  {job.error_code ? (
                    <div className={`mb-1 inline-flex rounded-full px-2 py-0.5 text-[0.68rem] font-black ${
                      job.error_code === CLOUD_PRIVATE_PRINTER_BLOCKED
                        ? 'bg-[rgba(151,34,34,0.14)] text-[rgb(116,22,22)]'
                        : 'bg-white text-[var(--muted)]'
                    }`}>
                      {job.error_code}
                    </div>
                  ) : null}
                  {padNotice ? (
                    <div className={`mb-2 rounded-[12px] px-2.5 py-1.5 text-[0.72rem] font-semibold ${padDirectNoticeToneClass(padNotice.tone)}`}>
                      {padNotice.message}
                    </div>
                  ) : null}
                  {pendingNotice ? (
                    <div className={`mb-2 rounded-[12px] px-2.5 py-1.5 text-[0.72rem] font-semibold ${padDirectNoticeToneClass(pendingNotice.tone)}`}>
                      {pendingNotice.message}
                    </div>
                  ) : null}
                  {nativeDiagnostics ? (
                    <div className="mb-2 rounded-[12px] bg-[rgba(151,34,34,0.08)] px-2.5 py-1.5 text-[0.72rem] font-semibold text-[rgb(116,22,22)]">
                      <div>
                        原生诊断：
                        {nativeDiagnostics.nativeErrorCode ? ` ${nativeDiagnostics.nativeErrorCode}` : ''}
                        {nativeDiagnostics.phase ? ` / ${nativeDiagnostics.phase}` : ''}
                      </div>
                      <div className="mt-1 text-[0.68rem]">
                        {nativeDiagnostics.endpoint || job.printer_endpoint || '-'}
                        {nativeDiagnostics.bytesWritten ? ` · bytes ${nativeDiagnostics.bytesWritten}` : ''}
                        {nativeDiagnostics.deviceId ? ` · device #${nativeDiagnostics.deviceId}` : ''}
                        {nativeDiagnostics.exceptionClass ? ` · ${nativeDiagnostics.exceptionClass}` : ''}
                      </div>
                    </div>
                  ) : null}
                  <div className="font-semibold text-[var(--on-surface)]">
                    {printJobOperatorMessage(job) || '-'}
                  </div>
                  {job.operator_message && job.error_message && job.operator_message !== job.error_message ? (
                    <div className="mt-1 max-w-[20rem] truncate text-[0.72rem] text-[var(--muted)]">
                      技术信息：{job.error_message}
                    </div>
                  ) : null}
                </td>
                <td className="px-3 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      onClick={() => onPreview(job)}
                      className="rounded-full bg-[rgba(38,86,160,0.12)] px-3 py-1.5 text-[0.78rem] font-semibold text-[rgb(38,86,160)]"
                    >
                      预览小票
                    </button>
                    <button
                      type="button"
                      onClick={() => onReprint(job.id)}
                      className="rounded-full bg-[rgba(18,141,77,0.12)] px-3 py-1.5 text-[0.78rem] font-semibold text-[rgb(25,112,69)]"
                    >
                      重新打印
                    </button>
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function AssignmentRow({
  moduleCode,
  label,
  future,
  printers,
  assignment,
  onSave,
  onTest,
}: {
  moduleCode: string
  label: string
  future: boolean
  printers: PrinterConfigRecord[]
  assignment: PrinterAssignmentRecord
  onSave: (assignment: PrinterAssignmentRecord) => void
  onTest: (moduleCode: string) => void
}) {
  const [draft, setDraft] = useState<PrinterAssignmentRecord>(assignment)
  const selectedPrinter = printers.find((printer) => printer.id === draft.printer_id)
  const status = !draft.printer_id
    ? {
        label: `${moduleDisplayLabel(moduleCode)} 未分配打印机`,
        className: 'bg-[rgba(151,34,34,0.12)] text-[rgb(116,22,22)]',
      }
    : !draft.enabled
      ? {
          label: '分配已停用',
          className: 'bg-[rgba(180,120,20,0.16)] text-[rgb(130,82,14)]',
        }
      : selectedPrinter && selectedPrinter.enabled === false
        ? {
            label: '已分配打印机已停用',
            className: 'bg-[rgba(151,34,34,0.12)] text-[rgb(116,22,22)]',
          }
        : {
            label: '已启用',
            className: 'bg-[rgba(18,141,77,0.12)] text-[rgb(25,112,69)]',
          }

  useEffect(() => {
    setDraft(assignment)
  }, [assignment])

  return (
    <div className="rounded-[20px] bg-[rgba(26,28,25,0.04)] px-4 py-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[0.96rem] font-bold text-[var(--on-surface)]">{label}</div>
          <div className="mt-1 text-[0.82rem] text-[var(--muted)]">
            {future ? '预留给后续打印路由。' : `${selectedPrinter?.name ?? '未选择打印机'} · ${draft.enabled ? '模块打印已启用' : '模块打印已关闭'}`}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <span className={`rounded-full px-3 py-1.5 text-[0.78rem] font-semibold ${status.className}`}>{future ? '未来模块' : status.label}</span>
          {future ? (
            <span className="rounded-full bg-[rgba(26,28,25,0.06)] px-3 py-1.5 text-[0.78rem] font-semibold text-[var(--muted)]">预留</span>
          ) : null}
        </div>
      </div>

      <div className="mt-3 grid gap-3 md:grid-cols-[minmax(0,1fr)_12rem_12rem_auto_auto_auto] md:items-center">
        <select
          value={draft.printer_id == null ? '' : String(draft.printer_id)}
          disabled={future}
          onChange={(event) => setDraft((current) => ({ ...current, printer_id: event.target.value ? Number(event.target.value) : null }))}
          className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.94rem] text-[var(--on-surface)] outline-none disabled:opacity-60"
        >
          <option value="">未分配</option>
          {printers.map((printer) => (
            <option key={printer.id} value={printer.id}>
              {printer.name} ({printer.ip_address}:{printer.port}){printer.enabled === false ? ' - 配置已停用' : ''}
            </option>
          ))}
        </select>

        <select
          value={draft.font_size ?? 'MEDIUM'}
          disabled={future}
          onChange={(event) => setDraft((current) => ({ ...current, font_size: event.target.value }))}
          className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.94rem] text-[var(--on-surface)] outline-none disabled:opacity-60"
        >
          {FONT_SIZE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>

        {moduleCode === 'FRONTDESK_RECEIPT' ? (
          <select
            value={draft.takeout_receipt_copies ?? 1}
            disabled={future}
            onChange={(event) => setDraft((current) => ({ ...current, takeout_receipt_copies: Number(event.target.value) }))}
            className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.94rem] text-[var(--on-surface)] outline-none disabled:opacity-60"
            aria-label="Takeout receipt copies"
          >
            <option value={1}>外卖小票：1 份</option>
            <option value={2}>外卖小票：2 份</option>
          </select>
        ) : (
          <div className="rounded-[14px] bg-[rgba(26,28,25,0.035)] px-3 py-2 text-[0.82rem] font-semibold text-[var(--muted)]">
            份数：不适用
          </div>
        )}

        <label className="inline-flex items-center gap-2 text-[0.88rem] font-medium text-[var(--on-surface)]">
          <input
            type="checkbox"
            disabled={future}
            checked={draft.enabled}
            onChange={(event) => setDraft((current) => ({ ...current, enabled: event.target.checked }))}
          />
          已启用
        </label>

        <button
          type="button"
          disabled={future}
          onClick={() => onSave({ ...draft, module_code: moduleCode })}
          className="rounded-full bg-[var(--primary)] px-4 py-2 text-[0.84rem] font-semibold text-white disabled:cursor-not-allowed disabled:bg-[rgba(97,0,0,0.18)]"
        >
          保存
        </button>
        {!future && (moduleCode === 'FRONTDESK_RECEIPT' || moduleCode === 'GRAB' || moduleCode === 'HOT_KITCHEN') ? (
          <button
            type="button"
            onClick={() => onTest(moduleCode)}
            className="rounded-full bg-[rgba(18,141,77,0.12)] px-4 py-2 text-[0.84rem] font-semibold text-[rgb(25,112,69)]"
          >
            测试 {moduleDisplayLabel(moduleCode)}
          </button>
        ) : null}
      </div>
    </div>
  )
}
