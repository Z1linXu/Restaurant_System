import { describe, expect, it } from 'vitest'
import { menuManagementPath, menuManagementSection, type MenuManagementSection } from './menuManagementSections'

describe('Menu Management section URLs', () => {
  it.each(['menu_items', 'addons', 'categories', 'stations', 'combo', 'pricing', 'printing_display_rules'] satisfies MenuManagementSection[])(
    'round-trips section %s with the selected Store', (section) => {
      const path = menuManagementPath(2, section)
      expect(path).toMatch(/^\/stores\/2\/admin\/menu\/items/)
      expect(menuManagementSection(new URL(path, 'https://staging.example').search)).toBe(section)
    },
  )
  it('keeps the old Menu Items route and safely falls back for unknown sections', () => {
    expect(menuManagementPath(2)).toBe('/stores/2/admin/menu/items')
    expect(menuManagementSection('?section=unknown')).toBe('menu_items')
    expect(menuManagementSection('?section=printing-display-rules')).toBe('printing_display_rules')
  })
})
