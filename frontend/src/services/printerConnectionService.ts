import { fetchPrintCenterOverview, triggerPrinterConnectionTest, type PrinterConfigRecord, type PrinterConnectionTestResponse } from './printingAdminService'
import { parseAndroidBridgeJson } from '../types/androidPadBridge'

export interface ConfiguredConnectionResult extends PrinterConnectionTestResponse {
  local_printer?: PrinterConfigRecord
}

// Read the Store-authorized configuration again before accessing the Pad's LAN.
// A connection probe opens/closes a socket only: no print job, payload or worker kick.
export async function testConfiguredPrinterConnection(storeId: number, printerId: number): Promise<ConfiguredConnectionResult> {
  const overview = await fetchPrintCenterOverview(storeId)
  const printer = overview.printers.find((candidate) => candidate.id === printerId)
  if (overview.store_id !== storeId || !printer || printer.store_id !== storeId) {
    throw new Error('Printer does not belong to store')
  }
  if (overview.printing_mode !== 'PAD_DIRECT') return triggerPrinterConnectionTest(storeId, printerId)
  const bridge = typeof window === 'undefined' ? undefined : window.RestaurantPrinter
  const checked_at = new Date().toISOString()
  const failure = (message: string): ConfiguredConnectionResult => ({ success: false, message, checked_at, local_printer: printer })
  if (!bridge?.testConnection) return failure('请在店内 Android Pad 上运行连接测试。')
  if (!printer.enabled || !printer.ip_address?.trim() || !Number.isInteger(printer.port) || printer.port < 1 || printer.port > 65535) {
    return failure('打印机已停用或地址、端口未配置，请先保存打印机配置。')
  }
  try {
    const result = parseAndroidBridgeJson<{ success?: boolean; message?: string; bytes_written?: number }>(bridge.testConnection(JSON.stringify({
      ip: printer.ip_address, port: printer.port, timeoutMs: printer.timeout_ms || 3000,
    })))
    if (!result || typeof result.success !== 'boolean') return failure('Android 打印机连接测试返回无效结果。')
    return { success: result.success, message: result.message || (result.success ? 'Connection successful' : 'Connection failed'), checked_at, local_printer: printer }
  } catch (error) {
    return failure(error instanceof Error ? error.message : 'Android 打印机连接测试失败。')
  }
}

export function connectionResultKey(printer: PrinterConfigRecord) {
  return `${printer.store_id}:${printer.id}:${printer.ip_address}:${printer.port}:${printer.enabled}:${printer.timeout_ms}`
}
