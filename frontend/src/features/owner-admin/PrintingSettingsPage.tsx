import { useEffect, useMemo, useState } from 'react'
import { navigateTo } from '../frontdesk/navigation'
import { isFeatureEnabled, type FeaturePackage } from '../feature-flags/featureConfig'
import { fetchPlatformOverview, type PlatformAdminOverview } from '../../services/platformAdminService'
import {
  disablePrinterConfig,
  fetchPrintCenterOverview,
  savePrinterConfig,
  triggerCurrentFontSizeTest,
  triggerAssignedModulePrintTest,
  triggerGrabFontTest,
  triggerPrinterEncodingTest,
  triggerPrinterTest,
  updatePrinterAssignment,
  updatePrintingStatus,
  type PrintCenterOverview,
  type PrinterAssignmentRecord,
  type PrinterConfigRecord,
} from '../../services/printingAdminService'

type ToastState = { kind: 'success' | 'error'; message: string } | null

type PrinterEditorState = PrinterConfigRecord

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

export function PrintingSettingsPage() {
  const [overview, setOverview] = useState<PlatformAdminOverview | null>(null)
  const [printCenter, setPrintCenter] = useState<PrintCenterOverview | null>(null)
  const [selectedStoreId, setSelectedStoreId] = useState('1')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState<ToastState>(null)
  const [error, setError] = useState<string | null>(null)
  const [printerEditor, setPrinterEditor] = useState<PrinterEditorState | null>(null)

  const loadData = async (storeId: number) => {
    setLoading(true)
    setError(null)
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
  }

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

  const handleDisablePrinter = async (printerId: number) => {
    try {
      setToast(null)
      await disablePrinterConfig(printerId, Number(selectedStoreId))
      setPrintCenter(await fetchPrintCenterOverview(Number(selectedStoreId)))
      setToast({ kind: 'success', message: 'Printer disabled.' })
    } catch (actionError) {
      setToast({ kind: 'error', message: actionError instanceof Error ? actionError.message : 'Failed to disable printer' })
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

  return (
    <div className="min-h-screen bg-[linear-gradient(180deg,#f6f3ec_0%,#efe9dd_100%)] text-[var(--on-surface)]">
      <div className="grid min-h-screen xl:grid-cols-[260px_minmax(0,1fr)]">
        <aside className="border-r border-[rgba(97,0,0,0.08)] bg-[rgba(255,255,255,0.8)] px-4 py-5 backdrop-blur-sm">
          <div className="rounded-[24px] bg-[rgba(97,0,0,0.04)] px-4 py-4">
            <div className="text-[1.6rem] font-black tracking-[-0.05em] text-[var(--primary)]">Owner Console</div>
            <div className="mt-1 text-[0.84rem] leading-5 text-[var(--muted)]">
              {asString(overview?.organizations?.[0]?.name, 'Restaurant Organization')}
            </div>
          </div>

          <nav className="mt-5 space-y-2">
            {([
              { label: 'Home', path: '/admin/dashboard', icon: '⌂', description: 'Daily operating overview' },
              { label: 'Dining Tables', path: '/admin/settings/tables', icon: '▣', description: 'Table layout and split setup' },
              { label: 'Menu Management', path: '/admin/menu/items', icon: '☰', description: 'Menu maintenance workspace' },
              { label: 'Reports', path: '/admin/reports/sales', icon: '◫', description: 'Sales and performance reports' },
              { label: 'Printing Settings', path: '/admin/settings/printing', icon: '🖨', description: 'Print Center and assignments' },
            ] as Array<{ label: string; path: string; icon: string; description: string; feature?: FeaturePackage }>).map((item) => ({
              ...item,
              feature: (item.path.startsWith('/admin/reports') ? 'ANALYTICS' : item.path.startsWith('/admin/settings/printing') ? 'PRINTING' : 'ADMIN') as FeaturePackage,
            })).filter((item) => isFeatureEnabled(item.feature)).map((item) => {
              const active = item.path === '/admin/settings/printing'
              return (
                <button
                  key={item.path}
                  type="button"
                  onClick={() => navigateTo(item.path)}
                  className={`w-full rounded-[20px] px-4 py-3 text-left transition ${
                    active
                      ? 'bg-[var(--primary)] text-white shadow-[0_18px_34px_rgba(97,0,0,0.18)]'
                      : 'bg-transparent text-[rgba(26,28,25,0.78)] hover:bg-[rgba(97,0,0,0.06)]'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <span className="text-[1.2rem] leading-none">{item.icon}</span>
                    <div>
                      <div className="text-[0.98rem] font-semibold">{item.label}</div>
                      <div className={`mt-0.5 text-[0.76rem] ${active ? 'text-[rgba(255,255,255,0.82)]' : 'text-[var(--muted)]'}`}>
                        {item.description}
                      </div>
                    </div>
                  </div>
                </button>
              )
            })}
          </nav>
        </aside>

        <main className="px-5 py-5 xl:px-6">
          <div className="mx-auto max-w-[1500px] space-y-5">
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
                  <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">Print Center Status</div>
                  <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                    Global store-level gate for all printing. Module assignment still applies underneath.
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => handleStatusToggle(!printCenter?.printing_enabled)}
                  disabled={loading}
                  className={`rounded-full px-4 py-2 text-[0.88rem] font-semibold ${
                    printCenter?.printing_enabled
                      ? 'bg-[rgba(18,141,77,0.12)] text-[rgb(25,112,69)]'
                      : 'bg-[rgba(97,0,0,0.08)] text-[var(--primary)]'
                  }`}
                >
                  {printCenter?.printing_enabled ? 'Enabled' : 'Disabled'}
                </button>
              </div>
            </section>

            <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">Printer List</div>
                  <div className="mt-1 text-[0.86rem] text-[var(--muted)]">
                    Add and maintain TCP printers used by business modules. For RP820 local testing, use `GBK` as the default text encoding.
                  </div>
                </div>
                <button
                  type="button"
                  onClick={startCreatePrinter}
                  className="rounded-full bg-[var(--primary)] px-4 py-2 text-[0.88rem] font-semibold text-white"
                >
                  Add Printer
                </button>
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
                    <label className="inline-flex items-center gap-2 text-[0.88rem] font-medium text-[var(--on-surface)]">
                      <input
                        type="checkbox"
                        checked={printerEditor.enabled}
                        onChange={(event) => setPrinterEditor((current) => current ? { ...current, enabled: event.target.checked } : current)}
                      />
                      Enabled
                    </label>
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
                {(printCenter?.printers ?? []).map((printer) => (
                  <div key={printer.id} className="rounded-[20px] bg-[rgba(26,28,25,0.04)] px-4 py-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <div className="text-[1rem] font-bold text-[var(--on-surface)]">{printer.name}</div>
                        <div className="mt-1 text-[0.84rem] text-[var(--muted)]">
                          {printer.ip_address}:{printer.port} · {printer.printer_type} · {printer.paper_width_mm}mm · {printer.text_encoding ?? 'GBK'}
                          {printer.escpos_code_page != null ? ` · CP ${printer.escpos_code_page}` : ''}
                          {` · ${FONT_SIZE_OPTIONS.find((option) => option.value === (printer.font_size ?? 'MEDIUM'))?.label ?? (printer.font_size ?? 'MEDIUM')}`}
                        </div>
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
                          Test Print
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
                          onClick={() => handleDisablePrinter(printer.id!)}
                          className="rounded-full bg-[rgba(97,0,0,0.08)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--primary)]"
                        >
                          Disable
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
                {!loading && !(printCenter?.printers?.length ?? 0) ? (
                  <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
                    No printers configured yet.
                  </div>
                ) : null}
              </div>
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
        </main>
      </div>
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

  useEffect(() => {
    setDraft(assignment)
  }, [assignment])

  return (
    <div className="rounded-[20px] bg-[rgba(26,28,25,0.04)] px-4 py-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[0.96rem] font-bold text-[var(--on-surface)]">{label}</div>
          <div className="mt-1 text-[0.82rem] text-[var(--muted)]">
            {future ? 'Reserved for future routing.' : 'Editable in Phase 1.'}
          </div>
        </div>
        {future ? (
          <span className="rounded-full bg-[rgba(26,28,25,0.06)] px-3 py-1.5 text-[0.78rem] font-semibold text-[var(--muted)]">Future</span>
        ) : null}
      </div>

      <div className="mt-3 grid gap-3 md:grid-cols-[minmax(0,1fr)_12rem_auto_auto_auto] md:items-center">
        <select
          value={draft.printer_id == null ? '' : String(draft.printer_id)}
          disabled={future}
          onChange={(event) => setDraft((current) => ({ ...current, printer_id: event.target.value ? Number(event.target.value) : null }))}
          className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2 text-[0.94rem] text-[var(--on-surface)] outline-none disabled:opacity-60"
        >
          <option value="">Unassigned</option>
          {printers.map((printer) => (
            <option key={printer.id} value={printer.id}>
              {printer.name} ({printer.ip_address}:{printer.port})
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
