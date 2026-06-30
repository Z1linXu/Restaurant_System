import { useState } from 'react'
import type {
  ChoiceOption,
  ItemCustomizationDraft,
  LocalizedText,
  MenuItem,
  OrderLineItem,
  OrderSession,
} from '../types/ordering'
import { calculateTax, calculateTotal } from '../utils/tax'

function getChoiceLabel(options: ChoiceOption[] | undefined, optionId: string | undefined): LocalizedText | null {
  if (!options || !optionId) {
    return null
  }

  const option = options.find((item) => item.id === optionId)
  return option ? { en: option.labelEn, zh: option.labelZh } : null
}

function getSelectedOptions(options: ChoiceOption[] | undefined, optionIds: string[]) {
  if (!options?.length || !optionIds.length) {
    return []
  }

  return options
    .filter((option) => optionIds.includes(option.id))
    .map((option) => ({ en: option.labelEn, zh: option.labelZh }))
}

function getSelectedAddOns(options: ChoiceOption[] | undefined, addOnQuantities: Record<string, number>) {
  if (!options?.length) {
    return []
  }

  return options
    .filter((option) => (addOnQuantities[option.id] ?? 0) > 0)
    .map((option) => {
      const quantity = addOnQuantities[option.id] ?? 0
      return {
        en: quantity > 1 ? `${option.labelEn} x${quantity}` : option.labelEn,
        zh: quantity > 1 ? `${option.labelZh} x${quantity}` : option.labelZh,
      }
    })
}

export function buildDefaultDraft(menuItem: MenuItem): ItemCustomizationDraft {
  return {
    sizeId: menuItem.customization?.sizes?.options[0]?.id,
    soupBaseId: menuItem.customization?.soupBases?.options[0]?.id,
    noodleTypeId: menuItem.customization?.noodleTypes?.[0]?.id,
    spicyLevelId: menuItem.customization?.spicyLevels?.[0]?.id,
    comboEnabled: false,
    comboEggId: menuItem.customization?.combo?.eggs[0]?.id,
    comboSideId: menuItem.customization?.combo?.sides[0]?.id,
    comboSideRemoveIds: [],
    addOnQuantities: {},
    removeIds: [],
    quantity: 1,
  }
}

function sumPrice(options: ChoiceOption[] | undefined, selectedIds: string[]) {
  if (!options?.length || !selectedIds.length) {
    return 0
  }

  return options
    .filter((option) => selectedIds.includes(option.id))
    .reduce((sum, option) => sum + (option.priceDelta ?? 0), 0)
}

function sumAddOnPrice(options: ChoiceOption[] | undefined, addOnQuantities: Record<string, number>) {
  if (!options?.length) {
    return 0
  }

  return options.reduce((sum, option) => sum + (option.priceDelta ?? 0) * (addOnQuantities[option.id] ?? 0), 0)
}

function calculateUnitPrice(menuItem: MenuItem, draft: ItemCustomizationDraft) {
  const sizePrice =
    menuItem.customization?.sizes?.options.find((option) => option.id === draft.sizeId)?.priceDelta ?? 0
  const soupBasePrice =
    menuItem.customization?.soupBases?.options.find((option) => option.id === draft.soupBaseId)?.priceDelta ?? 0
  const comboPrice = draft.comboEnabled ? (menuItem.customization?.combo?.upcharge ?? 0) : 0
  const addOnPrice = sumAddOnPrice(menuItem.customization?.addOns, draft.addOnQuantities)
  const removeOptionPrice = sumPrice(menuItem.customization?.removeOptions, draft.removeIds)
  const comboSideRemovePrice = draft.comboEnabled
    ? sumPrice(menuItem.customization?.combo?.sideRemoveOptions, draft.comboSideRemoveIds)
    : 0

  return menuItem.price + sizePrice + soupBasePrice + comboPrice + addOnPrice + removeOptionPrice + comboSideRemovePrice
}

function buildSummaryTags(menuItem: MenuItem, draft: ItemCustomizationDraft) {
  const summaryTags: LocalizedText[] = []
  const customization = menuItem.customization

  const sizeLabel = getChoiceLabel(customization?.sizes?.options, draft.sizeId)
  const soupBaseLabel = getChoiceLabel(customization?.soupBases?.options, draft.soupBaseId)
  const noodleTypeLabel = getChoiceLabel(customization?.noodleTypes, draft.noodleTypeId)
  const spicyLabel = getChoiceLabel(customization?.spicyLevels, draft.spicyLevelId)

  if (sizeLabel) {
    summaryTags.push(sizeLabel)
  }

  if (soupBaseLabel) {
    summaryTags.push(soupBaseLabel)
  }

  if (noodleTypeLabel) {
    summaryTags.push(noodleTypeLabel)
  }

  if (spicyLabel) {
    summaryTags.push(spicyLabel)
  }

  if (draft.comboEnabled) {
    summaryTags.push({ en: 'Combo', zh: '套餐' })

    const eggLabel = getChoiceLabel(customization?.combo?.eggs, draft.comboEggId)
    const sideLabel = getChoiceLabel(customization?.combo?.sides, draft.comboSideId)
    const sideRemoveLabels = getSelectedOptions(customization?.combo?.sideRemoveOptions, draft.comboSideRemoveIds)

    if (eggLabel) {
      summaryTags.push(eggLabel)
    }

    if (sideLabel) {
      summaryTags.push(sideLabel)
    }
    summaryTags.push(...sideRemoveLabels)
  }

  summaryTags.push(...getSelectedAddOns(customization?.addOns, draft.addOnQuantities))
  summaryTags.push(...getSelectedOptions(customization?.removeOptions, draft.removeIds))

  return summaryTags
}

function buildLineItem(menuItem: MenuItem, draft: ItemCustomizationDraft, itemId?: string): OrderLineItem {
  const unitPrice = calculateUnitPrice(menuItem, draft)
  const summaryTags = buildSummaryTags(menuItem, draft)

  return {
    id: itemId ?? `item-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    menuItemId: menuItem.id,
    nameEn: menuItem.nameEn,
    nameZh: menuItem.nameZh,
    quantity: draft.quantity,
    unitPrice,
    lineSubtotal: unitPrice * draft.quantity,
    selection: draft,
    summaryTags,
    notes: '',
  }
}

export function calculateTotals(session: OrderSession | null) {
  const subtotal = session?.items.reduce((sum, item) => sum + item.lineSubtotal, 0) ?? 0
  const tax = calculateTax(subtotal)
  const total = calculateTotal(subtotal)

  return { subtotal, tax, total }
}

export function useOrderSessions(menuItems: MenuItem[]) {
  const [sessions, setSessions] = useState<OrderSession[]>([])

  const ensureSession = (orderId: string, slotLabel: string, tableLabel: string) => {
    setSessions((current) => {
      const existing = current.find((session) => session.orderId === orderId)
      if (existing) {
        return current
      }

      return [
        ...current,
        {
          orderId,
          slotLabel,
          tableLabel,
          status: 'draft',
          isModifiedAfterSubmit: false,
          items: [],
        },
      ]
    })
  }

  const getSession = (orderId: string) => sessions.find((session) => session.orderId === orderId) ?? null

  const addOrUpdateItem = (orderId: string, menuItemId: string, draft: ItemCustomizationDraft, editingItemId?: string) => {
    const menuItem = menuItems.find((item) => item.id === menuItemId) ?? null
    if (!menuItem) {
      return
    }

    const nextItem = buildLineItem(menuItem, draft, editingItemId)

    setSessions((current) =>
      current.map((session) =>
        session.orderId === orderId
          ? {
              ...session,
              items: editingItemId
                ? session.items.map((item) => (item.id === editingItemId ? nextItem : item))
                : [...session.items, nextItem],
            }
          : session,
      ),
    )
  }

  const incrementItem = (orderId: string, itemId: string) => {
    setSessions((current) =>
      current.map((session) =>
        session.orderId === orderId
          ? {
              ...session,
              items: session.items.map((item) =>
                item.id === itemId
                  ? {
                      ...item,
                      quantity: item.quantity + 1,
                      selection: { ...item.selection, quantity: item.quantity + 1 },
                      lineSubtotal: item.unitPrice * (item.quantity + 1),
                    }
                  : item,
              ),
            }
          : session,
      ),
    )
  }

  const decrementItem = (orderId: string, itemId: string) => {
    setSessions((current) =>
      current.map((session) =>
        session.orderId === orderId
          ? {
              ...session,
              items: session.items.map((item) =>
                item.id === itemId
                  ? {
                      ...item,
                      quantity: Math.max(1, item.quantity - 1),
                      selection: { ...item.selection, quantity: Math.max(1, item.quantity - 1) },
                      lineSubtotal: item.unitPrice * Math.max(1, item.quantity - 1),
                    }
                  : item,
              ),
            }
          : session,
      ),
    )
  }

  const removeItem = (orderId: string, itemId: string) => {
    setSessions((current) =>
      current.map((session) =>
        session.orderId === orderId
          ? {
              ...session,
              items: session.items.filter((item) => item.id !== itemId),
            }
          : session,
      ),
    )
  }

  const saveDraft = (orderId: string) => {
    setSessions((current) =>
      current.map((session) =>
        session.orderId === orderId
          ? {
              ...session,
              status: 'draft',
            }
          : session,
      ),
    )
  }

  const submitOrder = (orderId: string) => {
    setSessions((current) =>
      current.map((session) =>
        session.orderId === orderId
          ? {
              ...session,
              status: 'submitted',
            }
          : session,
      ),
    )
  }

  const cancelOrder = (orderId: string) => {
    setSessions((current) => current.filter((session) => session.orderId !== orderId))
  }

  return {
    sessions,
    getSession,
    ensureSession,
    addOrUpdateItem,
    incrementItem,
    decrementItem,
    removeItem,
    saveDraft,
    submitOrder,
    cancelOrder,
    buildDefaultDraft,
  }
}
