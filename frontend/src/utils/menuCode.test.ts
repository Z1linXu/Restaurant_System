import { describe, expect, it } from 'vitest'
import { menuCodeFromSku, withMenuCode } from './menuCode'

describe('approved menu code display', () => {
  it('covers the 32 stable printed identities without relying on names or positions', () => {
    for (const [letter, count] of Object.entries({ A: 9, B: 8, C: 6, D: 6, E: 3 })) {
      for (let number = 1; number <= count; number++) expect(menuCodeFromSku(`AN_${letter}${number}`)).toBe(`${letter}${number}`)
    }
    expect(withMenuCode('AN_C6', 'Duo de grillades')).toBe('C6 Duo de grillades')
    expect(withMenuCode('AN_C1', '炸鸡排饭')).toBe('C1 炸鸡排饭')
  })
  it('preserves refills, unknown identities and other restaurant SKUs', () => {
    for (const sku of ['AN_REFILL_NOODLES', 'AN_REFILL_RICE', 'B1', 'traditional_beef_noodle', 'AN_C7', 'AN_A01', 'AN_E4', null]) {
      expect(menuCodeFromSku(sku)).toBeNull()
      expect(withMenuCode(sku, 'Original name')).toBe('Original name')
    }
  })
  it('uses the snapshot identity after renaming and avoids repeated prefixes in all languages', () => {
    expect(withMenuCode('AN_A1', 'New name')).toBe('A1 New name')
    for (const name of ['A1 A1 虾云吞汤', 'A1 - Shrimp soup', 'A1: Soupe wonton']) {
      expect(withMenuCode('AN_A1', name).match(/A1/g)).toHaveLength(1)
    }
  })
})
