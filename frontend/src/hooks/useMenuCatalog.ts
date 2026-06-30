import { useEffect, useMemo, useState } from 'react'
import { fetchMenuCatalog } from '../services/menuService'
import type { BackendMenuCatalog, BackendMenuItem, ChoiceOption, MenuItem, OrderingCatalog } from '../types/ordering'

function formatCategoryLabel(code: string) {
  return code
    .toLowerCase()
    .split('_')
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ')
}

function mapOption(option: BackendMenuItem['options'][number]): ChoiceOption {
  return {
    id: String(option.id),
    labelEn: option.name_en,
    labelZh: option.name_zh,
    priceDelta: Number(option.price_delta ?? 0),
    optionType: option.option_type,
    optionCode: option.option_code,
    optionGroup: option.option_group,
    parentOptionId: option.parent_option_id == null ? null : String(option.parent_option_id),
    sortOrder: option.sort_order,
    sideItemRemoveOptions: option.side_item_remove_options?.map(mapOption),
  }
}

function isComboUpcharge(option: ChoiceOption) {
  if (option.optionGroup === 'COMBO' || option.optionCode === 'combo') {
    return true
  }
  // Legacy fallback for databases created before option_group/option_code existed.
  return option.labelZh === '套餐' || option.labelEn === 'Combo'
}

function isComboEgg(option: ChoiceOption) {
  if (option.optionGroup === 'COMBO_EGG') {
    return true
  }
  // Legacy fallback for databases created before option_group existed.
  return option.labelZh.includes('套餐') && (option.labelZh.includes('卤蛋') || option.labelZh.includes('煎蛋'))
}

function isComboSide(option: ChoiceOption) {
  if (option.optionGroup === 'COMBO_SIDE') {
    return true
  }
  // Legacy fallback for databases created before option_group existed.
  return option.labelZh.includes('套餐') && (
    option.labelZh.includes('毛豆') || option.labelZh.includes('土豆丝') || option.labelZh.includes('拌黄瓜')
  )
}

function isComboSideRemove(option: ChoiceOption) {
  return option.optionGroup === 'COMBO_SIDE_REMOVE'
}

function normalizeRequestLabel(value: string | undefined) {
  return (value ?? '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, ' ')
}

function sideRemoveDedupeKey(option: ChoiceOption) {
  const zh = normalizeRequestLabel(option.labelZh)
  const en = normalizeRequestLabel(option.labelEn)
  if (zh || en) {
    return `${zh}|${en}`
  }
  return option.optionCode || option.id
}

function sortChoiceOptions(left: ChoiceOption, right: ChoiceOption) {
  return (left.sortOrder ?? 999999) - (right.sortOrder ?? 999999) || Number(left.id) - Number(right.id)
}

function buildSideRemoveOptions(comboSides: ChoiceOption[], comboSideRemoves: ChoiceOption[]) {
  const resultBySide = new Map<string, ChoiceOption[]>()

  comboSides.forEach((side) => {
    const managedSideRemoves = side.sideItemRemoveOptions ?? []
    // Menu Management owns combo side requests. Legacy child options are only used when
    // the real side item does not expose active REMOVE options.
    const sourceOptions = managedSideRemoves.length
      ? managedSideRemoves
      : comboSideRemoves.filter((option) => option.parentOptionId === side.id)
    const deduped = new Map<string, ChoiceOption>()
    sourceOptions.sort(sortChoiceOptions).forEach((option) => {
      const key = sideRemoveDedupeKey(option)
      if (!deduped.has(key)) {
        deduped.set(key, { ...option, parentOptionId: side.id })
      }
    })
    resultBySide.set(side.id, Array.from(deduped.values()))
  })

  return Array.from(resultBySide.values()).flat()
}

function isFreeToggleAddOn(option: ChoiceOption) {
  return (option.priceDelta ?? 0) === 0 && (option.labelZh === '加香菜' || option.labelZh === '加葱')
}

function mapCatalog(data: BackendMenuCatalog): OrderingCatalog {
  const categories = data.categories.map((category) => ({
    id: String(category.id),
    code: category.code,
    labelEn: category.name_en || formatCategoryLabel(category.code),
    labelZh: category.name_zh || category.name_en || category.code,
  }))

  const items: MenuItem[] = data.categories.flatMap((category) =>
    category.items.map((item) => {
      const optionsByType = item.options.reduce<Record<string, ChoiceOption[]>>((groups, option) => {
        const mapped = mapOption(option)
        groups[option.option_type] = [...(groups[option.option_type] ?? []), mapped]
        return groups
      }, {})
      const allOptions = item.options.map(mapOption)

      const customization =
        item.options.length > 0
          ? {
              combo: (() => {
                const comboUpcharge = allOptions.find(isComboUpcharge)
                const comboEggs = allOptions.filter(isComboEgg)
                const comboSides = allOptions.filter(isComboSide)
                const comboSideRemoves = allOptions.filter(isComboSideRemove)
                if (!comboUpcharge || comboEggs.length === 0 || comboSides.length === 0) {
                  return undefined
                }
                return {
                  optionId: comboUpcharge.id,
                  upcharge: comboUpcharge.priceDelta ?? 0,
                  eggs: comboEggs,
                  sides: comboSides,
                  sideRemoveOptions: buildSideRemoveOptions(comboSides, comboSideRemoves),
                }
              })(),
              sizes: optionsByType.size?.length
                ? {
                    required: true,
                    options: optionsByType.size,
                  }
                : undefined,
              soupBases: optionsByType.soup_base?.length
                ? {
                    required: true,
                    options: optionsByType.soup_base,
                  }
                : undefined,
              noodleTypes: optionsByType.noodle_type,
              spicyLevels: optionsByType.spicy_level,
              addOns: (optionsByType.addon ?? [])
                .filter((option) => !isComboUpcharge(option) && !isComboEgg(option) && !isComboSide(option))
                .sort((left, right) => {
                  const leftRank = isFreeToggleAddOn(left) ? 0 : 1
                  const rightRank = isFreeToggleAddOn(right) ? 0 : 1
                  if (leftRank !== rightRank) {
                    return leftRank - rightRank
                  }
                  return 0
                }),
              removeOptions: (optionsByType.remove ?? []).filter((option) => !isComboSideRemove(option)),
            }
          : undefined

      return {
        id: String(item.id),
        sku: item.sku,
        categoryId: String(category.id),
        categoryCode: category.code,
        itemType: item.item_type,
        nameEn: item.name_en,
        nameZh: item.name_zh,
        descriptionEn: '',
        descriptionZh: '',
        price: Number(item.base_price),
        customization,
      }
    }),
  )

  return {
    storeId: String(data.store_id),
    categories,
    items,
  }
}

export function useMenuCatalog(storeId: number) {
  const [catalog, setCatalog] = useState<OrderingCatalog | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    const loadCatalog = async () => {
      setLoading(true)
      setError(null)

      try {
        const payload = await fetchMenuCatalog(storeId)
        if (!active) {
          return
        }
        setCatalog(mapCatalog(payload))
      } catch (loadError) {
        if (!active) {
          return
        }
        setError(loadError instanceof Error ? loadError.message : 'Failed to load menu catalog')
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void loadCatalog()

    return () => {
      active = false
    }
  }, [storeId])

  const categories = useMemo(() => catalog?.categories ?? [], [catalog])
  const items = useMemo(() => catalog?.items ?? [], [catalog])

  return {
    catalog,
    categories,
    items,
    loading,
    error,
  }
}
