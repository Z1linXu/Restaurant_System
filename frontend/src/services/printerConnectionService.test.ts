import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { fetchPrintCenterOverview, triggerPrinterConnectionTest, type PrinterConfigRecord } from './printingAdminService'
import { testConfiguredPrinterConnection } from './printerConnectionService'
vi.mock('./printingAdminService', () => ({ fetchPrintCenterOverview: vi.fn(), triggerPrinterConnectionTest: vi.fn() }))
const printer: PrinterConfigRecord = { id: 3, store_id: 1, name: 'Printer', ip_address: '192.0.2.10', port: 9100, enabled: true, timeout_ms: 1000, printer_type: 'ESC_POS_TCP', paper_width_mm: 80 }
let testConnection: ReturnType<typeof vi.fn>
beforeEach(() => {
  vi.resetAllMocks()
  testConnection = vi.fn(() => JSON.stringify({ success: true, message: 'Connection successful', bytes_written: 0 }))
  vi.stubGlobal('window', { RestaurantPrinter: { testConnection } })
  vi.mocked(fetchPrintCenterOverview).mockResolvedValue({ store_id: 1, printing_enabled: true, printing_mode: 'PAD_DIRECT', printers: [printer], assignments: [] })
})
afterEach(() => vi.unstubAllGlobals())
it('uses the Store-authorized endpoint through Android, without creating jobs or invoking the cloud TCP path', async () => {
  const result = await testConfiguredPrinterConnection(1, 3)
  expect(fetchPrintCenterOverview).toHaveBeenCalledWith(1)
  expect(JSON.parse(testConnection.mock.calls[0][0])).toEqual({ ip: printer.ip_address, port: 9100, timeoutMs: 1000 })
  expect(result.success).toBe(true)
  expect(result.local_printer).toEqual(printer)
  expect(triggerPrinterConnectionTest).not.toHaveBeenCalled()
})
it.each(['{"success":false,"message":"Connect timed out"}', '{}', 'invalid'])('reports native connection failures without claiming success: %s', async (raw) => {
  testConnection.mockReturnValue(raw)
  expect((await testConfiguredPrinterConnection(1, 3)).success).toBe(false)
  expect(triggerPrinterConnectionTest).not.toHaveBeenCalled()
})
it('requires Android in PAD_DIRECT mode', async () => {
  vi.stubGlobal('window', {})
  expect((await testConfiguredPrinterConnection(1, 3)).message).toContain('Android Pad')
  expect(triggerPrinterConnectionTest).not.toHaveBeenCalled()
})
it.each([{}, { store_id: 2 }, { printers: [{ ...printer, store_id: 2 }] }, { printers: [] }])('refuses foreign or absent printer configuration before LAN access: %s', async (override) => {
  if (Object.keys(override).length) vi.mocked(fetchPrintCenterOverview).mockResolvedValue({ store_id: 1, printing_enabled: true, printing_mode: 'PAD_DIRECT', printers: [printer], assignments: [], ...override })
  else vi.mocked(fetchPrintCenterOverview).mockRejectedValue(new Error('Forbidden'))
  await expect(testConfiguredPrinterConnection(1, 3)).rejects.toThrow()
  expect(testConnection).not.toHaveBeenCalled()
})
it.each([{ enabled: false }, { ip_address: '' }, { port: 0 }])('rejects an unusable endpoint: %s', async (override) => {
  vi.mocked(fetchPrintCenterOverview).mockResolvedValue({ store_id: 1, printing_enabled: true, printing_mode: 'PAD_DIRECT', printers: [{ ...printer, ...override }], assignments: [] })
  expect((await testConfiguredPrinterConnection(1, 3)).success).toBe(false)
  expect(testConnection).not.toHaveBeenCalled()
})
it('keeps the existing backend connection test in MOCK/REAL modes', async () => {
  for (const printing_mode of ['MOCK', 'REAL', 'DISABLED'] as const) {
    vi.mocked(fetchPrintCenterOverview).mockResolvedValue({ store_id: 1, printing_enabled: true, printing_mode, printers: [printer], assignments: [] })
    vi.mocked(triggerPrinterConnectionTest).mockResolvedValue({ success: true, message: 'Mock', checked_at: '2026-11-13' })
    expect((await testConfiguredPrinterConnection(1, 3)).success).toBe(true)
  }
  expect(triggerPrinterConnectionTest).toHaveBeenCalledTimes(3)
  expect(testConnection).not.toHaveBeenCalled()
})
