import type { StoreSafeSummary } from '../../types/storeConfiguration'

export interface StorePresentation {
  brandName: string
  locale: string
  currency: 'CAD'
  timezone: string | undefined
}

export const NEUTRAL_STORE_PRESENTATION: StorePresentation = {
  brandName: 'Restaurant System',
  locale: 'en-CA',
  currency: 'CAD',
  timezone: undefined,
}

// Presentation is deliberately limited to the currently supported schema/CAD.
// An unsupported summary must never lend its branding to the legacy fallback.
export function resolveStorePresentation(summary: StoreSafeSummary | null | undefined): StorePresentation {
  if (!summary || summary.schema_version !== 1 || summary.currency !== 'CAD'
    || !Number.isSafeInteger(summary.config_revision) || summary.config_revision < 0
    || !['NEUTRAL', 'PROVISIONAL_COMPATIBILITY'].includes(summary.compatibility_mode)
    || typeof summary.brand_name !== 'string' || !summary.brand_name.trim()
    || typeof summary.locale !== 'string' || typeof summary.timezone !== 'string') {
    return NEUTRAL_STORE_PRESENTATION
  }
  try {
    if (!Intl.NumberFormat.supportedLocalesOf([summary.locale]).length) return NEUTRAL_STORE_PRESENTATION
    new Intl.DateTimeFormat(summary.locale, { timeZone: summary.timezone }).format(0)
    return { brandName: summary.brand_name.trim(), locale: summary.locale, currency: 'CAD', timezone: summary.timezone }
  } catch {
    return NEUTRAL_STORE_PRESENTATION
  }
}

export function formatStoreMoney(value: number, presentation = NEUTRAL_STORE_PRESENTATION) {
  return new Intl.NumberFormat(presentation.locale, {
    style: 'currency', currency: 'CAD', minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).format(value)
}

export function currentStoreDisplayName(currentStoreName: string | null | undefined, userStoreName?: string | null) {
  return currentStoreName?.trim() || userStoreName?.trim() || 'Restaurant System'
}
