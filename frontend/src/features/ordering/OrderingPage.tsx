import { useEffect, useMemo, useRef, useState } from 'react'
import { Card } from '../../components/ui/Card'
import { useIpadLandscape } from '../../hooks/useIpadLandscape'
import { useConnectionStatus } from '../../hooks/useConnectionStatus'
import { buildDefaultDraft, calculateTotals } from '../../hooks/useOrderSessions'
import { useDraftOrder } from '../../hooks/useDraftOrder'
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
      return '当前设备离线，点餐内容会保存在本机；服务器确认前尚未进入厨房。'
    case 'BACKEND_UNREACHABLE':
      return '网络不稳定，暂时无法连接餐厅服务器；点餐内容会保存在本机。'
    case 'AUTH_REQUIRED':
      return '登录状态已失效，请重新登录后继续。'
    case 'ONLINE_DEGRADED':
      return '网络不稳定，点餐内容会保存在本机；请留意订单提交状态。'
    default:
      return null
  }
}

function getDraftSubtotal(item: MenuItem, draft: ItemCustomizationDraft) {
  const sizeDelta = item.customization?.sizes?.options.find((option) => option.id === draft.sizeId)?.priceDelta ?? 0
  const soupBaseDelta =
    item.customization?.soupBases?.options.find((option) => option.id === draft.soupBaseId)?.priceDelta ?? 0
  const comboDelta = draft.comboEnabled ? (item.customization?.combo?.upcharge ?? 0) : 0
  const comboGroupDelta = draft.comboEnabled
    ? item.customization?.combo?.groups
      ?.reduce((sum, group) => {
        const legacySelection = group.groupCode === 'COMBO_EGG'
          ? draft.comboEggId
          : group.groupCode === 'COMBO_SIDE'
            ? draft.comboSideId
            : undefined
        const selectedId = draft.comboSelections?.[group.groupCode]
          ?? legacySelection
          ?? group.defaultOptionId
          ?? (group.required ? group.options[0]?.id : undefined)
        const selected = group.options.find((option) => option.id === selectedId)
        return sum + (selected?.priceDelta ?? 0)
      }, 0) ?? 0
    : 0
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

  return (item.price + sizeDelta + soupBaseDelta + comboDelta + comboGroupDelta + comboSideRemoveDelta + addOnDelta + removeDelta) * draft.quantity
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
  const { user } = useAuth()
  const { organizationId } = useCurrentStore()
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
    incrementItem,
    decrementItem,
    removeItem,
    cancelOrder,
    submitOrder,
    refreshOrder,
    updateHeader,
    saveLocalDraftNow,
    localSubmitState,
    outboxRecord,
    draftSubmissionLocked,
    retryQueuedOrder,
    returnQueuedOrderToDraft,
  } = useDraftOrder(storeId, slotLabel, tableLabel, orderType, pickupLabel, items, {
    accountId: user?.id ?? null,
    organizationId,
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
      setMenuUpdateNotice('菜单已更新，新添加菜品将使用最新菜单；当前草稿中的菜品保持原价格和选项。')
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
    ? '正在检查菜单更新…'
    : catalogRefreshStatus === 'UPDATED'
      ? '菜单刷新成功，新添加菜品将使用最新菜单。'
      : catalogRefreshStatus === 'CURRENT'
        ? '菜单已是最新版本。'
        : catalogRefreshStatus === 'AUTH_REQUIRED'
          ? '菜单刷新需要重新登录。'
          : catalogRefreshStatus === 'BACKEND_UNREACHABLE'
            ? '后端暂时不可达，继续使用本机缓存菜单。'
            : catalogRefreshStatus === 'FAILED'
              ? '菜单刷新失败，继续使用本机缓存菜单。'
              : null

  const draftSubmissionLockMessage = draftSubmissionLocked
    ? localSubmitState === 'SUBMITTING'
      ? '订单正在提交到后端，暂时不能继续点菜。'
      : localSubmitState === 'QUEUED'
        ? '订单已进入待提交队列。如需修改，请先在订单栏选择“返回修改”。'
        : localSubmitState === 'FAILED_RETRYABLE'
          ? '订单等待自动重试。如需修改，请先返回修改。'
          : localSubmitState === 'CONFLICT' || localSubmitState === 'FAILED_VALIDATION'
            ? '订单需要处理同步冲突或菜单校验问题，请先在订单栏处理。'
            : '订单正在同步，暂时不能继续点菜。'
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
        const matchesCategory = item.categoryId === activeCategoryId
        const haystack = `${item.nameEn} ${item.nameZh} ${item.descriptionEn} ${item.descriptionZh}`.toLowerCase()
        return matchesCategory && haystack.includes(menuSearch.toLowerCase())
      }),
    [activeCategoryId, items, menuSearch],
  )

  const handleSelectMenuItem = (item: MenuItem) => {
    if (draftSubmissionLocked) return
    if (isQuickAddItem(item)) {
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
    if (draftSubmissionLocked) return
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
    if (draftSubmissionLocked) return
    if (isQuickAddItem(item)) {
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
    if (!customizationState || draftSubmissionLocked) {
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
    if (!window.confirm('确定要取消并删除本机草稿吗？')) {
      return
    }
    await cancelOrder()
    onDraftCancelled(slotLabel, tableLabel)
  }

  const handleReturnQueuedOrderToDraft = async () => {
    if (!window.confirm('返回修改后，必须重新检查菜单、价格和菜品状态。确定继续吗？')) return
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
        ? submittedOrder.items
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
                正在使用缓存菜单
                {catalogLastUpdatedAt ? ` · 最后更新 ${new Date(catalogLastUpdatedAt).toLocaleString()}` : ''}
              </span>
              <div className="flex flex-wrap gap-2">
                <button type="button" onClick={() => setMenuNoticeCollapsed(false)} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black">
                  展开
                </button>
                <button type="button" onClick={() => refreshCatalog()} disabled={catalogUpdating} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black disabled:opacity-50">
                  {catalogUpdating ? '刷新中…' : '刷新菜单'}
                </button>
              </div>
            </div>
          ) : (
            <div className={`rounded-[20px] border px-5 py-3 text-[0.95rem] font-bold ${cacheStale ? 'border-[rgba(151,34,34,0.3)] bg-[rgba(151,34,34,0.1)] text-[rgb(116,22,22)]' : 'border-[rgba(92,106,69,0.28)] bg-[rgba(92,106,69,0.1)] text-[rgb(59,73,40)]'}`}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span>
                  当前使用本机缓存菜单
                  {catalogLastUpdatedAt ? `，最后更新：${new Date(catalogLastUpdatedAt).toLocaleString()}` : ''}
                  {catalogUpdating ? '；正在后台检查更新…' : ''}
                  {cacheStale ? '。当前菜单数据较旧，价格和售罄状态可能已变化。' : ''}
                </span>
                <div className="flex flex-wrap gap-2">
                  <button type="button" onClick={() => refreshCatalog()} disabled={catalogUpdating} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black disabled:opacity-50">
                    {catalogUpdating ? '刷新中…' : '刷新菜单'}
                  </button>
                  {!catalogUpdateError ? (
                    <button type="button" onClick={dismissMenuNotice} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black">
                      收起提示
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
              {catalogUpdating ? '刷新中…' : '重试刷新'}
            </button>
          </div>
        ) : null}
        {menuUpdateNotice ? (
          menuNoticeCollapsed ? (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-[18px] border border-[rgba(92,106,69,0.28)] bg-[rgba(92,106,69,0.1)] px-4 py-2.5 text-[0.86rem] font-bold text-[rgb(59,73,40)]">
              <span>菜单已更新 · 新添加菜品使用最新菜单</span>
              <div className="flex flex-wrap gap-2">
                <button type="button" onClick={() => setMenuNoticeCollapsed(false)} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black">
                  展开
                </button>
                <button type="button" onClick={() => refreshCatalog()} disabled={catalogUpdating} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black disabled:opacity-50">
                  {catalogUpdating ? '刷新中…' : '刷新菜单'}
                </button>
              </div>
            </div>
          ) : (
            <div className="rounded-[20px] border border-[rgba(92,106,69,0.28)] bg-[rgba(92,106,69,0.1)] px-5 py-3 text-[0.95rem] font-bold text-[rgb(59,73,40)]">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span>{menuUpdateNotice}</span>
                <div className="flex flex-wrap gap-2">
                  <button type="button" onClick={() => refreshCatalog()} disabled={catalogUpdating} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black disabled:opacity-50">
                    {catalogUpdating ? '刷新中…' : '刷新菜单'}
                  </button>
                  <button type="button" onClick={dismissMenuNotice} className="rounded-full bg-white/70 px-3 py-1.5 text-xs font-black">
                    收起提示
                  </button>
                </div>
              </div>
            </div>
          )
        ) : null}

        {menuRefreshStatusLabel ? (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-[16px] bg-[rgba(26,28,25,0.05)] px-4 py-2.5 text-[0.84rem] font-semibold text-[var(--muted)]">
            <span>{menuRefreshStatusLabel}</span>
            {catalogRefreshStatus === 'AUTH_REQUIRED' ? <span className="font-black text-[var(--primary)]">请重新登录</span> : null}
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

        {(catalogError || draftError || persistenceError) ? (
          <div className="rounded-[24px] bg-[rgba(97,0,0,0.06)] px-5 py-4 text-base font-medium text-[var(--primary)]">
            {catalogError ?? draftError ?? persistenceError}
          </div>
        ) : null}

        {draftSubmissionLockMessage ? (
          <div className="rounded-[20px] border border-[rgba(151,34,34,0.22)] bg-[rgba(151,34,34,0.08)] px-5 py-3 text-[0.95rem] font-bold text-[rgb(116,22,22)]">
            {draftSubmissionLockMessage}
          </div>
        ) : null}

        {lastLocalSavedAt ? (
          <div className="text-right text-xs font-semibold text-[var(--muted)]">
            本机草稿已保存：{new Date(lastLocalSavedAt).toLocaleTimeString()}
          </div>
        ) : null}

        <div className={`ordering-workspace-grid grid items-start ${isIpadLandscape ? 'grid-cols-[14rem_minmax(0,1fr)_24rem] gap-3' : 'gap-6 xl:grid-cols-[18rem_minmax(0,1fr)_31rem]'}`}>
          <Card tone="well" className={`ordering-sidebar-scroll ${isIpadLandscape ? 'rounded-[24px] p-3.5' : 'rounded-[32px] p-5'}`}>
            <CategoryNav categories={categories} activeCategoryId={activeCategoryId} onSelect={setActiveCategoryId} compact={isIpadLandscape} />
          </Card>

          <Card tone="base" className={`flex flex-col overflow-hidden ${isIpadLandscape ? 'rounded-[24px] p-4' : 'rounded-[32px] p-6'}`}>
            <div className={`shrink-0 ${isIpadLandscape ? 'mb-3 flex items-center justify-between gap-3' : 'mb-5 flex items-center justify-between gap-4'}`}>
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
                    canDecrement={!draftSubmissionLocked && latestMutableItemByMenuItemId.has(item.id)}
                    disabled={draftSubmissionLocked}
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
              subtotal={subtotal}
              tax={tax}
              total={total}
              busy={saving}
              localSubmitState={localSubmitState}
              showSubmissionStatus={outboxRecord != null}
              orderLocked={draftSubmissionLocked}
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
          submitDisabled={draftSubmissionLocked}
          submitDisabledReason={draftSubmissionLockMessage ?? undefined}
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
