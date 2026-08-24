import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ItemCustomizationDraft, MenuItem, OrderLineItem } from '../types/ordering'
import { buildFrozenSubmitPayload } from '../hooks/useDraftOrder'
import {
  isTerminalPrintJobPollingError,
  mapOptions,
  startOrderPrintJobCoordinator,
} from './orderService'
import { ApiRequestError, apiRequest } from './apiClient'
import type { LocalDraftRecord } from '../offline/localDrafts'

vi.mock('./apiClient', async () => {
  const actual = await vi.importActual<typeof import('./apiClient')>('./apiClient')
  return {
    ...actual,
    apiRequest: vi.fn(),
  }
})

const mockedApiRequest = vi.mocked(apiRequest)

function menuItem(overrides: Partial<MenuItem> = {}): MenuItem {
  return {
    id: '71',
    sku: 'traditional_beef_noodle',
    categoryId: '11',
    categoryCode: 'SOUP_NOODLE',
    stationId: '3',
    itemType: 'noodle',
    nameEn: 'Traditional Beef Noodle',
    nameZh: '传统牛肉面',
    descriptionEn: '',
    descriptionZh: '',
    price: 16,
    customization: {
      combo: {
        optionId: '710',
        option: { id: '710', labelEn: 'Combo', labelZh: '套餐', priceDelta: 5, optionCode: 'combo', optionGroup: 'COMBO' },
        upcharge: 5,
        groups: [
          {
            groupCode: 'COMBO_DRINK',
            labelEn: 'Drink',
            labelZh: '饮料',
            selectionRule: 'OPTIONAL_ONE',
            required: false,
            defaultOptionId: '3002',
            options: [
              { id: '3002', labelEn: 'Sprite', labelZh: '雪碧', optionCode: 'combo_sprite', optionGroup: 'COMBO_DRINK' },
            ],
          },
        ],
        eggs: [],
        sides: [],
        sideRemoveOptions: [],
      },
    },
    ...overrides,
  }
}

function draft(overrides: Partial<ItemCustomizationDraft> = {}): ItemCustomizationDraft {
  return {
    comboEnabled: true,
    comboSelections: { COMBO_DRINK: '3001' },
    comboSideRemoveIds: [],
    addOnQuantities: {},
    removeIds: [],
    quantity: 1,
    notes: '',
    ...overrides,
  }
}

describe('order option mapping for dynamic combo groups', () => {
  it('keeps noodle display numbering out of the order payload snapshot', () => {
    const item = menuItem({
      customization: {
        noodleTypes: [{
          id: '720',
          labelEn: 'Thin',
          labelZh: '细',
          optionType: 'noodle_type',
          optionCode: 'noodle_thin',
          optionGroup: 'NOODLE_TYPE',
        }],
      },
    })

    expect(mapOptions(draft({ comboEnabled: false, noodleTypeId: '720' }), item)).toEqual([expect.objectContaining({
      option_id: 720,
      option_code_snapshot: 'noodle_thin',
      option_name_snapshot_en: 'Thin',
      option_name_snapshot_zh: '细',
    })])
  })

  it('uses the current default when a disabled dynamic component disappears from the menu snapshot', () => {
    const options = mapOptions(draft(), menuItem())

    expect(options).toEqual(expect.arrayContaining([
      expect.objectContaining({ option_id: 710, option_code_snapshot: 'combo', option_group_snapshot: 'COMBO' }),
      expect.objectContaining({ option_id: 3002, option_code_snapshot: 'combo_sprite', option_group_snapshot: 'COMBO_DRINK' }),
    ]))
    expect(options).not.toEqual(expect.arrayContaining([
      expect.objectContaining({ option_id: 3001 }),
    ]))
  })

  it('preserves existing frozen draft option snapshots instead of silently rewriting old lines', () => {
    const frozenLine: OrderLineItem = {
      id: 'line-1',
      menuItemId: '71',
      nameEn: 'Traditional Beef Noodle',
      nameZh: '传统牛肉面',
      quantity: 1,
      unitPrice: 16,
      lineSubtotal: 21,
      categoryCodeSnapshot: 'SOUP_NOODLE',
      stationIdSnapshot: '3',
      skuSnapshot: 'traditional_beef_noodle',
      itemTypeSnapshot: 'noodle',
      optionSnapshots: [
        {
          optionId: '710',
          optionType: 'addon',
          optionCode: 'combo',
          optionGroup: 'COMBO',
          parentOptionId: null,
          nameEn: 'Combo',
          nameZh: '套餐',
          priceDelta: 5,
          quantity: 1,
        },
        {
          optionId: '3001',
          optionType: 'addon',
          optionCode: 'combo_coke',
          optionGroup: 'COMBO_DRINK',
          parentOptionId: null,
          nameEn: 'Coke',
          nameZh: '可乐',
          priceDelta: 0,
          quantity: 1,
        },
      ],
      selection: draft(),
      summaryTags: [{ en: 'Coke', zh: '可乐' }],
      notes: '',
    }
    const record = {
      accountId: 1,
      organizationId: 9,
      storeId: 1,
      key: 'draft',
      localDraftId: 'draft-1',
      clientOrderId: 'client-1',
      contextKey: 'table:T1',
      context: {
        orderType: 'dine_in',
        slotLabel: 'T1',
        tableLabel: 'T1',
        tableNo: 'T1',
        pickupNo: null,
      },
      mode: 'LOCAL_NEW_ORDER',
      serverOrderId: null,
      serverOrderSnapshot: null,
      items: [frozenLine],
      menuRevision: 12,
      createdAt: '2026-08-14T00:00:00.000Z',
      updatedAt: '2026-08-14T00:00:00.000Z',
      submitState: 'LOCAL_DRAFT',
      payloadHash: 'hash',
      lastError: null,
      nextRetryAt: null,
      schemaVersion: 1,
    } satisfies LocalDraftRecord

    const payload = buildFrozenSubmitPayload(record, [frozenLine], [menuItem()])

    expect(payload.items[0].options).toEqual(expect.arrayContaining([
      expect.objectContaining({ option_id: 3001, option_code_snapshot: 'combo_coke', option_group_snapshot: 'COMBO_DRINK' }),
    ]))
  })
})

function printJob(status: 'PENDING' | 'PRINTED' | 'FAILED' | 'CANCELLED', moduleCode: string): import('./printingAdminService').PrintJobRecord {
  return {
    id: moduleCode === 'GRAB' ? 1 : 2,
    store_id: 7,
    order_id: 91,
    module_code: moduleCode,
    receipt_type: moduleCode,
    status,
    created_at: '2026-08-22T00:00:00.000Z',
  }
}

function printOption(moduleCode: string, available = true): import('../types/ordering').OrderPrintOption {
  return {
    module_code: moduleCode,
    label: moduleCode,
    available,
    unavailable_reason: available ? null : 'disabled',
  }
}

describe('frontdesk print-job coordinator', () => {
  afterEach(() => {
    vi.useRealTimers()
    mockedApiRequest.mockReset()
  })

  it('keeps one coordinator per Store/order even when the update batch changes', async () => {
    vi.useFakeTimers()
    mockedApiRequest
      .mockResolvedValueOnce([printOption('GRAB'), printOption('FRONTDESK_RECEIPT')] as never)
      .mockResolvedValueOnce([
        printJob('PRINTED', 'GRAB'),
        printJob('PRINTED', 'FRONTDESK_RECEIPT'),
      ] as never)
    const onAttention = vi.fn()

    startOrderPrintJobCoordinator({
      storeId: 7,
      orderId: 91,
      updateBatchId: null,
      delaysMs: [0],
      onAttention,
    })
    startOrderPrintJobCoordinator({
      storeId: 7,
      orderId: 91,
      updateBatchId: 12,
      delaysMs: [0],
      onAttention,
    })

    await vi.advanceTimersByTimeAsync(0)
    expect(mockedApiRequest).toHaveBeenCalledTimes(2)
    expect(onAttention).not.toHaveBeenCalled()
  })

  it('completes against the Store enabled-role options instead of hard-coding GRAB plus receipt', async () => {
    vi.useFakeTimers()
    mockedApiRequest
      .mockResolvedValueOnce([
        printOption('GRAB', false),
        printOption('FRONTDESK_RECEIPT', false),
        printOption('HOT_KITCHEN', true),
      ] as never)
      .mockResolvedValueOnce([printJob('PRINTED', 'HOT_KITCHEN')] as never)

    startOrderPrintJobCoordinator({
      storeId: 7,
      orderId: 91,
      delaysMs: [0, 1, 2],
      onAttention: vi.fn(),
    })

    await vi.advanceTimersByTimeAsync(10)
    expect(mockedApiRequest).toHaveBeenCalledTimes(2)
  })

  it('terminates polling for access, conflict, and capability failures', async () => {
    vi.useFakeTimers()
    mockedApiRequest.mockRejectedValue(new ApiRequestError(403, 'denied'))
    const onUnavailable = vi.fn()
    startOrderPrintJobCoordinator({
      storeId: 7,
      orderId: 91,
      delaysMs: [0, 1, 2],
      onAttention: vi.fn(),
      onUnavailable,
    })

    await vi.advanceTimersByTimeAsync(0)
    await vi.advanceTimersByTimeAsync(10)
    expect(mockedApiRequest).toHaveBeenCalledTimes(1)
    expect(onUnavailable).toHaveBeenCalledTimes(1)
    expect(isTerminalPrintJobPollingError(new ApiRequestError(409, 'conflict'))).toBe(true)
    expect(isTerminalPrintJobPollingError(new ApiRequestError(200, 'MODULE_HARDWARE_CAPABILITY_MISSING'))).toBe(true)
  })

  it('cancels delayed polling without a stale request', async () => {
    vi.useFakeTimers()
    const coordinator = startOrderPrintJobCoordinator({
      storeId: 7,
      orderId: 91,
      delaysMs: [100],
      onAttention: vi.fn(),
    })
    coordinator.cancel()
    await vi.advanceTimersByTimeAsync(200)
    expect(mockedApiRequest).not.toHaveBeenCalled()
  })
})
