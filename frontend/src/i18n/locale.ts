export const SUPPORTED_LOCALES = ['zh-CN', 'en', 'fr-CA'] as const
export type AppLocale = typeof SUPPORTED_LOCALES[number]
export const LOCALE_STORAGE_KEY = 'restaurant.ui.locale.v1'

export function supportedLocale(value?: string | null): AppLocale | null {
  const language = value?.trim().toLowerCase()
  if (language === 'zh' || language === 'zh-cn' || language === 'zh-hans') return 'zh-CN'
  if (language === 'fr' || language?.startsWith('fr-')) return 'fr-CA'
  if (language === 'en' || language?.startsWith('en-')) return 'en'
  return null
}

export interface BusinessNames {
  en?: string | null
  zh?: string | null
  fr?: string | null
  name?: string | null
  translations?: Partial<Record<AppLocale, string>> | null
}

export function missingBusinessTranslation(value: BusinessNames, locale: AppLocale): boolean {
  const requested = value.translations?.[locale] || (locale === 'zh-CN' ? value.zh : locale === 'fr-CA' ? value.fr : value.en)
  return !requested?.trim()
}

/** Display only. Never rewrites menu fields, selection IDs or order snapshots. */
export function businessLabel(value: BusinessNames, locale: AppLocale, fallback = ''): string {
  const names = { 'zh-CN': value.translations?.['zh-CN'] || value.zh, en: value.translations?.en || value.en, 'fr-CA': value.translations?.['fr-CA'] || value.fr }
  return [names[locale], names.en, names['zh-CN'], names['fr-CA'], value.name]
    .find((candidate): candidate is string => typeof candidate === 'string' && candidate.trim().length > 0)?.trim() || fallback
}

/** Accent-insensitive search, including confirmed translations, without altering them. */
export function searchableText(...values: (string | null | undefined)[]): string {
  return values.filter(Boolean).join(' ').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().trim()
}
