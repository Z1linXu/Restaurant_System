import { describe, expect, it } from 'vitest'
import {
  buildDefaultSizeOrder,
  buildDefaultNoodleTypeOrder,
  defaultSizeOptionId,
  defaultNoodleTypeOptionId,
} from './menuOptionDefaults'

const options = [
  { id: 1, option_type: 'noodle_type', option_group: 'NOODLE_TYPE', sort_order: 10, is_active: true },
  { id: 2, option_type: 'noodle_type', option_group: 'NOODLE_TYPE', sort_order: 20, is_active: true },
  { id: 3, option_type: 'addon', option_group: 'ADD_ON', sort_order: 5, is_active: true },
  { id: 4, option_type: 'size', option_group: 'SIZE', sort_order: 20, is_active: true },
  { id: 5, option_type: 'size', option_group: 'SIZE', sort_order: 10, is_active: true },
  { id: 6, option_type: 'size', option_group: 'SIZE', sort_order: 30, is_active: false },
]

describe('menu option defaults', () => {
  it('uses the first active noodle type as the configured default', () => {
    expect(defaultNoodleTypeOptionId(options)).toBe(1)
  })

  it('builds a stable full-group reorder payload when setting a new default', () => {
    expect(buildDefaultNoodleTypeOrder(options, 2)).toEqual([
      { id: 2, sort_order: 10 },
      { id: 1, sort_order: 20 },
    ])
  })

  it('does not allow an inactive option to become the default', () => {
    expect(buildDefaultNoodleTypeOrder([{ ...options[0], is_active: false }], 1)).toBeNull()
  })

  it('uses the first active size as the configured default', () => {
    expect(defaultSizeOptionId(options)).toBe(5)
  })

  it('builds a stable full-size reorder payload when setting a new default', () => {
    expect(buildDefaultSizeOrder(options, 4)).toEqual([
      { id: 4, sort_order: 10 },
      { id: 5, sort_order: 20 },
      { id: 6, sort_order: 30 },
    ])
  })
})
