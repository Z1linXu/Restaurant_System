import { describe, expect, it } from 'vitest'
import type { DiningTable } from '../types/dinein'
import { buildTableSlots, mapBackendDiningTable, visibleTableSlotsForStore } from './useTableBoard'

function table(overrides: Partial<DiningTable> = {}): DiningTable {
  return {
    id: 1,
    label: 'C1',
    seats: 4,
    zone: 'MAIN',
    tableConfig: 'split_supported',
    occupancyMode: 'empty',
    ...overrides,
  }
}

describe('Frontdesk Store-scoped table presentation', () => {
  it('maps only backend table data and never synthesizes demo T1-T8 fixtures', () => {
    const mapped = mapBackendDiningTable({
      id: 18,
      store_id: 21,
      table_code: 'C1',
      table_name: 'Chinatown 1',
      capacity: 4,
      area_name: 'MAIN',
      table_config: 'split_supported',
      supports_split: true,
      sort_order: 1,
      is_active: true,
    })

    expect(mapped.label).toBe('Chinatown 1')
    expect(buildTableSlots([])).toEqual([])
    expect(JSON.stringify([mapped])).not.toMatch(/T[1-8]/)
  })

  it('hides old Store rows synchronously during a rapid Store switch', () => {
    expect(visibleTableSlotsForStore([table()], 18, 21)).toEqual([])
    expect(visibleTableSlotsForStore([table()], 21, 21)).toHaveLength(1)
  })

  it('preserves left, right, and whole-table entry semantics', () => {
    const [entry] = buildTableSlots([table()])
    expect(entry.action).toBe('entry')
    expect(entry.mode).toBe('full')

    const split = buildTableSlots([table({
      occupancyMode: 'split',
      splitOrders: {
        A: { orderId: 'A-1', orderDbId: 101, backendTableNo: 'C1-A' },
      },
    })])
    expect(split.map((slot) => [slot.seatCode, slot.action])).toEqual([
      ['A', 'edit'],
      ['B', 'start'],
    ])
  })
})
