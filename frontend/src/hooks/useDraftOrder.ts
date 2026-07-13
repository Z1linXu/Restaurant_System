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
import { normalizeComboDraft, resolveComboSelection, resolveComboUpcharge } from '../utils/comboSelection'
import { resolveNoodleTypeId } from '../utils/noodleTypeDefaults'

function calculateDraftLineSubtotal(menuItem: MenuItem | undefined, draft: ItemCustomizationDraft) {
  if (!menuItem) {
    return 0
  }
  const sizeDelta = menuItem.customization?.sizes?.options.find((option) => option.id === draft.sizeId)?.priceDelta ?? 0
  const soupBaseDelta =
    menuItem.customization?.soupBases?.options.find((option) => option.id === draft.soupBaseId)?.priceDelta ?? 0
  const comboDelta = resolveComboUpcharge(draft, menuItem.customization?.combo)
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
      return
    }
    if (comboConfig?.eggs.some((comboOption) => comboOption.id === optionId)) {
      draft.comboEggId = optionId
      return
    }
    if (comboConfig?.sides.some((comboOption) => comboOption.id === optionId)) {
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
  draft.noodleTypeId = resolveNoodleTypeId(menuItem, draft.noodleTypeId)
  if (!draft.spicyLevelId && menuItem?.customization?.spicyLevels?.[0]?.id) {
    draft.spicyLevelId = menuItem.customization.spicyLevels[0].id
  }
  return normalizeComboDraft(draft)
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
  const comboSelection = resolveComboSelection(draft, menuItem.customization?.combo)
  if (comboSelection.enabled) {
    summaryTags.push({ en: 'Combo', zh: '套餐' })
    if (draft.comboEggId) {
      pushOptionTag(draft.comboEggId)
    } else if (comboSelection.isNoEgg) {
      summaryTags.push({ en: 'No egg', zh: '走蛋' })
    }
    pushOptionTag(draft.comboSideId)
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

export function useDraftOrder(
  storeId: number,
  slotLabel: string,
  tableLabel: string,
  orderType: 'dine_in' | 'pickup',
  pickupLabel: string | null,
  catalogItems: MenuItem[],
) {
  const [order, setOrder] = useState<BackendOrderResponse | null>(null)
  const [stagedItems, setStagedItems] = useState<OrderLineItem[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submitInFlightRef = useRef(false)
  const updateIdempotencyKeyRef = useRef<string | null>(null)

  useEffect(() => {
    let active = true

    const load = async () => {
      setLoading(true)
      setError(null)

      try {
        const resolvedOrder = await ensureEditableOrder({
          storeId,
          orderType,
          tableNo: orderType === 'dine_in' ? slotLabel : null,
          pickupNo: orderType === 'pickup' ? pickupLabel : null,
        })
        if (active) {
          setOrder(resolvedOrder)
        }
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : 'Failed to open draft order')
        }
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void load()

    return () => {
      active = false
    }
  }, [orderType, pickupLabel, slotLabel, storeId])

  useEffect(() => {
    if (!order) {
      setStagedItems(null)
      return
    }
    if (order.status === 'draft') {
      setStagedItems(null)
      return
    }
    const mappedItems = order.items.map((item) => mapOrderItem(item, catalogItems, true))
    setStagedItems(mappedItems)
  }, [catalogItems, order])

  const runMutation = useCallback(async (operation: () => Promise<BackendOrderResponse>) => {
    setSaving(true)
    setError(null)
    try {
      const nextOrder = await operation()
      setOrder(nextOrder)
      return nextOrder
    } catch (mutationError) {
      const message = mutationError instanceof Error ? mutationError.message : 'Draft order update failed'
      setError(message)
      throw mutationError
    } finally {
      setSaving(false)
    }
  }, [])

  const session = useMemo(() => {
    if (!order) {
      return null
    }
    const baseSession = mapOrderToSession(order, slotLabel, tableLabel, catalogItems)
    if (order.status === 'draft' || !stagedItems) {
      return baseSession
    }
    const dirty = stagedItems.some((item) => item.id.startsWith('temp-'))

    return {
      ...baseSession,
      isModifiedAfterSubmit: dirty || baseSession.isModifiedAfterSubmit,
      items: stagedItems,
    }
  }, [catalogItems, order, slotLabel, stagedItems, tableLabel])

  const isDraftOrder = order?.status === 'draft'

  const syncStagedItems = (updater: (items: OrderLineItem[]) => OrderLineItem[]) => {
    setStagedItems((current) => updater(current ?? []))
    return Promise.resolve(order)
  }

  return {
    order,
    session,
    loading,
    saving,
    error,
    addItem: async (menuItem: MenuItem, draft: ItemCustomizationDraft) => {
      if (!order) {
        return null
      }
      if (!isDraftOrder) {
        return syncStagedItems((items) => [...items, buildLocalLineItem(menuItem, draft)])
      }
      return runMutation(() => addDraftOrderItem(order.id, menuItem, draft, draft.notes))
    },
    updateItem: async (itemId: string | number, draft: ItemCustomizationDraft) => {
      if (!order) {
        return null
      }
      if (!isDraftOrder) {
        return syncStagedItems((items) => items.map((item) => {
          if (item.id !== String(itemId) || item.locked) {
            return item
          }
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
      if (!order) {
        return null
      }
      const itemKey = String(itemId)
      if (!isDraftOrder) {
        return syncStagedItems((items) =>
          items.map((item) => item.id === itemKey && !item.locked ? { ...item, notes, selection: { ...item.selection, notes } } : item),
        )
      }
      const numericItemId = Number(itemId)
      const targetItem = order.items.find((item) => item.id === numericItemId)
      if (!targetItem) {
        return null
      }
      const menuItem = catalogItems.find((item) => item.id === String(targetItem.menu_item_id))
      const selection = { ...buildItemSelection(targetItem, menuItem), notes }
      return runMutation(() => updateDraftOrderItemWithMenuItem(order.id, numericItemId, menuItem, selection, notes))
    },
    incrementItem: async (itemId: string | number, currentQuantity: number) => {
      if (!order) {
        return null
      }
      if (!isDraftOrder) {
        return syncStagedItems((items) =>
          items.map((item) => {
            if (item.id !== String(itemId) || item.locked) {
              return item
            }
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
          }),
        )
      }
      return runMutation(() => updateDraftOrderItemQuantity(order.id, Number(itemId), currentQuantity + 1))
    },
    decrementItem: async (itemId: string | number, currentQuantity: number) => {
      if (!order) {
        return null
      }
      if (!isDraftOrder) {
        return syncStagedItems((items) => {
          const target = items.find((item) => item.id === String(itemId))
          if (target?.locked) {
            return items
          }
          if (currentQuantity <= 1) {
            return items.filter((item) => item.id !== String(itemId))
          }
          return items.map((item) => {
            if (item.id !== String(itemId)) {
              return item
            }
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
      if (currentQuantity <= 1) {
        return runMutation(() => removeDraftOrderItem(order.id, Number(itemId)))
      }
      return runMutation(() => updateDraftOrderItemQuantity(order.id, Number(itemId), currentQuantity - 1))
    },
    removeItem: async (itemId: string | number) => {
      if (!order) {
        return null
      }
      if (!isDraftOrder) {
        return syncStagedItems((items) => items.filter((item) => item.id !== String(itemId) || item.locked))
      }
      return runMutation(() => removeDraftOrderItem(order.id, Number(itemId)))
    },
    cancelOrder: async () => {
      if (!order) {
        return null
      }
      return runMutation(() => cancelDraftOrder(order.id))
    },
    submitOrder: async () => {
      if (!order) {
        return null
      }
      if (submitInFlightRef.current) {
        return order
      }
      submitInFlightRef.current = true
      if (!isDraftOrder) {
        const newItems = (stagedItems ?? []).filter((item) => item.id.startsWith('temp-'))
        if (!newItems.length) {
          setError('No new items to update')
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
          setStagedItems(refreshed.items.map((item) => mapOrderItem(item, catalogItems, true)))
          updateIdempotencyKeyRef.current = null
          return refreshed
        } catch (mutationError) {
          const message = mutationError instanceof Error ? mutationError.message : 'Order update failed'
          setError(message)
          throw mutationError
        } finally {
          setSaving(false)
          submitInFlightRef.current = false
        }
      }
      try {
        return await runMutation(() => submitDraftOrder(order.id))
      } catch (mutationError) {
        if (
          mutationError instanceof Error
          && (mutationError.message.includes('Only draft orders can be submitted')
            || mutationError.message.includes('already completed'))
        ) {
          try {
            const refreshed = await fetchOrderDetail(order.id)
            setOrder(refreshed)
          } catch {
            // Keep the original submit error visible if refresh also fails.
          }
        }
        throw mutationError
      } finally {
        submitInFlightRef.current = false
      }
    },
    refreshOrder: async () => {
      if (!order) {
        return null
      }
      return runMutation(() => fetchOrderDetail(order.id))
    },
    updateHeader: async (nextPickupLabel: string | null) => {
      if (!order) {
        return null
      }
      return runMutation(() =>
        updateEditableOrderHeader(order.id, {
          orderType,
          tableNo: orderType === 'dine_in' ? slotLabel : null,
          pickupNo: orderType === 'pickup' ? nextPickupLabel : null,
        }),
      )
    },
  }
}
