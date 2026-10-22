import type { ChoiceOption, ItemCustomizationDraft, MenuItem, OrderLineOptionSnapshot } from '../types/ordering'
import { resolveComboGroupOptionId } from './comboSelection'

interface OrderOptionPayload {
  option_id: number
  quantity: number
  option_type_snapshot?: string | null
  option_code_snapshot?: string | null
  option_group_snapshot?: string | null
  parent_option_id_snapshot?: number | null
  option_name_snapshot_zh?: string
  option_name_snapshot_en?: string
  option_price_snapshot?: number
}

export function mapOptions(draft: ItemCustomizationDraft, menuItem?: MenuItem) {
  const optionPayloads: OrderOptionPayload[] = []
  const optionsById = menuItem ? collectMenuItemOptions(menuItem) : new Map<string, ChoiceOption>()
  const pushedOptionIds = new Set<string>()

  const pushOption = (optionId: string | undefined, quantity = 1) => {
    if (!optionId) {
      return
    }
    if (pushedOptionIds.has(optionId)) {
      return
    }
    pushedOptionIds.add(optionId)
    const option = optionsById.get(optionId)
    const payload: OrderOptionPayload = {
      option_id: Number(optionId),
      quantity,
    }
    if (option) {
      payload.option_type_snapshot = option.optionType ?? null
      payload.option_code_snapshot = option.optionCode ?? null
      payload.option_group_snapshot = option.optionGroup ?? null
      payload.parent_option_id_snapshot = option.parentOptionId == null ? null : Number(option.parentOptionId)
      payload.option_name_snapshot_zh = option.labelZh
      payload.option_name_snapshot_en = option.labelEn
      payload.option_price_snapshot = option.priceDelta ?? 0
    }
    optionPayloads.push(payload)
  }

  pushOption(draft.sizeId)
  pushOption(draft.soupBaseId)
  pushOption(draft.noodleTypeId)
  pushOption(draft.spicyLevelId)

  if (draft.comboEnabled) {
    pushOption(menuItem?.customization?.combo?.optionId)
    const comboGroups = menuItem?.customization?.combo?.groups ?? []
    if (comboGroups.length) {
      comboGroups.forEach((group) => {
        pushOption(resolveComboGroupOptionId(draft, group))
      })
    } else {
      pushOption(draft.comboEggId ?? menuItem?.customization?.combo?.eggs[0]?.id)
      pushOption(draft.comboSideId ?? menuItem?.customization?.combo?.sides[0]?.id)
    }
    draft.comboSideRemoveIds.forEach((optionId) => pushOption(optionId))
  }

  draft.removeIds.forEach((optionId) => pushOption(optionId))

  Object.entries(draft.addOnQuantities).forEach(([optionId, quantity]) => {
    if (quantity > 0) {
      pushOption(optionId, quantity)
    }
  })

  return optionPayloads
}

function collectMenuItemOptions(menuItem: MenuItem) {
  const options = [
    ...(menuItem.customization?.sizes?.options ?? []),
    ...(menuItem.customization?.soupBases?.options ?? []),
    ...(menuItem.customization?.noodleTypes ?? []),
    ...(menuItem.customization?.spicyLevels ?? []),
    ...(menuItem.customization?.combo?.option ? [menuItem.customization.combo.option] : []),
    ...(menuItem.customization?.combo?.groups.flatMap((group) => group.options) ?? []),
    ...(menuItem.customization?.combo?.eggs ?? []),
    ...(menuItem.customization?.combo?.sides ?? []),
    ...(menuItem.customization?.combo?.sideRemoveOptions ?? []),
    ...(menuItem.customization?.addOns ?? []),
    ...(menuItem.customization?.removeOptions ?? []),
  ]
  return new Map(options.map((option) => [option.id, option]))
}

export function buildOptionSnapshots(menuItem: MenuItem, draft: ItemCustomizationDraft): OrderLineOptionSnapshot[] {
  const optionsById = collectMenuItemOptions(menuItem)
  return mapOptions(draft, menuItem).map(({ option_id, quantity }) => {
    const option = optionsById.get(String(option_id))
    if (!option) throw new Error(`菜单缓存缺少选项 ${option_id}，请重新选择该菜品。`)
    return {
      optionId: option.id,
      optionType: option.optionType ?? null,
      optionCode: option.optionCode ?? null,
      optionGroup: option.optionGroup ?? null,
      parentOptionId: option.parentOptionId ?? null,
      nameEn: option.labelEn,
      nameZh: option.labelZh,
      priceDelta: option.priceDelta ?? 0,
      quantity,
    }
  })
}

// Catalog prices have two decimal places; sum integer cents before multiplying quantity.
export function calculateSnapshotLineSubtotal(
  basePrice: number,
  quantity: number,
  options: Pick<OrderLineOptionSnapshot, 'priceDelta' | 'quantity'>[],
) {
  const unitCents = Math.round(basePrice * 100)
    + options.reduce((sum, option) => sum + Math.round(option.priceDelta * 100) * option.quantity, 0)
  return unitCents * quantity / 100
}

export function calculateDraftLineSubtotal(menuItem: MenuItem | undefined, draft: ItemCustomizationDraft) {
  if (!menuItem) return 0
  return calculateSnapshotLineSubtotal(menuItem.price, draft.quantity, mapOptions(draft, menuItem).map((option) => ({
    priceDelta: option.option_price_snapshot ?? 0,
    quantity: option.quantity,
  })))
}
