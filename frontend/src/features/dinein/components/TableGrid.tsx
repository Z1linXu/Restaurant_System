import type { TableSlot } from '../../../types/dinein'
import { TableCard } from './TableCard'

interface TableGridProps {
  slots: TableSlot[]
  onEntrySelect: (slotId: string, selection: 'left' | 'right' | 'full') => void
  onStart: (slotId: string) => void
  onEdit: (slotId: string) => void
  onPrint: (slot: TableSlot) => void
  onFinish: (slot: TableSlot) => void
  compact?: boolean
}

export function TableGrid({ slots, onEntrySelect, onStart, onEdit, onPrint, onFinish, compact = false }: TableGridProps) {
  return (
    <div
      className={
        compact
          ? 'grid grid-cols-4 gap-3 min-[1280px]:grid-cols-5'
          : 'grid grid-cols-[repeat(auto-fit,minmax(15.5rem,1fr))] gap-4 xl:grid-cols-[repeat(auto-fit,minmax(14.5rem,1fr))] 2xl:grid-cols-[repeat(auto-fit,minmax(14rem,1fr))]'
      }
    >
      {slots.map((slot) => (
        <TableCard
          key={slot.id}
          slot={slot}
          onEntrySelect={onEntrySelect}
          onStart={onStart}
          onEdit={onEdit}
          onPrint={onPrint}
          onFinish={onFinish}
          compact={compact}
        />
      ))}
    </div>
  )
}
