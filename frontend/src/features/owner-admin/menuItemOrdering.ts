export interface SortableMenuItem {
  id?: number
  category_id: number
  sort_order: number
  name_zh?: string
}

export function sortMenuItemsByDisplayOrder<T extends SortableMenuItem>(items: T[]) {
  return [...items].sort((left, right) =>
    left.category_id - right.category_id
      || left.sort_order - right.sort_order
      || (left.id ?? Number.MAX_SAFE_INTEGER) - (right.id ?? Number.MAX_SAFE_INTEGER)
      || (left.name_zh ?? '').localeCompare(right.name_zh ?? '', 'zh-Hans'),
  )
}

export function moveMenuItemWithinCategory<T extends SortableMenuItem>(
  items: T[],
  categoryId: number,
  itemId: number,
  direction: 'up' | 'down',
) {
  const categoryItems = sortMenuItemsByDisplayOrder(
    items.filter((item) => item.category_id === categoryId && item.id != null),
  )
  const currentIndex = categoryItems.findIndex((item) => item.id === itemId)
  const targetIndex = direction === 'up' ? currentIndex - 1 : currentIndex + 1
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= categoryItems.length) {
    return null
  }
  const reordered = [...categoryItems]
  ;[reordered[currentIndex], reordered[targetIndex]] = [reordered[targetIndex], reordered[currentIndex]]
  return reordered.map((item) => item.id as number)
}

export function applyCategoryItemOrder<T extends SortableMenuItem>(
  items: T[],
  categoryId: number,
  itemIds: number[],
) {
  const orderById = new Map(itemIds.map((id, index) => [id, (index + 1) * 10]))
  return sortMenuItemsByDisplayOrder(items.map((item) => (
    item.category_id === categoryId && item.id != null && orderById.has(item.id)
      ? { ...item, sort_order: orderById.get(item.id) as number }
      : item
  )))
}
