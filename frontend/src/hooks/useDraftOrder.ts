import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  addDraftOrderItem,
  cancelDraftOrder,
  ensureEditableOrder,
  fetchOrderDetail,
  removeDraftOrderItem,
  submitDraftOrder,
  submitOrderUpdate,
  updateEditableOrderHeader,
  updateDraftOrderItemWithMenuItem,
  updateDraftOrderItemQuantity,
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
import { normalizeComboDraft, resolveComboSelection, resolveComboUpcharge } from '../utils/comboSelection'
import { resolveNoodleTypeId } from '../utils/noodleTypeDefaults'
import {
  buildDraftContextKey,
  cleanupExpiredLocalDrafts,
  createLocalDraftRecord,
  deleteLocalDraft,
  readLocalDraft,
  saveLocalDraft,
  type LocalDraftContext,
  type LocalDraftRecord,
  type LocalDraftScope,
} from '../offline/localDrafts'

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
    pickupNo: orderType === 'pickup' ? pickupLabel : null,
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
    localDraftRef.current = null

    const load = async () => {
      let cached: LocalDraftRecord | null = null
      if (localScope) {
        try {
          void cleanupExpiredLocalDrafts().catch(() => undefined)
          cached = await readLocalDraft(localScope, contextKey)
          if (!cached) {
            cached = createLocalDraftRecord(localScope, localContext, offlineIdentity.menuRevision)
            cached = await saveLocalDraft(cached)
          }
          if (!active) return
          localDraftRef.current = cached
          setLocalDraftId(cached.localDraftId)
          setClientOrderId(cached.clientOrderId)
          setLastLocalSavedAt(cached.updatedAt)
          if (cached.serverOrderSnapshot) {
            setOrder(cached.serverOrderSnapshot)
            setStagedItems(cached.items)
            setLoading(false)
          }
        } catch (storageError) {
          if (active) {
            setPersistenceError(storageError instanceof Error ? storageError.message : '本机草稿存储不可用')
          }
        }
      }

      try {
        const resolvedOrder = await ensureEditableOrder({
          storeId,
          orderType,
          tableNo: orderType === 'dine_in' ? slotLabel : null,
          pickupNo: orderType === 'pickup' ? pickupLabel : null,
        })
        if (!active) return
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
        if (cached?.serverOrderSnapshot) {
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
    if (!order) return null
    const baseSession = mapOrderToSession(order, slotLabel, tableLabel, catalogItems)
    const effectiveItems = stagedItems ?? baseSession.items
    const dirty = effectiveItems.some((item) => item.id.startsWith('temp-'))
    return {
      ...baseSession,
      isModifiedAfterSubmit: dirty || baseSession.isModifiedAfterSubmit,
      items: effectiveItems,
    }
  }, [catalogItems, order, slotLabel, stagedItems, tableLabel])

  useEffect(() => {
    if (!session || !localDraftRef.current) return
    localDraftRef.current = {
      ...localDraftRef.current,
      context: localContext,
      mode: order?.status === 'draft' ? 'LOCAL_NEW_ORDER' : 'SERVER_ORDER_UPDATE',
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
    try {
      const saved = await saveLocalDraft(localDraftRef.current)
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

  const isDraftOrder = order?.status === 'draft'
  const syncStagedItems = (updater: (items: OrderLineItem[]) => OrderLineItem[]) => {
    setStagedItems((current) => updater(current ?? []))
    return Promise.resolve(order)
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
    localSubmitState: localDraftRef.current?.submitState ?? 'LOCAL_DRAFT',
    saveLocalDraftNow: flushLocalDraft,
    addItem: async (menuItem: MenuItem, draft: ItemCustomizationDraft) => {
      if (!order) return null
      if (!isDraftOrder) return syncStagedItems((items) => [...items, buildLocalLineItem(menuItem, draft)])
      return runMutation(() => addDraftOrderItem(order.id, menuItem, draft, draft.notes))
    },
    updateItem: async (itemId: string | number, draft: ItemCustomizationDraft) => {
      if (!order) return null
      if (!isDraftOrder) {
        return syncStagedItems((items) => items.map((item) => {
          if (item.id !== String(itemId) || item.locked) return item
          return {
            ...item,
            quantity: draft.quantity,
            selection: draft,
            notes: draft.notes,
            lineSubtotal: calculateDraftLineSubtotal(
              catalogItems.find((catalogItem) => catalogItem.id === item.menuItemId),
              draft,
            ),
          }
        }))
      }
      const numericItemId = Number(itemId)
      const targetItem = order.items.find((item) => item.id === numericItemId)
      const menuItem = catalogItems.find((item) => item.id === String(targetItem?.menu_item_id))
      return runMutation(() => updateDraftOrderItemWithMenuItem(order.id, numericItemId, menuItem, draft, draft.notes))
    },
    updateItemNote: async (itemId: string | number, notes: string) => {
      if (!order) return null
      const itemKey = String(itemId)
      if (!isDraftOrder) {
        return syncStagedItems((items) => items.map((item) => (
          item.id === itemKey && !item.locked ? { ...item, notes, selection: { ...item.selection, notes } } : item
        )))
      }
      const numericItemId = Number(itemId)
      const targetItem = order.items.find((item) => item.id === numericItemId)
      if (!targetItem) return null
      const menuItem = catalogItems.find((item) => item.id === String(targetItem.menu_item_id))
      const selection = { ...buildItemSelection(targetItem, menuItem), notes }
      return runMutation(() => updateDraftOrderItemWithMenuItem(order.id, numericItemId, menuItem, selection, notes))
    },
    incrementItem: async (itemId: string | number, currentQuantity: number) => {
      if (!order) return null
      if (!isDraftOrder) {
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
      }
      return runMutation(() => updateDraftOrderItemQuantity(order.id, Number(itemId), currentQuantity + 1))
    },
    decrementItem: async (itemId: string | number, currentQuantity: number) => {
      if (!order) return null
      if (!isDraftOrder) {
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
      }
      if (currentQuantity <= 1) return runMutation(() => removeDraftOrderItem(order.id, Number(itemId)))
      return runMutation(() => updateDraftOrderItemQuantity(order.id, Number(itemId), currentQuantity - 1))
    },
    removeItem: async (itemId: string | number) => {
      if (!order) return null
      if (!isDraftOrder) return syncStagedItems((items) => items.filter((item) => item.id !== String(itemId) || item.locked))
      return runMutation(() => removeDraftOrderItem(order.id, Number(itemId)))
    },
    cancelOrder: async () => {
      if (!order) return null
      const cancelled = await runMutation(() => cancelDraftOrder(order.id))
      await removeLocalRecord()
      return cancelled
    },
    submitOrder: async () => {
      if (!order) return null
      if (submitInFlightRef.current) return order
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
      try {
        const submitted = await runMutation(() => submitDraftOrder(order.id))
        recordSubmit('SUCCEEDED')
        return submitted
      } catch (mutationError) {
        if (mutationError instanceof Error && (
          mutationError.message.includes('Only draft orders can be submitted')
          || mutationError.message.includes('already completed')
        )) {
          try {
            const refreshed = await fetchOrderDetail(order.id)
            setOrder(refreshed)
            setStagedItems(mapServerItems(refreshed))
          } catch {
            // Keep the original submit error visible if refresh also fails.
          }
        }
        recordSubmit('FAILED', mutationError)
        throw mutationError
      } finally {
        submitInFlightRef.current = false
      }
    },
    refreshOrder: async () => {
      if (!order) return null
      return runMutation(() => fetchOrderDetail(order.id))
    },
    updateHeader: async (nextPickupLabel: string | null) => {
      if (!order) return null
      return runMutation(() => updateEditableOrderHeader(order.id, {
        orderType,
        tableNo: orderType === 'dine_in' ? slotLabel : null,
        pickupNo: orderType === 'pickup' ? nextPickupLabel : null,
      }))
    },
  }
}
