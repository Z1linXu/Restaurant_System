export interface MenuRouteContext {
  slotLabel: string
  tableLabel: string
  orderType: 'dine_in' | 'pickup'
  pickupLabel: string | null
  workstation: string | null
}

export function navigateTo(path: string) {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export function inferFrontdeskWorkstation(pathname: string) {
  const match = pathname.match(/^\/frontdesk\/menu\/([^/?#]+)/)
  return match?.[1] ?? null
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
  const workstationSegment = context.workstation ? `/${context.workstation}` : ''
  return `/frontdesk/menu${workstationSegment}?${params.toString()}`
}

export function buildFrontdeskBoardPath(workstation: string | null) {
  return workstation ? `/frontdesk/menu/${workstation}` : '/frontdesk'
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
    workstation: inferFrontdeskWorkstation(pathname),
  }
}
