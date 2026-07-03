import { useEffect, useMemo, useState } from 'react'
import { isFeatureEnabled } from '../feature-flags/featureConfig'
import { fetchPlatformOverview, type PlatformAdminOverview } from '../../services/platformAdminService'
import { useCurrentStore } from '../store/StoreContext'
import {
  deletePrinterConfig,
  fetchPrintJobs,
  fetchPrintCenterOverview,
  fetchStoreDevices,
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

type ToastState = { kind: 'success' | 'error'; message: string } | null

type PrinterEditorState = PrinterConfigRecord
type PrintingMode = 'REAL' | 'MOCK' | 'DISABLED' | 'PAD_DIRECT'

const MODULE_OPTIONS = [
  { code: 'GRAB', label: 'GRAB', future: false },
  { code: 'FRONTDESK_RECEIPT', label: 'FRONTDESK_RECEIPT', future: false },
  { code: 'HOT_KITCHEN', label: 'HOT_KITCHEN', future: true },
  { code: 'COLD_KITCHEN', label: 'COLD_KITCHEN', future: true },
  { code: 'BAR', label: 'BAR', future: true },
  { code: 'TAKEOUT_RECEIPT', label: 'TAKEOUT_RECEIPT', future: true },
] as const

const FONT_SIZE_OPTIONS = [
  { value: 'XS', label: 'Extra Small' },
  { value: 'SMALL', label: 'Small' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LARGE', label: 'Large' },
  { value: 'XL', label: 'Extra Large' },
] as const

const CLOUD_PRIVATE_PRINTER_BLOCKED = 'CLOUD_PRIVATE_PRINTER_BLOCKED'
const CLOUD_PRIVATE_PRINTER_WARNING =
  '云端服务器不能直接连接店内局域网打印机。请切换到 PAD_DIRECT、MOCK、DISABLED，或使用本地打印桥。'

const PRINTING_MODE_OPTIONS: Array<{ value: PrintingMode; label: string; description: string }> = [
  { value: 'REAL', label: 'Real Printer / 真实打印机', description: 'Connect to configured ESC/POS printers over TCP.' },
  { value: 'MOCK', label: 'Mock / 无打印机测试模式', description: 'Render receipts and mark print jobs successful without socket connections.' },
  { value: 'PAD_DIRECT', label: 'Pad Direct / 平板本地打印', description: 'Backend queues rendered jobs; Android Pad claims and prints locally.' },
  { value: 'DISABLED', label: 'Disabled / 关闭打印', description: 'Do not dispatch automatic printing.' },
]

function asString(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback
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
    return 'Never'
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

function connectionBadge(printer: PrinterConfigRecord) {
  if (printer.last_connection_success_at) {
    return {
      label: `Connected: ${formatDateTime(printer.last_connection_success_at)}`,
      className: 'bg-[rgba(18,141,77,0.12)] text-[rgb(25,112,69)]',
    }
  }
  if (printer.last_connection_failed_at) {
    return {
      label: `Failed: ${formatDateTime(printer.last_connection_failed_at)}`,
      className: 'bg-[rgba(97,0,0,0.08)] text-[var(--primary)]',
    }
  }
  return {
    label: 'Not tested',
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
  return 'bg-[rgba(26,28,25,0.08)] text-[var(--muted)]'
}

export function PrintingSettingsPage() {
  const { storeId } = useCurrentStore()
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

  const loadData = async (storeId: number) => {
    setLoading(true)
    setJobsLoading(true)
    setDevicesLoading(true)
    setError(null)
    setJobsError(null)
    setDevicesError(null)
    try {
      const [platformOverview, printOverview] = await Promise.all([
        fetchPlatformOverview(storeId),
        fetchPrintCenterOverview(storeId),
      ])
      setOverview(platformOverview)
      setPrintCenter(printOverview)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load Print Center settings')
    } finally {
      setLoading(false)
    }

    try {
      setPrintJobs(await fetchPrintJobs({ storeId }))
    } catch (jobError) {
      setPrintJobs([])
      setJobsError(jobError instanceof Error ? jobError.message : 'Failed to load print jobs')
    } finally {
      setJobsLoading(false)
    }

    try {
      setStoreDevices(await fetchStoreDevices(storeId))
    } catch (deviceError) {
      setStoreDevices([])
      setDevicesError(deviceError instanceof Error ? deviceError.message : 'Failed to load Pad devices')
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
      setToast({ kind: 'success', message: 'Printer settings saved.' })
    } catch (saveError) {
      setToast({ kind: 'error', message: saveError instanceof Error ? saveError.message : 'Failed to save printer' })
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
        message: `Printer is currently assigned to modules: ${assignedModules.join(', ')}. Remove assignments before deleting.`,
      })
      return
    }
    if (!window.confirm(`Are you sure you want to delete printer '${printer.name}'?`)) {
      return
    }
    try {
      setToast(null)
      await deletePrinterConfig(printer.id, Number(selectedStoreId))
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: 'success', message: 'Printer deleted.' })
    } catch (actionError) {
      setToast({ kind: 'error', message: actionError instanceof Error ? actionError.message : 'Failed to delete printer' })
    }
  }

  const handleTestPrint = async (printerId: number, moduleCode?: string) => {
    try {
      setToast(null)
      const result = await triggerPrinterTest(Number(selectedStoreId), printerId, moduleCode)
      setToast({ kind: result.success ? 'success' : 'error', message: result.message })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : 'Test print failed' })
    }
  }

  const handleConnectionTest = async (printerId: number) => {
    try {
      setToast(null)
      const result = await triggerPrinterConnectionTest(Number(selectedStoreId), printerId)
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: result.success ? 'success' : 'error', message: result.success ? 'Connection successful.' : `Connection failed: ${result.message}` })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : 'Connection test failed' })
    }
  }

  const handleAllConnectionTests = async () => {
    const printers = printCenter?.printers ?? []
    if (!printers.length) {
      setToast({ kind: 'error', message: 'No printers configured.' })
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
          ? `${failedCount} printer connection test${failedCount === 1 ? '' : 's'} failed. Check each printer card for details.`
          : 'All printer connections tested successfully.',
      })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : 'Connection tests failed' })
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
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : 'Current font size test failed' })
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
          ? `Encoding test sent with failures: ${failures.map((entry) => entry.encoding).join(', ')}.`
          : `Encoding test tickets sent. Recommended default: ${printer.text_encoding ?? 'GBK'}.`,
      })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : 'Encoding test failed' })
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
          ? `GRAB font test sent with failures: ${failures.map((entry) => entry.test_mode).join(', ')}.`
          : `GRAB font size test tickets sent: ${result.results.map((entry) => `${entry.test_mode} (${entry.command_bytes})`).join(' · ')}.`,
      })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : 'GRAB font size test failed' })
    }
  }

  const handleStatusToggle = async (nextEnabled: boolean) => {
    try {
      setToast(null)
      await updatePrintingStatus(Number(selectedStoreId), nextEnabled)
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: 'success', message: `Printing ${nextEnabled ? 'enabled' : 'disabled'} for this store.` })
    } catch (statusError) {
      setToast({ kind: 'error', message: statusError instanceof Error ? statusError.message : 'Failed to update printing status' })
    }
  }

  const handleModeChange = async (nextMode: PrintingMode) => {
    try {
      setToast(null)
      await updatePrintingMode(Number(selectedStoreId), nextMode)
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: 'success', message: `Printing mode changed to ${nextMode}.` })
    } catch (statusError) {
      setToast({ kind: 'error', message: statusError instanceof Error ? statusError.message : 'Failed to update printing mode' })
    }
  }

  const handleAssignmentSave = async (assignment: PrinterAssignmentRecord) => {
    try {
      setToast(null)
      await updatePrinterAssignment(assignment)
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: 'success', message: `Assignment saved for ${assignment.module_code}.` })
    } catch (assignmentError) {
      setToast({ kind: 'error', message: assignmentError instanceof Error ? assignmentError.message : 'Failed to save assignment' })
    }
  }

  const handleModuleTestPrint = async (moduleCode: string) => {
    try {
      setToast(null)
      const result = await triggerAssignedModulePrintTest(Number(selectedStoreId), moduleCode)
      setToast({ kind: result.success ? 'success' : 'error', message: result.message })
    } catch (testError) {
      setToast({ kind: 'error', message: testError instanceof Error ? testError.message : 'Module test print failed' })
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
        message: result.status === 'PRINTED' ? `Print job #${jobId} reprinted.` : `Reprint failed: ${result.error_message ?? 'Unknown error'}`,
      })
    } catch (reprintError) {
      setToast({ kind: 'error', message: reprintError instanceof Error ? reprintError.message : 'Reprint failed' })
    }
  }

  const refreshJobs = async () => {
    try {
      setJobsLoading(true)
      setJobsError(null)
      setPrintJobs(await fetchPrintJobs({ storeId: Number(selectedStoreId) }))
    } catch (jobError) {
      setJobsError(jobError instanceof Error ? jobError.message : 'Failed to refresh print jobs')
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
      setDevicesError(deviceError instanceof Error ? deviceError.message : 'Failed to refresh Pad devices')
    } finally {
      setDevicesLoading(false)
    }
  }

  return (
    <>
      <div className="space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-4 rounded-[28px] bg-[rgba(255,255,255,0.84)] px-5 py-4 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div>
                <div className="text-[1.7rem] font-black tracking-[-0.05em] text-[var(--on-surface)]">Print Center</div>
                <div className="mt-1 text-[0.9rem] text-[var(--muted)]">
                  Multi-printer routing foundation for grab tickets and frontdesk receipts.
                </div>
              </div>

              <div className="rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-2.5">
                <div className="text-[0.72rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">Store Scope</div>
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
                  <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">Print Center Mode</div>
                  <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                    Choose real printers, mock no-printer testing, or disabled automatic printing.
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
                  Pad Direct mode queues rendered print jobs for an Android Pad to claim and print locally. The backend will not connect to LAN printers.
                </div>
              ) : null}
              {printingMode === 'DISABLED' ? (
                <div className="mt-4 rounded-[18px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.9rem] font-semibold text-[var(--primary)]">
                  Printing is disabled. Orders still submit normally, but automatic print jobs are cancelled.
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
                Legacy status toggle: {printCenter?.printing_enabled ? 'Enabled' : 'Disabled'}
              </button>
            </section>

            <button
              type="button"
              onClick={() => document.getElementById('failed-print-jobs')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
              className={`w-full rounded-[26px] px-5 py-4 text-left shadow-[0_18px_34px_rgba(26,28,25,0.05)] ${
                failedPrintJobs.length
                  ? 'bg-[rgba(151,34,34,0.12)] text-[rgb(116,22,22)]'
                  : 'bg-[rgba(18,141,77,0.1)] text-[rgb(25,112,69)]'
              }`}
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-[1.1rem] font-black">Failed Print Jobs: {failedPrintJobs.length}</div>
                  <div className="mt-1 text-[0.86rem] font-medium">
                    {failedPrintJobs.length
                      ? 'Kitchen or receipt print failures need immediate reprint.'
                      : 'No failed print jobs today.'}
                  </div>
                </div>
                <span className="rounded-full bg-white/70 px-4 py-2 text-[0.84rem] font-bold">
                  View Failed Jobs
                </span>
              </div>
            </button>

            <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">Pad Direct Devices</div>
                  <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                    Registered Android Pad printers for local LAN printing. Tokens are never shown after registration.
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => void refreshDevices()}
                  className="rounded-full bg-[rgba(26,28,25,0.06)] px-4 py-2 text-[0.84rem] font-semibold text-[var(--on-surface)]"
                >
                  {devicesLoading ? 'Refreshing...' : 'Refresh Devices'}
                </button>
              </div>
              <PadDevicesTable devices={storeDevices} />
              {devicesError ? (
                <div className="mt-3 rounded-[16px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.86rem] font-medium text-[var(--primary)]">
                  Pad devices failed to load: {devicesError}
                </div>
              ) : null}
            </section>

            <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">Printer List</div>
                  <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                    Add and maintain TCP printers used by business modules. For RP820 local testing, use `GBK` as the default text encoding.
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={handleAllConnectionTests}
                    disabled={testingAllConnections || !(printCenter?.printers ?? []).length}
                    className="rounded-full bg-[rgba(38,86,160,0.12)] px-4 py-2 text-[0.88rem] font-semibold text-[rgb(38,86,160)] disabled:opacity-60"
                  >
                    {testingAllConnections ? 'Testing...' : 'Test All Connections'}
                  </button>
                  <button
                    type="button"
                    onClick={startCreatePrinter}
                    className="rounded-full bg-[var(--primary)] px-4 py-2 text-[0.88rem] font-semibold text-white"
                  >
                    Add Printer
                  </button>
                </div>
              </div>

              {printerEditor ? (
                <div className="mt-4 rounded-[20px] bg-[rgba(26,28,25,0.04)] p-4">
                  <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      Name
                      <input
                        value={printerEditor.name}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, name: event.target.value } : current)}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      Printer IP
                      <input
                        value={printerEditor.ip_address}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, ip_address: event.target.value } : current)}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      Port
                      <input
                        type="number"
                        value={printerEditor.port}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, port: Number(event.target.value) } : current)}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      Timeout (ms)
                      <input
                        type="number"
                        value={printerEditor.timeout_ms}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, timeout_ms: Number(event.target.value) } : current)}
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      Text Encoding
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
                      ESC/POS Code Page
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
                        placeholder="Optional"
                        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.95rem] text-[var(--on-surface)] outline-none"
                      />
                    </label>
                    <label className="text-[0.84rem] text-[var(--muted)]">
                      Font Size
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
                      Printer configs are always active. Use Assignment status to control module printing.
                    </span>
                    <button
                      type="button"
                      onClick={handleSavePrinter}
                      disabled={saving}
                      className="rounded-full bg-[var(--primary)] px-4 py-2 text-[0.88rem] font-semibold text-white disabled:opacity-60"
                    >
                      {saving ? 'Saving...' : 'Save Printer'}
                    </button>
                    <button
                      type="button"
                      onClick={() => setPrinterEditor(null)}
                      className="rounded-full bg-[rgba(26,28,25,0.06)] px-4 py-2 text-[0.88rem] font-semibold text-[var(--on-surface)]"
                    >
                      Cancel
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
                          <span className="rounded-full bg-white px-2.5 py-1">Modules: {assignmentsByPrinter.get(printer.id!)?.join(', ') || 'None'}</span>
                          <span className="rounded-full bg-white px-2.5 py-1">Last print: {formatDateTime(printer.last_successful_print_at)}</span>
                          <span className="rounded-full bg-white px-2.5 py-1">Last fail: {formatDateTime(printer.last_failed_print_at)}</span>
                          <span className={`rounded-full px-2.5 py-1 ${connection.className}`}>Connection: {connection.label}</span>
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
                          Edit
                        </button>
                        <button
                          type="button"
                          onClick={() => handleTestPrint(printer.id!, 'FRONTDESK_RECEIPT')}
                          className="rounded-full bg-[rgba(18,141,77,0.12)] px-3 py-1.5 text-[0.82rem] font-semibold text-[rgb(25,112,69)]"
                        >
                          Direct Test Print
                        </button>
                        <button
                          type="button"
                          onClick={() => handleConnectionTest(printer.id!)}
                          className="rounded-full bg-[rgba(38,86,160,0.12)] px-3 py-1.5 text-[0.82rem] font-semibold text-[rgb(38,86,160)]"
                        >
                          Test Connection
                        </button>
                        {isFeatureEnabled('DEVELOPER_TOOLS') ? (
                          <>
                            <button
                              type="button"
                              onClick={() => handleEncodingTest(printer)}
                              className="rounded-full bg-[rgba(38,86,160,0.12)] px-3 py-1.5 text-[0.82rem] font-semibold text-[rgb(38,86,160)]"
                            >
                              Encoding Test
                            </button>
                            <button
                              type="button"
                              onClick={() => handleCurrentFontSizeTest(printer.id!)}
                              className="rounded-full bg-[rgba(38,86,160,0.12)] px-3 py-1.5 text-[0.82rem] font-semibold text-[rgb(38,86,160)]"
                            >
                              Test Current Font Size
                            </button>
                            <button
                              type="button"
                              onClick={() => handleGrabFontTest(printer)}
                              className="rounded-full bg-[rgba(97,0,0,0.08)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--primary)]"
                            >
                              GRAB Font Size Test
                            </button>
                          </>
                        ) : null}
                        <button
                          type="button"
                          onClick={() => handleDeletePrinter(printer)}
                          className="rounded-full bg-[rgba(97,0,0,0.08)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--primary)]"
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  </div>
                  )
                })}
                {!loading && !(printCenter?.printers?.length ?? 0) ? (
                  <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
                    No printers configured yet.
                  </div>
                ) : null}
              </div>
            </section>

            <section id="failed-print-jobs" className="scroll-mt-4 rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">Failed Print Jobs</div>
                  <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                    Fix the printer, then reprint failed kitchen or frontdesk receipts from here.
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => void refreshJobs()}
                  className="rounded-full bg-[rgba(26,28,25,0.06)] px-4 py-2 text-[0.84rem] font-semibold text-[var(--on-surface)]"
                >
                  {jobsLoading ? 'Refreshing...' : 'Refresh Jobs'}
                </button>
              </div>
              <PrintJobsTable jobs={failedPrintJobs} emptyText="No failed print jobs today." onReprint={handleReprintJob} onPreview={setPreviewJob} />
              {jobsError ? (
                <div className="mt-3 rounded-[16px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.86rem] font-medium text-[var(--primary)]">
                  Print jobs failed to load: {jobsError}
                </div>
              ) : null}
            </section>

            <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">Recent Print Jobs</div>
              <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                Today&apos;s GRAB and frontdesk receipt print lifecycle.
              </div>
              <PrintJobsTable jobs={printJobs.slice(0, 12)} emptyText="No print jobs today." onReprint={handleReprintJob} onPreview={setPreviewJob} />
              {jobsError ? (
                <div className="mt-3 rounded-[16px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.86rem] font-medium text-[var(--primary)]">
                  Recent jobs unavailable. Printer settings above are still editable.
                </div>
              ) : null}
            </section>

            <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">Printer Assignment</div>
              <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                Phase 1 activates GRAB and FRONTDESK_RECEIPT. Other modules are reserved for future routing.
              </div>

              <div className="mt-4 space-y-3">
                {isFeatureEnabled('DEVELOPER_TOOLS') ? (
                  <div className="rounded-[18px] bg-[rgba(38,86,160,0.08)] px-4 py-3 text-[0.86rem] text-[rgb(38,86,160)]">
                    Encoding Test is a temporary debug tool for Chinese printer compatibility. RP820 should default to <span className="font-semibold">GBK</span>; use GB2312 only as a fallback.
                  </div>
                ) : null}
                <div className="rounded-[18px] bg-[rgba(97,0,0,0.06)] px-4 py-3 text-[0.86rem] text-[rgba(97,0,0,0.84)]">
                  Font size can be set per module assignment. If an assignment has no font size, the printer font size is used as fallback.
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
                <div className="text-[1.1rem] font-black text-[var(--on-surface)]">Rendered Receipt Preview</div>
                <div className="mt-1 text-[0.82rem] text-[var(--muted)]">
                  Job #{previewJob.id} · {previewJob.module_code} · {previewJob.status}
                </div>
                {previewJob.execution_mode === 'PAD_DIRECT' ? (
                  <div className="mt-1 text-[0.78rem] font-semibold text-[rgb(118,77,21)]">
                    Pad Direct payload: {previewJob.escpos_payload_base64 ? `${previewJob.escpos_payload_base64.length} base64 chars` : 'not generated'}
                  </div>
                ) : null}
              </div>
              <button
                type="button"
                onClick={() => setPreviewJob(null)}
                className="rounded-full bg-[rgba(26,28,25,0.06)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--on-surface)]"
              >
                Close
              </button>
            </div>
            <pre className="max-h-[70vh] overflow-auto whitespace-pre-wrap px-5 py-4 font-mono text-[0.92rem] leading-6 text-[var(--on-surface)]">
              {previewJob.rendered_text_snapshot || 'No rendered receipt snapshot saved for this job.'}
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
        No Pad devices registered for this store yet.
      </div>
    )
  }

  return (
    <div className="mt-4 overflow-x-auto rounded-[18px] bg-[rgba(26,28,25,0.04)]">
      <table className="min-w-full text-left text-[0.84rem]">
        <thead className="text-[0.72rem] uppercase tracking-[0.14em] text-[var(--muted)]">
          <tr>
            <th className="px-3 py-3">Device</th>
            <th className="px-3 py-3">Type</th>
            <th className="px-3 py-3">Status</th>
            <th className="px-3 py-3">Last Seen</th>
            <th className="px-3 py-3">App</th>
            <th className="px-3 py-3">Platform</th>
          </tr>
        </thead>
        <tbody>
          {devices.map((device) => (
            <tr key={device.id} className="border-t border-[rgba(26,28,25,0.06)]">
              <td className="px-3 py-3">
                <div className="font-semibold text-[var(--on-surface)]">{device.device_name ?? `Device #${device.id}`}</div>
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
                  {device.is_active === false ? 'INACTIVE' : device.status ?? 'UNKNOWN'}
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
            <th className="px-3 py-3">Created</th>
            <th className="px-3 py-3">Order</th>
            <th className="px-3 py-3">Module</th>
            <th className="px-3 py-3">Printer</th>
            <th className="px-3 py-3">Status</th>
            <th className="px-3 py-3">Pad Claim</th>
            <th className="px-3 py-3">Retry</th>
            <th className="px-3 py-3">Error</th>
            <th className="px-3 py-3 text-right">Action</th>
          </tr>
        </thead>
        <tbody>
          {jobs.map((job) => (
            <tr key={job.id} className="border-t border-[rgba(26,28,25,0.06)]">
              <td className="px-3 py-3 font-medium text-[var(--on-surface)]">{formatDateTime(job.created_at)}</td>
              <td className="px-3 py-3 text-[var(--muted)]">{job.order_id ? `#${job.order_id}` : '-'}</td>
              <td className="px-3 py-3 font-semibold text-[var(--on-surface)]">{job.module_code}</td>
              <td className="px-3 py-3 text-[var(--muted)]">{job.printer_name ?? job.printer_endpoint ?? '-'}</td>
              <td className="px-3 py-3">
                <span className={`rounded-full px-2.5 py-1 text-[0.72rem] font-bold ${statusTone(job.status)}`}>{job.status}</span>
                {job.execution_mode ? (
                  <div className="mt-1 text-[0.72rem] font-semibold text-[var(--muted)]">{job.execution_mode}</div>
                ) : null}
              </td>
              <td className="px-3 py-3 text-[var(--muted)]">
                {job.claimed_by_device_id ? (
                  <div>
                    <div className="font-semibold text-[var(--on-surface)]">Device #{job.claimed_by_device_id}</div>
                    <div className="text-[0.74rem]">until {formatDateTime(job.claim_expires_at)}</div>
                  </div>
                ) : job.printed_by_device_id ? (
                  <div>
                    <div className="font-semibold text-[var(--on-surface)]">Printed by #{job.printed_by_device_id}</div>
                    <div className="text-[0.74rem]">Pad Direct</div>
                  </div>
                ) : job.execution_mode === 'PAD_DIRECT' ? (
                  <span>Waiting for Pad</span>
                ) : (
                  <span>-</span>
                )}
              </td>
              <td className="px-3 py-3 text-[var(--muted)]">{job.retry_count ?? 0}/{job.max_retry_count ?? 0}</td>
              <td className="max-w-[18rem] truncate px-3 py-3 text-[var(--muted)]" title={job.error_message ?? ''}>
                {job.error_message ?? '-'}
              </td>
              <td className="px-3 py-3 text-right">
                <div className="flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => onPreview(job)}
                    className="rounded-full bg-[rgba(38,86,160,0.12)] px-3 py-1.5 text-[0.78rem] font-semibold text-[rgb(38,86,160)]"
                  >
                    Preview Receipt
                  </button>
                  <button
                    type="button"
                    onClick={() => onReprint(job.id)}
                    className="rounded-full bg-[rgba(18,141,77,0.12)] px-3 py-1.5 text-[0.78rem] font-semibold text-[rgb(25,112,69)]"
                  >
                    Reprint
                  </button>
                </div>
              </td>
            </tr>
          ))}
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
        label: `${moduleCode} has no printer assigned`,
        className: 'bg-[rgba(151,34,34,0.12)] text-[rgb(116,22,22)]',
      }
    : !draft.enabled
      ? {
          label: 'Assignment Disabled',
          className: 'bg-[rgba(180,120,20,0.16)] text-[rgb(130,82,14)]',
        }
      : selectedPrinter && selectedPrinter.enabled === false
        ? {
            label: 'Assigned Printer Disabled',
            className: 'bg-[rgba(151,34,34,0.12)] text-[rgb(116,22,22)]',
          }
        : {
            label: 'Enabled',
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
            {future ? 'Reserved for future routing.' : `${selectedPrinter?.name ?? 'No printer selected'} · ${draft.enabled ? 'Module printing enabled' : 'Module printing disabled'}`}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <span className={`rounded-full px-3 py-1.5 text-[0.78rem] font-semibold ${status.className}`}>{future ? 'Future' : status.label}</span>
          {future ? (
            <span className="rounded-full bg-[rgba(26,28,25,0.06)] px-3 py-1.5 text-[0.78rem] font-semibold text-[var(--muted)]">Reserved</span>
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
          <option value="">Unassigned</option>
          {printers.map((printer) => (
            <option key={printer.id} value={printer.id}>
              {printer.name} ({printer.ip_address}:{printer.port}){printer.enabled === false ? ' - disabled config' : ''}
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
            <option value={1}>Takeout: 1 copy</option>
            <option value={2}>Takeout: 2 copies</option>
          </select>
        ) : (
          <div className="rounded-[14px] bg-[rgba(26,28,25,0.035)] px-3 py-2 text-[0.82rem] font-semibold text-[var(--muted)]">
            Copies: n/a
          </div>
        )}

        <label className="inline-flex items-center gap-2 text-[0.88rem] font-medium text-[var(--on-surface)]">
          <input
            type="checkbox"
            disabled={future}
            checked={draft.enabled}
            onChange={(event) => setDraft((current) => ({ ...current, enabled: event.target.checked }))}
          />
          Enabled
        </label>

        <button
          type="button"
          disabled={future}
          onClick={() => onSave({ ...draft, module_code: moduleCode })}
          className="rounded-full bg-[var(--primary)] px-4 py-2 text-[0.84rem] font-semibold text-white disabled:cursor-not-allowed disabled:bg-[rgba(97,0,0,0.18)]"
        >
          Save
        </button>
        {!future && (moduleCode === 'FRONTDESK_RECEIPT' || moduleCode === 'GRAB') ? (
          <button
            type="button"
            onClick={() => onTest(moduleCode)}
            className="rounded-full bg-[rgba(18,141,77,0.12)] px-4 py-2 text-[0.84rem] font-semibold text-[rgb(25,112,69)]"
          >
            Test {moduleCode}
          </button>
        ) : null}
      </div>
    </div>
  )
}
