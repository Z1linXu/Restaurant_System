export interface StoreOperationalLifecycle {
  status?: string | null
  lifecycle_status?: string | null
  operational_state?: string | null
  is_live?: boolean | null
}

export function isStoreLive(store: StoreOperationalLifecycle | null | undefined) {
  if (!store) return false
  if (store.is_live != null) return store.is_live === true
  if (store.operational_state != null) return store.operational_state.toUpperCase() === 'LIVE'
  return (store.status ?? '').toLowerCase() === 'active'
    && (store.lifecycle_status ?? '').toUpperCase() === 'ACTIVE'
}
