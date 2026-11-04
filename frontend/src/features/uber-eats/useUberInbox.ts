import { useMemo, useSyncExternalStore } from 'react'
import { useAuth } from '../auth/useAuth'
import { disabledInbox, emptyInboxPage, sharedUberInbox, uniqueUberOrders } from './uberInboxSubscription'
export function useUberInbox(storeId: number | undefined) {
  const auth = useAuth()
  const identity = JSON.stringify([
    auth.user?.id, auth.user?.organization_id, auth.user?.role_code, auth.sessionMode,
    [...auth.permissions].sort(), Object.entries(auth.features).sort(([a], [b]) => a.localeCompare(b)),
  ])
  const enabled = Boolean(storeId && auth.user && auth.sessionMode === 'ONLINE')
  const subscription = useMemo(() => enabled && storeId ? sharedUberInbox(`${storeId}:${identity}`, storeId) : null, [enabled, identity, storeId])
  const state = useSyncExternalStore(subscription?.subscribe ?? noSubscribe, subscription?.getSnapshot ?? getDisabled, getDisabled)
  const today = state.feed?.today ?? emptyInboxPage
  const attention = state.feed?.attention ?? emptyInboxPage
  return {
    workspaceKey: enabled ? identity : null,
    orders: uniqueUberOrders([...today.orders, ...attention.orders]), today, attention,
    businessDate: state.feed?.business_date ?? null, timeZone: state.feed?.time_zone ?? null,
    loading: enabled && state.loading, error: state.error,
    refresh: subscription?.refresh ?? noop, loadMore: subscription?.loadMore ?? noop,
  }
}
function noop() {}
function noSubscribe() { return noop }
function getDisabled() { return disabledInbox }
