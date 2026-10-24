import { describe, expect, it } from 'vitest'
import type { BackendDiningTableConfig, SplitLabelMode } from '../types/dinein'
import { formatDiningTableLabel, formatSplitSlotLabel, normalizeSplitLabelMode, orderLocationDisplay, splitSideLabel } from './tableDisplay'

const table = (name: string, mode: SplitLabelMode = 'LEFT_RIGHT', id = 1): BackendDiningTableConfig => ({
  id, store_id: 2, table_code: 'T12', table_name: name, area_name: 'Main', table_config: 'split_supported',
  capacity: 4, supports_split: true, sort_order: 1, is_active: true, split_label_mode: mode,
})

describe('split table display is separate from raw slot identity', () => {
  it.each([['1-A', '1-左'], ['1-B', '1-右']])('keeps existing default LEFT_RIGHT output for %s', (raw, display) => {
    expect(formatDiningTableLabel(raw, [table('1')])).toBe(display)
    expect(formatSplitSlotLabel(raw)).toBe(display)
  })
  it.each([['1-A', '1里'], ['1-B', '1外']])('shows INSIDE_OUTSIDE for %s without changing raw input', (raw, display) => {
    const tables = [table('1', 'INSIDE_OUTSIDE')]
    const before = JSON.stringify(tables)
    expect(formatDiningTableLabel(raw, tables)).toBe(display)
    expect(JSON.stringify(tables)).toBe(before)
    expect(raw).toMatch(/-[AB]$/)
  })
  it('matches full table names and final seat suffixes, preserving literal Chinese names and duplicate table codes', () => {
    const tables = [table('1-左', 'INSIDE_OUTSIDE'), table('1里', 'LEFT_RIGHT', 2), table('10', 'LEFT_RIGHT', 3), table('11', 'INSIDE_OUTSIDE', 4)]
    expect(formatDiningTableLabel('1-左', tables)).toBe('1-左')
    expect(formatDiningTableLabel('1-左-A', tables)).toBe('1-左里')
    expect(formatDiningTableLabel('1里-B', tables)).toBe('1里-右')
    expect(formatDiningTableLabel('10-A', tables)).toBe('10-左')
    expect(formatDiningTableLabel('11-A', tables)).toBe('11里')
  })
  it('keeps an exact configured name ending in A as a full table', () => {
    expect(formatDiningTableLabel('1-A', [table('1'), table('1-A', 'INSIDE_OUTSIDE', 2)])).toBe('1-A')
  })
  it('falls back to LEFT_RIGHT when multiple tables share the same name', () => {
    expect(formatDiningTableLabel('1-A', [table('1', 'INSIDE_OUTSIDE'), table('1', 'LEFT_RIGHT', 2)])).toBe('1-左')
    expect(formatDiningTableLabel('1-B', [table('1', 'INSIDE_OUTSIDE'), table('1', 'INSIDE_OUTSIDE', 2)])).toBe('1-右')
  })
  it('uses the existing legacy fallback for unknown configuration', () => {
    expect(formatDiningTableLabel('old-A', [])).toBe('old-左')
    expect(formatDiningTableLabel('unknown-B', [table('1', 'INSIDE_OUTSIDE')])).toBe('unknown-右')
    expect(formatDiningTableLabel('1里', [])).toBe('1里')
    expect(normalizeSplitLabelMode(undefined)).toBe('LEFT_RIGHT')
    expect(normalizeSplitLabelMode('future')).toBe('LEFT_RIGHT')
    expect(splitSideLabel('A', 'INSIDE_OUTSIDE')).toBe('里')
    expect(splitSideLabel('B', 'INSIDE_OUTSIDE')).toBe('外')
  })
  it('prefers the server display projection and never formats a takeout pickup label', () => {
    expect(orderLocationDisplay({ table_no: '1-A', table_display_name: '1里' }, '现场')).toBe('1里')
    expect(orderLocationDisplay({ table_no: '1-B' }, '现场')).toBe('1-右')
    expect(orderLocationDisplay({ table_no: null, pickup_no: 'TO-A' }, '现场')).toBe('TO-A')
  })
})
