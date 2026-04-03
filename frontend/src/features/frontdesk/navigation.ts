export interface MenuRouteContext {
  slotLabel: string
  tableLabel: string
  orderType: 'dine_in' | 'pickup'
  pickupLabel: string | null
}

export function navigateTo(path: string) {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export function buildMenuPath(context: MenuRouteContext) {
  const params = new URLSearchParams({
    slot: context.slotLabel,
    table: context.tableLabel,
    type: context.orderType,
  })
  if (context.pickupLabel) {
    params.set('pickup', context.pickupLabel)
  }
  return `/frontdesk/menu?${params.toString()}`
}

export function parseMenuRoute(pathname: string, search: string): MenuRouteContext | null {
  if (!pathname.startsWith('/frontdesk/menu') && !pathname.startsWith('/menu')) {
    return null
  }

  const params = new URLSearchParams(search)
  const slotLabel = params.get('slot')
  const tableLabel = params.get('table')
  const orderType = params.get('type')

  if (!slotLabel || !tableLabel || (orderType !== 'dine_in' && orderType !== 'pickup')) {
    return null
  }

  return {
    slotLabel,
    tableLabel,
    orderType,
    pickupLabel: params.get('pickup'),
  }
}
