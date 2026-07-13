import type { LocalDraftSubmitState } from '../../offline/localDrafts'

export type OfflineOrderStatusTone = 'neutral' | 'warning' | 'progress' | 'success' | 'danger'

export interface OfflineOrderStatusView {
  title: string
  description: string
  tone: OfflineOrderStatusTone
  serverConfirmed: boolean
  canRetry: boolean
  canReturnToDraft: boolean
  canCancelLocal: boolean
}

export interface OfflineOrderBadge {
  clientOrderId: string
  contextLabel: string
  tableNo: string | null
  pickupNo: string | null
  state: LocalDraftSubmitState
  itemCount: number
  updatedAt: string
  lastErrorCode: string | null
}

const STATUS_VIEWS: Record<LocalDraftSubmitState, OfflineOrderStatusView> = {
  LOCAL_DRAFT: {
    title: '本机草稿',
    description: '订单仅保存在本机，尚未进入厨房。',
    tone: 'neutral',
    serverConfirmed: false,
    canRetry: false,
    canReturnToDraft: false,
    canCancelLocal: true,
  },
  QUEUED: {
    title: '等待网络提交',
    description: '等待网络自动提交，请勿重复下单。',
    tone: 'warning',
    serverConfirmed: false,
    canRetry: true,
    canReturnToDraft: true,
    canCancelLocal: true,
  },
  SUBMITTING: {
    title: '正在提交',
    description: '正在提交订单，请等待服务器确认。',
    tone: 'progress',
    serverConfirmed: false,
    canRetry: false,
    canReturnToDraft: false,
    canCancelLocal: false,
  },
  SUBMITTED: {
    title: '服务器已确认',
    description: '订单已成功进入服务器和厨房。',
    tone: 'success',
    serverConfirmed: true,
    canRetry: false,
    canReturnToDraft: false,
    canCancelLocal: false,
  },
  FAILED_RETRYABLE: {
    title: '将自动重试',
    description: '提交暂时失败，将在网络恢复后自动重试。',
    tone: 'warning',
    serverConfirmed: false,
    canRetry: true,
    canReturnToDraft: true,
    canCancelLocal: true,
  },
  CONFLICT: {
    title: '需要人工检查',
    description: '菜单、价格或菜品状态已变化，请检查订单。',
    tone: 'danger',
    serverConfirmed: false,
    canRetry: false,
    canReturnToDraft: true,
    canCancelLocal: true,
  },
  CANCELLED_LOCAL: {
    title: '本机提交已取消',
    description: '该本机订单没有进入服务器或厨房。',
    tone: 'neutral',
    serverConfirmed: false,
    canRetry: false,
    canReturnToDraft: false,
    canCancelLocal: false,
  },
}

export function offlineOrderStatusView(state: LocalDraftSubmitState) {
  return STATUS_VIEWS[state]
}

export function isActiveOfflineOrderState(state: LocalDraftSubmitState) {
  return state !== 'SUBMITTED' && state !== 'CANCELLED_LOCAL'
}

export function offlineOrderBadgeLabel(state: LocalDraftSubmitState) {
  return offlineOrderStatusView(state).title
}

export function offlineOrderMatchesTable(
  order: OfflineOrderBadge,
  slot: { label: string; baseTableLabel: string; action: 'entry' | 'start' | 'edit'; backendTableNo?: string },
) {
  if (!order.tableNo) return false
  const exactTable = slot.backendTableNo ?? slot.label
  if (order.tableNo === exactTable) return true
  return slot.action === 'entry'
    && (order.tableNo === slot.baseTableLabel || order.tableNo.startsWith(`${slot.baseTableLabel}-`))
}
