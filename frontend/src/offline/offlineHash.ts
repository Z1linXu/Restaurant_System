export function stableJson(value: unknown): string {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value)
  }
  if (Array.isArray(value)) {
    return `[${value.map(stableJson).join(',')}]`
  }
  const entries = Object.entries(value as Record<string, unknown>)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, nested]) => `${JSON.stringify(key)}:${stableJson(nested)}`)
  return `{${entries.join(',')}}`
}

export function fnv1a32(value: string) {
  let hash = 0x811c9dc5
  const bytes = new TextEncoder().encode(value)
  bytes.forEach((byte) => {
    hash = Math.imul(hash ^ byte, 0x01000193) >>> 0
  })
  return hash.toString(16).padStart(8, '0')
}

export function stablePayloadHash(value: unknown) {
  return `fnv1a32:${fnv1a32(stableJson(value))}`
}

