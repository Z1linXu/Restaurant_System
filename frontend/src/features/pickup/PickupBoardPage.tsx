import { Card } from '../../components/ui/Card'
import { useIpadLandscape } from '../../hooks/useIpadLandscape'
import { usePickupBoard } from '../../hooks/usePickupBoard'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { PickupOrderCard } from './components/PickupOrderCard'
import { useCurrentStore } from '../store/StoreContext'

export function PickupBoardPage() {
  const { storeId } = useCurrentStore()
  const isIpadLandscape = useIpadLandscape()
  const { orders, busyTaskIds, error, completeItem, completeAll } = usePickupBoard(storeId)

  return (
    <div className={`min-h-screen bg-[var(--surface)] ${isIpadLandscape ? 'px-3 py-3' : 'px-5 py-4 md:px-7 xl:px-8'}`}>
      <div className={`mx-auto ${isIpadLandscape ? 'max-w-none space-y-3' : 'max-w-[1720px] space-y-6'}`}>
        <FrontdeskTopNav activeItem="pickup" />

        <div className={`rounded-[24px] bg-[rgba(255,255,255,0.74)] shadow-[0_14px_32px_rgba(26,28,25,0.05)] ${isIpadLandscape ? 'px-4 py-3' : 'px-6 py-5'}`}>
          <div className="flex items-end justify-between gap-4">
            <div>
              <p className="text-[0.76rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Pickup / Handoff</p>
              <h1 className={`mt-1 font-display font-extrabold tracking-[-0.05em] text-[var(--on-surface)] ${isIpadLandscape ? 'text-[2rem]' : 'text-[2.5rem]'}`}>
                Ready Board
              </h1>
            </div>
            <div className="text-right text-[0.82rem] font-medium text-[var(--muted)]">
              Current ready items update live as kitchen marks them ready.
            </div>
          </div>
        </div>

        {error ? (
          <div className="rounded-[20px] bg-[rgba(97,0,0,0.08)] px-4 py-3 font-medium text-[var(--primary)]">{error}</div>
        ) : null}

        <div className={`grid ${isIpadLandscape ? 'grid-cols-[17rem_minmax(0,1fr)] gap-3' : 'gap-5 xl:grid-cols-[18rem_minmax(0,1fr)]'}`}>
          <Card tone="well" className={`${isIpadLandscape ? 'rounded-[24px] p-4' : 'rounded-[30px] p-5'}`}>
            <p className="text-[0.76rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Preparing</p>
            <div className="mt-3 rounded-[18px] bg-[rgba(26,28,25,0.04)] p-4">
              <p className="text-[0.96rem] font-semibold text-[var(--on-surface)]">Watching current ready shelf</p>
              <p className="mt-2 text-[0.84rem] leading-6 text-[var(--muted)]">
                Only items currently marked
                <span className="font-semibold text-[var(--on-surface)]"> READY_FOR_PICKUP</span>
                {' '}appear on this board. Delivery orders are excluded.
              </p>
            </div>
          </Card>

          <Card tone="feature" className={`${isIpadLandscape ? 'rounded-[24px] p-4' : 'rounded-[30px] p-5'}`}>
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <p className="text-[0.76rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Ready / Pickup Board</p>
                <p className="mt-1 text-[0.92rem] font-medium text-[var(--muted)]">Oldest ready orders first. Delivery is excluded.</p>
              </div>
              <div className="rounded-full bg-[rgba(97,0,0,0.08)] px-3 py-1.5 text-[0.82rem] font-semibold text-[var(--primary)]">
                {orders.length} orders
              </div>
            </div>

            {orders.length ? (
              <div className={`grid ${isIpadLandscape ? 'gap-3 md:grid-cols-2' : 'gap-4 xl:grid-cols-2'}`}>
                {orders.map((order) => (
                  <PickupOrderCard
                    key={order.orderId}
                    order={order}
                    busyTaskIds={busyTaskIds}
                    onCompleteItem={(taskId) => void completeItem(taskId)}
                    onCompleteAll={(orderId) => void completeAll(orderId)}
                  />
                ))}
              </div>
            ) : (
              <div className="flex min-h-[24rem] items-center justify-center rounded-[22px] bg-[rgba(26,28,25,0.04)] text-center">
                <div>
                  <p className="text-[1.05rem] font-semibold text-[var(--on-surface)]">No ready items yet</p>
                  <p className="mt-2 text-[0.88rem] text-[var(--muted)]">As soon as kitchen marks an item ready, it will appear here.</p>
                </div>
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}
