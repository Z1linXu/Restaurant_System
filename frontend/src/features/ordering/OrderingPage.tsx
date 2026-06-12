import { useEffect, useMemo, useState } from 'react'
import { Card } from '../../components/ui/Card'
import { useIpadLandscape } from '../../hooks/useIpadLandscape'
import { buildDefaultDraft, calculateTotals } from '../../hooks/useOrderSessions'
import { useDraftOrder } from '../../hooks/useDraftOrder'
import type { ItemCustomizationDraft, MenuItem, OrderingCatalog } from '../../types/ordering'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { TakeoutEntryDialog } from '../dinein/components/TakeoutEntryDialog'
import { CategoryNav } from './components/CategoryNav'
import { ItemCustomizationModal } from './components/ItemCustomizationModal'
import { MenuItemCard } from './components/MenuItemCard'
import { OrderingTopBar } from './components/OrderingTopBar'
import { OrderSummaryPanel } from './components/OrderSummaryPanel'

interface OrderingPageProps {
  catalog: {
    catalog: OrderingCatalog | null
    categories: OrderingCatalog['categories']
    items: OrderingCatalog['items']
    loading: boolean
    error: string | null
  }
  slotLabel: string
  tableLabel: string
  orderType?: 'dine_in' | 'pickup'
  pickupLabel?: string | null
  workstationLabel?: string | null
  onBack: () => void
  onDraftCancelled: (slotLabel: string, tableLabel: string) => void
  onOrderSubmitted: (slotLabel: string, tableLabel: string) => void
}

interface CustomizationState {
  item: MenuItem
  mode: 'add' | 'edit'
  draft: ItemCustomizationDraft
  editingItemId?: number
}

function getDraftSubtotal(item: MenuItem, draft: ItemCustomizationDraft) {
  const sizeDelta = item.customization?.sizes?.options.find((option) => option.id === draft.sizeId)?.priceDelta ?? 0
  const soupBaseDelta =
    item.customization?.soupBases?.options.find((option) => option.id === draft.soupBaseId)?.priceDelta ?? 0
  const comboDelta = draft.comboEnabled ? (item.customization?.combo?.upcharge ?? 0) : 0
  const addOnDelta =
    item.customization?.addOns
      ?.reduce((sum, option) => sum + (option.priceDelta ?? 0) * (draft.addOnQuantities[option.id] ?? 0), 0) ?? 0
  const removeDelta =
    item.customization?.removeOptions
      ?.filter((option) => draft.removeIds.includes(option.id))
      .reduce((sum, option) => sum + (option.priceDelta ?? 0), 0) ?? 0

  return (item.price + sizeDelta + soupBaseDelta + comboDelta + addOnDelta + removeDelta) * draft.quantity
}

export function OrderingPage({
  catalog,
  slotLabel,
  tableLabel,
  orderType = 'dine_in',
  pickupLabel = null,
  workstationLabel = null,
  onBack,
  onDraftCancelled,
  onOrderSubmitted,
}: OrderingPageProps) {
  const { categories, items, loading: catalogLoading, error: catalogError } = catalog
  const isIpadLandscape = useIpadLandscape()
  const [activeCategoryId, setActiveCategoryId] = useState('')
  const [menuSearch, setMenuSearch] = useState('')
  const [customizationState, setCustomizationState] = useState<CustomizationState | null>(null)
  const [takeoutDialogOpen, setTakeoutDialogOpen] = useState(false)
  const [quickAddStates, setQuickAddStates] = useState<Record<string, 'idle' | 'adding' | 'added'>>({})
  const {
    session,
    order,
    loading: draftLoading,
    saving,
    error: draftError,
    addItem,
    updateItem,
    updateItemNote,
    incrementItem,
    decrementItem,
    removeItem,
    cancelOrder,
    submitOrder,
    refreshOrder,
    updateHeader,
  } = useDraftOrder(slotLabel, tableLabel, orderType, pickupLabel, items)

  useEffect(() => {
    if (!activeCategoryId && categories[0]?.id) {
      setActiveCategoryId(categories[0].id)
    }
  }, [activeCategoryId, categories])

  const { subtotal, tax, total } = useMemo(() => calculateTotals(session), [session])

  const filteredItems = useMemo(
    () =>
      items.filter((item) => {
        const matchesCategory = item.categoryId === activeCategoryId
        const haystack = `${item.nameEn} ${item.nameZh} ${item.descriptionEn} ${item.descriptionZh}`.toLowerCase()
        return matchesCategory && haystack.includes(menuSearch.toLowerCase())
      }),
    [activeCategoryId, items, menuSearch],
  )

  const handleSelectMenuItem = (item: MenuItem) => {
    if (item.categoryCode === 'FRIED' && !item.customization) {
      void addItem(item, buildDefaultDraft(item))
      return
    }
    setCustomizationState({
      item,
      mode: 'add',
      draft: buildDefaultDraft(item),
    })
  }

  const handleQuickAddItem = async (item: MenuItem) => {
    setQuickAddStates((current) => ({
      ...current,
      [item.id]: 'adding',
    }))

    try {
      await addItem(item, buildDefaultDraft(item))
      setQuickAddStates((current) => ({
        ...current,
        [item.id]: 'added',
      }))
      window.setTimeout(() => {
        setQuickAddStates((current) => ({
          ...current,
          [item.id]: 'idle',
        }))
      }, 900)
    } catch {
      setQuickAddStates((current) => ({
        ...current,
        [item.id]: 'idle',
      }))
    }
  }

  const handleEditItem = (itemId: string) => {
    const orderItem = session?.items.find((item) => item.id === itemId)
    const menuItem = items.find((item) => item.id === orderItem?.menuItemId)
    if (!orderItem || !menuItem) {
      return
    }

    setCustomizationState({
      item: menuItem,
      mode: 'edit',
      draft: orderItem.selection,
      editingItemId: Number(orderItem.id),
    })
  }

  const handleModalSubmit = async () => {
    if (!customizationState) {
      return
    }

    if (customizationState.mode === 'edit' && customizationState.editingItemId) {
      await updateItem(customizationState.editingItemId, customizationState.draft)
    } else {
      await addItem(customizationState.item, customizationState.draft)
    }
    setCustomizationState(null)
  }

  const handleCancelOrder = async () => {
    await cancelOrder()
    onDraftCancelled(slotLabel, tableLabel)
  }

  const handleSubmitOrder = async () => {
    if (session?.status === 'draft') {
      const submittedOrder = await submitOrder()
      if (!submittedOrder) {
        return
      }
      onOrderSubmitted(slotLabel, tableLabel)
      return
    }

    if (session?.isModifiedAfterSubmit) {
      const updatedOrder = await submitOrder()
      if (!updatedOrder) {
        return
      }
      onOrderSubmitted(slotLabel, tableLabel)
    }
  }

  const effectivePickupLabel =
    orderType === 'pickup'
      ? (order?.pickup_no ?? pickupLabel ?? slotLabel)
      : null

  const handleUpdatePickupLabel = async (nextValue: string) => {
    const normalized = nextValue.trim()
    const fallbackPickupLabel = pickupLabel ?? slotLabel
    await updateHeader(normalized || fallbackPickupLabel)
    setTakeoutDialogOpen(false)
  }

  return (
    <div className={`min-h-screen bg-[var(--surface)] ${isIpadLandscape ? 'px-3 py-3' : 'px-5 py-4 md:px-7 xl:px-8'}`}>
      <div className={`mx-auto ${isIpadLandscape ? 'max-w-none space-y-3' : 'max-w-[1720px] space-y-6'}`}>
        {isIpadLandscape ? <FrontdeskTopNav activeItem="menu" /> : null}

        <OrderingTopBar
          tableLabel={tableLabel}
          slotLabel={slotLabel}
          orderType={orderType}
          pickupLabel={effectivePickupLabel}
          workstationLabel={workstationLabel}
          onEditPickupLabel={orderType === 'pickup' ? () => setTakeoutDialogOpen(true) : undefined}
          onBack={onBack}
          searchValue={menuSearch}
          onSearchChange={setMenuSearch}
          compact={isIpadLandscape}
        />

        {(catalogError || draftError) ? (
          <div className="rounded-[24px] bg-[rgba(97,0,0,0.06)] px-5 py-4 text-base font-medium text-[var(--primary)]">
            {catalogError ?? draftError}
          </div>
        ) : null}

        <div className={`grid ${isIpadLandscape ? 'grid-cols-[14rem_minmax(0,1fr)_24rem] gap-3' : 'gap-6 xl:grid-cols-[18rem_minmax(0,1fr)_31rem]'}`}>
          <Card tone="well" className={`${isIpadLandscape ? 'rounded-[24px] p-3.5' : 'rounded-[32px] p-5'}`}>
            <CategoryNav categories={categories} activeCategoryId={activeCategoryId} onSelect={setActiveCategoryId} compact={isIpadLandscape} />
          </Card>

          <Card tone="base" className={`${isIpadLandscape ? 'rounded-[24px] p-4' : 'rounded-[32px] p-6'}`}>
            <div className={`${isIpadLandscape ? 'mb-3 flex items-center justify-between gap-3' : 'mb-5 flex items-center justify-between gap-4'}`}>
              <div>
                <h1 className={`font-display font-extrabold tracking-[-0.05em] text-[var(--on-surface)] ${isIpadLandscape ? 'text-[2rem]' : 'text-[2.6rem]'}`}>
                  Main Selection
                </h1>
                <p className={`${isIpadLandscape ? 'text-[0.95rem]' : 'text-[1.15rem]'} font-medium text-[var(--muted)]`}>
                  {catalogLoading ? 'Loading menu...' : draftLoading ? 'Loading draft...' : 'Tap an item to customize and add it.'}
                </p>
              </div>
              <div className={`rounded-[20px] bg-[rgba(26,28,25,0.04)] font-semibold text-[var(--muted)] ${isIpadLandscape ? 'px-3 py-2 text-[0.8rem]' : 'px-4 py-3 text-sm'}`}>
                {categories.find((category) => category.id === activeCategoryId)?.labelEn} /{' '}
                {categories.find((category) => category.id === activeCategoryId)?.labelZh}
              </div>
            </div>

            {catalogLoading ? (
              <div className={`grid ${isIpadLandscape ? 'gap-3 md:grid-cols-2' : 'gap-5 md:grid-cols-2'}`}>
                {Array.from({ length: 4 }).map((_, index) => (
                  <div key={`menu-loading-${index}`} className="h-[16rem] animate-pulse rounded-[28px] bg-[rgba(26,28,25,0.05)]" />
                ))}
              </div>
            ) : (
              <div className={`grid ${isIpadLandscape ? 'gap-3 md:grid-cols-2' : 'gap-5 md:grid-cols-2'}`}>
                {filteredItems.map((item) => (
                  <MenuItemCard
                    key={item.id}
                    item={item}
                    onSelect={handleSelectMenuItem}
                    onQuickAdd={item.categoryCode === 'FRIED' ? handleQuickAddItem : undefined}
                    quickAddState={quickAddStates[item.id] ?? 'idle'}
                    compact={isIpadLandscape}
                  />
                ))}
              </div>
            )}
          </Card>

          {session ? (
            <OrderSummaryPanel
              session={session}
              subtotal={subtotal}
              tax={tax}
              total={total}
              busy={saving}
              onIncrementItem={(itemId) => {
                const item = session.items.find((currentItem) => currentItem.id === itemId)
                if (item) {
                  void incrementItem(Number(item.id), item.quantity)
                }
              }}
              onDecrementItem={(itemId) => {
                const item = session.items.find((currentItem) => currentItem.id === itemId)
                if (item) {
                  void decrementItem(Number(item.id), item.quantity)
                }
              }}
              onEditItem={handleEditItem}
              onRemoveItem={(itemId) => void removeItem(Number(itemId))}
              onUpdateItemNote={(itemId, notes) => void updateItemNote(itemId, notes)}
              onSaveDraft={() => void refreshOrder()}
              onCancelOrder={() => void handleCancelOrder()}
              onSubmitOrder={() => void handleSubmitOrder()}
              compact={isIpadLandscape}
            />
          ) : (
            <div className={`flex h-full min-h-[28rem] items-center justify-center bg-[rgba(255,255,255,0.82)] shadow-[0_18px_42px_rgba(26,28,25,0.06)] ${isIpadLandscape ? 'rounded-[24px] p-4' : 'rounded-[32px] p-5'}`}>
              <div className={`text-center text-[var(--muted)] ${isIpadLandscape ? 'text-[0.95rem]' : 'text-[1.05rem]'}`}>
                {draftLoading ? 'Loading draft order...' : 'Unable to open draft order.'}
              </div>
            </div>
          )}
        </div>
      </div>

      {customizationState ? (
        <ItemCustomizationModal
          item={customizationState.item}
          draft={customizationState.draft}
          mode={customizationState.mode}
          subtotal={getDraftSubtotal(customizationState.item, customizationState.draft)}
          onClose={() => setCustomizationState(null)}
          onChange={(nextDraft) => setCustomizationState((current) => (current ? { ...current, draft: nextDraft } : null))}
          onSubmit={() => void handleModalSubmit()}
        />
      ) : null}

      <TakeoutEntryDialog
        open={takeoutDialogOpen}
        initialValue={effectivePickupLabel ?? ''}
        allowEmpty
        confirmLabel="Save"
        helperText="Optionally add a customer name or phone number. If left blank, the generated takeout number will stay in use."
        onClose={() => setTakeoutDialogOpen(false)}
        onConfirm={(value) => void handleUpdatePickupLabel(value)}
      />
    </div>
  )
}
