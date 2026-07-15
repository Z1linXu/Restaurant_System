import { describe, expect, it } from 'vitest'
import {
  applyCategoryItemOrder,
  enterMenuItemReorderMode,
  isMenuItemMoveDisabled,
  moveMenuItemWithinCategory,
  sortMenuItemsByDisplayOrder,
  type SortableMenuItem,
} from './menuItemOrdering'

const items: SortableMenuItem[] = [
  { id: 12, category_id: 2, sort_order: 20, name_zh: '第二' },
  { id: 11, category_id: 2, sort_order: 10, name_zh: '第一' },
  { id: 21, category_id: 3, sort_order: 10, name_zh: '其他分类' },
]

describe('menu item display ordering', () => {
  it('sorts by category position and stable id fallback', () => {
    expect(sortMenuItemsByDisplayOrder(items).map((item) => item.id)).toEqual([11, 12, 21])
  })

  it('moves only within the selected category', () => {
    expect(moveMenuItemWithinCategory(items, 2, 12, 'up')).toEqual([12, 11])
    expect(moveMenuItemWithinCategory(items, 2, 11, 'up')).toBeNull()
    expect(moveMenuItemWithinCategory(items, 2, 12, 'down')).toBeNull()
  })

  it('renumbers the complete category without changing another category', () => {
    const reordered = applyCategoryItemOrder(items, 2, [12, 11])
    expect(reordered.filter((item) => item.category_id === 2).map((item) => [item.id, item.sort_order]))
      .toEqual([[12, 10], [11, 20]])
    expect(reordered.find((item) => item.id === 21)?.sort_order).toBe(10)
  })

  it('temporarily clears unrelated filters when entering reorder mode', () => {
    const mode = enterMenuItemReorderMode({
      searchTerm: 'tea',
      stationFilter: '4',
      statusFilter: 'active',
    })

    expect(mode.active).toEqual({
      searchTerm: '',
      stationFilter: 'all',
      statusFilter: 'all',
    })
    expect(mode.previous).toEqual({
      searchTerm: 'tea',
      stationFilter: '4',
      statusFilter: 'active',
    })
  })

  it('only disables the unavailable direction at category boundaries', () => {
    expect(isMenuItemMoveDisabled(0, 3, 'up')).toBe(true)
    expect(isMenuItemMoveDisabled(0, 3, 'down')).toBe(false)
    expect(isMenuItemMoveDisabled(1, 3, 'up')).toBe(false)
    expect(isMenuItemMoveDisabled(1, 3, 'down')).toBe(false)
    expect(isMenuItemMoveDisabled(2, 3, 'up')).toBe(false)
    expect(isMenuItemMoveDisabled(2, 3, 'down')).toBe(true)
    expect(isMenuItemMoveDisabled(0, 1, 'down')).toBe(true)
  })
})
