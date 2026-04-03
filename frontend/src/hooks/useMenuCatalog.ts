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
  }
}

function isComboUpcharge(option: ChoiceOption) {
  return option.labelZh === '套餐' || option.labelEn === 'Combo'
}

function isComboEgg(option: ChoiceOption) {
  return option.labelZh.includes('套餐') && (option.labelZh.includes('卤蛋') || option.labelZh.includes('煎蛋'))
}

function isComboSide(option: ChoiceOption) {
  return option.labelZh.includes('套餐') && (
    option.labelZh.includes('毛豆') || option.labelZh.includes('土豆丝') || option.labelZh.includes('拌黄瓜')
  )
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
        groups[option.option_type] = [...(groups[option.option_type] ?? []), mapOption(option)]
        return groups
      }, {})

      const customization =
        item.options.length > 0
          ? {
              combo: (() => {
                const addonOptions = optionsByType.addon ?? []
                const comboUpcharge = addonOptions.find(isComboUpcharge)
                const comboEggs = addonOptions.filter(isComboEgg)
                const comboSides = addonOptions.filter(isComboSide)
                if (!comboUpcharge || comboEggs.length === 0 || comboSides.length === 0) {
                  return undefined
                }
                return {
                  optionId: comboUpcharge.id,
                  upcharge: comboUpcharge.priceDelta ?? 0,
                  eggs: comboEggs,
                  sides: comboSides,
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
              removeOptions: optionsByType.remove,
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

export function useMenuCatalog() {
  const [catalog, setCatalog] = useState<OrderingCatalog | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    const loadCatalog = async () => {
      setLoading(true)
      setError(null)

      try {
        const payload = await fetchMenuCatalog()
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
  }, [])

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
