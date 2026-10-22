export type MenuManagementSection = 'menu_items' | 'categories' | 'stations' | 'combo' | 'pricing' | 'addons' | 'printing_display_rules'

export function menuManagementSection(search: string): MenuManagementSection {
  const value = new URLSearchParams(search).get('section')
  if (value === 'printing-display-rules') return 'printing_display_rules'
  return value === 'addons' || value === 'categories' || value === 'stations' || value === 'combo' || value === 'pricing'
    ? value : 'menu_items'
}

export function menuManagementPath(storeId: number, section: MenuManagementSection = 'menu_items') {
  const query = section === 'printing_display_rules' ? 'printing-display-rules' : section
  return `/stores/${storeId}/admin/menu/items${section === 'menu_items' ? '' : `?section=${query}`}`
}
