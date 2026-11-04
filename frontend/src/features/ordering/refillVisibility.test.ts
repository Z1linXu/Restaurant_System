import { describe, expect, it } from 'vitest'
import type { MenuItem, OrderLineItem } from '../../types/ordering'
import { canShowRefill } from './refillVisibility'

const item = { refillCategoryCode: 'NOODLE' } as MenuItem
const main = { quantity: 1, unitPrice: 15, categoryCodeSnapshot: 'NOODLE' } as OrderLineItem

describe('refill display follows explicit menu metadata', () => {
  it('shows ordinary items without inventing refill rules from their names', () => {
    expect(canShowRefill({ nameEn: 'Noodle refill' } as MenuItem, 'pickup', [])).toBe(true)
  })
  it('requires dine-in with a positive-priced main of the same actual category', () => {
    expect(canShowRefill(item, 'dine_in', [main])).toBe(true)
    expect(canShowRefill(item, 'pickup', [main])).toBe(false)
    expect(canShowRefill(item, 'dine_in', [])).toBe(false)
    expect(canShowRefill(item, 'dine_in', [{ ...main, categoryCodeSnapshot: 'RICE' }])).toBe(false)
    expect(canShowRefill(item, 'dine_in', [{ ...main, quantity: 0 }])).toBe(false)
    expect(canShowRefill(item, 'dine_in', [{ ...main, unitPrice: 0 }])).toBe(false)
  })
  it('uses submitted snapshot categories and does not require a mutable current catalog lookup', () => {
    expect(canShowRefill({ refillCategoryCode: 'RICE' } as MenuItem, 'dine_in', [{ ...main, locked: true, categoryCodeSnapshot: 'RICE' }])).toBe(true)
    expect(canShowRefill(item, 'dine_in', [{ ...main, categoryCodeSnapshot: null }])).toBe(false)
  })
})
