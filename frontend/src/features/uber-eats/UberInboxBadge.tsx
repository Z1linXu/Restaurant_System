import { useUberInbox } from './useUberInbox'
import { needsUberAttention } from '../../services/uberEatsService'
import { navigateTo } from '../frontdesk/navigation'
export function UberInboxBadge({ storeId }: { storeId: number }) {
  const { orders, error } = useUberInbox(storeId)
  const count = orders.filter(needsUberAttention).length
  return <button type="button" className="min-h-12 rounded-2xl bg-emerald-950 px-4 font-bold text-white" onClick={() => navigateTo(`/stores/${storeId}/frontdesk/uber-eats`)}>Uber Eats {count > 0 && <span className="ml-2 rounded-full bg-white px-2 text-emerald-950">{count}</span>}{error && <span aria-label="Uber 订单连接异常"> !</span>}</button>
}
