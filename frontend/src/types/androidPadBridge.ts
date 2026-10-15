export interface AndroidPadDeviceStatus {
  paired?: boolean
  can_replace_binding?: boolean
  legacy_pairing_retained?: boolean
  device_id?: string | number | null
  store_id?: string | number | null
  device_store_id?: string | number | null
  device_name?: string | null
  registered_at?: string | null
  token_last4?: string | null
  app_version?: string | null
  platform?: string | null
  auto_print_enabled?: boolean
  success?: boolean
  message?: string | null
}

export interface AndroidPadPrintWorkerStatus {
  success?: boolean
  auto_enabled?: boolean
  worker_running?: boolean
  worker_state?: string | null
  worker_state_label?: string | null
  app_foreground?: boolean
  job_in_progress?: boolean
  recovering?: boolean
  error_stopped?: boolean
  user_stopped?: boolean
  poll_scheduled?: boolean
  watchdog_scheduled?: boolean
  last_poll_at_ms?: number | null
  last_poll_finished_at_ms?: number | null
  last_poll_result_count?: number | null
  last_poll_duration_ms?: number | null
  last_error?: string | null
  last_stop_reason?: string | null
  last_start_reason?: string | null
  next_delay_ms?: number | null
  recovery_delay_ms?: number | null
  recovery_attempt?: number | null
  consecutive_errors?: number | null
  current_job_id?: string | number | null
  current_module?: string | null
  current_printer_endpoint?: string | null
  device_id?: string | number | null
  store_id?: string | number | null
  message?: string | null
}

export interface AndroidPadDeviceBridge {
  attestPrintRequest?: (json: string) => Promise<string>
  saveDeviceCredentials: (json: string) => Promise<string>
  getDeviceStatus: () => Promise<string>
  clearDeviceCredentials: () => Promise<string>
  getPrintWorkerStatus?: () => Promise<string>
  kickPrintWorker?: (json: string) => Promise<string>
}

interface NativeMessageChannel {
  postMessage: (message: string) => void
  onmessage?: (event: { data: string }) => void
}
declare global {
  interface Window {
    RestaurantPadMessages?: NativeMessageChannel
    // Detection only: never invoke an unverified legacy JavascriptInterface.
    RestaurantPadDevice?: unknown
  }
}

let channel: NativeMessageChannel | undefined
let bridge: AndroidPadDeviceBridge | undefined
let sequence = 0
const pending = new Map<string, { resolve: (value: string) => void; reject: (error: Error) => void; timer: ReturnType<typeof setTimeout> }>()
export function getAndroidPadDeviceBridge(): AndroidPadDeviceBridge | undefined {
  const next = typeof window === 'undefined' ? undefined : window.RestaurantPadMessages
  if (!next) return undefined
  if (channel === next) return bridge
  for (const operation of pending.values()) {
    clearTimeout(operation.timer)
    operation.reject(new Error('Pad bridge changed'))
  }
  pending.clear()
  channel = next
  next.onmessage = (event) => {
    try {
      const message = JSON.parse(event.data) as { request_id: string; result: string }
      const operation = pending.get(message.request_id)
      if (!operation) return
      pending.delete(message.request_id)
      clearTimeout(operation.timer)
      operation.resolve(message.result)
    } catch { /* Invalid replies time out; never substitute a successful result. */ }
  }
  const call = (method: string, json = '{}') => new Promise<string>((resolve, reject) => {
    const requestId = String(++sequence)
    const timer = setTimeout(() => {
      pending.delete(requestId)
      reject(new Error('Pad response timed out / Pad 响应超时'))
    }, 5000)
    pending.set(requestId, { resolve, reject, timer })
    try { next.postMessage(JSON.stringify({ request_id: requestId, method, payload: json })) }
    catch (error) { clearTimeout(timer); pending.delete(requestId); reject(error) }
  })
  bridge = {
    attestPrintRequest: (json) => call('attestPrintRequest', json),
    saveDeviceCredentials: (json) => call('saveDeviceCredentials', json),
    getDeviceStatus: () => call('getDeviceStatus'),
    clearDeviceCredentials: () => call('clearDeviceCredentials'),
    getPrintWorkerStatus: () => call('getPrintWorkerStatus'),
    kickPrintWorker: (json) => call('kickPrintWorker', json),
  }
  return bridge
}

export function parseAndroidBridgeJson<T>(raw: string | null | undefined): T | null {
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}
