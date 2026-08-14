const DISPLAY_ORDER_STEP = 10

export const comboConfigurationLayoutClasses = {
  controlField: 'grid min-w-0 gap-1',
  displayOrderField: 'grid min-w-[120px] gap-1',
  groupRow: 'grid items-start gap-3 sm:grid-cols-2 xl:grid-cols-[minmax(130px,1fr)_minmax(130px,1fr)_minmax(160px,180px)_minmax(120px,140px)_minmax(96px,auto)_minmax(92px,auto)_minmax(100px,auto)_minmax(84px,auto)]',
  componentRow: 'grid items-start gap-3 rounded-[16px] bg-white p-3 sm:grid-cols-2 xl:grid-cols-[minmax(120px,1fr)_minmax(120px,1fr)_minmax(120px,130px)_minmax(96px,auto)_minmax(145px,160px)_minmax(160px,1.2fr)_minmax(72px,auto)_minmax(80px,auto)]',
  reorderControls: 'grid min-w-[96px] grid-cols-2 gap-2 self-end',
  mappingField: 'grid min-w-[145px] gap-1',
  linkedItemField: 'grid min-w-[160px] gap-1',
  defaultRow: 'flex items-center gap-2 text-[0.78rem] font-semibold text-[var(--muted)] sm:col-span-2 xl:col-span-8',
}

export function moveWithDisplayOrder<T extends { display_order?: number | null }>(
  values: T[],
  fromIndex: number,
  direction: -1 | 1,
) {
  const toIndex = fromIndex + direction
  if (toIndex < 0 || toIndex >= values.length) {
    return values
  }
  const moved = [...values]
  const [item] = moved.splice(fromIndex, 1)
  moved.splice(toIndex, 0, item)
  return moved.map((value, index) => ({
    ...value,
    display_order: (index + 1) * DISPLAY_ORDER_STEP,
  }))
}
