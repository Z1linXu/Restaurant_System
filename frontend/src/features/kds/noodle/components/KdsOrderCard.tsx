import { useMemo, useState } from 'react'
import { Button } from '../../../../components/ui/Button'
import { markKitchenTaskReadyForPickup } from '../../../../services/kdsService'
import type { BackendKdsTaskDisplay, NoodleStationOrder } from '../../../../types/kds'
import { KitchenItemRow } from './KitchenItemRow'
import type { KdsDisplaySizeMode } from './KdsTopBar'

function formatOrderType(orderType: string) {
  return orderType.replaceAll('_', '-').toUpperCase()
}

function groupLabelForStation(stationCode: string) {
  if (stationCode === 'COLD') {
    return 'SIDE'
  }
  if (stationCode === 'NOODLE') {
    return 'NOODLE'
  }
  if (stationCode === 'WOK') {
    return 'WOK'
  }
  if (stationCode === 'DEEPFRIED') {
    return 'FRIED'
  }
  return stationCode
}

interface KdsOrderCardProps {
  order: NoodleStationOrder
  now: Date
  onCompleted: () => void
  compact?: boolean
  displayMode?: KdsDisplaySizeMode
}

interface RenderRow {
  key: string
  sectionLabel: string | null
  task: BackendKdsTaskDisplay
  taskIds: number[]
  quantity: number
  primaryOverride?: string | null
  secondaryOverride?: string | null
}

export function KdsOrderCard({ order, now, onCompleted, compact = false, displayMode = 'standard' }: KdsOrderCardProps) {
  const [selectedTaskIds, setSelectedTaskIds] = useState<number[]>([])
  const [saving, setSaving] = useState(false)
  const [optimisticCompletedTaskIds, setOptimisticCompletedTaskIds] = useState<number[]>([])

  const isTaskCompleted = (taskId: number) => {
    if (optimisticCompletedTaskIds.includes(taskId)) {
      return true
    }
    const task = order.tasks.find((candidate) => candidate.task_id === taskId)
    return task ? ['ready_for_pickup', 'served', 'done'].includes(task.status) : false
  }

  const visibleRows = useMemo<RenderRow[]>(() => {
    const stationPriority = { SIDE: 0, NOODLE: 1, WOK: 2, FRIED: 3 } as const

    const groupedRows = [...order.tasks]
      .map((task) => ({
        task,
        sectionLabel: groupLabelForStation(task.station_code),
      }))
      .sort((left, right) => {
        const leftPriority = stationPriority[left.sectionLabel as keyof typeof stationPriority] ?? 99
        const rightPriority = stationPriority[right.sectionLabel as keyof typeof stationPriority] ?? 99
        if (leftPriority !== rightPriority) {
          return leftPriority - rightPriority
        }
        return left.task.task_id - right.task.task_id
      })
      .reduce<RenderRow[]>((rows, { task, sectionLabel }) => {
        const completed = ['ready_for_pickup', 'served', 'done'].includes(task.status)
        if (sectionLabel === 'FRIED' || sectionLabel === 'SIDE') {
          const existing = rows.find(
            (row) =>
              row.sectionLabel === sectionLabel &&
              row.primaryOverride == null &&
              row.secondaryOverride == null &&
              row.task.item_name_snapshot_zh === task.item_name_snapshot_zh &&
              ['ready_for_pickup', 'served', 'done'].includes(row.task.status) === completed,
          )
          if (existing && !completed && !['ready_for_pickup', 'served', 'done'].includes(existing.task.status)) {
            existing.taskIds.push(task.task_id)
            existing.quantity += task.quantity
            return rows
          }
        }

        rows.push({
          key: `task-${task.task_id}`,
          sectionLabel,
          task,
          taskIds: [task.task_id],
          quantity: task.quantity,
        })
        return rows
      }, [])

    return groupedRows
  }, [order.tasks])

  const elapsedMs = order.detail.submitted_at ? now.getTime() - new Date(order.detail.submitted_at).getTime() : 0
  const elapsedMinutes = Math.max(0, Math.floor(elapsedMs / 60000))
  const isUrgent = elapsedMinutes >= 10
  const completedByBackend = order.tasks.every((task) => isTaskCompleted(task.task_id))
  const isCompleted = completedByBackend

  const cardClasses = isCompleted
    ? 'bg-[rgba(255,255,255,0.4)] opacity-70'
    : order.detail.is_modified_after_submit
      ? 'bg-[rgba(138,22,22,0.06)]'
      : isUrgent
        ? 'bg-[rgba(240,166,35,0.12)]'
        : 'bg-[rgba(255,255,255,0.86)]'

  const timeLabel = `${String(Math.floor(elapsedMinutes / 60)).padStart(2, '0')}:${String(elapsedMinutes % 60).padStart(2, '0')}`
  const displayLabel = order.detail.table_no ?? order.detail.pickup_no ?? `#${order.detail.id}`

  const toggleRowSelected = (taskIds: number[]) => {
    if (taskIds.length === 0) {
      return
    }
    setSelectedTaskIds((current) => {
      const allSelected = taskIds.every((taskId) => current.includes(taskId))
      if (allSelected) {
        return current.filter((taskId) => !taskIds.includes(taskId))
      }
      return [...new Set([...current, ...taskIds])]
    })
  }

  const completeTasks = async (tasks: BackendKdsTaskDisplay[]) => {
    const actionableTasks = tasks.filter((task) => !['ready_for_pickup', 'served', 'done'].includes(task.status))
    if (actionableTasks.length === 0) {
      return
    }

    setSaving(true)

    try {
      await Promise.all(actionableTasks.map((task) => markKitchenTaskReadyForPickup(task.task_id)))
      setOptimisticCompletedTaskIds((current) => [...new Set([...current, ...actionableTasks.map((task) => task.task_id)])])
      setSelectedTaskIds([])
      onCompleted()
    } finally {
      setSaving(false)
    }
  }

  const cardScale =
    displayMode === 'compact'
      ? {
          shell: 'rounded-[18px]',
          header: 'px-3.5 py-3',
          title: 'text-[1.75rem]',
          meta: 'text-[0.76rem]',
          timer: 'px-3 py-1.5 text-[0.84rem]',
          body: 'space-y-1.5 px-3.5 py-2.5',
          actions: 'grid-cols-1 gap-2 px-3.5 pb-3',
          button: 'min-h-8 rounded-[12px] px-3 py-2 text-[0.68rem] whitespace-normal leading-tight',
        }
      : displayMode === 'large'
        ? {
            shell: 'rounded-[26px]',
            header: 'px-5 py-4.5',
            title: 'text-[2.55rem]',
            meta: 'text-[0.94rem]',
            timer: 'px-4 py-2 text-[1rem]',
            body: 'space-y-3 px-5 py-4',
            actions: 'grid-cols-1 gap-2 px-5 pb-4 md:grid-cols-2',
            button: 'min-h-12 rounded-[18px] text-[0.92rem] font-bold uppercase tracking-[0.06em] whitespace-normal leading-tight',
          }
        : displayMode === 'xlarge'
          ? {
              shell: 'rounded-[28px]',
              header: 'px-6 py-5',
              title: 'text-[2.9rem]',
              meta: 'text-[1rem]',
              timer: 'px-4 py-2 text-[1.05rem]',
              body: 'space-y-3 px-5 py-4.5',
              actions: 'grid-cols-1 gap-3 px-5 pb-5 md:grid-cols-2',
              button: 'min-h-14 rounded-[20px] text-[1rem] font-bold uppercase tracking-[0.06em] whitespace-normal leading-tight',
            }
          : {
              shell: compact ? 'rounded-[22px]' : 'rounded-[28px]',
              header: compact ? 'px-4 py-3.5' : 'px-6 py-5',
              title: compact ? 'text-[2.2rem]' : 'text-[3rem]',
              meta: compact ? 'text-[0.82rem]' : 'text-[0.98rem]',
              timer: compact ? 'px-3 py-1.5 text-[0.9rem]' : 'px-4 py-2 text-[1.05rem]',
              body: compact ? 'space-y-2 px-4 py-3' : 'space-y-3 px-5 py-4',
              actions: compact ? 'grid-cols-1 gap-2 px-4 pb-3.5' : 'md:grid-cols-2 gap-3 px-5 pb-5',
              button: compact ? 'min-h-9 rounded-[14px] px-3 py-2 text-[0.72rem] font-bold uppercase tracking-[0.06em] whitespace-normal leading-tight' : 'min-h-16 rounded-[20px] text-[1.02rem] font-bold uppercase tracking-[0.08em]',
            }

  return (
    <div className={`p-0 shadow-[0_14px_36px_rgba(26,28,25,0.08)] ring-1 ring-[rgba(26,28,25,0.05)] ${cardClasses} ${cardScale.shell}`}>
      <div className={`flex items-start justify-between gap-4 border-b border-[rgba(26,28,25,0.05)] ${cardScale.header}`}>
        <div>
          <div className="flex items-center gap-3">
            <h2 className={`font-display font-extrabold tracking-[-0.08em] text-[var(--on-surface)] ${cardScale.title}`}>{displayLabel}</h2>
            {order.detail.is_modified_after_submit ? (
              <span className="rounded-full bg-[rgba(138,22,22,0.1)] px-3 py-1 text-[0.76rem] font-bold tracking-[0.14em] text-[var(--primary)]">
                UPDATED
              </span>
            ) : null}
          </div>
          <div className={`mt-1 flex flex-wrap items-center gap-2 font-semibold text-[var(--muted)] ${cardScale.meta}`}>
            <span>{formatOrderType(order.detail.order_type)}</span>
          </div>
        </div>

        <div
          className={`flex items-center gap-2 rounded-full font-bold ${
            isUrgent && !isCompleted
              ? 'bg-[var(--primary)] text-[var(--on-primary)]'
              : 'bg-[rgba(26,28,25,0.06)] text-[var(--on-surface)]'
          } ${cardScale.timer}`}
        >
          <span aria-hidden>◔</span>
          <span>{timeLabel}</span>
        </div>
      </div>

      <div className={cardScale.body}>
        {visibleRows.map((row) => (
          <KitchenItemRow
            key={row.key}
            task={row.task}
            sectionLabel={row.sectionLabel === 'NOODLE' ? null : row.sectionLabel}
            selected={row.taskIds.some((taskId) => selectedTaskIds.includes(taskId))}
            completed={row.taskIds.length > 0 && row.taskIds.every((taskId) => isTaskCompleted(taskId))}
            compact={compact}
            onToggle={() => toggleRowSelected(row.taskIds)}
            selectable={row.taskIds.length > 0}
            primaryOverride={row.primaryOverride}
            secondaryOverride={row.secondaryOverride}
            quantityOverride={row.quantity}
            displayMode={displayMode}
          />
        ))}
      </div>

      <div className={`grid ${cardScale.actions}`}>
        <Button
          variant="secondary"
          size="lg"
          fullWidth
          disabled={selectedTaskIds.length === 0 || isCompleted || saving}
          className={cardScale.button}
          onClick={() => void completeTasks(order.tasks.filter((task) => selectedTaskIds.includes(task.task_id)))}
        >
          Complete Selected
        </Button>
        <Button
          variant="primary"
          size="lg"
          fullWidth
          disabled={isCompleted || saving}
          className={`${cardScale.button} font-bold uppercase tracking-[0.06em] ${
            isUrgent && !order.detail.is_modified_after_submit ? 'from-[#d48300] to-[#ff9f0a] text-[#2f1d00]' : ''
          }`}
          onClick={() => void completeTasks(order.tasks)}
        >
          {saving ? 'Updating...' : isCompleted ? 'Completed' : 'Complete All'}
        </Button>
      </div>
    </div>
  )
}
