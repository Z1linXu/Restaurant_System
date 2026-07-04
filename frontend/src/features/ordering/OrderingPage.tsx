import { useEffect, useMemo, useRef, useState } from 'react'
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
import { fetchOrderPrintJobs } from '../../services/orderService'
import type { PrintJobRecord } from '../../services/printingAdminService'
import { printJobDisplayLabel, printJobOperatorDisplayMessage } from '../../utils/displayLabels'

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
  storeId: number
  onBack: () => void
  onDraftCancelled: (slotLabel: string, tableLabel: string) => void
  onOrderSubmitted: (slotLabel: string, tableLabel: string, orderId: number, updateBatchId?: number | null) => void
}

interface CustomizationState {
  item: MenuItem
  mode: 'add' | 'edit'
  draft: ItemCustomizationDraft
  editingItemId?: string
}

const PRINT_ATTENTION_MODULES = new Set(['GRAB', 'FRONTDESK_RECEIPT'])
const PRINT_ATTENTION_STATUSES = new Set(['FAILED', 'CANCELLED'])

function getDraftSubtotal(item: MenuItem, draft: ItemCustomizationDraft) {
  const sizeDelta = item.customization?.sizes?.options.find((option) => option.id === draft.sizeId)?.priceDelta ?? 0
  const soupBaseDelta =
    item.customization?.soupBases?.options.find((option) => option.id === draft.soupBaseId)?.priceDelta ?? 0
  const comboDelta = draft.comboEnabled ? (item.customization?.combo?.upcharge ?? 0) : 0
  const comboSideRemoveDelta = draft.comboEnabled
    ? item.customization?.combo?.sideRemoveOptions
      ?.filter((option) => draft.comboSideRemoveIds.includes(option.id))
      .reduce((sum, option) => sum + (option.priceDelta ?? 0), 0) ?? 0
    : 0
  const addOnDelta =
    item.customization?.addOns
      ?.reduce((sum, option) => sum + (option.priceDelta ?? 0) * (draft.addOnQuantities[option.id] ?? 0), 0) ?? 0
  const removeDelta =
    item.customization?.removeOptions
      ?.filter((option) => draft.removeIds.includes(option.id))
      .reduce((sum, option) => sum + (option.priceDelta ?? 0), 0) ?? 0

  return (item.price + sizeDelta + soupBaseDelta + comboDelta + comboSideRemoveDelta + addOnDelta + removeDelta) * draft.quantity
}

function printJobNeedsAttention(job: PrintJobRecord) {
  return PRINT_ATTENTION_MODULES.has(job.module_code) && PRINT_ATTENTION_STATUSES.has(job.status)
}

function printJobLabel(job: PrintJobRecord) {
  return printJobDisplayLabel(job)
}

function printJobReason(job: PrintJobRecord) {
  return printJobOperatorDisplayMessage(job) || '打印状态异常，请检查打印中心。'
}

function buildPrintAttentionMessage(jobs: PrintJobRecord[]) {
  const details = jobs
    .map((job) => `${printJobLabel(job)}: ${printJobReason(job)}`)
    .join(' ')

  return `订单已保存，但打印需要处理。${details} 请到订单记录或打印中心立即重打。`
}

export function OrderingPage({
  catalog,
  slotLabel,
  tableLabel,
  orderType = 'dine_in',
  pickupLabel = null,
  workstationLabel = null,
  storeId,
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
  const [printWarning, setPrintWarning] = useState<string | null>(null)
  const [isOnline, setIsOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine))
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
  } = useDraftOrder(storeId, slotLabel, tableLabel, orderType, pickupLabel, items)
  const refreshOrderRef = useRef(refreshOrder)

  useEffect(() => {
    refreshOrderRef.current = refreshOrder
  }, [refreshOrder])

  useEffect(() => {
    const handleOnline = () => {
      setIsOnline(true)
      if (document.visibilityState === 'visible') {
        void refreshOrderRef.current()
      }
    }
    const handleOffline = () => setIsOnline(false)
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && navigator.onLine) {
        setIsOnline(true)
        void refreshOrderRef.current()
      }
    }

    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [])

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
      editingItemId: orderItem.id,
    })
  }

  const closeCustomizationModal = () => {
    setCustomizationState(null)
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
    closeCustomizationModal()
  }

  const handleCancelOrder = async () => {
    await cancelOrder()
    onDraftCancelled(slotLabel, tableLabel)
  }

  const checkOrderPrintJobs = async (orderId: number, updateBatchId?: number | null) => {
    const delays = [450, 900, 1400, 2200]
    for (const delay of delays) {
      await new Promise((resolve) => window.setTimeout(resolve, delay))
      try {
        const jobs = await fetchOrderPrintJobs(orderId)
        const relevantJobs = jobs.filter((job) => {
          if (!PRINT_ATTENTION_MODULES.has(job.module_code)) {
            return false
          }
          return updateBatchId
            ? job.order_update_batch_id === updateBatchId
            : job.order_update_batch_id == null
        })
        const attentionJobs = relevantJobs.filter(printJobNeedsAttention)
        if (attentionJobs.length) {
          setPrintWarning(buildPrintAttentionMessage(attentionJobs))
          return false
        }
        const printedModules = new Set(
          relevantJobs
            .filter((job) => job.status === 'PRINTED')
            .map((job) => job.module_code),
        )
        if (printedModules.has('GRAB') && printedModules.has('FRONTDESK_RECEIPT')) {
          return true
        }
      } catch {
        // Do not block ordering if print status polling is temporarily unavailable.
      }
    }
    return true
  }

  const handleSubmitOrder = async () => {
    setPrintWarning(null)
    if (session?.status === 'draft') {
      const submittedOrder = await submitOrder()
      if (!submittedOrder) {
        return
      }
      const printOk = await checkOrderPrintJobs(submittedOrder.id)
      if (!printOk) {
        return
      }
      onOrderSubmitted(slotLabel, tableLabel, submittedOrder.id, null)
      return
    }

    if (session?.isModifiedAfterSubmit) {
      const updatedOrder = await submitOrder()
      if (!updatedOrder) {
        return
      }
      const updateBatchId = updatedOrder.items
        .filter((item) => item.added_revision === updatedOrder.current_revision)
        .map((item) => item.order_update_batch_id)
        .find((batchId): batchId is number => batchId != null)
      const printOk = await checkOrderPrintJobs(updatedOrder.id, updateBatchId)
      if (!printOk) {
        return
      }
      onOrderSubmitted(slotLabel, tableLabel, updatedOrder.id, updateBatchId ?? null)
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

        {printWarning ? (
          <div className="rounded-[20px] border-2 border-[rgba(151,34,34,0.35)] bg-[rgba(151,34,34,0.12)] px-5 py-4 text-[1rem] font-black text-[rgb(116,22,22)] shadow-[0_18px_34px_rgba(151,34,34,0.12)]">
            {printWarning}
          </div>
        ) : null}

        {!isOnline ? (
          <div className="rounded-[20px] border border-[rgba(151,34,34,0.25)] bg-[rgba(151,34,34,0.1)] px-5 py-4 text-[1rem] font-bold text-[rgb(116,22,22)]">
            当前设备离线，请检查网络后重试。
          </div>
        ) : null}

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

        <div className={`grid min-h-0 items-stretch ${isIpadLandscape ? 'h-[calc(100vh-12.5rem)] grid-cols-[14rem_minmax(0,1fr)_24rem] gap-3' : 'gap-6 xl:h-[calc(100vh-14rem)] xl:grid-cols-[18rem_minmax(0,1fr)_31rem]'}`}>
          <Card tone="well" className={`${isIpadLandscape ? 'rounded-[24px] p-3.5' : 'rounded-[32px] p-5'}`}>
            <CategoryNav categories={categories} activeCategoryId={activeCategoryId} onSelect={setActiveCategoryId} compact={isIpadLandscape} />
          </Card>

          <Card tone="base" className={`${isIpadLandscape ? 'rounded-[24px] p-4' : 'rounded-[32px] p-6'}`}>
            <div className={`${isIpadLandscape ? 'mb-3 flex items-center justify-between gap-3' : 'mb-5 flex items-center justify-between gap-4'}`}>
              <div>
                <h1 className={`font-display font-extrabold tracking-[-0.05em] text-[var(--on-surface)] ${isIpadLandscape ? 'text-[2rem]' : 'text-[2.6rem]'}`}>
                  菜单
                </h1>
                <p className={`${isIpadLandscape ? 'text-[0.95rem]' : 'text-[1.15rem]'} font-medium text-[var(--muted)]`}>
                  {catalogLoading ? '正在加载菜单...' : draftLoading ? '正在加载订单...' : '点击菜品选择规格并加入订单。'}
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
                  void incrementItem(item.id, item.quantity)
                }
              }}
              onDecrementItem={(itemId) => {
                const item = session.items.find((currentItem) => currentItem.id === itemId)
                if (item) {
                  void decrementItem(item.id, item.quantity)
                }
              }}
              onEditItem={handleEditItem}
              onRemoveItem={(itemId) => void removeItem(itemId)}
              onUpdateItemNote={(itemId, notes) => void updateItemNote(itemId, notes)}
              onSaveDraft={() => void refreshOrder()}
              onCancelOrder={() => void handleCancelOrder()}
              onSubmitOrder={() => void handleSubmitOrder()}
              compact={isIpadLandscape}
            />
          ) : (
            <div className={`flex h-full min-h-[28rem] items-center justify-center bg-[rgba(255,255,255,0.82)] shadow-[0_18px_42px_rgba(26,28,25,0.06)] ${isIpadLandscape ? 'rounded-[24px] p-4' : 'rounded-[32px] p-5'}`}>
              <div className={`text-center text-[var(--muted)] ${isIpadLandscape ? 'text-[0.95rem]' : 'text-[1.05rem]'}`}>
                {draftLoading ? '正在加载订单...' : '无法打开当前订单。'}
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
          onClose={closeCustomizationModal}
          onChange={(nextDraft) => setCustomizationState((current) => (current ? { ...current, draft: nextDraft } : null))}
          onSubmit={() => void handleModalSubmit()}
        />
      ) : null}

      <TakeoutEntryDialog
        open={takeoutDialogOpen}
        initialValue={effectivePickupLabel ?? ''}
        allowEmpty
        confirmLabel="保存"
        helperText="可填写顾客姓名或电话；不填则继续使用系统生成的外卖编号。"
        onClose={() => setTakeoutDialogOpen(false)}
        onConfirm={(value) => void handleUpdatePickupLabel(value)}
      />
    </div>
  )
}
