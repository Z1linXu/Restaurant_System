import { useLocale } from '../../../i18n/useLocale'
import { translate } from '../../../i18n/engine'
import { useEffect, useMemo, useState } from 'react'
import {
  getAndroidPadDeviceBridge,
  parseAndroidBridgeJson,
  type AndroidPadPrintWorkerStatus,
} from '../../../types/androidPadBridge'

function secondsSince(timestampMs: number | null | undefined) {
  if (!timestampMs || timestampMs <= 0) {
    return null
  }
  return Math.max(0, Math.round((Date.now() - timestampMs) / 1000))
}

function parseWorkerStatus(raw: string | null | undefined) {
  return parseAndroidBridgeJson<AndroidPadPrintWorkerStatus>(raw)
}

function statusTone(status: AndroidPadPrintWorkerStatus | null, bridgeAvailable: boolean) {
  if (!bridgeAvailable) {
    return 'neutral'
  }
  if (!status?.auto_enabled) {
    return 'warning'
  }
  if (status?.error_stopped || status?.worker_state === 'ERROR_STOPPED') {
    return 'danger'
  }
  if (status?.recovering || status?.worker_state === 'RECOVERING') {
    return 'warning'
  }
  if (status?.worker_running) {
    return 'success'
  }
  return 'danger'
}

function toneClass(tone: string) {
  if (tone === 'success') {
    return 'border-[rgba(33,114,75,0.22)] bg-[rgba(33,114,75,0.1)] text-[rgb(24,92,58)]'
  }
  if (tone === 'danger') {
    return 'border-[rgba(151,34,34,0.28)] bg-[rgba(151,34,34,0.12)] text-[rgb(116,22,22)]'
  }
  if (tone === 'warning') {
    return 'border-[rgba(180,120,20,0.28)] bg-[rgba(180,120,20,0.14)] text-[rgb(120,78,14)]'
  }
  return 'border-[rgba(26,28,25,0.08)] bg-[rgba(255,255,255,0.58)] text-[var(--muted)]'
}

function statusTitle(status: AndroidPadPrintWorkerStatus | null, bridgeAvailable: boolean) {
  if (!bridgeAvailable) {
    return translate('printing.worker.browser')
  }
  if (!status?.auto_enabled) {
    return translate('printing.worker.off')
  }
  if (status?.error_stopped || status?.worker_state === 'ERROR_STOPPED') {
    return `${translate('printing.worker.stopped')}${status.last_stop_reason ? `：${status.last_stop_reason}` : ''}`
  }
  if (status?.recovering || status?.worker_state === 'RECOVERING') {
    const delay = status.recovery_delay_ms && status.recovery_delay_ms > 0 ? `，${Math.round(status.recovery_delay_ms / 1000)} 秒后重试` : ''
    return `${translate('printing.worker.recovering')}${delay}`
  }
  if (status?.worker_running) {
    const age = secondsSince(status.last_poll_at_ms)
    return `${translate('printing.worker.running')}${age == null ? '' : `，最后检查 ${age} 秒前`}`
  }
  return translate('printing.worker.idle')
}

export function PrintWorkerHealthBanner() {
  const { t, locale } = useLocale()
  const [bridgeAvailable, setBridgeAvailable] = useState(false)
  const [status, setStatus] = useState<AndroidPadPrintWorkerStatus | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)

  const refreshStatus = () => {
    const bridge = getAndroidPadDeviceBridge()
    setBridgeAvailable(Boolean(bridge?.getPrintWorkerStatus))
    if (!bridge?.getPrintWorkerStatus) {
      setStatus(null)
      return
    }
    setStatus(parseWorkerStatus(bridge.getPrintWorkerStatus()))
  }

  useEffect(() => {
    refreshStatus()
    const intervalId = window.setInterval(refreshStatus, 5000)
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        refreshStatus()
      }
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      window.clearInterval(intervalId)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [])

  const tone = useMemo(() => statusTone(status, bridgeAvailable), [bridgeAvailable, status])
  const title = useMemo(() => statusTitle(status, bridgeAvailable), [bridgeAvailable, status, locale])

  const handleKick = () => {
    const bridge = getAndroidPadDeviceBridge()
    if (!bridge?.kickPrintWorker) {
      setActionMessage(translate('printing.worker.notNative'))
      return
    }
    if (status && status.auto_enabled === false) {
      setActionMessage(translate('printing.worker.enableHint'))
      return
    }
    const recoverErrorStopped = Boolean(status?.error_stopped || status?.worker_state === 'ERROR_STOPPED')
    if (recoverErrorStopped && !window.confirm(translate('printing.worker.confirmRecovery'))) {
      return
    }
    try {
      const result = parseAndroidBridgeJson<{ success?: boolean; data?: { status?: string; message?: string }; message?: string }>(
        bridge.kickPrintWorker(JSON.stringify({
          reason: 'frontdesk-print-health',
          recover_error_stopped: recoverErrorStopped,
        })),
      )
      setActionMessage(result?.data?.message ?? result?.message ?? translate('printing.worker.requested'))
      window.setTimeout(refreshStatus, 300)
    } catch (error) {
      setActionMessage(error instanceof Error ? error.message : translate('printing.worker.failed'))
    }
  }

  return (
    <div className={`rounded-[20px] border px-4 py-3 shadow-[0_12px_28px_rgba(26,28,25,0.06)] ${toneClass(tone)}`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[0.98rem] font-black">{title}</div>
          <div className="mt-1 text-[0.78rem] font-semibold opacity-80">
            {bridgeAvailable
              ? `状态：${status?.worker_state_label ?? status?.worker_state ?? '未知'} · Device ${status?.device_id ?? '-'} · Store ${status?.store_id ?? '-'}`
              : translate('printing.worker.browserHint')}
          </div>
          {status?.last_error ? (
            <div className="mt-1 text-[0.78rem] font-semibold opacity-90">{t('printing.worker.lastError')}: {status.last_error}</div>
          ) : null}
          {actionMessage ? (
            <div className="mt-1 text-[0.78rem] font-semibold opacity-90">{actionMessage}</div>
          ) : null}
        </div>
        <button
          type="button"
          className="min-h-11 rounded-[16px] bg-[var(--primary)] px-4 text-[0.92rem] font-black text-white shadow-[0_12px_24px_rgba(97,0,0,0.18)] disabled:bg-stone-200 disabled:text-stone-500"
          onClick={handleKick}
        >
          {t('printing.worker.check')}
        </button>
      </div>
    </div>
  )
}
