import { useEffect, useState } from 'react'
import {
  LOCAL_DRAFT_UPDATED_EVENT,
  listLocalDraftsForScope,
  type LocalDraftScope,
} from '../../offline/localDrafts'
import {
  ORDER_OUTBOX_UPDATED_EVENT,
  listOrderOutboxForAccount,
  type OrderOutboxRecord,
} from '../../offline/orderOutbox'
import {
  isActiveOfflineOrderState,
  type OfflineOrderBadge,
} from './offlineOrderStatus'
import { fetchOrderDetail } from '../../services/orderService'
import {
  collectServerOrderIds,
  finalizeOfflineOrderRecords,
  terminalLocalStateForServerStatus,
} from '../../offline/orderLifecycle'

function itemCount(items: Array<{ quantity: number }>) {
  return items.reduce((total, item) => total + item.quantity, 0)
}

export const OFFLINE_RECONCILIATION_CONCURRENCY = 3

export async function runBoundedReconciliation<T>(
  values: readonly T[],
  worker: (value: T) => Promise<void>,
  isActive: () => boolean = () => true,
  concurrency = OFFLINE_RECONCILIATION_CONCURRENCY,
) {
  let nextIndex = 0
  const workerCount = Math.min(Math.max(1, concurrency), values.length)
  const runWorker = async () => {
    while (isActive()) {
      const index = nextIndex
      nextIndex += 1
      if (index >= values.length) return
      await worker(values[index])
    }
  }

  await Promise.all(Array.from({ length: workerCount }, () => runWorker()))
}

export function useStoreOfflineOrders(scope: LocalDraftScope | null, enabled = true) {
  const [orders, setOrders] = useState<OfflineOrderBadge[]>([])
  const accountId = scope?.accountId ?? null
  const organizationId = scope?.organizationId ?? null
  const storeId = scope?.storeId ?? null

  useEffect(() => {
    setOrders([])
    if (accountId == null || organizationId == null || storeId == null || !enabled) return
    const activeScope = { accountId, organizationId, storeId }

    let active = true
    let reloadTimer: number | null = null
    let loadInFlight = false
    let loadPending = false

    const loadOnce = async () => {
      try {
        let [drafts, accountOutbox] = await Promise.all([
          listLocalDraftsForScope(activeScope),
          listOrderOutboxForAccount(activeScope.accountId),
        ])
        if (!active) return
        const scopedOutbox = accountOutbox.filter((record) => (
          record.organizationId === activeScope.organizationId && record.storeId === activeScope.storeId
        ))
        if (typeof navigator === 'undefined' || navigator.onLine) {
          let terminalRecordChanged = false
          const serverOrderIds = collectServerOrderIds(drafts, scopedOutbox)
          await runBoundedReconciliation(
            serverOrderIds,
            async (serverOrderId) => {
              if (!active) return
              try {
                const serverOrder = await fetchOrderDetail(serverOrderId)
                if (!active) return
                if (!terminalLocalStateForServerStatus(serverOrder.status)) return
                const finalized = await finalizeOfflineOrderRecords(activeScope, serverOrder.id, serverOrder.status)
                terminalRecordChanged ||= finalized.drafts > 0 || finalized.outbox > 0
              } catch {
                // Reconciliation is best effort; offline records remain protected until the server is reachable.
              }
            },
            () => active,
          )
          if (!active) return
          if (terminalRecordChanged) {
            ;[drafts, accountOutbox] = await Promise.all([
              listLocalDraftsForScope(activeScope),
              listOrderOutboxForAccount(activeScope.accountId),
            ])
          }
        }
        if (!active) return

        const scopedLatestOutbox = accountOutbox
          .filter((record) => record.organizationId === activeScope.organizationId && record.storeId === activeScope.storeId)
          .reduce((records, record) => {
            const current = records.get(record.clientOrderId)
            if (!current || record.updatedAt.localeCompare(current.updatedAt) >= 0) {
              records.set(record.clientOrderId, record)
            }
            return records
          }, new Map<string, OrderOutboxRecord>())
        const badgesByClientOrderId = new Map<string, OfflineOrderBadge>()

        // The outbox is authoritative for a client id. Building it first prevents a
        // stale draft row from duplicating or resurrecting an outbox row.
        scopedLatestOutbox.forEach((outbox) => {
          if (!isActiveOfflineOrderState(outbox.state)) return
          badgesByClientOrderId.set(outbox.clientOrderId, {
            clientOrderId: outbox.clientOrderId,
            contextLabel: outbox.frozenPayload.table_no ?? outbox.frozenPayload.pickup_no ?? '本机订单',
            tableNo: outbox.frozenPayload.table_no,
            pickupNo: outbox.frozenPayload.pickup_no,
            state: outbox.state,
            itemCount: itemCount(outbox.frozenPayload.items),
            updatedAt: outbox.updatedAt,
            lastErrorCode: outbox.lastErrorCode,
          })
        })

        drafts.forEach((draft) => {
          if (draft.mode !== 'LOCAL_NEW_ORDER' || draft.items.length === 0) return
          if (scopedLatestOutbox.has(draft.clientOrderId)) return
          if (!isActiveOfflineOrderState(draft.submitState)) return
          const current = badgesByClientOrderId.get(draft.clientOrderId)
          if (current && current.updatedAt.localeCompare(draft.updatedAt) > 0) return
          badgesByClientOrderId.set(draft.clientOrderId, {
            clientOrderId: draft.clientOrderId,
            contextLabel: draft.context.tableNo ?? draft.context.pickupNo ?? draft.context.slotLabel,
            tableNo: draft.context.tableNo,
            pickupNo: draft.context.pickupNo,
            state: draft.submitState,
            itemCount: itemCount(draft.items),
            updatedAt: draft.updatedAt,
            lastErrorCode: null,
          })
        })

        const badges = [...badgesByClientOrderId.values()]
        badges.sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
        setOrders(badges)
      } catch {
        if (active) setOrders([])
      }
    }

    const load = async () => {
      if (!active) return
      if (loadInFlight) {
        loadPending = true
        return
      }
      loadInFlight = true
      try {
        do {
          loadPending = false
          await loadOnce()
        } while (active && loadPending)
      } finally {
        loadInFlight = false
      }
    }
    const scheduleLoad = () => {
      if (reloadTimer != null) window.clearTimeout(reloadTimer)
      reloadTimer = window.setTimeout(() => {
        reloadTimer = null
        void load()
      }, 80)
    }

    void load()
    window.addEventListener(LOCAL_DRAFT_UPDATED_EVENT, scheduleLoad)
    window.addEventListener(ORDER_OUTBOX_UPDATED_EVENT, scheduleLoad)
    window.addEventListener('online', scheduleLoad)
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') scheduleLoad()
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      active = false
      if (reloadTimer != null) window.clearTimeout(reloadTimer)
      window.removeEventListener(LOCAL_DRAFT_UPDATED_EVENT, scheduleLoad)
      window.removeEventListener(ORDER_OUTBOX_UPDATED_EVENT, scheduleLoad)
      window.removeEventListener('online', scheduleLoad)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [accountId, enabled, organizationId, storeId])

  return enabled && accountId != null && organizationId != null && storeId != null ? orders : []
}
