import { useEffect, useState } from 'react'
import { fetchUberOrders, type UberOrder } from '../../services/uberEatsService'
import { subscribeToFrontdeskOrders } from '../../services/orderService'
export function useUberInbox(storeId: number | undefined) {
  const [state, setState] = useState<{ store: number | undefined; orders: UberOrder[]; loading: boolean; error: string | null }>({ store: storeId, orders: [], loading: true, error: null })
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    if (!storeId) return
    let active = true
    let inFlight = false
    const load = async () => {
      if (inFlight) return
      inFlight = true
      try { const orders = await fetchUberOrders(storeId); if (active) setState({ store: storeId, orders, loading: false, error: null }) }
      catch (error) { if (active) setState(previous => ({ store: storeId, orders: previous.store === storeId ? previous.orders : [], loading: false, error: error instanceof Error ? error.message : '无法加载 Uber 订单' })) }
      finally { inFlight = false }
    }
    void load()
    const interval = window.setInterval(() => { if (!document.hidden) void load() }, 5000)
    const unsubscribe = subscribeToFrontdeskOrders(storeId, () => { void load() })
    return () => { active = false; window.clearInterval(interval); unsubscribe() }
  }, [storeId, revision])
  return { orders: state.store === storeId ? state.orders : [], loading: state.store !== storeId || state.loading, error: state.store === storeId ? state.error : null, refresh: () => setRevision(value => value + 1) }
}
