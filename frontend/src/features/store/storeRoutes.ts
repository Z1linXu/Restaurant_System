import type { WorkspaceResponse, WorkspaceStore } from '../../services/storeWorkspaceService'

export function stripStorePrefix(pathname: string) {
  const match = pathname.match(/^\/stores\/(\d+)(\/.*)?$/)
  if (!match) {
    return { storeId: null as number | null, path: pathname }
  }
  return {
    storeId: Number(match[1]),
    path: match[2] && match[2] !== '' ? match[2] : '/',
  }
}

export function buildStorePath(storeId: number, path: string) {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `/stores/${storeId}${normalized}`
}

export function replaceStoreId(pathname: string, storeId: number) {
  const stripped = stripStorePrefix(pathname)
  return buildStorePath(storeId, stripped.path)
}

export function chooseDefaultStore(workspaces: WorkspaceResponse): WorkspaceStore | null {
  if (!workspaces.stores.length) {
    return null
  }
  const preferred = workspaces.default_store_id
    ? workspaces.stores.find((store) => store.id === workspaces.default_store_id)
    : null
  return preferred ?? workspaces.stores[0] ?? null
}

export function isAdminWorkspaceRole(roleCode: string | null | undefined) {
  const role = roleCode?.toUpperCase()
  return role === 'OWNER' || role === 'ADMIN' || role === 'MANAGER'
}

export function defaultStorePathForRole(roleCode: string | null | undefined, storeId: number) {
  const role = roleCode?.toUpperCase()
  if (role === 'FRONTDESK') {
    return buildStorePath(storeId, '/frontdesk')
  }
  if (role === 'HOT_KITCHEN') {
    return buildStorePath(storeId, '/kds/hot-kitchen')
  }
  if (role === 'NOODLE_VIEW') {
    return buildStorePath(storeId, '/kds/noodle')
  }
  if (role === 'PASS') {
    return buildStorePath(storeId, '/pickup')
  }
  return buildStorePath(storeId, '/admin/dashboard')
}

export function defaultWorkspacePathForRole(roleCode: string | null | undefined, workspaces: WorkspaceResponse) {
  const store = chooseDefaultStore(workspaces)
  if (!store) {
    return null
  }
  if (isAdminWorkspaceRole(roleCode) && workspaces.stores.length > 1) {
    return '/owner/dashboard'
  }
  return defaultStorePathForRole(roleCode, store.id)
}

export function mapLegacyPathToStorePath(pathname: string, storeId: number) {
  if (pathname === '/admin' || pathname === '/admin/') {
    return buildStorePath(storeId, '/admin/dashboard')
  }
  if (pathname.startsWith('/orders')) {
    return buildStorePath(storeId, '/frontdesk/order')
  }
  if (pathname.startsWith('/menu')) {
    return buildStorePath(storeId, pathname.replace(/^\/menu/, '/frontdesk/menu'))
  }
  return buildStorePath(storeId, pathname)
}
