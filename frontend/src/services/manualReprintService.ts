import { ApiRequestError, apiRequest, getAccessToken, getApiUserMessage } from './apiClient'
import { translate } from '../i18n/engine'
import type { MessageKey } from '../i18n/messages'
import { getAndroidPadDeviceBridge } from '../types/androidPadBridge'
import type { PrintJobRecord } from './printingAdminService'
import { deploymentStorageKey } from '../utils/deploymentNamespace'

export interface ReprintConfirmation {
  confirmation_fingerprint: string
  active_jobs: Array<{ job_id: number; status: string; module: string; claimed_device_id?: number; preferred_device_id?: number; age_seconds: number }>
}
const inFlight = new Map<string, Promise<PrintJobRecord>>()
const storageKey = (path: string) => deploymentStorageKey(`restaurant-manual-reprint:${path}`)

function intentScope() {
  // Storage partition only, NOT authorization. Backend still verifies the JWT and Store access.
  const encoded = getAccessToken()?.split('.')[1]
  if (!encoded) throw new Error(translate('reprint.loginRequired'))
  const user = JSON.parse(atob(encoded.replace(/-/g, '+').replace(/_/g, '/'))) as { user_id?: number; organization_id?: number }
  if (!Number.isSafeInteger(user.user_id)) throw new Error(translate('reprint.accountUnknown'))
  return `${window.__RESTAURANT_API_BASE_URL__ ?? window.location.origin}|${user.organization_id ?? ''}|${user.user_id}`
}

export function printResultMessage(job: Pick<PrintJobRecord, 'id' | 'status'> & { error_code?: string | null }) {
  if (job.error_code === 'ANDROID_PRINT_UNCERTAIN' || job.error_code === 'ANDROID_RESTART_WITH_IN_FLIGHT_JOB'
    || job.error_code === 'ACTIVITY_DESTROYED_IN_FLIGHT')
    return `#${job.id} ${translate('reprint.uncertain')}`
  const key = ({ PENDING: 'reprint.PENDING', CLAIMED: 'reprint.CLAIMED', PRINTING: 'reprint.PRINTING',
    PRINTED: 'reprint.PRINTED', FAILED: 'reprint.FAILED', CANCELLED: 'reprint.CANCELLED' } as Record<string, MessageKey>)[job.status]
  return `#${job.id} ${translate(key ?? 'reprint.unknown')}`
}
export function reprintErrorMessage(error: unknown) {
  if (error instanceof ApiRequestError && (error.status === 0 || error.status >= 500))
    return translate('reprint.unknownRetry')
  return getApiUserMessage(error, translate('reprint.unknown'))
}

export function confirmActivePrintJobs(info: ReprintConfirmation): Promise<boolean> {
  return new Promise(resolve => {
    const dialog = document.createElement('dialog')
    dialog.style.cssText = 'max-width:520px;border:0;border-radius:16px;padding:24px;font-size:18px'
    const title = document.createElement('h3'); title.textContent = translate('reprint.confirm')
    const warning = document.createElement('p')
    warning.textContent = info.active_jobs.some(job => job.status === 'PRINTING')
      ? translate('reprint.printingWarning')
      : translate('reprint.activeWarning')
    const jobs = document.createElement('pre'); jobs.style.whiteSpace = 'pre-wrap'
    jobs.textContent = info.active_jobs.map(job => `#${job.job_id} · ${job.module} · ${job.status}\nPad ${job.claimed_device_id ?? job.preferred_device_id ?? translate('reprint.awaitClaim')} · ${job.age_seconds}s`).join('\n')
    const finish = (confirmed: boolean) => { dialog.close(); dialog.remove(); resolve(confirmed) }
    const cancel = document.createElement('button'); cancel.textContent = translate('common.cancel'); cancel.onclick = () => finish(false)
    const confirm = document.createElement('button'); confirm.textContent = translate('reprint.confirm'); confirm.onclick = () => finish(true)
    for (const button of [cancel, confirm]) button.style.cssText = 'padding:14px;margin:8px;font-size:18px'
    dialog.oncancel = event => { event.preventDefault(); finish(false) }
    dialog.append(title, warning, jobs, cancel, confirm); document.body.append(dialog); dialog.showModal()
  })
}

export function manualReprint(path: string, body: Record<string, unknown> = {}, confirm = confirmActivePrintJobs): Promise<PrintJobRecord> {
  const identity = `${intentScope()}|${path}|${JSON.stringify(body)}`
  const existing = inFlight.get(identity)
  if (existing) return existing
  const key = localStorage.getItem(storageKey(identity)) ?? crypto.randomUUID()
  // Survives renderer/WebView recreation. Contains only intent ID, never a credential.
  localStorage.setItem(storageKey(identity), key)
  const forget = () => localStorage.removeItem(storageKey(identity))
  const operation = (async () => {
    let confirmation: string | undefined
    for (;;) {
      try {
        const job = await apiRequest<PrintJobRecord>(path, { method: 'POST', body: JSON.stringify({
          ...body, idempotency_key: key, confirmation_fingerprint: confirmation,
        }) })
        forget()
        try { getAndroidPadDeviceBridge()?.kickPrintWorker?.(JSON.stringify({ reason: 'manual-reprint', order_id: job.order_id, job_id: job.id })) } catch { /* polling remains the reliability path */ }
        return job
      } catch (error) {
        if (error instanceof ApiRequestError && error.code === 'REPRINT_CONFIRMATION_REQUIRED') {
          const info = (error.raw as { data: ReprintConfirmation }).data
          if (!await confirm(info)) { forget(); throw new Error(translate('reprint.cancelled')) }
          confirmation = info.confirmation_fingerprint
          continue // Backend rechecks, and may request a fresh confirmation if ownership changed.
        }
        // Keep the same key on unknown outcomes, including reload; never automatically resubmit.
        // Even a later 401/403 cannot prove an earlier timed-out request did not print.
        throw error
      }
    }
  })().finally(() => inFlight.delete(identity))
  inFlight.set(identity, operation)
  return operation
}

/** Follow exactly the returned NEW job, never infer success from another module/job. */
export function followReprint(job: PrintJobRecord, update: (job: PrintJobRecord) => void) {
  let stopped = false
  let timer: ReturnType<typeof setTimeout> | undefined
  let polls = 0
  const poll = async () => {
    if (stopped || ++polls > 30) return
    try {
      const path = job.order_id ? `/api/v1/orders/${job.order_id}/print-jobs` : `/api/v1/admin/printing/jobs?store_id=${job.store_id}`
      const jobs = await apiRequest<PrintJobRecord[]>(path)
      const current = jobs.find(candidate => candidate.id === job.id)
      if (!stopped && current) {
        update(current)
        if (['PRINTED', 'FAILED', 'CANCELLED'].includes(current.status)) return
      }
    } catch { /* Refresh failure is not physical print failure. Keep last confirmed status. */ }
    if (!stopped) timer = setTimeout(poll, 2000)
  }
  update(job)
  if (!['PRINTED', 'FAILED', 'CANCELLED'].includes(job.status)) timer = setTimeout(poll, 1000)
  return () => { stopped = true; if (timer) clearTimeout(timer) }
}
