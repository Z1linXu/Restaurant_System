import { Button } from '../../../components/ui/Button'
import { Card } from '../../../components/ui/Card'
import type { TableSlot } from '../../../types/dinein'
import { TableStatusBadge } from './TableStatusBadge'

interface TableCardProps {
  slot: TableSlot
  onEntrySelect: (slotId: string, selection: 'left' | 'right' | 'full') => void
  onStart: (slotId: string) => void
  onEdit: (slotId: string) => void
  onFinish: (slot: TableSlot) => void
  compact?: boolean
}

const statusAccent = {
  available: 'before:bg-[#6a8a6d]',
  occupied: 'before:bg-[#8d8176]',
  alert: 'before:bg-[#c85d22]',
} as const

const titleColor = {
  available: 'text-[var(--on-surface)]',
  occupied: 'text-[rgba(53,43,38,0.92)]',
  alert: 'text-[#b4481c]',
} as const

export function TableCard({ slot, onEntrySelect, onStart, onEdit, onFinish, compact = false }: TableCardProps) {
  const buttonLabel = slot.action === 'edit' ? 'Edit order' : 'Start order'

  return (
    <div className="text-left">
      <Card
        tone={slot.action === 'edit' ? 'feature' : 'well'}
        className={`relative overflow-hidden transition duration-200 before:absolute before:bottom-0 before:left-0 before:top-0 before:w-[6px] ${statusAccent[slot.status]} bg-[var(--surface-container-lowest)] shadow-[0_14px_30px_rgba(26,28,25,0.06)] ${compact ? 'min-h-[9.3rem] p-3.5 pl-5' : 'min-h-[11.5rem] p-4 pl-6'}`}
      >
        <div className={`flex h-full flex-col ${compact ? 'gap-3' : 'gap-4'}`}>
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className={`font-display font-extrabold tracking-[-0.07em] ${titleColor[slot.status]} ${compact ? 'text-[2.2rem]' : 'text-[2.6rem]'}`}>
                {slot.label}
              </p>
              <p className={`${compact ? 'mt-0.5 text-[0.8rem]' : 'mt-1 text-sm'} text-[var(--muted)]`}>{slot.zone}</p>
            </div>
            <TableStatusBadge status={slot.status} />
          </div>

          <div className={`mt-auto ${compact ? 'space-y-2' : 'space-y-3'}`}>
            {slot.alertMessage ? (
              <div className="rounded-[18px] bg-[rgba(200,93,34,0.10)] px-4 py-3 text-sm font-semibold text-[#b4481c]">
                {slot.alertMessage}
              </div>
            ) : null}

            {slot.action === 'entry' ? (
              compact ? (
                <div className="grid gap-2">
                  <div className="grid grid-cols-2 gap-2">
                    <Button
                      size="lg"
                      fullWidth
                      className="min-h-[2.8rem] rounded-[16px] px-3 text-[0.95rem]"
                      onClick={() => onEntrySelect(slot.id, 'left')}
                    >
                      Left
                    </Button>
                    <Button
                      size="lg"
                      fullWidth
                      className="min-h-[2.8rem] rounded-[16px] px-3 text-[0.95rem]"
                      onClick={() => onEntrySelect(slot.id, 'right')}
                    >
                      Right
                    </Button>
                  </div>
                  <Button
                    size="lg"
                    fullWidth
                    className="min-h-[2.7rem] rounded-[16px] px-3 text-[0.92rem]"
                    variant="secondary"
                    onClick={() => onEntrySelect(slot.id, 'full')}
                  >
                    Full table
                  </Button>
                </div>
              ) : (
                <div className="grid gap-3">
                  <Button
                    size="lg"
                    fullWidth
                    className="min-h-[3.35rem] rounded-[18px]"
                    onClick={() => onEntrySelect(slot.id, 'left')}
                  >
                    Left
                  </Button>
                  <Button
                    size="lg"
                    fullWidth
                    className="min-h-[3.35rem] rounded-[18px]"
                    onClick={() => onEntrySelect(slot.id, 'right')}
                  >
                    Right
                  </Button>
                  <Button
                    size="lg"
                    fullWidth
                    className="min-h-[3.35rem] rounded-[18px]"
                    variant="secondary"
                    onClick={() => onEntrySelect(slot.id, 'full')}
                  >
                    Full
                  </Button>
                </div>
              )
            ) : (
              <div className={`grid ${compact ? 'gap-2' : 'gap-3'}`}>
                <Button
                  size="lg"
                  fullWidth
                  className={`${compact ? 'min-h-[2.8rem] rounded-[16px] px-3 text-[0.95rem]' : 'min-h-[3.35rem] rounded-[18px]'}`}
                  variant={slot.action === 'edit' ? 'secondary' : 'primary'}
                  onClick={() => (slot.action === 'edit' ? onEdit(slot.id) : onStart(slot.id))}
                >
                  {buttonLabel}
                </Button>
                {slot.action === 'edit' && slot.orderDbId && slot.orderStatus && ['submitted', 'preparing', 'ready'].includes(slot.orderStatus) ? (
                  <Button
                    size="lg"
                    fullWidth
                    className={`${compact ? 'min-h-[2.6rem] rounded-[16px] px-3 text-[0.92rem]' : 'min-h-[3.35rem] rounded-[18px]'}`}
                    variant="tertiary"
                    onClick={() => onFinish(slot)}
                  >
                    Finish
                  </Button>
                ) : null}
              </div>
            )}
          </div>
        </div>
      </Card>
    </div>
  )
}
