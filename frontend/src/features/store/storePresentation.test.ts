import { describe, expect, it } from 'vitest'
import type { StoreSafeSummary } from '../../types/storeConfiguration'
import type { StoreContextResponse } from '../../services/storeWorkspaceService'
import { mapStoreContext } from './StoreContextCore'
import { currentStoreDisplayName, formatStoreMoney, NEUTRAL_STORE_PRESENTATION, resolveStorePresentation } from './storePresentation'

const summary: StoreSafeSummary = {
  schema_version: 1, config_revision: 7, brand_name: 'Current Brand', locale: 'fr-CA', currency: 'CAD',
  timezone: 'America/Toronto', tax_policy_reference: 'store-tax', profile_code: 'NEUTRAL', compatibility_mode: 'NEUTRAL',
}

describe('Store presentation with bounded CAD support', () => {
  it('uses only supported presentation fields and the current Store name', () => {
    const presentation = resolveStorePresentation(summary)
    expect(presentation.brandName).toBe('Current Brand')
    expect(formatStoreMoney(12.3, presentation)).toBe(new Intl.NumberFormat('fr-CA', { style: 'currency', currency: 'CAD' }).format(12.3))
    expect(currentStoreDisplayName('Selected Store', 'User home Store')).toBe('Selected Store')
    expect(currentStoreDisplayName(null, 'User home Store')).toBe('User home Store')
    expect(Object.keys(presentation).sort()).toEqual(['brandName', 'currency', 'locale', 'timezone'])
  })

  it.each([
    null, undefined, { ...summary, schema_version: 2 }, { ...summary, currency: 'USD' },
    { ...summary, config_revision: -1 }, { ...summary, compatibility_mode: 'FUTURE' },
    { ...summary, locale: 'bad_locale' }, { ...summary, locale: 'zz-ZZ' },
    { ...summary, timezone: 'Not/AZone' }, { ...summary, brand_name: ' ' },
  ])('falls back completely to the neutral presentation for unsupported summary %#', input => {
    expect(resolveStorePresentation(input as StoreSafeSummary)).toEqual(NEUTRAL_STORE_PRESENTATION)
    expect(formatStoreMoney(12.3, resolveStorePresentation(input as StoreSafeSummary))).toBe('$12.30')
  })

  it('does not lend another Store branding while a new Store is loading', () => {
    const old = { id: 1, name: 'Old Store', safe_summary: summary } as StoreContextResponse
    expect(mapStoreContext(1, old, false, null).presentation.brandName).toBe('Current Brand')
    const next = mapStoreContext(2, old, false, null)
    expect(next.loading).toBe(true)
    expect(next.storeName).toBe('门店 2')
    expect(next.presentation).toEqual(NEUTRAL_STORE_PRESENTATION)
  })
})
