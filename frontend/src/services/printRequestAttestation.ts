import { getAndroidPadDeviceBridge } from '../types/androidPadBridge'

export async function attestPrintRequest(path: string, init: RequestInit, headers: Record<string, string>) {
  const method = (init.method ?? 'GET').toUpperCase()
  const target = /^\/api\/v1\/stores\/\d+\/orders\/idempotent-submit$/.test(path)
    || /^\/api\/v1\/orders\/\d+\/(submit|updates|reprint)$/.test(path)
    || /^\/api\/v1\/admin\/printing\/jobs\/\d+\/reprint$/.test(path)
  if (method !== 'POST' || !target) return headers
  const bridge = getAndroidPadDeviceBridge()
  if (!bridge) {
    if (window.RestaurantPadDevice || window.__RESTAURANT_APP_CONFIG__?.mode === 'BUNDLED_ASSETS') {
      throw new Error('请更新 Pad App 以验证打印来源 / Pad app update required')
    }
    return headers
  }
  if (!bridge.attestPrintRequest) throw new Error('请更新 Pad App 以验证打印来源 / Pad app update required')
  if (init.body != null && typeof init.body !== 'string') throw new Error('Unsupported print request body')
  const proof = JSON.parse(await bridge.attestPrintRequest(JSON.stringify({
    method, path, body: init.body ?? '', authorization: headers.Authorization ?? '',
  }))) as { success: boolean; device_id: string; timestamp: string; signature: string }
  if (!proof?.success || typeof proof.device_id !== 'string' || !proof.device_id
    || typeof proof.timestamp !== 'string' || !/^\d+$/.test(proof.timestamp)
    || typeof proof.signature !== 'string' || !proof.signature) throw new Error('Pad 请求验证失败，请检查配对状态')
  return { ...headers, 'X-Print-Device-Id': proof.device_id, 'X-Print-Timestamp': proof.timestamp, 'X-Print-Signature': proof.signature }
}
