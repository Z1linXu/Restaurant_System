import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiRequestError, apiRequest } from './apiClient'
import { followReprint, manualReprint, printResultMessage, reprintErrorMessage } from './manualReprintService'
import type { PrintJobRecord } from './printingAdminService'

vi.mock('./apiClient', async () => ({ ...await vi.importActual<typeof import('./apiClient')>('./apiClient'), apiRequest: vi.fn(),
  getAccessToken: () => `header.${btoa(JSON.stringify({ user_id: 1, organization_id: 1 }))}.signature` }))
const api = vi.mocked(apiRequest)
const storage = new Map<string, string>()
const kick = vi.fn()
const job = (status = 'PENDING', id = 101) => ({ id, status, store_id: 1, order_id: 9 } as PrintJobRecord)
beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: (k: string) => storage.get(k) ?? null, setItem: (k: string, v: string) => storage.set(k, v), removeItem: (k: string) => storage.delete(k) })
  vi.stubGlobal('window', { location: { origin: 'http://synthetic.invalid' }, RestaurantPadDevice: { kickPrintWorker: kick } })
  api.mockReset(); kick.mockReset()
})
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

describe('shared Dine-in / Orders / Print Center reprint contract', () => {
  it.each(['PENDING', 'CLAIMED', 'PRINTING'])('%s is neither failure nor physical completion', status => {
    expect(printResultMessage(job(status))).not.toMatch(/打印失败|打印完成/)
  })
  it('only PRINTED completes and timeout/uncertain output is not fake failure', () => {
    expect(printResultMessage(job('PRINTED'))).toContain('打印完成')
    expect(printResultMessage(job('FAILED'))).toContain('打印失败')
    expect(printResultMessage({ ...job('FAILED'), error_code: 'ANDROID_PRINT_UNCERTAIN' })).toContain('结果待确认')
    expect(reprintErrorMessage(new ApiRequestError(0, 'timeout'))).toContain('结果待确认')
  })
  it('coalesces double click, keeps identity after lost response and kicks only returned new job', async () => {
    const path = '/api/v1/orders/9/reprint'
    api.mockRejectedValueOnce(new ApiRequestError(0, 'timeout'))
    const first = manualReprint(path, { receipt_type: 'GRAB' })
    expect(manualReprint(path, { receipt_type: 'GRAB' })).toBe(first)
    await expect(first).rejects.toThrow('timeout')
    const firstKey = JSON.parse(api.mock.calls[0][1]!.body as string).idempotency_key
    expect(kick).not.toHaveBeenCalled()
    api.mockResolvedValueOnce(job())
    expect((await manualReprint(path, { receipt_type: 'GRAB' })).id).toBe(101)
    expect(JSON.parse(api.mock.calls[1][1]!.body as string).idempotency_key).toBe(firstKey)
    expect(kick).toHaveBeenCalledTimes(1)
    expect(kick.mock.calls[0][0]).toContain('101')
    api.mockResolvedValueOnce(job('PENDING', 102))
    await manualReprint(path, { receipt_type: 'GRAB' })
    expect(JSON.parse(api.mock.calls[2][1]!.body as string).idempotency_key).not.toBe(firstKey)
  })
  it('requires explicit active-job confirmation and keeps request identity through recheck', async () => {
    const info = { confirmation_fingerprint: 'current', active_jobs: [{ job_id: 3, status: 'PRINTING', module: 'GRAB', age_seconds: 40 }] }
    api.mockRejectedValueOnce(new ApiRequestError(409, 'confirm', undefined, { data: info }, 'REPRINT_CONFIRMATION_REQUIRED'))
      .mockResolvedValueOnce(job())
    const confirm = vi.fn().mockResolvedValue(true)
    await manualReprint('/api/v1/admin/printing/jobs/3/reprint', {}, confirm)
    expect(confirm).toHaveBeenCalledWith(info)
    const first = JSON.parse(api.mock.calls[0][1]!.body as string)
    const second = JSON.parse(api.mock.calls[1][1]!.body as string)
    expect(second.idempotency_key).toBe(first.idempotency_key)
    expect(second.confirmation_fingerprint).toBe('current')
  })
  it('unknown result survives a later 401 and JS module/WebView recreation', async () => {
    const path = '/api/v1/orders/77/reprint'
    api.mockRejectedValueOnce(new ApiRequestError(0, 'timeout')).mockRejectedValueOnce(new ApiRequestError(401, 'login'))
    await expect(manualReprint(path)).rejects.toThrow('timeout')
    const key = JSON.parse(api.mock.calls[0][1]!.body as string).idempotency_key
    await expect(manualReprint(path)).rejects.toThrow('login')
    vi.resetModules()
    const restarted = await import('./manualReprintService')
    const restartedApi = await import('./apiClient')
    vi.mocked(restartedApi.apiRequest).mockResolvedValueOnce(job('PRINTED', 177))
    await restarted.manualReprint(path)
    const calls = vi.mocked(restartedApi.apiRequest).mock.calls
    expect(JSON.parse(calls[calls.length - 1][1]!.body as string).idempotency_key).toBe(key)
  })
  it('cancellation does not create a second request', async () => {
    api.mockRejectedValueOnce(new ApiRequestError(409, 'confirm', undefined, { data: {} }, 'REPRINT_CONFIRMATION_REQUIRED'))
    await expect(manualReprint('/api/v1/admin/printing/jobs/4/reprint', {}, async () => false)).rejects.toThrow('已取消')
    expect(api).toHaveBeenCalledTimes(1); expect(kick).not.toHaveBeenCalled()
  })
  it('follows returned job ID, not another completed module; cleanup stops refresh', async () => {
    vi.useFakeTimers()
    api.mockResolvedValueOnce([job('PRINTED', 99), job('CLAIMED')]).mockResolvedValueOnce([job('PRINTED')])
    const update = vi.fn(); const stop = followReprint(job(), update)
    await vi.advanceTimersByTimeAsync(1000)
    expect(update.mock.lastCall?.[0].status).toBe('CLAIMED')
    await vi.advanceTimersByTimeAsync(2000)
    expect(update.mock.lastCall?.[0].status).toBe('PRINTED')
    stop(); await vi.advanceTimersByTimeAsync(10000); expect(api).toHaveBeenCalledTimes(2)
  })
})
