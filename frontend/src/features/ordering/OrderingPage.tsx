import { translate } from '../../i18n/engine'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Card } from '../../components/ui/Card'
import { useIpadLandscape } from '../../hooks/useIpadLandscape'
import { useConnectionStatus } from '../../hooks/useConnectionStatus'
import { buildDefaultDraft, calculateTotals } from '../../hooks/useOrderSessions'
import { useDraftOrder } from '../../hooks/useDraftOrder'
import { calculateDraftLineSubtotal } from '../../utils/orderPricing'
import type { ItemCustomizationDraft, MenuItem, OrderingCatalog } from '../../types/ordering'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { PrintWorkerHealthBanner } from '../frontdesk/components/PrintWorkerHealthBanner'
import { TakeoutEntryDialog } from '../dinein/components/TakeoutEntryDialog'
import { CategoryNav } from './components/CategoryNav'
import { ItemCustomizationModal } from './components/ItemCustomizationModal'
import { MenuItemCard } from './components/MenuItemCard'
import { OrderingTopBar } from './components/OrderingTopBar'
import { OrderSummaryPanel } from './components/OrderSummaryPanel'
import { getAndroidPadDeviceBridge } from '../../types/androidPadBridge'
import type { ConnectionState } from '../../services/networkStatus'
import { menuCacheNoticeDismissalKey } from '../../offline/menuCacheNotice'
import { useAuth } from '../auth/useAuth'
import { useCurrentStore } from '../store/useStoreContext'
import { isQuickAddItem } from './orderingCustomizationRules'
import { DiningTableOrderGate } from './DiningTableOrderGate'
import { useLocale } from '../../i18n/useLocale'
import { searchableText } from '../../i18n/locale'
import { canShowRefill } from './refillVisibility'

interface OrderingPageProps {
  catalog: {
    catalog: OrderingCatalog | null
    categories: OrderingCatalog['categories']
    items: OrderingCatalog['items']
    loading: boolean
    error: string | null
    source: 'CACHE' | 'NETWORK' | null
    lastUpdatedAt: string | null
    updating: boolean
    updateError: string | null
    refreshStatus: import('../../hooks/useMenuCatalog').MenuRefreshStatus
    refreshCatalog: () => void
    cacheStale: boolean
  }
  slotLabel: string
  tableLabel: string
  tableDisplayName?: string
  slotDisplayName?: string
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

function connectionWarning(state: ConnectionState) {
  switch (state) {
    case 'BROWSER_OFFLINE':
      return translate('network.offline')
    case 'BACKEND_UNREACHABLE':
      return translate('network.unreachable')
    case 'AUTH_REQUIRED':
      return translate('network.authRequired')
    case 'ONLINE_DEGRADED':
      return translate('network.unreachable')
    default:
      return null
  }
}

function kickPadDirectPrintWorker(reason: string, orderId: number, updateBatchId?: number | null) {
  const bridge = getAndroidPadDeviceBridge()
  if (!bridge?.kickPrintWorker) {
    return
  }
  try {
    bridge.kickPrintWorker(JSON.stringify({
      reason,
      order_id: orderId,
      order_update_batch_id: updateBatchId ?? null,
    }))
  } catch {
    // The Android bridge is an optimization for local Pad printing; ordering must not depend on it.
  }
}

export function OrderingPage(props: OrderingPageProps) {
  const key = `${props.storeId}:${props.orderType ?? 'dine_in'}:${props.slotLabel}:${props.tableLabel}:${props.pickupLabel ?? ''}`
  return <DiningTableOrderGate storeId={props.storeId} tableNo={props.slotLabel} enabled={(props.orderType ?? 'dine_in') === 'dine_in'} onBack={props.onBack}><OrderingWorkspace key={key} {...props} /></DiningTableOrderGate>
}

function OrderingWorkspace({
  catalog,
  slotLabel,
  tableLabel,
  tableDisplayName,
  slotDisplayName,
  orderType = 'dine_in',
  pickupLabel = null,
  workstationLabel = null,
  storeId,
  onBack,
  onDraftCancelled,
  onOrderSubmitted,
}: OrderingPageProps) {
  const { t, label } = useLocale()
  const { user } = useAuth()
  const { organizationId, presentation, storeCode } = useCurrentStore()
  const unifiedOrderNotes = storeCode === 'ALLO_NOUILLES_TEST'
  const {
    categories,
    items,
    loading: catalogLoading,
    error: catalogError,
    source: catalogSource,
    lastUpdatedAt: catalogLastUpdatedAt,
    updating: catalogUpdating,
    updateError: catalogUpdateError,
    refreshStatus: catalogRefreshStatus,
    refreshCatalog,
    cacheStale,
  } = catalog
  const isIpadLandscape = useIpadLandscape()
  const [activeCategoryId, setActiveCategoryId] = useState('')
  const [menuSearch, setMenuSearch] = useState('')
  const [customizationState, setCustomizationState] = useState<CustomizationState | null>(null)
  const [takeoutDialogOpen, setTakeoutDialogOpen] = useState(false)
  const [quickAddStates, setQuickAddStates] = useState<Record<string, 'idle' | 'adding' | 'added'>>({})
  const [menuUpdateNotice, setMenuUpdateNotice] = useState<string | null>(null)
  const [menuNoticeCollapsed, setMenuNoticeCollapsed] = useState(false)
  const connection = useConnectionStatus()
  const handledSubmittedOrderIdsRef = useRef(new Set<number>())
  const localSubmitRequestedRef = useRef(false)
  const mountedRef = useRef(true)
  const contextGenerationRef = useRef(0)
  const contextKeyRef = useRef('')
  const previousMenuRevisionRef = useRef<number | null>(null)
  const {
    session,
    order,
    loading: draftLoading,
    saving,
    error: draftError,
    persistenceError,
    lastLocalSavedAt,
    addItem,
    updateItem,
    updateItemNote,
    updateOrderNotes,
    incrementItem,
    decrementItem,
    removeItem,
    cancelOrder,
    submitOrder,
    getConfirmedUpdateBatchId,
    refreshOrder,
    updateHeader,
    saveLocalDraftNow,
    localSubmitState,
    outboxRecord,
    draftSubmissionLocked,
    draftEditingLocked,
    retryQueuedOrder,
    returnQueuedOrderToDraft,
  } = useDraftOrder(storeId, slotLabel, tableLabel, orderType, pickupLabel, items, {
    accountId: user?.id ?? null,
    organizationId,
    orderNotesEnabled: unifiedOrderNotes,
    menuRevision: catalog.catalog?.menuRevision ?? 0,
  })
  const refreshOrderRef = useRef(refreshOrder)

  const orderingContextKey = `${storeId}:${orderType}:${slotLabel}:${tableLabel}:${pickupLabel ?? ''}`

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  useEffect(() => {
    contextGenerationRef.current += 1
    contextKeyRef.current = orderingContextKey
    handledSubmittedOrderIdsRef.current.clear()
  }, [orderingContextKey])

  useEffect(() => {
    refreshOrderRef.current = refreshOrder
  }, [refreshOrder])

  useEffect(() => {
    const handleOnline = () => {
      if (document.visibilityState === 'visible') {
        void refreshOrderRef.current()
      }
    }
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && navigator.onLine) {
        void refreshOrderRef.current()
      }
    }

    window.addEventListener('online', handleOnline)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      window.removeEventListener('online', handleOnline)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [])

  useEffect(() => {
    const revision = catalog.catalog?.menuRevision ?? null
    const previous = previousMenuRevisionRef.current
    if (revision != null && previous != null && revision !== previous && catalogSource === 'NETWORK') {
      setMenuUpdateNotice(translate('menu.updatedDraft'))
      setMenuNoticeCollapsed(false)
    }
    if (revision != null) previousMenuRevisionRef.current = revision
  }, [catalog.catalog?.menuRevision, catalogSource])

  const menuNoticeKey = menuCacheNoticeDismissalKey(
    { accountId: user?.id ?? null, organizationId: organizationId ?? null, storeId },
    catalog.catalog?.menuRevision ?? null,
  )

  useEffect(() => {
    if (!menuUpdateNotice && !cacheStale) {
      return
    }
    try {
      setMenuNoticeCollapsed(window.sessionStorage.getItem(menuNoticeKey) === '1')
    } catch {
      setMenuNoticeCollapsed(false)
    }
  }, [cacheStale, menuNoticeKey, menuUpdateNotice])

  const dismissMenuNotice = () => {
    try {
      window.sessionStorage.setItem(menuNoticeKey, '1')
    } catch {
      // Session storage is optional; the full warning remains available in memory.
    }
    setMenuNoticeCollapsed(true)
  }

  const menuRefreshStatusLabel = catalogRefreshStatus === 'CHECKING'
    ? translate('menu.checking')
    : catalogRefreshStatus === 'UPDATED'
      ? translate('menu.updated')
      : catalogRefreshStatus === 'CURRENT'
        ? translate('menu.current')
        : catalogRefreshStatus === 'AUTH_REQUIRED'
          ? translate('menu.authRefresh')
          : catalogRefreshStatus === 'BACKEND_UNREACHABLE'
            ? t('menu.failedRefresh')
            : catalogRefreshStatus === 'FAILED'
              ? translate('menu.failedRefresh')
              : null

  const draftSubmissionLockMessage = draftSubmissionLocked
    ? localSubmitState === 'SUBMITTING'
      ? translate('order.lockSubmitting')
      : localSubmitState === 'QUEUED'
        ? translate('order.lockQueued')
        : localSubmitState === 'FAILED_RETRYABLE'
          ? translate('order.lockRetry')
          : localSubmitState === 'CONFLICT' || localSubmitState === 'FAILED_VALIDATION'
            ? translate('order.lockReview')
            : translate('order.lockSync')
    : null

  useEffect(() => {
    if (!activeCategoryId && categories[0]?.id) {
      setActiveCategoryId(categories[0].id)
    }
  }, [activeCategoryId, categories])

  const { subtotal, tax, total } = useMemo(() => calculateTotals(session), [session])
  const orderedQuantityByMenuItemId = useMemo(() => {
    const quantities = new Map<string, number>()
    session?.items.forEach((item) => {
      quantities.set(item.menuItemId, (quantities.get(item.menuItemId) ?? 0) + item.quantity)
    })
    return quantities
  }, [session?.items])
  const latestMutableItemByMenuItemId = useMemo(() => {
    const mutableItems = new Map<string, { id: string; quantity: number }>()
    session?.items.forEach((item) => {
      if (!item.locked) {
        mutableItems.set(item.menuItemId, { id: item.id, quantity: item.quantity })
      }
    })
    return mutableItems
  }, [session?.items])

  const filteredItems = useMemo(
    () =>
      items.filter((item) => {
        const search = searchableText(menuSearch)
        const matchesCategory = search.length > 0 || item.categoryId === activeCategoryId
        const haystack = searchableText(item.sku, item.nameEn, item.nameZh, item.nameFr, item.descriptionEn, item.descriptionZh)
        return matchesCategory && haystack.includes(search) && canShowRefill(item, orderType, session?.items)
      }),
    [activeCategoryId, items, menuSearch, orderType, session?.items],
  )

  const handleSelectMenuItem = (item: MenuItem) => {
    if (draftEditingLocked) return
    if (isQuickAddItem(item, unifiedOrderNotes)) {
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
    if (draftEditingLocked) return
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

  const handleMenuCardAdd = async (item: MenuItem) => {
    if (draftEditingLocked) return
    if (isQuickAddItem(item, unifiedOrderNotes)) {
      await handleQuickAddItem(item)
      return
    }
    handleSelectMenuItem(item)
  }

  const handleDecrementMenuItem = (menuItemId: string) => {
    const targetItem = latestMutableItemByMenuItemId.get(menuItemId)
    if (!targetItem) {
      return
    }
    if (targetItem.quantity <= 1) {
      void removeItem(targetItem.id)
      return
    }
    void decrementItem(targetItem.id, targetItem.quantity)
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
    if (!customizationState || draftEditingLocked) {
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
    if (!window.confirm(t('order.cancelDraftConfirm'))) {
      return
    }
    await cancelOrder()
    onDraftCancelled(slotLabel, tableLabel)
  }

  const handleReturnQueuedOrderToDraft = async () => {
    if (!window.confirm(t('order.returnConfirm'))) return
    await returnQueuedOrderToDraft()
  }

  const handleSubmitOrder = async () => {
    if (session?.status !== 'draft' && !session?.isModifiedAfterSubmit) {
      return
    }

    const generation = contextGenerationRef.current
    const contextKey = orderingContextKey
    localSubmitRequestedRef.current = true
    try {
      const submittedOrder = await submitOrder()
      if (!submittedOrder
        || !mountedRef.current
        || contextGenerationRef.current !== generation
        || contextKeyRef.current !== contextKey) {
        return
      }

      handledSubmittedOrderIdsRef.current.add(submittedOrder.id)
      const isUpdate = session?.status !== 'draft'
      const updateBatchId = isUpdate
        ? getConfirmedUpdateBatchId() ?? submittedOrder.items
          .filter((item) => item.added_revision === submittedOrder.current_revision)
          .map((item) => item.order_update_batch_id)
          .find((batchId): batchId is number => batchId != null)
        : null
      kickPadDirectPrintWorker(isUpdate ? 'order-update-submit' : 'order-submit', submittedOrder.id, updateBatchId)
      onOrderSubmitted(slotLabel, tableLabel, submittedOrder.id, updateBatchId)
    } finally {
      localSubmitRequestedRef.current = false
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

  useEffect(() => {
    const submittedOrderId = outboxRecord?.state === 'SUBMITTED' ? outboxRecord.serverOrderId : null
    if (!mountedRef.current
      || !submittedOrderId
      || localSubmitRequestedRef.current
      || handledSubmittedOrderIdsRef.current.has(submittedOrderId)) return
    handledSubmittedOrderIdsRef.current.add(submittedOrderId)
    kickPadDirectPrintWorker('order-outbox-submitted', submittedOrderId, null)
    onOrderSubmitted(slotLabel, tableLabel, submittedOrderId, null)
  }, [onOrderSubmitted, outboxRecord?.serverOrderId, outboxRecord?.state, slotLabel, tableLabel])

  return (
    <div className={`ordering-page-safe min-h-screen bg-[var(--surface)] ${isIpadLandscape ? 'px-3 py-3' : 'px-5 py-4 md:px-7 xl:px-8'}`}>
      <div className={`mx-auto ${isIpadLandscape ? 'max-w-none space-y-3' : 'max-w-[1720px] space-y-6'}`}>
        {isIpadLandscape ? <FrontdeskTopNav activeItem="menu" /> : null}
        {isIpadLandscape ? <PrintWorkerHealthBanner /> : null}

        {connectionWarning(connection.state) ? (
          <div className="rounded-[20px] border border-[rgba(151,34,34,0.25)] bg-[rgba(151,34,34,0.1)] px-5 py-4 text-[1rem] font-bold text-[rgb(116,22,22)]">
            {connectionWarning(connection.state)}
          </div>
        ) : null}

        {catalogSource === 'CACHE' ? (
          menuNoticeCollapsed && !catalogUpdateError ? (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-[18px] border border-[rgba(92,106,69,0.28)] bg-[rgba(92,106,69,0.1)] px-4 py-2.5 text-[0.86rem] font-bold text-[rgb(59,73,40)]">
              <span>
                {t('menu.cached')}
                {catalogLastUpdatedAt ? ` · ${t('menu.lastUpdated', { time: new Date(catalogLastUpdatedAt).toLocaleString() })}` : ''}
              </span>
              <div className="flex flex-wrap gap-2">
                <button type="button" onClick={() => setMenuNoticeCollapsed(false)} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black">
                  {t('common.expand')}
                </button>
                <button type="button" onClick={() => refreshCatalog()} disabled={catalogUpdating} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black disabled:opacity-50">
                  {catalogUpdating ? t('common.loading') : translate('menu.refresh')}
                </button>
              </div>
            </div>
          ) : (
            <div className={`rounded-[20px] border px-5 py-3 text-[0.95rem] font-bold ${cacheStale ? 'border-[rgba(151,34,34,0.3)] bg-[rgba(151,34,34,0.1)] text-[rgb(116,22,22)]' : 'border-[rgba(92,106,69,0.28)] bg-[rgba(92,106,69,0.1)] text-[rgb(59,73,40)]'}`}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span>
                  {t('menu.cached')}
                  {catalogLastUpdatedAt ? ` · ${t('menu.lastUpdated', { time: new Date(catalogLastUpdatedAt).toLocaleString() })}` : ''}
                  {catalogUpdating ? ` · ${t('menu.checking')}` : ''}
                  {cacheStale ? ` · ${t('menu.old')}` : ''}
                </span>
                <div className="flex flex-wrap gap-2">
                  <button type="button" onClick={() => refreshCatalog()} disabled={catalogUpdating} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black disabled:opacity-50">
                    {catalogUpdating ? t('common.loading') : translate('menu.refresh')}
                  </button>
                  {!catalogUpdateError ? (
                    <button type="button" onClick={dismissMenuNotice} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black">
                      {t('common.collapse')}
                    </button>
                  ) : null}
                </div>
              </div>
            </div>
          )
        ) : null}

        {catalogUpdateError ? (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-[20px] border border-[rgba(151,34,34,0.22)] bg-[rgba(151,34,34,0.08)] px-5 py-3 text-[0.95rem] font-bold text-[rgb(116,22,22)]">
            <span>{catalogUpdateError}</span>
            <button type="button" onClick={() => refreshCatalog()} disabled={catalogUpdating} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black disabled:opacity-50">
              {catalogUpdating ? t('common.loading') : t('menu.refresh')}
            </button>
          </div>
        ) : null}
        {menuUpdateNotice ? (
          menuNoticeCollapsed ? (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-[18px] border border-[rgba(92,106,69,0.28)] bg-[rgba(92,106,69,0.1)] px-4 py-2.5 text-[0.86rem] font-bold text-[rgb(59,73,40)]">
              <span>{t('menu.updated')}</span>
              <div className="flex flex-wrap gap-2">
                <button type="button" onClick={() => setMenuNoticeCollapsed(false)} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black">
                  {t('common.expand')}
                </button>
                <button type="button" onClick={() => refreshCatalog()} disabled={catalogUpdating} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black disabled:opacity-50">
                  {catalogUpdating ? t('common.loading') : translate('menu.refresh')}
                </button>
              </div>
            </div>
          ) : (
            <div className="rounded-[20px] border border-[rgba(92,106,69,0.28)] bg-[rgba(92,106,69,0.1)] px-5 py-3 text-[0.95rem] font-bold text-[rgb(59,73,40)]">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span>{menuUpdateNotice}</span>
                <div className="flex flex-wrap gap-2">
                  <button type="button" onClick={() => refreshCatalog()} disabled={catalogUpdating} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black disabled:opacity-50">
                    {catalogUpdating ? t('common.loading') : translate('menu.refresh')}
                  </button>
                  <button type="button" onClick={dismissMenuNotice} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black">
                    {t('common.collapse')}
                  </button>
                </div>
              </div>
            </div>
          )
        ) : null}

        {menuRefreshStatusLabel ? (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-[16px] bg-[rgba(26,28,25,0.05)] px-4 py-2.5 text-[0.84rem] font-semibold text-[var(--muted)]">
            <span>{menuRefreshStatusLabel}</span>
            {catalogRefreshStatus === 'AUTH_REQUIRED' ? <span className="font-black text-[var(--primary)]">{t('network.authRequired')}</span> : null}
          </div>
        ) : null}

        <OrderingTopBar
          tableLabel={tableLabel}
          slotLabel={slotLabel}
          tableDisplayName={tableDisplayName}
          slotDisplayName={orderType === 'dine_in' ? order?.table_display_name || slotDisplayName : slotDisplayName}
          orderType={orderType}
          pickupLabel={effectivePickupLabel}
          workstationLabel={workstationLabel}
          onEditPickupLabel={orderType === 'pickup' ? () => setTakeoutDialogOpen(true) : undefined}
          onBack={onBack}
          searchValue={menuSearch}
          onSearchChange={setMenuSearch}
          compact={isIpadLandscape}
        />

        {(catalogError || draftError || persistenceError) ? (
          <div className="rounded-[24px] bg-[rgba(97,0,0,0.06)] px-5 py-4 text-base font-medium text-[var(--primary)]">
            {catalogError ?? draftError ?? persistenceError}
          </div>
        ) : null}
        {presentation?.notice ? <p className="rounded-2xl bg-[var(--surface-container-low)] px-4 py-3 text-sm text-[var(--on-surface)]">{label({ translations: presentation.notice })}</p> : null}

        {draftSubmissionLockMessage ? (
          <div className="rounded-[20px] border border-[rgba(151,34,34,0.22)] bg-[rgba(151,34,34,0.08)] px-5 py-3 text-[0.95rem] font-bold text-[rgb(116,22,22)]">
            {draftSubmissionLockMessage}
          </div>
        ) : null}

        {lastLocalSavedAt ? (
          <div className="text-right text-xs font-semibold text-[var(--muted)]">
            {t('order.localSaved')}: {new Date(lastLocalSavedAt).toLocaleTimeString()}
          </div>
        ) : null}

        <div className={`ordering-workspace-grid grid items-start ${isIpadLandscape ? 'grid-cols-[12rem_minmax(0,1fr)_22rem] gap-3' : 'gap-6 xl:grid-cols-[15rem_minmax(0,1fr)_27rem]'}`}>
          <Card tone="well" className={`ordering-sidebar-scroll ${isIpadLandscape ? 'rounded-[24px] p-3.5' : 'rounded-[32px] p-5'}`}>
            <CategoryNav categories={categories} activeCategoryId={activeCategoryId} onSelect={(categoryId) => { setActiveCategoryId(categoryId); setMenuSearch('') }} compact={isIpadLandscape} />
          </Card>

          <Card tone="base" className={`flex flex-col overflow-hidden ${isIpadLandscape ? 'rounded-[24px] p-4' : 'rounded-[32px] p-6'}`}>
            <div className={`shrink-0 ${isIpadLandscape ? 'mb-3 flex items-center justify-between gap-3' : 'mb-5 flex items-center justify-between gap-4'}`}>
              <div>
                <h1 className={`font-display font-extrabold tracking-[-0.05em] text-[var(--on-surface)] ${isIpadLandscape ? 'text-[2rem]' : 'text-[2.6rem]'}`}>
                  {t('nav.menu')}
                </h1>
                <p className={`${isIpadLandscape ? 'text-[0.95rem]' : 'text-[1.15rem]'} font-medium text-[var(--muted)]`}>
                  {catalogLoading || draftLoading ? t('common.loading') : t('menu.choose')}
                </p>
              </div>
              <div className={`rounded-[20px] bg-[rgba(26,28,25,0.04)] font-semibold text-[var(--muted)] ${isIpadLandscape ? 'px-3 py-2 text-[0.8rem]' : 'px-4 py-3 text-sm'}`}>
                {menuSearch.trim() ? t('menu.results') : label({ en: categories.find((category) => category.id === activeCategoryId)?.labelEn, zh: categories.find((category) => category.id === activeCategoryId)?.labelZh, fr: categories.find((category) => category.id === activeCategoryId)?.labelFr })}
              </div>
            </div>

            {catalogLoading ? (
              <div className={`ordering-menu-scroll grid ${isIpadLandscape ? 'gap-3 md:grid-cols-2' : 'gap-5 md:grid-cols-2'}`}>
                {Array.from({ length: 4 }).map((_, index) => (
                  <div key={`menu-loading-${index}`} className="h-[16rem] animate-pulse rounded-[28px] bg-[rgba(26,28,25,0.05)]" />
                ))}
              </div>
            ) : (
              <div className={`ordering-menu-scroll grid ${isIpadLandscape ? 'gap-3 md:grid-cols-2' : 'gap-5 md:grid-cols-2'}`}>
                {filteredItems.map((item) => (
                  <MenuItemCard
                    key={item.id}
                    item={item}
                    onSelect={handleSelectMenuItem}
                    onQuickAdd={handleMenuCardAdd}
                    onDecrement={() => handleDecrementMenuItem(item.id)}
                    quickAddState={quickAddStates[item.id] ?? 'idle'}
                    orderedQuantity={orderedQuantityByMenuItemId.get(item.id) ?? 0}
                    canDecrement={!draftEditingLocked && latestMutableItemByMenuItemId.has(item.id)}
                    disabled={draftEditingLocked}
                    disabledReason={draftSubmissionLockMessage ?? undefined}
                    compact={isIpadLandscape}
                  />
                ))}
              </div>
            )}
          </Card>

          {session ? (
            <OrderSummaryPanel
              session={session}
              unifiedOrderNotes={unifiedOrderNotes}
              onUpdateOrderNotes={updateOrderNotes}
              slotDisplayName={orderType === 'dine_in' ? order?.table_display_name || slotDisplayName : session.slotLabel}
              subtotal={subtotal}
              tax={tax}
              total={total}
              busy={saving}
              localSubmitState={localSubmitState}
              showSubmissionStatus={outboxRecord != null}
              orderLocked={draftSubmissionLocked}
              itemsLocked={draftEditingLocked}
              lastBackendSuccessAt={connection.lastSuccessAt}
              submissionErrorCode={outboxRecord?.lastErrorCode}
              nextRetryAt={outboxRecord?.nextRetryAt}
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
              onSaveDraft={() => void saveLocalDraftNow()}
              onCancelOrder={() => void handleCancelOrder()}
              onSubmitOrder={() => void handleSubmitOrder()}
              onRetryQueuedOrder={() => void retryQueuedOrder()}
              onReturnQueuedOrderToDraft={() => void handleReturnQueuedOrderToDraft()}
              compact={isIpadLandscape}
            />
          ) : (
            <div className={`flex min-h-[34rem] items-center justify-center bg-[rgba(255,255,255,0.82)] shadow-[0_18px_42px_rgba(26,28,25,0.06)] ${isIpadLandscape ? 'rounded-[24px] p-4' : 'rounded-[32px] p-5'}`}>
              <div className={`text-center text-[var(--muted)] ${isIpadLandscape ? 'text-[0.95rem]' : 'text-[1.05rem]'}`}>
                {draftLoading ? t('common.loading') : t('order.openFailed')}
              </div>
            </div>
          )}
        </div>
      </div>

      {customizationState ? (
        <ItemCustomizationModal
          allowRiceNotes={unifiedOrderNotes}
          item={customizationState.item}
          draft={customizationState.draft}
          mode={customizationState.mode}
          subtotal={calculateDraftLineSubtotal(customizationState.item, customizationState.draft)}
          onClose={closeCustomizationModal}
          onChange={(nextDraft) => setCustomizationState((current) => (current ? { ...current, draft: nextDraft } : null))}
          onSubmit={() => void handleModalSubmit()}
          submitDisabled={draftEditingLocked}
          submitDisabledReason={draftSubmissionLockMessage ?? undefined}
        />
      ) : null}

      <TakeoutEntryDialog
        open={takeoutDialogOpen}
        initialValue={effectivePickupLabel ?? ''}
        allowEmpty
        confirmLabel={t('common.save')}
        helperText={t('order.pickupHint')}
        onClose={() => setTakeoutDialogOpen(false)}
        onConfirm={(value) => void handleUpdatePickupLabel(value)}
      />
    </div>
  )
}
