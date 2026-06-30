export function formatSplitSlotLabel(value: string | null | undefined) {
  if (!value) {
    return value ?? ''
  }

  return value
    .replace(/-A\b/g, '-左')
    .replace(/-B\b/g, '-右')
}
