import { describe, expect, it } from 'vitest'
import {
  comboConfigurationLayoutClasses,
  moveWithDisplayOrder,
} from './comboConfigurationLayout'

describe('ComboConfigurationPanel layout contract', () => {
  it('keeps reorder controls in an independent responsive grid column', () => {
    const allClasses = Object.values(comboConfigurationLayoutClasses).join(' ')

    expect(comboConfigurationLayoutClasses.groupRow).toContain('xl:grid-cols-[')
    expect(comboConfigurationLayoutClasses.componentRow).toContain('xl:grid-cols-[')
    expect(comboConfigurationLayoutClasses.reorderControls).toContain('min-w-[96px]')
    expect(comboConfigurationLayoutClasses.reorderControls).toContain('grid-cols-2')
    expect(comboConfigurationLayoutClasses.displayOrderField).toContain('min-w-[120px]')
    expect(comboConfigurationLayoutClasses.mappingField).toContain('min-w-[145px]')
    expect(comboConfigurationLayoutClasses.linkedItemField).toContain('min-w-[160px]')

    expect(comboConfigurationLayoutClasses.componentRow).toContain('sm:grid-cols-2')
    expect(comboConfigurationLayoutClasses.componentRow).toContain('minmax(96px,auto)')
    expect(allClasses).not.toContain('absolute')
    expect(allClasses).not.toContain('grid-cols-[1fr_auto_auto]')
  })

  it('keeps wide-row minimum width within common laptop content budgets and wraps on tablet', () => {
    const gridGapPx = 12
    const componentWideMinimumPx = 120 + 120 + 120 + 96 + 145 + 160 + 72 + 80 + (7 * gridGapPx)
    const groupWideMinimumPx = 130 + 130 + 160 + 120 + 96 + 92 + 100 + 84 + (7 * gridGapPx)

    expect(componentWideMinimumPx).toBeLessThanOrEqual(1000)
    expect(groupWideMinimumPx).toBeLessThanOrEqual(1000)
    expect(comboConfigurationLayoutClasses.componentRow).toContain('sm:grid-cols-2')
    expect(comboConfigurationLayoutClasses.groupRow).toContain('sm:grid-cols-2')
  })

  it('preserves Up/Down display_order resequencing behavior', () => {
    const result = moveWithDisplayOrder([
      { code: 'combo_tea_egg', display_order: 10 },
      { code: 'combo_fried_egg', display_order: 20 },
      { code: 'combo_cucumber_salad', display_order: 30 },
    ], 2, -1)

    expect(result.map((item) => item.code)).toEqual([
      'combo_tea_egg',
      'combo_cucumber_salad',
      'combo_fried_egg',
    ])
    expect(result.map((item) => item.display_order)).toEqual([10, 20, 30])
  })

  it('does not change ordering when the requested move is outside bounds', () => {
    const values = [
      { code: 'combo_tea_egg', display_order: 10 },
      { code: 'combo_fried_egg', display_order: 20 },
    ]

    expect(moveWithDisplayOrder(values, 0, -1)).toBe(values)
    expect(moveWithDisplayOrder(values, 1, 1)).toBe(values)
  })
})
