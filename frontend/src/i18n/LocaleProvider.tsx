import { useEffect, type ReactNode } from 'react'
import { I18nextProvider, useTranslation } from 'react-i18next'
import { appI18n } from './engine'

function LocaleDocument() {
  const { i18n } = useTranslation()
  useEffect(() => { document.documentElement.lang = i18n.resolvedLanguage ?? 'en' }, [i18n.resolvedLanguage])
  return null
}

export function LocaleProvider({ children }: { children: ReactNode }) {
  return <I18nextProvider i18n={appI18n}><LocaleDocument />{children}</I18nextProvider>
}
