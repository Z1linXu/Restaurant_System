import { useEffect, useMemo, useState } from 'react'
import { useMenuCatalog } from '../../hooks/useMenuCatalog'
import { Card } from '../../components/ui/Card'
import { useIpadLandscape } from '../../hooks/useIpadLandscape'
import { useTableBoard } from '../../hooks/useTableBoard'
import {
  buildFrontdeskBoardPath,
  buildMenuPath,
  inferFrontdeskWorkstation,
  navigateTo,
  parseMenuRoute,
} from '../frontdesk/navigation'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { OrderingPage } from '../ordering/OrderingPage'
import { completeOrder } from '../../services/orderService'
import type { TableSlot } from '../../types/dinein'
import { DineInSidebar } from './components/DineInSidebar'
import { DineInTopBar } from './components/DineInTopBar'
import { TableGrid } from './components/TableGrid'
import { TableStatusLegend } from './components/TableStatusLegend'

function buildGeneratedTakeoutLabel() {
  const stamp = Date.now().toString().slice(-4)
  const suffix = Math.random().toString(36).slice(2, 4).toUpperCase()
  return `TO-${stamp}${suffix}`
}

interface DineInPageProps {
  routePath: string
  routeSearch: string
}

export function DineInPage({ routePath, routeSearch }: DineInPageProps) {
  const isIpadLandscape = useIpadLandscape()
  const workstation = inferFrontdeskWorkstation(routePath)
  const workstationLabel = workstation ? `Menu ${workstation.toUpperCase()}` : null
  const [sidebarCollapsed, setSidebarCollapsed] = useState(isIpadLandscape)
  const [activeOrderingContext, setActiveOrderingContext] = useState<{
    slotLabel: string
    tableLabel: string
    orderType: 'dine_in' | 'pickup'
    pickupLabel: string | null
    workstation: string | null
  } | null>(null)
  const [submissionMessage, setSubmissionMessage] = useState<string | null>(null)
  const menuCatalog = useMenuCatalog()
  const { tableSlots, statusCounts, startOrder, editOrder, endOrder, refreshFromBackend, refreshTableAfterFinish } = useTableBoard()
  const workstationCompact = isIpadLandscape
  const boardPath = buildFrontdeskBoardPath(workstation)

  useEffect(() => {
    setSidebarCollapsed(isIpadLandscape)
  }, [isIpadLandscape])

  useEffect(() => {
    const routeContext = parseMenuRoute(routePath, routeSearch)
    setActiveOrderingContext(routeContext)
  }, [routePath, routeSearch])

  const visibleSlots = useMemo(() => tableSlots, [tableSlots])

  const handleEntrySelect = (slotId: string, selection: 'left' | 'right' | 'full') => {
    const target = startOrder(slotId, selection)
    if (!target) {
      return
    }

    const nextContext = {
      slotLabel: target.slotLabel,
      tableLabel: target.slotLabel.split('-')[0] ?? target.slotLabel,
      orderType: 'dine_in',
      pickupLabel: null,
      workstation,
    } as const
    setActiveOrderingContext(nextContext)
    navigateTo(buildMenuPath(nextContext))
  }

  const handleStart = (slotId: string) => {
    const target = startOrder(slotId)
    if (!target) {
      return
    }

    const nextContext = {
      slotLabel: target.slotLabel,
      tableLabel: target.slotLabel.split('-')[0] ?? target.slotLabel,
      orderType: 'dine_in',
      pickupLabel: null,
      workstation,
    } as const
    setActiveOrderingContext(nextContext)
    navigateTo(buildMenuPath(nextContext))
  }

  const handleEdit = (slotId: string) => {
    const target = editOrder(slotId)
    if (!target) {
      return
    }

    const nextContext = {
      slotLabel: target.slotLabel,
      tableLabel: target.slotLabel.split('-')[0] ?? target.slotLabel,
      orderType: 'dine_in',
      pickupLabel: null,
      workstation,
    } as const
    setActiveOrderingContext(nextContext)
    navigateTo(buildMenuPath(nextContext))
  }

  const handleTakeoutEntry = () => {
    const pickupLabel = buildGeneratedTakeoutLabel()
    const nextContext = {
      slotLabel: pickupLabel,
      tableLabel: 'Takeout',
      orderType: 'pickup',
      pickupLabel,
      workstation,
    } as const
    setActiveOrderingContext(nextContext)
    navigateTo(buildMenuPath(nextContext))
  }

  const handleBackToTables = () => {
    setActiveOrderingContext(null)
    navigateTo(boardPath)
    void refreshFromBackend()
  }

  const handleFinish = async (slot: TableSlot) => {
    if (!slot.orderDbId) {
      window.alert('Unable to finish this order because the order id is missing.')
      return
    }

    const confirmed = window.confirm(
      'Finish this order?\nThis will complete the current order and free the table.',
    )

    if (!confirmed) {
      return
    }

    try {
      await completeOrder(slot.orderDbId)
      setSubmissionMessage(`Order completed for ${slot.label}.`)
      await refreshTableAfterFinish(slot.baseTableLabel)

      // The frontdesk board read model can lag slightly behind the order completion mutation.
      // Re-check a few times so split tables collapse back automatically once both sides are done.
      for (let attempt = 0; attempt < 3; attempt += 1) {
        await new Promise((resolve) => window.setTimeout(resolve, 700))
        await refreshTableAfterFinish(slot.baseTableLabel)
      }
    } catch (error) {
      window.alert(error instanceof Error ? error.message : 'Failed to finish order')
    }
  }

  if (activeOrderingContext) {
    return (
      <OrderingPage
        catalog={menuCatalog}
        slotLabel={activeOrderingContext.slotLabel}
        tableLabel={activeOrderingContext.tableLabel}
        orderType={activeOrderingContext.orderType}
        pickupLabel={activeOrderingContext.pickupLabel}
        workstationLabel={workstationLabel}
        onBack={handleBackToTables}
        onDraftCancelled={(slotLabel, tableLabel) => {
          if (activeOrderingContext.orderType === 'dine_in') {
            const seatCode = slotLabel.includes('-') ? (slotLabel.split('-')[1] as 'A' | 'B') : 'full'
            endOrder(tableLabel, seatCode)
          }
          setActiveOrderingContext(null)
          navigateTo(boardPath)
          void refreshFromBackend()
        }}
        onOrderSubmitted={(slotLabel) => {
          setSubmissionMessage(
            activeOrderingContext.orderType === 'pickup'
              ? `Takeout order confirmed for ${activeOrderingContext.pickupLabel ?? slotLabel}.`
              : `Order confirmed for ${slotLabel}.`,
          )
          setActiveOrderingContext(null)
          navigateTo(boardPath)
          void refreshFromBackend()
        }}
      />
    )
  }

  return (
    <div className="min-h-screen bg-[var(--surface)] text-[var(--on-surface)]">
      {workstationCompact ? (
        <div className="px-3 py-3">
          <div className="mx-auto max-w-none space-y-3">
            <FrontdeskTopNav activeItem={null} />
            <DineInTopBar
              onTakeoutClick={handleTakeoutEntry}
              workstationLabel={workstationLabel}
              compact={workstationCompact}
            />

            {submissionMessage ? (
              <div className={`rounded-[20px] bg-[rgba(97,0,0,0.08)] font-semibold text-[var(--primary)] ${workstationCompact ? 'px-4 py-2.5 text-[0.95rem]' : 'px-5 py-4 text-base'}`}>
                {submissionMessage}
              </div>
            ) : null}

            <Card tone="base" className={`bg-[rgba(255,255,255,0.36)] shadow-none ring-0 ${workstationCompact ? 'space-y-3 rounded-[24px] p-3.5' : 'space-y-5 rounded-[30px] p-4 md:p-5 xl:p-5'}`}>
              <TableStatusLegend counts={statusCounts} compact={workstationCompact} />
              <TableGrid
                slots={visibleSlots}
                onEntrySelect={handleEntrySelect}
                onStart={handleStart}
                onEdit={handleEdit}
                onFinish={(slot) => void handleFinish(slot)}
                compact={workstationCompact}
              />
            </Card>
          </div>
        </div>
      ) : (
        <div
          className={`grid min-h-screen transition-[grid-template-columns] duration-200 ${
            sidebarCollapsed
              ? 'xl:grid-cols-[5.75rem_minmax(0,1fr)]'
              : 'xl:grid-cols-[15.5rem_minmax(0,1fr)] 2xl:grid-cols-[17rem_minmax(0,1fr)]'
          }`}
        >
          <DineInSidebar
            collapsed={sidebarCollapsed}
            compact={false}
            onToggleCollapse={() => setSidebarCollapsed((current) => !current)}
          />

          <div className="px-4 py-4 md:px-5 xl:px-6">
            <div className="mx-auto max-w-[1880px] space-y-5">
              <DineInTopBar
                onTakeoutClick={handleTakeoutEntry}
                workstationLabel={workstationLabel}
              />

              {submissionMessage ? (
                <div className="rounded-[24px] bg-[rgba(97,0,0,0.08)] px-5 py-4 text-base font-semibold text-[var(--primary)]">
                  {submissionMessage}
                </div>
              ) : null}

              <Card tone="base" className="space-y-5 rounded-[30px] bg-[rgba(255,255,255,0.36)] p-4 md:p-5 xl:p-5 shadow-none ring-0">
                <TableStatusLegend counts={statusCounts} />
                <TableGrid
                  slots={visibleSlots}
                  onEntrySelect={handleEntrySelect}
                  onStart={handleStart}
                  onEdit={handleEdit}
                  onFinish={(slot) => void handleFinish(slot)}
                />
              </Card>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
