import { describe, expect, it } from 'vitest'
import {
  applyCategoryItemOrder,
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
})
