import { describe, expect, it } from 'vitest'
import {
  buildDefaultNoodleTypeOrder,
  defaultNoodleTypeOptionId,
} from './menuOptionDefaults'

const options = [
  { id: 1, option_type: 'noodle_type', option_group: 'NOODLE_TYPE', sort_order: 10, is_active: true },
  { id: 2, option_type: 'noodle_type', option_group: 'NOODLE_TYPE', sort_order: 20, is_active: true },
  { id: 3, option_type: 'addon', option_group: 'ADD_ON', sort_order: 5, is_active: true },
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
})
