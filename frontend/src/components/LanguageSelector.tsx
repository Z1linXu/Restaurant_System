import { useLocale } from '../i18n/useLocale'
import { SUPPORTED_LOCALES, type AppLocale } from '../i18n/locale'

const names: Record<AppLocale, string> = { 'zh-CN': '简体中文', en: 'English', 'fr-CA': 'Français' }

export function LanguageSelector() {
  const { locale, setLocale, t } = useLocale()
  return <label className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-white/80 px-3 text-sm font-semibold text-[var(--on-surface)]">
    <span className="sr-only">{t('common.language')}</span>
    <select aria-label={t('common.language')} className="min-h-11 max-w-32 bg-transparent" value={locale} onChange={(event) => setLocale(event.target.value as AppLocale)}>
      {SUPPORTED_LOCALES.map((value) => <option key={value} value={value}>{names[value]}</option>)}
    </select>
  </label>
}
