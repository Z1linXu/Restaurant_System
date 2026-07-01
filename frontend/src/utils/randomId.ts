function randomHexFromCrypto(byteLength: number) {
  const cryptoApi = globalThis.crypto
  if (!cryptoApi?.getRandomValues) {
    return null
  }
  const bytes = new Uint8Array(byteLength)
  cryptoApi.getRandomValues(bytes)
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

export function createClientId(prefix = 'client') {
  const cryptoApi = globalThis.crypto
  if (cryptoApi?.randomUUID) {
    return `${prefix}-${cryptoApi.randomUUID()}`
  }

  const randomHex = randomHexFromCrypto(16)
  if (randomHex) {
    return `${prefix}-${randomHex.slice(0, 8)}-${randomHex.slice(8, 12)}-${randomHex.slice(12, 16)}-${randomHex.slice(16, 20)}-${randomHex.slice(20)}`
  }

  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

export function createIdempotencyKey(scope = 'request') {
  return createClientId(scope)
}
