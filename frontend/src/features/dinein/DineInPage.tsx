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
import { completeOrder, fetchOrderPrintOptions, reprintOrderReceipt } from '../../services/orderService'
import type { TableSlot } from '../../types/dinein'
import type { OrderPrintOption } from '../../types/ordering'
import { formatSplitSlotLabel } from '../../utils/tableDisplay'
import { DineInSidebar } from './components/DineInSidebar'
import { DineInTopBar } from './components/DineInTopBar'
import { TableGrid } from './components/TableGrid'
import { TableStatusLegend } from './components/TableStatusLegend'
import { useCurrentStore } from '../store/StoreContext'

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
  console.count('DineInPage render')
  const { storeId } = useCurrentStore()
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
  } | null>(() => parseMenuRoute(routePath, routeSearch))
  const [submissionMessage, setSubmissionMessage] = useState<string | null>(null)
  const [printTarget, setPrintTarget] = useState<TableSlot | null>(null)
  const [printOptions, setPrintOptions] = useState<OrderPrintOption[]>([])
  const [printBusy, setPrintBusy] = useState<string | null>(null)
  const [printError, setPrintError] = useState<string | null>(null)
  const menuCatalog = useMenuCatalog(storeId)
  const tableBoardEnabled = activeOrderingContext == null
  const { tableSlots, statusCounts, startOrder, editOrder, endOrder, refreshFromBackend, refreshTableAfterFinish } = useTableBoard({
    enabled: tableBoardEnabled,
    storeId,
  })
  const workstationCompact = isIpadLandscape
  const boardPath = buildFrontdeskBoardPath(workstation, storeId)

  const updateActiveOrderingContext = (
    nextContext: typeof activeOrderingContext,
    reason: string,
  ) => {
    console.count('setActiveOrderingContext called')
    console.log('[DineInPage] setActiveOrderingContext requested', {
      reason,
      previousContext: activeOrderingContext,
      nextContext,
      routePath,
      routeSearch,
    })
    setActiveOrderingContext(nextContext)
  }

  useEffect(() => {
    setSidebarCollapsed(isIpadLandscape)
  }, [isIpadLandscape])

  useEffect(() => {
    const routeContext = parseMenuRoute(routePath, routeSearch)
    console.log('[DineInPage] route parsing effect', {
      routePath,
      routeSearch,
      routeContext,
      currentContext: activeOrderingContext,
    })
    updateActiveOrderingContext(routeContext, 'route parsing effect')
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
    updateActiveOrderingContext(nextContext, 'entry select')
    navigateTo(buildMenuPath(nextContext, storeId))
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
    updateActiveOrderingContext(nextContext, 'start order')
    navigateTo(buildMenuPath(nextContext, storeId))
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
    updateActiveOrderingContext(nextContext, 'edit order')
    navigateTo(buildMenuPath(nextContext, storeId))
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
    updateActiveOrderingContext(nextContext, 'takeout entry')
    navigateTo(buildMenuPath(nextContext, storeId))
  }

  const handleBackToTables = () => {
    updateActiveOrderingContext(null, 'back to tables')
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
      setSubmissionMessage(`Order completed for ${formatSplitSlotLabel(slot.label)}.`)
      await refreshTableAfterFinish(slot.baseTableLabel)

      // Keep one short fallback refresh for read-model lag; realtime and 30s polling handle the rest.
      await new Promise((resolve) => window.setTimeout(resolve, 900))
      await refreshTableAfterFinish(slot.baseTableLabel)
    } catch (error) {
      window.alert(error instanceof Error ? error.message : 'Failed to finish order')
    }
  }

  const handlePrint = async (slot: TableSlot) => {
    if (!slot.orderDbId) {
      window.alert('Unable to print because the order id is missing.')
      return
    }
    setPrintTarget(slot)
    setPrintOptions([])
    setPrintError(null)
    try {
      setPrintOptions(await fetchOrderPrintOptions(slot.orderDbId))
    } catch (error) {
      setPrintError(error instanceof Error ? error.message : 'Failed to load print options')
    }
  }

  const handleManualReprint = async (option: OrderPrintOption) => {
    if (!printTarget?.orderDbId || !option.available) {
      return
    }
    try {
      setPrintBusy(option.module_code)
      setPrintError(null)
      const result = await reprintOrderReceipt(printTarget.orderDbId, option.module_code)
      if (result.status !== 'PRINTED') {
        throw new Error(result.error_message ?? 'Print failed')
      }
      setSubmissionMessage(`${option.label} sent for ${formatSplitSlotLabel(printTarget.label)}.`)
      setPrintTarget(null)
    } catch (error) {
      setPrintError(error instanceof Error ? error.message : 'Print failed')
    } finally {
      setPrintBusy(null)
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
        storeId={storeId}
        onBack={handleBackToTables}
        onDraftCancelled={(slotLabel, tableLabel) => {
          if (activeOrderingContext.orderType === 'dine_in') {
            const seatCode = slotLabel.includes('-') ? (slotLabel.split('-')[1] as 'A' | 'B') : 'full'
            endOrder(tableLabel, seatCode)
          }
          updateActiveOrderingContext(null, 'draft cancelled')
          navigateTo(boardPath)
          void refreshFromBackend()
        }}
        onOrderSubmitted={(slotLabel) => {
          setSubmissionMessage(
            activeOrderingContext.orderType === 'pickup'
              ? `Takeout order confirmed for ${activeOrderingContext.pickupLabel ?? slotLabel}.`
              : `Order confirmed for ${formatSplitSlotLabel(slotLabel)}.`,
          )
          updateActiveOrderingContext(null, 'order submitted')
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
                onPrint={(slot) => void handlePrint(slot)}
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
                  onPrint={(slot) => void handlePrint(slot)}
                  onFinish={(slot) => void handleFinish(slot)}
                />
              </Card>
            </div>
          </div>
        </div>
      )}
      {printTarget ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[rgba(25,20,18,0.36)] p-4" onClick={() => setPrintTarget(null)}>
          <div className="w-full max-w-md rounded-[28px] bg-white p-5 shadow-2xl" onClick={(event) => event.stopPropagation()}>
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-semibold text-[var(--muted)]">Print current full order</p>
                <h2 className="mt-1 text-2xl font-extrabold">{formatSplitSlotLabel(printTarget.label)}</h2>
              </div>
              <button type="button" className="rounded-full px-3 py-2 font-bold" onClick={() => setPrintTarget(null)}>Close</button>
            </div>
            {printError ? <div className="mt-4 rounded-[16px] bg-red-50 px-4 py-3 font-semibold text-red-700">{printError}</div> : null}
            <div className="mt-5 space-y-3">
              {printOptions.length ? printOptions.map((option) => (
                <button
                  key={option.module_code}
                  type="button"
                  disabled={!option.available || printBusy != null}
                  className="min-h-14 w-full rounded-[18px] bg-[var(--primary)] px-4 text-left font-bold text-white disabled:bg-stone-200 disabled:text-stone-500"
                  onClick={() => void handleManualReprint(option)}
                >
                  <span>{printBusy === option.module_code ? 'Printing...' : option.label}</span>
                  {!option.available ? <span className="mt-1 block text-xs font-medium">{option.unavailable_reason}</span> : null}
                </button>
              )) : !printError ? <p className="py-5 text-center text-[var(--muted)]">Loading print options...</p> : null}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
