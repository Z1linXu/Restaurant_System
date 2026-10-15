import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, clearAuthTokens, getAccessToken, getRefreshToken, saveAuthTokens } from './apiClient'
import { activateOfflineAccount, invalidateOfflineSession } from '../offline/environmentScope'
import { logout } from './authService'
import { getAndroidPadDeviceBridge } from '../types/androidPadBridge'
import { attestPrintRequest } from './printRequestAttestation'

const storage = new Map<string, string>()
const fetchMock = vi.fn()
const response = (data: unknown, status = 200) => new Response(JSON.stringify({ success: status === 200, data }), { status })
beforeEach(() => {
  storage.clear(); vi.useRealTimers(); fetchMock.mockReset()
  const localStorage = { getItem: (key: string) => storage.get(key) ?? null, setItem: (key: string, value: string) => storage.set(key, value), removeItem: (key: string) => storage.delete(key) }
  vi.stubGlobal('window', Object.assign(new EventTarget(), { location: { origin: 'https://restaurant-pad.local' }, __RESTAURANT_API_BASE_URL__: 'https://a.synthetic.invalid', localStorage, setTimeout, clearTimeout }))
  vi.stubGlobal('fetch', fetchMock); vi.stubGlobal('navigator', { onLine: true })
  invalidateOfflineSession(); activateOfflineAccount(7)
})
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

describe('API credential environment and session lifecycle', () => {
  it('never adopts legacy tokens and keeps origin credentials apart, including refresh/logout', async () => {
    storage.set('restaurant_pos_access_token', 'legacy-unbound'); storage.set('restaurant_pos_refresh_token', 'legacy-refresh')
    expect(getAccessToken()).toBeNull(); expect(getRefreshToken()).toBeNull()
    saveAuthTokens('access-A', 'refresh-A')
    window.__RESTAURANT_API_BASE_URL__ = 'https://b.synthetic.invalid'
    expect(getAccessToken()).toBeNull(); expect(getRefreshToken()).toBeNull()
    fetchMock.mockResolvedValue(response({ ok: true })); await apiRequest('/api/v1/example')
    expect(fetchMock.mock.calls[0][0]).toBe('https://b.synthetic.invalid/api/v1/example')
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
    expect(fetchMock.mock.calls[0][1].redirect).toBe('error')
    window.__RESTAURANT_API_BASE_URL__ = 'https://a.synthetic.invalid'
    expect(getAccessToken()).toBe('access-A'); clearAuthTokens(); expect(getAccessToken()).toBeNull()
    expect(storage.get('restaurant_pos_access_token')).toBe('legacy-unbound')
  })
  it('clears identity synchronously before a delayed logout revocation completes', async () => {
    saveAuthTokens('access-A', 'refresh-A'); const expired = vi.fn(); window.addEventListener('restaurant-auth-expired', expired)
    let finish!: (value: Response) => void; fetchMock.mockReturnValue(new Promise((resolve) => { finish = resolve }))
    const operation = logout(); expect(getAccessToken()).toBeNull(); expect(getRefreshToken()).toBeNull(); expect(expired).toHaveBeenCalled()
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1)); finish(response(null)); await operation
  })
  it('does not send a captured logout token to a target changed by a logout listener', async () => {
    saveAuthTokens('access-A', 'refresh-A')
    window.addEventListener('restaurant-auth-expired', () => { window.__RESTAURANT_API_BASE_URL__ = 'https://b.synthetic.invalid' })
    await logout(); expect(fetchMock).not.toHaveBeenCalled()
  })
  it('does not save a delayed refresh into another environment or resurrect tokens after logout', async () => {
    saveAuthTokens('expired-A', 'refresh-A')
    let finish!: (value: Response) => void
    fetchMock.mockResolvedValueOnce(response(null, 401)).mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    const operation = apiRequest('/api/v1/example'); const rejected = expect(operation).rejects.toThrow('AUTH_ENVIRONMENT_CHANGED')
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(fetchMock.mock.calls[1][0]).toBe('https://a.synthetic.invalid/api/v1/auth/refresh')
    clearAuthTokens(); window.__RESTAURANT_API_BASE_URL__ = 'https://b.synthetic.invalid'; activateOfflineAccount(7)
    finish(response({ access_token: 'late-A', refresh_token: 'late-refresh-A' })); await rejected
    expect(getAccessToken()).toBeNull(); expect(fetchMock).toHaveBeenCalledTimes(2)
    window.__RESTAURANT_API_BASE_URL__ = 'https://a.synthetic.invalid'; expect(getAccessToken()).toBeNull()
  })
  it('refuses explicitly foreign API targets without transmitting a credential', async () => {
    saveAuthTokens('access-A', 'refresh-A')
    await expect(apiRequest('https://b.synthetic.invalid/api/v1/example')).rejects.toThrow('API_ENVIRONMENT_MISMATCH')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})

describe('trusted asynchronous native channel adapter', () => {
  it('routes a correlated reply, fails closed on timeout, and never calls legacy JavascriptInterface', async () => {
    const legacy = vi.fn(); window.RestaurantPadDevice = { attestPrintRequest: legacy }
    expect(getAndroidPadDeviceBridge()).toBeUndefined()
    await expect(attestPrintRequest('/api/v1/stores/3/orders/idempotent-submit', { method: 'POST', body: '{}' }, {})).rejects.toThrow('Pad app update required')
    expect(legacy).not.toHaveBeenCalled()
    const channel = { postMessage: vi.fn(), onmessage: undefined as ((event: { data: string }) => void) | undefined }; window.RestaurantPadMessages = channel
    const bridge = getAndroidPadDeviceBridge()!
    const operation = bridge.getDeviceStatus(); const message = JSON.parse(channel.postMessage.mock.calls[0][0])
    channel.onmessage!({ data: JSON.stringify({ request_id: message.request_id, result: '{"paired":false}' }) })
    expect(await operation).toBe('{"paired":false}')
    vi.useFakeTimers(); const timed = bridge.getDeviceStatus(); const rejected = expect(timed).rejects.toThrow('timed out')
    await vi.advanceTimersByTimeAsync(5001); await rejected
  })
  it.each(['{"success":false}', '{"success":true}', 'invalid-json'])('failed or malformed native proof %s never reaches fetch', async (result) => {
    window.RestaurantPadMessages = { postMessage: (raw) => {
      const request = JSON.parse(raw); queueMicrotask(() => window.RestaurantPadMessages!.onmessage!({ data: JSON.stringify({ request_id: request.request_id, result }) }))
    } }
    await expect(apiRequest('/api/v1/stores/3/orders/idempotent-submit', { method: 'POST', body: '{}' })).rejects.toThrow()
    expect(fetchMock).not.toHaveBeenCalled()
  })
  it('awaits a successful native proof and attaches only its correlated headers', async () => {
    saveAuthTokens('access-A', 'refresh-A')
    window.RestaurantPadMessages = { postMessage: (raw) => {
      const request = JSON.parse(raw)
      queueMicrotask(() => window.RestaurantPadMessages!.onmessage!({ data: JSON.stringify({ request_id: request.request_id,
        result: JSON.stringify({ success: true, device_id: '7', timestamp: '1792080000', signature: 'synthetic-proof' }) }) }))
    } }
    fetchMock.mockResolvedValue(response({ order_id: 42 }))
    await apiRequest('/api/v1/stores/3/orders/idempotent-submit', { method: 'POST', body: '{"idempotency_key":"stable"}' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][1].headers).toMatchObject({ Authorization: 'Bearer access-A', 'X-Print-Device-Id': '7', 'X-Print-Signature': 'synthetic-proof' })
    expect(fetchMock.mock.calls[0][1].body).toBe('{"idempotency_key":"stable"}')
  })

})
