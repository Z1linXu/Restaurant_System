export const TAX_RATE = 0.14975
export const TAX_RATE_LABEL = '14.975%'

export function calculateTax(subtotal: number) {
  return Math.round(subtotal * TAX_RATE * 100) / 100
}

export function calculateTotal(subtotal: number) {
  return Math.round((subtotal + calculateTax(subtotal)) * 100) / 100
}
