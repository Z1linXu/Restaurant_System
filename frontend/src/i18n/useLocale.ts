import { useTranslation } from 'react-i18next'
import { useCallback } from 'react'
import { chooseLocale } from './engine'
import { businessLabel, supportedLocale, type BusinessNames } from './locale'
import type { MessageKey } from './messages'

export function useLocale() {
  const { t, i18n } = useTranslation()
  const locale = supportedLocale(i18n.resolvedLanguage ?? i18n.language) ?? 'en'
  const localize = useCallback((key: MessageKey, values?: Record<string, string | number>) => String(t(key, values)), [t])
  const label = useCallback((value: BusinessNames) => businessLabel(value, locale, String(t('common.untitled'))), [locale, t])
  return {
    locale,
    setLocale: chooseLocale,
    t: localize,
    label,
  }
}
