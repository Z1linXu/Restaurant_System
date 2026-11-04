import { describe, expect, it } from 'vitest'
import { businessLabel, missingBusinessTranslation, searchableText, supportedLocale } from './locale'
import { messages } from './messages'

describe('restaurant display locales', () => {
  it('supports the three chosen locales without treating unknown input as a valid preference', () => {
    expect(supportedLocale('zh-Hans')).toBe('zh-CN')
    expect(supportedLocale('fr-CA')).toBe('fr-CA')
    expect(supportedLocale('en-US')).toBe('en')
    expect(supportedLocale('de')).toBeNull()
  })

  it('uses supplied business translations and falls back to the record itself without mutation', () => {
    const snapshot = { zh: '合成鸡肉', en: 'Synthetic chicken', fr: null }
    const frozen = JSON.stringify(snapshot)
    expect(businessLabel(snapshot, 'fr-CA')).toBe('Synthetic chicken')
    expect(JSON.stringify(snapshot)).toBe(frozen)
    expect(businessLabel({ ...snapshot, fr: 'Poulet synthétique' }, 'fr-CA')).toBe('Poulet synthétique')
    expect(businessLabel({ name: 'Noodles', translations: { 'fr-CA': 'Nouilles' } }, 'fr-CA')).toBe('Nouilles')
    expect(businessLabel({ en: '  ', zh: '合成商品' }, 'en')).toBe('合成商品')
    expect(missingBusinessTranslation(snapshot, 'fr-CA')).toBe(true)
    expect(missingBusinessTranslation(snapshot, 'en')).toBe(false)
  })

  it('matches accented French and Chinese without changing supplied menu names', () => {
    expect(searchableText('Poulet épicé', '合成鸡肉')).toContain(searchableText('EPICE'))
    expect(searchableText('Poulet épicé', '合成鸡肉')).toContain('合成鸡肉')
  })

  it('ships a nonempty value in every supported language for every application key', () => {
    for (const value of Object.values(messages)) {
      expect(value.en.trim()).not.toBe('')
      expect(value.zh.trim()).not.toBe('')
      expect(value.fr.trim()).not.toBe('')
    }
  })
})
