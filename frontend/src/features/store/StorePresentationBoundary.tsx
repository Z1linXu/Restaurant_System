import { useEffect, type ReactNode } from 'react'
import { useCurrentStore } from './useStoreContext'
import { applyStoreLocaleDefault } from '../../i18n/engine'

/** Presentation settings are separate from ordering, hardware and runtime capability. */
export function StorePresentationBoundary({ children }: { children: ReactNode }) {
  const { presentation } = useCurrentStore()
  useEffect(() => { applyStoreLocaleDefault(presentation?.default_locale) }, [presentation?.default_locale])
  useEffect(() => {
    const color = presentation?.primary_color
    if (!color || !/^#[\da-f]{6}$/i.test(color)) return
    const root = document.documentElement
    const previous = root.style.getPropertyValue('--primary')
    const previousContainer = root.style.getPropertyValue('--primary-container')
    root.style.setProperty('--primary', color)
    root.style.setProperty('--primary-container', color)
    return () => {
      if (previous) root.style.setProperty('--primary', previous); else root.style.removeProperty('--primary')
      if (previousContainer) root.style.setProperty('--primary-container', previousContainer); else root.style.removeProperty('--primary-container')
    }
  }, [presentation?.primary_color])
  return children
}
