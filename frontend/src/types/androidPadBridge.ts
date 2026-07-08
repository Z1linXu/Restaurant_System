export interface AndroidPadDeviceStatus {
  paired?: boolean
  device_id?: string | number | null
  store_id?: string | number | null
  device_name?: string | null
  registered_at?: string | null
  token_last4?: string | null
  app_version?: string | null
  platform?: string | null
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
  saveDeviceCredentials: (json: string) => string
  getDeviceStatus: () => string
  clearDeviceCredentials: () => string
  getPrintWorkerStatus?: () => string
  kickPrintWorker?: (json: string) => string
}

declare global {
  interface Window {
    RestaurantPadDevice?: AndroidPadDeviceBridge
  }
}

export function getAndroidPadDeviceBridge() {
  return typeof window === 'undefined' ? undefined : window.RestaurantPadDevice
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
