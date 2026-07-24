import { describe, expect, it } from 'vitest'
import {
  offlineOrderMatchesTable,
  isActiveOfflineOrderState,
  offlineOrderStatusView,
  type OfflineOrderBadge,
} from './offlineOrderStatus'

describe('offline ordering operator status', () => {
  it.each([
    ['LOCAL_DRAFT', '订单仅保存在本机，尚未进入厨房。', false],
    ['QUEUED', '等待网络自动提交，请勿重复下单。', false],
    ['SUBMITTING', '正在提交订单，请等待服务器确认。', false],
    ['SUBMITTED', '订单已成功进入服务器和厨房。', true],
    ['FAILED_RETRYABLE', '提交暂时失败，将在网络恢复后自动重试。', false],
    ['FAILED_VALIDATION', '服务器未接受该订单。请检查提示后返回修改，本机菜品仍然保留。', false],
    ['CONFLICT', '订单状态存在冲突，请确认当前桌台订单后再继续。', false],
    ['COMPLETED', '服务器已完成该订单，本机记录不会再恢复为草稿。', true],
    ['CANCELLED', '服务器已取消该订单，本机记录不会再恢复为草稿。', true],
  ] as const)('maps %s to an unambiguous kitchen-confirmation message', (state, message, confirmed) => {
    const view = offlineOrderStatusView(state)
    expect(view.description).toBe(message)
    expect(view.serverConfirmed).toBe(confirmed)
  })

  it('only counts records whose kitchen confirmation is unresolved', () => {
    expect(['LOCAL_DRAFT', 'QUEUED', 'SUBMITTING', 'FAILED_RETRYABLE', 'CONFLICT'].filter(
      (state) => isActiveOfflineOrderState(state as never),
    )).toEqual(['LOCAL_DRAFT', 'QUEUED', 'SUBMITTING', 'FAILED_RETRYABLE', 'CONFLICT'])
    expect(['SUBMITTED', 'FAILED_VALIDATION', 'COMPLETED', 'CANCELLED', 'CANCELLED_LOCAL'].some(
      (state) => isActiveOfflineOrderState(state as never),
    )).toBe(false)
  })

  it('matches split-table drafts only to their own seat and to an empty table entry card', () => {
    const order: OfflineOrderBadge = {
      clientOrderId: 'client-1',
      contextLabel: 'T1-A',
      tableNo: 'T1-A',
      pickupNo: null,
      state: 'QUEUED',
      itemCount: 2,
      updatedAt: '2026-07-13T10:00:00Z',
      lastErrorCode: null,
    }

    expect(offlineOrderMatchesTable(order, {
      label: 'T1-A',
      baseTableLabel: 'T1',
      action: 'edit',
      backendTableNo: 'T1-A',
    })).toBe(true)
    expect(offlineOrderMatchesTable(order, {
      label: 'T1-B',
      baseTableLabel: 'T1',
      action: 'start',
    })).toBe(false)
    expect(offlineOrderMatchesTable(order, {
      label: 'T1',
      baseTableLabel: 'T1',
      action: 'entry',
    })).toBe(true)
  })
})
