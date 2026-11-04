import i18next from 'i18next'
import { initReactI18next } from 'react-i18next'
import { messages, type MessageKey } from './messages'
import { LOCALE_STORAGE_KEY, supportedLocale, type AppLocale } from './locale'

function storedLocale(): AppLocale | null {
  try { return supportedLocale(window.localStorage.getItem(LOCALE_STORAGE_KEY)) } catch { return null }
}

let explicitLocale = storedLocale()
const browserLocale = typeof navigator === 'undefined' ? null : supportedLocale(navigator.language)
const resources = Object.fromEntries((['en', 'zh-CN', 'fr-CA'] as const).map((locale) => [locale, {
  translation: Object.fromEntries(Object.entries(messages).map(([key, values]) => [key, values[locale === 'zh-CN' ? 'zh' : locale === 'fr-CA' ? 'fr' : 'en']])),
}]))

/** One stable, eagerly loaded instance. Changing language never remounts ordering. */
export const appI18n = i18next.createInstance()
void appI18n.use(initReactI18next).init({
  resources,
  lng: explicitLocale ?? browserLocale ?? 'en',
  fallbackLng: 'en',
  supportedLngs: ['zh-CN', 'en', 'fr-CA'],
  keySeparator: false,
  initAsync: false,
  interpolation: { escapeValue: false },
  react: { useSuspense: false },
})

export function chooseLocale(locale: AppLocale) {
  explicitLocale = locale
  try { window.localStorage.setItem(LOCALE_STORAGE_KEY, locale) } catch { /* The in-memory choice still works. */ }
  void appI18n.changeLanguage(locale)
}

export function translate(key: MessageKey, values?: Record<string, string | number>): string {
  return String(appI18n.t(key, values))
}

/** Only an authenticated Store context can supply a default; explicit device choice wins. */
export function applyStoreLocaleDefault(value?: string | null) {
  const locale = supportedLocale(value)
  if (!explicitLocale && locale) void appI18n.changeLanguage(locale)
}
