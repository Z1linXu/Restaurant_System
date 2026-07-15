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

export function useStoreOfflineOrders(scope: LocalDraftScope | null, enabled = true) {
  const [orders, setOrders] = useState<OfflineOrderBadge[]>([])
  const accountId = scope?.accountId ?? null
  const organizationId = scope?.organizationId ?? null
  const storeId = scope?.storeId ?? null

  useEffect(() => {
    if (accountId == null || organizationId == null || storeId == null || !enabled) return
    const activeScope = { accountId, organizationId, storeId }

    let active = true
    let reloadTimer: number | null = null
    const load = async () => {
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
          await Promise.all(serverOrderIds.map(async (serverOrderId) => {
            try {
              const serverOrder = await fetchOrderDetail(serverOrderId)
              if (!terminalLocalStateForServerStatus(serverOrder.status)) return
              const finalized = await finalizeOfflineOrderRecords(activeScope, serverOrder.id, serverOrder.status)
              terminalRecordChanged ||= finalized.drafts > 0 || finalized.outbox > 0
            } catch {
              // Reconciliation is best effort; offline records remain protected until the server is reachable.
            }
          }))
          if (terminalRecordChanged) {
            ;[drafts, accountOutbox] = await Promise.all([
              listLocalDraftsForScope(activeScope),
              listOrderOutboxForAccount(activeScope.accountId),
            ])
          }
        }
        if (!active) return
        const outboxByClientId = new Map(
          accountOutbox
            .filter((record) => record.organizationId === activeScope.organizationId && record.storeId === activeScope.storeId)
            .map((record) => [record.clientOrderId, record]),
        )
        const seen = new Set<string>()
        const badges: OfflineOrderBadge[] = []

        drafts.forEach((draft) => {
          if (draft.mode !== 'LOCAL_NEW_ORDER' || draft.items.length === 0) return
          const outbox = outboxByClientId.get(draft.clientOrderId)
          const state = outbox?.state ?? draft.submitState
          if (!isActiveOfflineOrderState(state)) return
          seen.add(draft.clientOrderId)
          badges.push({
            clientOrderId: draft.clientOrderId,
            contextLabel: draft.context.tableNo ?? draft.context.pickupNo ?? draft.context.slotLabel,
            tableNo: draft.context.tableNo,
            pickupNo: draft.context.pickupNo,
            state,
            itemCount: itemCount(draft.items),
            updatedAt: outbox?.updatedAt ?? draft.updatedAt,
            lastErrorCode: outbox?.lastErrorCode ?? null,
          })
        })

        accountOutbox.forEach((outbox: OrderOutboxRecord) => {
          if (seen.has(outbox.clientOrderId)
            || outbox.organizationId !== activeScope.organizationId
            || outbox.storeId !== activeScope.storeId
            || !isActiveOfflineOrderState(outbox.state)) return
          badges.push({
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
        badges.sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
        setOrders(badges)
      } catch {
        if (active) setOrders([])
      }
    }
    const scheduleLoad = () => {
      if (reloadTimer != null) window.clearTimeout(reloadTimer)
      reloadTimer = window.setTimeout(() => void load(), 80)
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
