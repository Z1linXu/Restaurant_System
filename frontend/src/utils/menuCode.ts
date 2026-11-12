/** Codes from the approved Allô printed menu; unrelated SKUs remain unchanged. */
export function menuCodeFromSku(sku?: string | null): string | null {
  const match = /^AN_([A-E])(\d+)$/.exec(sku?.trim() ?? '')
  if (!match) return null
  const maximum: Record<string, number> = { A: 9, B: 8, C: 6, D: 6, E: 3 }
  const number = Number(match[2])
  return number >= 1 && number <= maximum[match[1]] && String(number) === match[2]
    ? `${match[1]}${number}` : null
}

export function withMenuCode(sku: string | null | undefined, name: string): string {
  const code = menuCodeFromSku(sku)
  if (!code) return name
  // Names may already have been prefixed by an earlier form; never duplicate it.
  const unprefixed = name.trim().replace(new RegExp(`^(?:${code}(?:\\s*[-.:]\\s*|\\s+))+`, 'i'), '')
  return unprefixed ? `${code} ${unprefixed}` : code
}
