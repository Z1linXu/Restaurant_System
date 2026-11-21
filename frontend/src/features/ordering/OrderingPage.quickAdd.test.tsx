/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { act, create } from 'react-test-renderer'
import { expect, it, vi } from 'vitest'
import { mapCatalog } from '../../hooks/useMenuCatalog'
import { validateMenuCatalog } from '../../offline/menuCache'
import type { BackendMenuCatalog, ItemCustomizationDraft, MenuItem, OrderLineItem } from '../../types/ordering'
import { OrderingPage } from './OrderingPage'
import { CategoryNav } from './components/CategoryNav'
import { ItemCustomizationModal } from './components/ItemCustomizationModal'
import { MenuItemCard } from './components/MenuItemCard'
import { OrderSummaryPanel } from './components/OrderSummaryPanel'

const draftSpy = vi.hoisted(() => ({ add: vi.fn(), delay: Promise.resolve() }))
vi.mock('../../hooks/useIpadLandscape', () => ({ useIpadLandscape: () => false }))
vi.mock('../../hooks/useConnectionStatus', () => ({ useConnectionStatus: () => ({ state: 'ONLINE' }) }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 1 }, permissions: [] }) }))
vi.mock('../store/useStoreContext', () => ({ useCurrentStore: () => ({ organizationId: 2 }) }))
vi.mock('../../hooks/useDraftOrder', async (original) => {
  const module = await original<typeof import('../../hooks/useDraftOrder')>()
  const { useState } = await import('react')
  return { ...module, useDraftOrder: () => {
    const [items, setItems] = useState<OrderLineItem[]>([])
    return {
      session: { orderId: 'local', slotLabel: 'TEST', status: 'draft', items },
      localSubmitState: 'LOCAL_DRAFT',
      addItem: async (item: MenuItem, draft: ItemCustomizationDraft) => {
        draftSpy.add(item.id, draft.quantity)
        await draftSpy.delay
        setItems((previous) => [...previous, module.buildLocalLineItem(item, draft)])
      },
      removeItem: (id: string) => setItems((previous) => previous.filter((item) => item.id !== id)),
    }
  } }
})

const skuPrices = [
  ['zha_jiang', 3], ['dan_dan_sauce', 3], ['bok_choy', 3], ['radish', 3],
  ['corn', 3], ['free_soup', 0], ['green_onion', 0], ['cilantro', 0],
] as const

function baseItem(sku: string, price: number): MenuItem {
  return { id: sku, sku, categoryId: 'addon', nameEn: sku, nameZh: sku, descriptionEn: '', descriptionZh: '', price }
}

it('uses the same one-tap behavior on the card and + while staging exactly one line per intentional tap', async () => {
  const stageFile = process.env.CHINATOWN_STAGE_CATALOG
  const payload: BackendMenuCatalog | undefined = stageFile ? JSON.parse(readFileSync(stageFile, 'utf8')) : undefined
  if (payload) validateMenuCatalog(payload, { accountId: 1, organizationId: payload.organization_id, storeId: 18 }, 47)
  const choice = { id: '51', labelEn: 'Default', labelZh: '默认' }
  const menu = payload ? mapCatalog(payload) : null
  const categories = menu?.categories ?? [{ id: 'addon', labelEn: 'Add On', labelZh: '加选' }]
  const addonCategoryId = (menu?.categories.find((category) => category.code === 'CATEGORY_ADD_ON') ?? categories[0]).id
  const items = [...(menu?.items ?? [
    ...skuPrices.map(([sku, price]) => baseItem('add_on_' + sku, price)),
    { ...baseItem('add_on_beef_broth', 3), customization: { sizes: { required: true, options: [
      { ...choice, id: '52', optionCode: 'size_large', portionPrice: 3 }, { ...choice, id: '53', optionCode: 'size_small', portionPrice: 2, priceDelta: -1 },
    ] }, spicyLevels: [choice], removeOptions: [{ ...choice, id: '54', optionCode: 'chili_on_side' }] } },
    { ...baseItem('traditional_beef_noodle', 16), customization: { noodleTypes: [choice] } },
  ]),
  { ...baseItem('sold_out_fixture', 3), categoryId: addonCategoryId, soldOut: true },
  { ...baseItem('fixed_size_fixture', 3), categoryId: addonCategoryId, customization: {
    sizes: { required: true, options: [{ ...choice, optionType: 'size', priceDelta: 2 }] },
  } }]
  vi.useFakeTimers()
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn(), setTimeout })
  vi.stubGlobal('document', { addEventListener: vi.fn(), removeEventListener: vi.fn(), body: { style: {} } })
  let view: ReturnType<typeof create> | undefined
  try {
    await act(async () => { view = create(<OrderingPage storeId={18} orderType="pickup" slotLabel="TEST" tableLabel="TEST"
      onBack={vi.fn()} onDraftCancelled={vi.fn()} onOrderSubmitted={vi.fn()}
      catalog={{ catalog: menu, categories, items, loading: false, error: null,
        source: 'NETWORK', lastUpdatedAt: null, updating: false, updateError: null,
        refreshStatus: 'CURRENT', refreshCatalog: vi.fn(), cacheStale: false }} />) })
    const root = view!.root
    const itemFor = (sku: string) => items.find((item) => item.sku === sku)!
    const show = async (sku: string) => act(async () => root.findByType(CategoryNav).props.onSelect(itemFor(sku).categoryId))
    const card = (sku: string) => root.findAllByType(MenuItemCard).find((c) => c.props.item.sku === sku)!
    const button = (sku: string, prefix: string) => card(sku).findByProps({ 'aria-label': prefix + itemFor(sku).nameEn })
    const tap = { stopPropagation: vi.fn() }
    await show('add_on_zha_jiang')
    for (const [sku] of skuPrices) {
      const code = 'add_on_' + sku
      await act(async () => {
        if (sku === 'cilantro') {
          card(code).findByProps({ role: 'button' }).props.onClick()
        } else {
          await button(code, 'Add ').props.onClick(tap)
        }
      })
      expect(root.findAllByType(ItemCustomizationModal)).toHaveLength(0)
    }
    expect(draftSpy.add.mock.calls.every(([, quantity]) => quantity === 1)).toBe(true)
    expect(root.findByType(OrderSummaryPanel).props.subtotal).toBe(15)
    expect(card('add_on_zha_jiang').findByProps({ role: 'status' }).children).toContain('Added')
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    let saved = () => {}
    draftSpy.delay = new Promise<void>((resolve) => { saved = resolve })
    await act(async () => {
      const first = button('add_on_corn', 'Add one ').props.onClick(tap)
      await card('add_on_corn').findByProps({ role: 'button' }).props.onClick()
      saved()
      await first
    })
    await act(async () => button('add_on_corn', 'Add one ').props.onClick(tap))
    expect(draftSpy.add.mock.calls.filter(([id]) => id === itemFor('add_on_corn').id)).toHaveLength(2)
    expect(root.findByType(OrderSummaryPanel).props.subtotal).toBe(18)
    await act(async () => button('add_on_corn', 'Remove one ').props.onClick(tap))
    expect(root.findByType(OrderSummaryPanel).props.subtotal).toBe(15)
    expect(button('sold_out_fixture', 'Add ').props.disabled).toBe(true)
    for (const sku of ['add_on_beef_broth', 'traditional_beef_noodle']) {
      await show(sku)
      await act(async () => button(sku, 'Add ').props.onClick(tap))
      const modal = root.findByType(ItemCustomizationModal)
      expect(modal.props.item.sku).toBe(sku)
      if (sku === 'add_on_beef_broth') {
        expect(modal.props.subtotal).toBe(3)
        expect(modal.props.item.customization.removeOptions.some((choice: { optionCode: string }) => choice.optionCode === 'chili_on_side')).toBe(true)
        const size = modal.props.item.customization.sizes.options.find((choice: { optionCode: string }) => choice.optionCode === 'size_small')
        await act(async () => modal.props.onChange({ ...modal.props.draft, sizeId: size.id }))
        expect(modal.props.subtotal).toBe(2)
      }
      await act(async () => modal.props.onClose())
    }
    await show('fixed_size_fixture')
    expect(card('fixed_size_fixture').findAllByType('div').some((node) => node.children.join('') === '$5.00')).toBe(true)
    await act(async () => button('fixed_size_fixture', 'Add ').props.onClick(tap))
    expect(root.findAllByType(ItemCustomizationModal)).toHaveLength(0)
    expect(root.findByType(OrderSummaryPanel).props.subtotal).toBe(20)
  } finally {
    await act(async () => view?.unmount())
    vi.useRealTimers()
    vi.unstubAllGlobals()
  }
})
