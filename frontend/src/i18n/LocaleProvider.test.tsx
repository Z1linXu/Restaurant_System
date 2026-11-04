import { useEffect, useState } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LocaleProvider } from './LocaleProvider'
import { useLocale } from './useLocale'
import { appI18n, chooseLocale } from './engine'
import { LOCALE_STORAGE_KEY } from './locale'

let view: ReactTestRenderer | undefined
beforeEach(async () => {
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('document', { documentElement: { lang: '' } })
  await appI18n.changeLanguage('en')
})
afterEach(() => { act(() => view?.unmount()); view = undefined; vi.unstubAllGlobals() })

describe('language switching preserves the mounted ordering tree', () => {
  it('keeps draft selection, quantity and a frozen submission identity through all three languages', async () => {
    let mounts = 0
    const frozenPayload = Object.freeze({ client_order_id: 'synthetic-fixed-id', idempotency_key: 'synthetic-fixed-id', expected_subtotal_amount: 25, items: [{ quantity: 2, options: [{ option_id: 7, quantity: 1 }] }] })
    const payloadBefore = JSON.stringify(frozenPayload)
    function OrderingHarness() {
      const { t, setLocale } = useLocale()
      const [quantity, setQuantity] = useState(2)
      const [selection] = useState('7')
      useEffect(() => { mounts++ }, [])
      return <><output data-quantity={quantity} data-selection={selection}>{t('order.submit')}</output>
        <button onClick={() => setQuantity(quantity + 1)}>+</button>
        <button onClick={() => setLocale('fr-CA')}>fr</button>
        <button onClick={() => setLocale('zh-CN')}>zh</button></>
    }
    await act(async () => { view = create(<LocaleProvider><OrderingHarness /></LocaleProvider>) })
    await act(async () => view!.root.findAllByType('button')[0].props.onClick())
    await act(async () => view!.root.findAllByType('button')[1].props.onClick())
    expect(view!.root.findByType('output').children).toEqual(['Envoyer la commande'])
    await act(async () => view!.root.findAllByType('button')[2].props.onClick())
    expect(view!.root.findByType('output').children).toEqual(['提交订单'])
    expect(view!.root.findByType('output').props['data-quantity']).toBe(3)
    expect(view!.root.findByType('output').props['data-selection']).toBe('7')
    expect(mounts).toBe(1)
    expect(JSON.stringify(frozenPayload)).toBe(payloadBefore)
  })

  it('uses only the locale preference key, and remains usable when browser storage is unavailable', async () => {
    const setItem = vi.fn()
    vi.stubGlobal('window', { localStorage: { setItem } })
    chooseLocale('fr-CA')
    expect(setItem).toHaveBeenCalledExactlyOnceWith(LOCALE_STORAGE_KEY, 'fr-CA')
    setItem.mockImplementation(() => { throw new Error('Storage unavailable') })
    expect(() => chooseLocale('zh-CN')).not.toThrow()
    expect(appI18n.resolvedLanguage).toBe('zh-CN')
  })
})
