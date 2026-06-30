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
  const normalized = pathname.replace(/^\/stores\/\d+/, '')
  const match = normalized.match(/^\/frontdesk\/menu\/([^/?#]+)/)
  return match?.[1] ?? null
}

function storePrefix(storeId?: number | null) {
  return storeId ? `/stores/${storeId}` : ''
}

export function buildMenuPath(context: MenuRouteContext, storeId?: number | null) {
  const params = new URLSearchParams({
    slot: context.slotLabel,
    table: context.tableLabel,
    type: context.orderType,
  })
  if (context.pickupLabel) {
    params.set('pickup', context.pickupLabel)
  }
  const workstationSegment = context.workstation ? `/${context.workstation}` : ''
  return `${storePrefix(storeId)}/frontdesk/menu${workstationSegment}?${params.toString()}`
}

export function buildFrontdeskBoardPath(workstation: string | null, storeId?: number | null) {
  return workstation ? `${storePrefix(storeId)}/frontdesk/menu/${workstation}` : `${storePrefix(storeId)}/frontdesk`
}

export function parseMenuRoute(pathname: string, search: string): MenuRouteContext | null {
  const normalized = pathname.replace(/^\/stores\/\d+/, '')
  if (!normalized.startsWith('/frontdesk/menu') && !normalized.startsWith('/menu')) {
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
    workstation: inferFrontdeskWorkstation(normalized),
  }
}
