import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  cancelDraftOrder,
  fetchOrderDetail,
  findEditableOrderByContext,
  mapOptions,
  submitOrderUpdate,
  updateEditableOrderHeader,
  type IdempotentOrderSubmitPayload,
} from '../services/orderService'
import type {
  BackendOrderItemOptionResponse,
  BackendOrderItemResponse,
  BackendOrderResponse,
  ItemCustomizationDraft,
  LocalizedText,
  MenuItem,
  OrderLineItem,
  OrderSession,
} from '../types/ordering'
import { createIdempotencyKey } from '../utils/randomId'
import { ApiRequestError } from '../services/apiClient'
import { recordAppOperation } from '../services/networkStatus'
import {
  buildDraftContextKey,
  cleanupExpiredLocalDrafts,
  deleteLocalDraft,
  deleteLocalDraftIfClientMatches,
  readLocalDraft,
  reopenRejectedLocalDraft,
  resolveLocalDraftForOpen,
  saveLocalDraft,
  saveLocalDraftIfClientMatches,
  type LocalDraftContext,
  type LocalDraftRecord,
  type LocalDraftScope,
} from '../offline/localDrafts'
import {
  ORDER_OUTBOX_UPDATED_EVENT,
  createOrderOutboxRecord,
  readOrderOutboxRecord,
  saveOrderOutboxRecord,
  type OrderOutboxRecord,
} from '../offline/orderOutbox'
import {
  kickOrderOutboxProcessor,
  processOrderOutboxRecord,
} from '../services/orderOutboxProcessor'

function calculateDraftLineSubtotal(menuItem: MenuItem | undefined, draft: ItemCustomizationDraft) {
  if (!menuItem) {
    return 0
  }
  const sizeDelta = menuItem.customization?.sizes?.options.find((option) => option.id === draft.sizeId)?.priceDelta ?? 0
  const soupBaseDelta =
    menuItem.customization?.soupBases?.options.find((option) => option.id === draft.soupBaseId)?.priceDelta ?? 0
  const comboDelta = draft.comboEnabled ? (menuItem.customization?.combo?.upcharge ?? 0) : 0
  const addOnDelta =
    menuItem.customization?.addOns
      ?.reduce((sum, option) => sum + (option.priceDelta ?? 0) * (draft.addOnQuantities[option.id] ?? 0), 0) ?? 0
  const removeDelta =
    menuItem.customization?.removeOptions
      ?.filter((option) => draft.removeIds.includes(option.id))
      .reduce((sum, option) => sum + (option.priceDelta ?? 0), 0) ?? 0

  return (menuItem.price + sizeDelta + soupBaseDelta + comboDelta + addOnDelta + removeDelta) * draft.quantity
}

function optionTag(option: BackendOrderItemOptionResponse): LocalizedText {
  if ((option.quantity ?? 1) > 1) {
    return {
      en: `${option.option_name_snapshot_en} x${option.quantity}`,
      zh: `${option.option_name_snapshot_zh} x${option.quantity}`,
    }
  }

  return {
    en: option.option_name_snapshot_en,
    zh: option.option_name_snapshot_zh,
  }
}

function buildItemSelection(item: BackendOrderItemResponse, menuItem: MenuItem | undefined): ItemCustomizationDraft {
  const draft: ItemCustomizationDraft = {
    itemId: String(item.id),
    sizeId: undefined,
    soupBaseId: undefined,
    noodleTypeId: undefined,
    spicyLevelId: undefined,
    comboEnabled: false,
    comboEggId: undefined,
    comboSideId: undefined,
    comboSideRemoveIds: [],
    addOnQuantities: {},
    removeIds: [],
    quantity: item.quantity,
    notes: item.notes ?? '',
  }

  item.options.forEach((option) => {
    const optionId = String(option.option_id)
    const comboConfig = menuItem?.customization?.combo
    if (comboConfig?.optionId === optionId) {
      draft.comboEnabled = true
      return
    }
    if (comboConfig?.eggs.some((comboOption) => comboOption.id === optionId)) {
      draft.comboEnabled = true
      draft.comboEggId = optionId
      return
    }
    if (comboConfig?.sides.some((comboOption) => comboOption.id === optionId)) {
      draft.comboEnabled = true
      draft.comboSideId = optionId
      return
    }
    if (comboConfig?.sideRemoveOptions.some((comboOption) => comboOption.id === optionId)) {
      draft.comboSideRemoveIds.push(optionId)
      return
    }
    switch (option.option_type_snapshot) {
      case 'size':
        draft.sizeId = optionId
        break
      case 'soup_base':
        draft.soupBaseId = optionId
        break
      case 'noodle_type':
        draft.noodleTypeId = optionId
        break
      case 'spicy_level':
        draft.spicyLevelId = optionId
        break
      case 'addon':
        draft.addOnQuantities[optionId] = option.quantity ?? 1
        break
      case 'remove':
        draft.removeIds.push(optionId)
        break
      default:
        break
    }
  })

  if (!draft.sizeId && menuItem?.customization?.sizes?.options[0]?.id) {
    draft.sizeId = menuItem.customization.sizes.options[0].id
  }
  if (!draft.soupBaseId && menuItem?.customization?.soupBases?.options[0]?.id) {
    draft.soupBaseId = menuItem.customization.soupBases.options[0].id
  }
  if (!draft.noodleTypeId && menuItem?.customization?.noodleTypes?.[0]?.id) {
    draft.noodleTypeId = menuItem.customization.noodleTypes[0].id
  }
  if (!draft.spicyLevelId && menuItem?.customization?.spicyLevels?.[0]?.id) {
    draft.spicyLevelId = menuItem.customization.spicyLevels[0].id
  }
  if (draft.comboEnabled && !draft.comboEggId && menuItem?.customization?.combo?.eggs[0]?.id) {
    draft.comboEggId = menuItem.customization.combo.eggs[0].id
  }
  if (draft.comboEnabled && !draft.comboSideId && menuItem?.customization?.combo?.sides[0]?.id) {
    draft.comboSideId = menuItem.customization.combo.sides[0].id
  }

  return draft
}

function mapOrderItem(item: BackendOrderItemResponse, catalogItems: MenuItem[], locked = false): OrderLineItem {
  const menuItem = catalogItems.find((catalogItem) => catalogItem.id === String(item.menu_item_id))

  return {
    id: String(item.id),
    menuItemId: String(item.menu_item_id),
    nameEn: item.item_name_snapshot_en || menuItem?.nameEn || '',
    nameZh: item.item_name_snapshot_zh || menuItem?.nameZh || '',
    quantity: item.quantity,
    unitPrice: Number(item.unit_price),
    lineSubtotal: Number(item.line_amount),
    selection: buildItemSelection(item, menuItem),
    summaryTags: item.options.map(optionTag),
    notes: item.notes ?? '',
    locked,
  }
}

function mapOrderToSession(order: BackendOrderResponse, slotLabel: string, tableLabel: string, catalogItems: MenuItem[]): OrderSession {
  return {
    orderId: String(order.id),
    slotLabel,
    tableLabel,
    status:
      order.status === 'draft'
        ? 'draft'
        : order.status === 'preparing'
          ? 'preparing'
          : 'submitted',
    isModifiedAfterSubmit: Boolean(order.is_modified_after_submit),
    items: order.items.map((item) => mapOrderItem(item, catalogItems, order.status !== 'draft')),
  }
}

function buildLocalLineItem(menuItem: MenuItem, draft: ItemCustomizationDraft): OrderLineItem {
  const summaryTags: LocalizedText[] = []

  const pushOptionTag = (optionId: string | undefined) => {
    if (!optionId) {
      return
    }
    const allOptions = [
      ...(menuItem.customization?.sizes?.options ?? []),
      ...(menuItem.customization?.soupBases?.options ?? []),
      ...(menuItem.customization?.noodleTypes ?? []),
      ...(menuItem.customization?.spicyLevels ?? []),
      ...(menuItem.customization?.combo?.eggs ?? []),
      ...(menuItem.customization?.combo?.sides ?? []),
      ...(menuItem.customization?.combo?.sideRemoveOptions ?? []),
      ...(menuItem.customization?.addOns ?? []),
      ...(menuItem.customization?.removeOptions ?? []),
    ]
    const matched = allOptions.find((option) => option.id === optionId)
    if (matched) {
      summaryTags.push({ en: matched.labelEn, zh: matched.labelZh })
    }
  }

  pushOptionTag(draft.sizeId)
  pushOptionTag(draft.soupBaseId)
  pushOptionTag(draft.noodleTypeId)
  pushOptionTag(draft.spicyLevelId)
  if (draft.comboEnabled) {
    summaryTags.push({ en: 'Combo', zh: '套餐' })
    pushOptionTag(draft.comboEggId ?? menuItem.customization?.combo?.eggs[0]?.id)
    pushOptionTag(draft.comboSideId ?? menuItem.customization?.combo?.sides[0]?.id)
    draft.comboSideRemoveIds.forEach((optionId) => pushOptionTag(optionId))
  }
  Object.entries(draft.addOnQuantities).forEach(([optionId, quantity]) => {
    if (quantity <= 0) {
      return
    }
    const option = menuItem.customization?.addOns?.find((current) => current.id === optionId)
    if (!option) {
      return
    }
    summaryTags.push({
      en: quantity > 1 ? `${option.labelEn} x${quantity}` : option.labelEn,
      zh: quantity > 1 ? `${option.labelZh} x${quantity}` : option.labelZh,
    })
  })
  draft.removeIds.forEach((optionId) => pushOptionTag(optionId))

  return {
    id: `temp-${menuItem.id}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    menuItemId: menuItem.id,
    nameEn: menuItem.nameEn,
    nameZh: menuItem.nameZh,
    quantity: draft.quantity,
    unitPrice: menuItem.price,
    lineSubtotal: calculateDraftLineSubtotal(menuItem, draft),
    selection: draft,
    summaryTags,
    notes: draft.notes,
    locked: false,
  }
}

interface DraftOfflineIdentity {
  accountId: number | null
  organizationId: number | null
  menuRevision: number
}

function buildFrozenSubmitPayload(
  record: LocalDraftRecord,
  items: OrderLineItem[],
  catalogItems: MenuItem[],
): IdempotentOrderSubmitPayload {
  return {
    client_order_id: record.clientOrderId,
    idempotency_key: record.clientOrderId,
    organization_id: record.organizationId,
    store_id: record.storeId,
    server_order_id: record.serverOrderId,
    order_type: record.context.orderType,
    table_no: record.context.orderType === 'dine_in' ? record.context.tableNo : null,
    pickup_no: record.context.orderType === 'pickup' ? record.context.pickupNo : null,
    menu_revision: record.menuRevision,
    expected_subtotal_amount: Number(
      items.reduce((total, item) => total + item.lineSubtotal, 0).toFixed(2),
    ),
    items: items.map((item) => {
      const menuItem = catalogItems.find((candidate) => candidate.id === item.menuItemId)
      if (!menuItem) {
        throw new Error(`菜单缓存缺少菜品 ${item.menuItemId}，请刷新菜单后检查订单。`)
      }
      return {
        menu_item_id: Number(item.menuItemId),
        quantity: item.quantity,
        combo_group_no: null,
        combo_role: 'standalone',
        notes: item.notes.trim() || null,
        options: mapOptions(item.selection, menuItem),
      }
    }),
  }
}

const LOCAL_DRAFT_SAVE_DEBOUNCE_MS = 200

export function useDraftOrder(
  storeId: number,
  slotLabel: string,
  tableLabel: string,
  orderType: 'dine_in' | 'pickup',
  pickupLabel: string | null,
  catalogItems: MenuItem[],
  offlineIdentity: DraftOfflineIdentity,
) {
  const [order, setOrder] = useState<BackendOrderResponse | null>(null)
  const [stagedItems, setStagedItems] = useState<OrderLineItem[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [persistenceError, setPersistenceError] = useState<string | null>(null)
  const [localDraftId, setLocalDraftId] = useState<string | null>(null)
  const [clientOrderId, setClientOrderId] = useState<string | null>(null)
  const [lastLocalSavedAt, setLastLocalSavedAt] = useState<string | null>(null)
  const [outboxRecord, setOutboxRecord] = useState<OrderOutboxRecord | null>(null)
  const submitInFlightRef = useRef(false)
  const updateIdempotencyKeyRef = useRef<string | null>(null)
  const localDraftRef = useRef<LocalDraftRecord | null>(null)
  const saveTimerRef = useRef<number | null>(null)
  const flushLocalDraftRef = useRef<() => Promise<void>>(async () => undefined)

  const localScope = useMemo<LocalDraftScope | null>(() => {
    if (offlineIdentity.accountId == null || offlineIdentity.organizationId == null) return null
    return {
      accountId: offlineIdentity.accountId,
      organizationId: offlineIdentity.organizationId,
      storeId,
    }
  }, [offlineIdentity.accountId, offlineIdentity.organizationId, storeId])
  const localContext = useMemo<LocalDraftContext>(() => ({
    orderType,
    slotLabel,
    tableLabel,
    tableNo: orderType === 'dine_in' ? slotLabel : null,
    pickupNo: orderType === 'pickup' ? (pickupLabel ?? slotLabel) : null,
  }), [orderType, pickupLabel, slotLabel, tableLabel])
  const contextKey = useMemo(() => buildDraftContextKey(localContext), [localContext])

  const mapServerItems = useCallback((nextOrder: BackendOrderResponse) => nextOrder.items.map(
    (item) => mapOrderItem(item, catalogItems, nextOrder.status !== 'draft'),
  ), [catalogItems])

  useEffect(() => {
    let active = true
    setOrder(null)
    setStagedItems(null)
    setLoading(true)
    setError(null)
    setPersistenceError(null)
    setOutboxRecord(null)
    localDraftRef.current = null

    const load = async () => {
      let cached: LocalDraftRecord | null = null
      if (localScope) {
        try {
          void cleanupExpiredLocalDrafts().catch(() => undefined)
          const storedDraft = await readLocalDraft(localScope, contextKey)
          let pendingSubmission = storedDraft
            ? await readOrderOutboxRecord(
                storedDraft.accountId,
                storedDraft.organizationId,
                storedDraft.storeId,
                storedDraft.clientOrderId,
              )
            : null
          const resolvedDraft = resolveLocalDraftForOpen(
            localScope,
            localContext,
            offlineIdentity.menuRevision,
            storedDraft,
            pendingSubmission?.state ?? null,
          )
          if (resolvedDraft !== storedDraft) {
            cached = await saveLocalDraft(resolvedDraft)
            pendingSubmission = null
          } else {
            cached = storedDraft
          }
          if (!active) return
          if (!cached) throw new Error('本机草稿初始化失败')
          localDraftRef.current = cached
          setLocalDraftId(cached.localDraftId)
          setClientOrderId(cached.clientOrderId)
          setLastLocalSavedAt(cached.updatedAt)
          setOrder(cached.serverOrderSnapshot)
          setStagedItems(cached.items)
          setLoading(false)
          if (active) setOutboxRecord(pendingSubmission)
        } catch (storageError) {
          if (active) {
            setPersistenceError(storageError instanceof Error ? storageError.message : '本机草稿存储不可用')
          }
        }
      }

      try {
        const resolvedOrder = await findEditableOrderByContext({
          storeId,
          orderType,
          tableNo: orderType === 'dine_in' ? slotLabel : null,
          pickupNo: orderType === 'pickup' ? (pickupLabel ?? slotLabel) : null,
        })
        if (!active) return
        if (!resolvedOrder) {
          setOrder(null)
          setStagedItems(cached?.items ?? [])
          return
        }
        const serverUpdatedAt = new Date(resolvedOrder.updated_at).getTime()
        const localUpdatedAt = cached ? new Date(cached.updatedAt).getTime() : 0
        const keepLocalItems = Boolean(
          cached
          && cached.serverOrderId === resolvedOrder.id
          && cached.items.length > 0
          && (cached.mode === 'SERVER_ORDER_UPDATE' || localUpdatedAt > serverUpdatedAt),
        )
        const nextItems = keepLocalItems ? cached!.items : mapServerItems(resolvedOrder)
        setOrder(resolvedOrder)
        setStagedItems(nextItems)
        if (resolvedOrder.status !== 'draft') setOutboxRecord(null)

        if (cached) {
          localDraftRef.current = {
            ...cached,
            context: localContext,
            mode: resolvedOrder.status === 'draft' ? 'LOCAL_NEW_ORDER' : 'SERVER_ORDER_UPDATE',
            serverOrderId: resolvedOrder.id,
            serverOrderSnapshot: resolvedOrder,
            items: nextItems,
            menuRevision: offlineIdentity.menuRevision,
            lastError: null,
          }
          void saveLocalDraft(localDraftRef.current)
            .then((saved) => {
              localDraftRef.current = saved
              if (active) setLastLocalSavedAt(saved.updatedAt)
            })
            .catch((storageError) => {
              if (active) setPersistenceError(storageError instanceof Error ? storageError.message : '本机草稿保存失败')
            })
        }
      } catch (loadError) {
        if (!active) return
        if (cached) {
          setOrder(cached.serverOrderSnapshot)
          setStagedItems(cached.items)
          setError('网络暂不可用，已恢复本机草稿；尚未同步的内容只保存在本机。')
        } else {
          setError(loadError instanceof Error ? loadError.message : 'Failed to open draft order')
        }
      } finally {
        if (active) setLoading(false)
      }
    }

    void load()
    return () => {
      active = false
    }
  }, [contextKey, localContext, localScope, mapServerItems, offlineIdentity.menuRevision, orderType, pickupLabel, slotLabel, storeId])

  const runMutation = useCallback(async (operation: () => Promise<BackendOrderResponse>) => {
    setSaving(true)
    setError(null)
    try {
      const nextOrder = await operation()
      setOrder(nextOrder)
      setStagedItems(mapServerItems(nextOrder))
      return nextOrder
    } catch (mutationError) {
      const message = mutationError instanceof Error ? mutationError.message : 'Draft order update failed'
      setError(message)
      throw mutationError
    } finally {
      setSaving(false)
    }
  }, [mapServerItems])

  const session = useMemo(() => {
    const baseSession = order
      ? mapOrderToSession(order, slotLabel, tableLabel, catalogItems)
      : localDraftId
        ? {
            orderId: localDraftId,
            slotLabel,
            tableLabel,
            status: 'draft' as const,
            isModifiedAfterSubmit: false,
            items: [] as OrderLineItem[],
          }
        : null
    if (!baseSession) return null
    const effectiveItems = stagedItems ?? baseSession.items
    const dirty = effectiveItems.some((item) => item.id.startsWith('temp-'))
    return {
      ...baseSession,
      isModifiedAfterSubmit: dirty || baseSession.isModifiedAfterSubmit,
      items: effectiveItems,
    }
  }, [catalogItems, localDraftId, order, slotLabel, stagedItems, tableLabel])

  useEffect(() => {
    if (!session || !localDraftRef.current) return
    localDraftRef.current = {
      ...localDraftRef.current,
      context: localContext,
      mode: !order || order.status === 'draft' ? 'LOCAL_NEW_ORDER' : 'SERVER_ORDER_UPDATE',
      serverOrderId: order?.id ?? null,
      serverOrderSnapshot: order,
      items: session.items,
      menuRevision: offlineIdentity.menuRevision,
    }
    if (saveTimerRef.current != null) window.clearTimeout(saveTimerRef.current)
    saveTimerRef.current = window.setTimeout(() => {
      void flushLocalDraftRef.current()
    }, LOCAL_DRAFT_SAVE_DEBOUNCE_MS)
    return () => {
      if (saveTimerRef.current != null) window.clearTimeout(saveTimerRef.current)
    }
  }, [offlineIdentity.menuRevision, localContext, order, session])

  const flushLocalDraft = useCallback(async () => {
    if (saveTimerRef.current != null) {
      window.clearTimeout(saveTimerRef.current)
      saveTimerRef.current = null
    }
    if (!localDraftRef.current) return
    const draftToSave = localDraftRef.current
    try {
      const saved = await saveLocalDraftIfClientMatches(draftToSave, draftToSave.clientOrderId)
      if (!saved || localDraftRef.current?.clientOrderId !== draftToSave.clientOrderId) return
      localDraftRef.current = saved
      setLastLocalSavedAt(saved.updatedAt)
      setPersistenceError(null)
    } catch (storageError) {
      const message = storageError instanceof Error ? storageError.message : '本机草稿保存失败'
      setPersistenceError(message)
      throw storageError
    }
  }, [])
  flushLocalDraftRef.current = flushLocalDraft

  useEffect(() => {
    const flush = () => {
      void flushLocalDraftRef.current().catch(() => undefined)
    }
    const handleVisibility = () => {
      if (document.visibilityState === 'hidden') flush()
    }
    window.addEventListener('pagehide', flush)
    document.addEventListener('visibilitychange', handleVisibility)
    return () => {
      window.removeEventListener('pagehide', flush)
      document.removeEventListener('visibilitychange', handleVisibility)
    }
  }, [])

  useEffect(() => {
    const handleOutboxUpdate = (event: Event) => {
      const record = (event as CustomEvent<OrderOutboxRecord>).detail
      if (!record || record.clientOrderId !== localDraftRef.current?.clientOrderId) return
      setOutboxRecord(record)
      const currentDraft = localDraftRef.current
      if (currentDraft) {
        const updatedDraft = {
          ...currentDraft,
          submitState: record.state,
          lastError: record.lastErrorMessage,
          nextRetryAt: record.nextRetryAt,
          serverOrderId: record.serverOrderId ?? currentDraft.serverOrderId,
        }
        if (record.state === 'SUBMITTED' && localScope) {
          if (saveTimerRef.current != null) {
            window.clearTimeout(saveTimerRef.current)
            saveTimerRef.current = null
          }
          localDraftRef.current = null
          void deleteLocalDraftIfClientMatches(
            localScope,
            contextKey,
            currentDraft.clientOrderId,
          ).catch(() => undefined)
        } else {
          localDraftRef.current = updatedDraft
          void saveLocalDraftIfClientMatches(updatedDraft, currentDraft.clientOrderId)
            .then((saved) => {
              if (!saved || localDraftRef.current?.clientOrderId !== currentDraft.clientOrderId) return
              localDraftRef.current = saved
              setLastLocalSavedAt(saved.updatedAt)
            })
            .catch(() => undefined)
        }
      }
      if (record.state === 'SUBMITTED' && record.serverOrderId) {
        void fetchOrderDetail(record.serverOrderId)
          .then((submittedOrder) => {
            setOrder(submittedOrder)
            setStagedItems(mapServerItems(submittedOrder))
          })
          .catch(() => undefined)
      }
    }
    window.addEventListener(ORDER_OUTBOX_UPDATED_EVENT, handleOutboxUpdate)
    return () => window.removeEventListener(ORDER_OUTBOX_UPDATED_EVENT, handleOutboxUpdate)
  }, [contextKey, localScope, mapServerItems])

  const isDraftOrder = !order || order.status === 'draft'
  const syncStagedItems = (updater: (items: OrderLineItem[]) => OrderLineItem[]) => {
    setStagedItems((current) => updater(current ?? []))
    return Promise.resolve(order)
  }

  const draftSubmissionLocked = Boolean(isDraftOrder && outboxRecord && ![
    'LOCAL_DRAFT',
    'CANCELLED_LOCAL',
  ].includes(outboxRecord.state))

  const requireLocalDraftEditable = () => {
    if (!draftSubmissionLocked) return
    const message = '订单已进入待提交队列。如需修改，请先选择“返回修改”。'
    setError(message)
    throw new Error(message)
  }

  const removeLocalRecord = async () => {
    if (!localScope) return
    if (saveTimerRef.current != null) window.clearTimeout(saveTimerRef.current)
    await deleteLocalDraft(localScope, contextKey)
    localDraftRef.current = null
  }

  return {
    order,
    session,
    loading,
    saving,
    error,
    persistenceError,
    localDraftId,
    clientOrderId,
    lastLocalSavedAt,
    localDraftMode: localDraftRef.current?.mode ?? 'LOCAL_NEW_ORDER',
    localSubmitState: outboxRecord?.state ?? localDraftRef.current?.submitState ?? 'LOCAL_DRAFT',
    outboxRecord,
    draftSubmissionLocked,
    saveLocalDraftNow: flushLocalDraft,
    addItem: async (menuItem: MenuItem, draft: ItemCustomizationDraft) => {
      requireLocalDraftEditable()
      if (isDraftOrder) return syncStagedItems((items) => [...items, buildLocalLineItem(menuItem, draft)])
      return syncStagedItems((items) => [...items, buildLocalLineItem(menuItem, draft)])
    },
    updateItem: async (itemId: string | number, draft: ItemCustomizationDraft) => {
      requireLocalDraftEditable()
      return syncStagedItems((items) => items.map((item) => {
        if (item.id !== String(itemId) || item.locked) return item
        const menuItem = catalogItems.find((candidate) => candidate.id === item.menuItemId)
        if (!menuItem) return item
        return { ...buildLocalLineItem(menuItem, draft), id: item.id }
      }))
    },
    updateItemNote: async (itemId: string | number, notes: string) => {
      requireLocalDraftEditable()
      const itemKey = String(itemId)
      return syncStagedItems((items) => items.map((item) => (
        item.id === itemKey && !item.locked ? { ...item, notes, selection: { ...item.selection, notes } } : item
      )))
    },
    incrementItem: async (itemId: string | number, currentQuantity: number) => {
      requireLocalDraftEditable()
      return syncStagedItems((items) => items.map((item) => {
        if (item.id !== String(itemId) || item.locked) return item
        const nextSelection = { ...item.selection, quantity: currentQuantity + 1 }
        return {
          ...item,
          quantity: currentQuantity + 1,
          selection: nextSelection,
          lineSubtotal: calculateDraftLineSubtotal(
            catalogItems.find((catalogItem) => catalogItem.id === item.menuItemId),
            nextSelection,
          ),
        }
      }))
    },
    decrementItem: async (itemId: string | number, currentQuantity: number) => {
      requireLocalDraftEditable()
      return syncStagedItems((items) => {
        const target = items.find((item) => item.id === String(itemId))
        if (target?.locked) return items
        if (currentQuantity <= 1) return items.filter((item) => item.id !== String(itemId))
        return items.map((item) => {
          if (item.id !== String(itemId)) return item
          const nextSelection = { ...item.selection, quantity: currentQuantity - 1 }
          return {
            ...item,
            quantity: currentQuantity - 1,
            selection: nextSelection,
            lineSubtotal: calculateDraftLineSubtotal(
              catalogItems.find((catalogItem) => catalogItem.id === item.menuItemId),
              nextSelection,
            ),
          }
        })
      })
    },
    removeItem: async (itemId: string | number) => {
      requireLocalDraftEditable()
      return syncStagedItems((items) => items.filter((item) => item.id !== String(itemId) || item.locked))
    },
    cancelOrder: async () => {
      if (outboxRecord?.state === 'SUBMITTING') {
        const message = '订单正在提交，暂时不能取消本机记录。请等待服务器确认。'
        setError(message)
        throw new Error(message)
      }
      if (outboxRecord) {
        await saveOrderOutboxRecord({
          ...outboxRecord,
          state: 'CANCELLED_LOCAL',
          nextRetryAt: null,
        })
      }
      if (order?.status === 'draft') {
        await cancelDraftOrder(order.id).catch(() => undefined)
      }
      await removeLocalRecord()
      setStagedItems([])
      setOutboxRecord(null)
      return order
    },
    submitOrder: async () => {
      if (submitInFlightRef.current) return null
      if (!session?.items.length) {
        setError('订单至少需要一个菜品。')
        return null
      }
      if (localDraftRef.current) {
        localDraftRef.current = { ...localDraftRef.current, items: session.items }
      }
      await flushLocalDraft().catch(() => undefined)
      const submitStartedAtMs = Date.now()
      const submitStartedAt = new Date(submitStartedAtMs).toISOString()
      const recordSubmit = (stage: 'STARTED' | 'SUCCEEDED' | 'FAILED', submitError?: unknown) => {
        recordAppOperation({
          operation: 'ORDER_SUBMIT',
          stage,
          storeId,
          startedAt: submitStartedAt,
          completedAt: stage === 'STARTED' ? null : new Date().toISOString(),
          latencyMs: stage === 'STARTED' ? null : Date.now() - submitStartedAtMs,
          errorCode: submitError instanceof ApiRequestError
            ? (submitError.code ?? `HTTP_${submitError.status}`)
            : submitError ? 'ORDER_SUBMIT_FAILED' : null,
        })
      }
      recordSubmit('STARTED')
      submitInFlightRef.current = true
      if (!isDraftOrder) {
        const newItems = (stagedItems ?? []).filter((item) => item.id.startsWith('temp-'))
        if (!newItems.length) {
          setError('No new items to update')
          recordSubmit('FAILED', new Error('No new items to update'))
          submitInFlightRef.current = false
          return order
        }
        setSaving(true)
        setError(null)
        try {
          const idempotencyKey = updateIdempotencyKeyRef.current ?? createIdempotencyKey('order-update')
          updateIdempotencyKeyRef.current = idempotencyKey
          const result = await submitOrderUpdate(order.id, idempotencyKey, newItems, catalogItems)
          const refreshed = result.order
          setOrder(refreshed)
          setStagedItems(mapServerItems(refreshed))
          updateIdempotencyKeyRef.current = null
          recordSubmit('SUCCEEDED')
          return refreshed
        } catch (mutationError) {
          setError(mutationError instanceof Error ? mutationError.message : 'Order update failed')
          recordSubmit('FAILED', mutationError)
          throw mutationError
        } finally {
          setSaving(false)
          submitInFlightRef.current = false
        }
      }
      setSaving(true)
      setError(null)
      try {
        const draft = localDraftRef.current
        if (!draft) throw new Error('本机草稿尚未准备完成，请稍后重试。')
        if (draft.menuRevision <= 0) throw new Error('菜单版本不可用，请先重新加载菜单。')

        let queued = await readOrderOutboxRecord(
          draft.accountId,
          draft.organizationId,
          draft.storeId,
          draft.clientOrderId,
        )
        if (queued?.state === 'SUBMITTED' && queued.serverOrderId) {
          const submittedOrder = await fetchOrderDetail(queued.serverOrderId)
          setOrder(submittedOrder)
          setStagedItems(mapServerItems(submittedOrder))
          recordSubmit('SUCCEEDED')
          return submittedOrder
        }
        if (queued?.state === 'CONFLICT' || queued?.state === 'FAILED_VALIDATION') {
          setOutboxRecord(queued)
          setError(queued.lastErrorMessage ?? '服务器未接受该订单，请返回修改并重新检查。')
          recordSubmit('FAILED', new Error(queued.lastErrorCode ?? 'ORDER_SUBMIT_CONFLICT'))
          return null
        }
        if (!queued || queued.state === 'CANCELLED_LOCAL' || queued.state === 'LOCAL_DRAFT') {
          queued = createOrderOutboxRecord(
            draft,
            buildFrozenSubmitPayload(draft, session.items, catalogItems),
          )
        }
        localDraftRef.current = await saveLocalDraft({
          ...draft,
          items: session.items,
          submitState: 'QUEUED',
          payloadHash: queued.payloadHash,
          lastError: null,
          nextRetryAt: queued.nextRetryAt,
        })
        queued = await saveOrderOutboxRecord(queued)
        setOutboxRecord(queued)

        const result = await processOrderOutboxRecord(queued)
        if (result.record) setOutboxRecord(result.record)
        if (result.order) {
          setOrder(result.order)
          setStagedItems(mapServerItems(result.order))
          recordSubmit('SUCCEEDED')
          return result.order
        }
        kickOrderOutboxProcessor()
        if (result.record?.state === 'CONFLICT' || result.record?.state === 'FAILED_VALIDATION') {
          setError(result.record.lastErrorMessage ?? '订单未被服务器接受，请人工检查。')
          recordSubmit('FAILED', new Error(result.record.lastErrorCode ?? 'ORDER_SUBMIT_CONFLICT'))
        } else {
          recordSubmit('FAILED', new Error(result.record?.lastErrorCode ?? 'ORDER_QUEUED'))
        }
        return null
      } catch (mutationError) {
        setError(mutationError instanceof Error ? mutationError.message : '订单加入本机提交队列失败')
        recordSubmit('FAILED', mutationError)
        throw mutationError
      } finally {
        setSaving(false)
        submitInFlightRef.current = false
      }
    },
    retryQueuedOrder: async () => {
      if (!outboxRecord || outboxRecord.state === 'SUBMITTED') return outboxRecord
      const queued = await saveOrderOutboxRecord({
        ...outboxRecord,
        state: 'QUEUED',
        nextRetryAt: new Date().toISOString(),
        lastErrorCode: null,
        lastErrorMessage: null,
      })
      setOutboxRecord(queued)
      kickOrderOutboxProcessor()
      return queued
    },
    returnQueuedOrderToDraft: async () => {
      if (!outboxRecord || !localDraftRef.current) return
      const latest = await readOrderOutboxRecord(
        outboxRecord.accountId,
        outboxRecord.organizationId,
        outboxRecord.storeId,
        outboxRecord.clientOrderId,
      )
      if (!latest) return
      if (latest.state === 'SUBMITTING' || latest.state === 'FAILED_RETRYABLE') {
        const message = '服务器是否已接单尚未确认，不能修改或取消。请先立即重试以确认原订单。'
        setError(message)
        throw new Error(message)
      }
      const cancelled = await saveOrderOutboxRecord({
        ...latest,
        state: 'CANCELLED_LOCAL',
        nextRetryAt: null,
      })
      setOutboxRecord(cancelled)
      const shouldRotateIdentity = latest.state === 'CONFLICT' || latest.state === 'FAILED_VALIDATION'
      const editableDraft = shouldRotateIdentity
        ? reopenRejectedLocalDraft(localDraftRef.current)
        : {
            ...localDraftRef.current,
            submitState: 'LOCAL_DRAFT' as const,
            lastError: null,
            nextRetryAt: null,
          }
      localDraftRef.current = await saveLocalDraft(editableDraft)
      setLocalDraftId(localDraftRef.current.localDraftId)
      setClientOrderId(localDraftRef.current.clientOrderId)
      if (shouldRotateIdentity) setOutboxRecord(null)
      setError(null)
    },
    refreshOrder: async () => {
      if (!order) {
        kickOrderOutboxProcessor()
        return null
      }
      return runMutation(() => fetchOrderDetail(order.id))
    },
    updateHeader: async (nextPickupLabel: string | null) => {
      if (isDraftOrder) {
        requireLocalDraftEditable()
        if (localDraftRef.current) {
          localDraftRef.current = {
            ...localDraftRef.current,
            context: {
              ...localDraftRef.current.context,
              pickupNo: orderType === 'pickup' ? nextPickupLabel : null,
            },
          }
          await flushLocalDraft()
        }
        return order
      }
      if (!order) return null
      return runMutation(() => updateEditableOrderHeader(order.id, {
        orderType,
        tableNo: orderType === 'dine_in' ? slotLabel : null,
        pickupNo: orderType === 'pickup' ? nextPickupLabel : null,
      }))
    },
  }
}
