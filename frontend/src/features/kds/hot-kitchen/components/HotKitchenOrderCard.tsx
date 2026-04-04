import { useMemo, useState } from 'react'
import { Button } from '../../../../components/ui/Button'
import { markKitchenTaskReadyForPickup } from '../../../../services/kdsService'
import type { NoodleStationOrder } from '../../../../types/kds'
import type { KdsDisplaySizeMode } from '../../noodle/components/KdsTopBar'
import { HotKitchenItemRow } from './HotKitchenItemRow'

interface HotKitchenOrderCardProps {
  order: NoodleStationOrder
  now: Date
  compact?: boolean
  displayMode?: KdsDisplaySizeMode
  onCompleted: () => void
}

function formatOrderType(orderType: string) {
  return orderType.replaceAll('_', '-').toUpperCase()
}

export function HotKitchenOrderCard({
  order,
  now,
  compact = false,
  displayMode = 'standard',
  onCompleted,
}: HotKitchenOrderCardProps) {
  const [savingIds, setSavingIds] = useState<number[]>([])
  const [optimisticHiddenTaskIds, setOptimisticHiddenTaskIds] = useState<number[]>([])

  const visibleTasks = useMemo(
    () =>
      order.tasks.filter(
        (task) =>
          !['ready_for_pickup', 'served', 'done'].includes(task.status)
          && !optimisticHiddenTaskIds.includes(task.task_id),
      ),
    [optimisticHiddenTaskIds, order.tasks],
  )

  const elapsedMs = order.detail.submitted_at ? now.getTime() - new Date(order.detail.submitted_at).getTime() : 0
  const elapsedMinutes = Math.max(0, Math.floor(elapsedMs / 60000))
  const timeLabel = `${String(Math.floor(elapsedMinutes / 60)).padStart(2, '0')}:${String(elapsedMinutes % 60).padStart(2, '0')}`
  const displayLabel = order.detail.table_no ?? order.detail.pickup_no ?? `#${order.detail.id}`

  const cardScale =
    displayMode === 'compact'
      ? { shell: 'rounded-[18px]', header: 'px-3.5 py-3', title: 'text-[1.6rem]', meta: 'text-[0.74rem]', timer: 'px-2.5 py-1 text-[0.78rem]', body: 'space-y-2 px-3.5 py-3', actionWrap: 'px-3.5 pb-3', action: 'min-h-8 rounded-[12px] px-3 py-2 text-[0.68rem]' }
      : displayMode === 'large'
        ? { shell: 'rounded-[24px]', header: 'px-5 py-4.5', title: 'text-[2.45rem]', meta: 'text-[1rem]', timer: 'px-4 py-2 text-[1rem]', body: 'space-y-3.5 px-5 py-4.5', actionWrap: 'px-5 pb-4.5', action: 'min-h-12 rounded-[18px] px-4.5 py-3 text-[0.94rem]' }
        : displayMode === 'xlarge'
          ? { shell: 'rounded-[26px]', header: 'px-5.5 py-5', title: 'text-[2.8rem]', meta: 'text-[1.08rem]', timer: 'px-4.5 py-2.5 text-[1.08rem]', body: 'space-y-4 px-5.5 py-5', actionWrap: 'px-5.5 pb-5', action: 'min-h-14 rounded-[20px] px-5 py-3.5 text-[1rem]' }
          : { shell: compact ? 'rounded-[20px]' : 'rounded-[24px]', header: compact ? 'px-4 py-3.5' : 'px-5 py-4', title: compact ? 'text-[1.85rem]' : 'text-[2.05rem]', meta: compact ? 'text-[0.8rem]' : 'text-[0.9rem]', timer: compact ? 'px-3 py-1.5 text-[0.84rem]' : 'px-3.5 py-1.5 text-[0.9rem]', body: compact ? 'space-y-2.5 px-4 py-3' : 'space-y-3 px-5 py-4', actionWrap: compact ? 'px-4 pb-3.5' : 'px-5 pb-4', action: compact ? 'min-h-9 rounded-[14px] px-3 py-2 text-[0.72rem]' : 'min-h-10 rounded-[16px] px-4 py-2.5 text-[0.8rem]' }

  const completeTasks = async (taskIds: number[]) => {
    const actionableTasks = visibleTasks.filter((task) => taskIds.includes(task.task_id))
    if (actionableTasks.length === 0) {
      return
    }

    setSavingIds((current) => [...new Set([...current, ...taskIds])])
    try {
      await Promise.all(actionableTasks.map((task) => markKitchenTaskReadyForPickup(task.task_id)))
      setOptimisticHiddenTaskIds((current) => [...new Set([...current, ...taskIds])])
      onCompleted()
    } finally {
      setSavingIds((current) => current.filter((taskId) => !taskIds.includes(taskId)))
    }
  }

  return (
    <article className={`bg-[rgba(255,255,255,0.88)] shadow-[0_14px_32px_rgba(26,28,25,0.08)] ring-1 ring-[rgba(26,28,25,0.05)] ${cardScale.shell}`}>
      <header className={`flex items-start justify-between gap-3 border-b border-[rgba(26,28,25,0.05)] ${cardScale.header}`}>
        <div>
          <div className={`font-display font-extrabold tracking-[-0.07em] text-[var(--on-surface)] ${cardScale.title}`}>
            {displayLabel}
          </div>
          <div className={`mt-1 flex flex-wrap items-center gap-2 font-semibold text-[var(--muted)] ${cardScale.meta}`}>
            <span>{formatOrderType(order.detail.order_type)}</span>
          </div>
        </div>

        <div className={`rounded-full bg-[rgba(26,28,25,0.06)] font-bold text-[var(--on-surface)] ${cardScale.timer}`}>
          {timeLabel}
        </div>
      </header>

      <div className={cardScale.body}>
        {visibleTasks.map((task) => (
          <HotKitchenItemRow
            key={task.task_id}
            task={task}
            compact={compact}
            displayMode={displayMode}
            saving={savingIds.includes(task.task_id)}
            onComplete={() => void completeTasks([task.task_id])}
          />
        ))}
      </div>

      {visibleTasks.length > 1 ? (
        <div className={cardScale.actionWrap}>
          <Button
            variant="secondary"
            fullWidth
            className={cardScale.action}
            disabled={savingIds.length > 0}
            onClick={() => void completeTasks(visibleTasks.map((task) => task.task_id))}
          >
            Complete All
          </Button>
        </div>
      ) : null}
    </article>
  )
}
