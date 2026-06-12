export type FeaturePackage =
  | 'CORE_POS'
  | 'PRINTING'
  | 'KDS'
  | 'ADMIN'
  | 'ANALYTICS'
  | 'PLATFORM'
  | 'DEVELOPER_TOOLS'

export const featureConfig: Record<FeaturePackage, boolean> = {
  CORE_POS: true,
  PRINTING: true,
  KDS: false,
  ADMIN: true,
  ANALYTICS: true,
  PLATFORM: false,
  DEVELOPER_TOOLS: false,
}

export function isFeatureEnabled(feature: FeaturePackage) {
  return featureConfig[feature]
}

export const routeFeatureMetadata: Array<{
  matcher: (pathname: string) => boolean
  requiredFeature: FeaturePackage
}> = [
  { matcher: (pathname) => pathname === '/' || pathname === '', requiredFeature: 'CORE_POS' },
  { matcher: (pathname) => pathname.startsWith('/login'), requiredFeature: 'CORE_POS' },
  { matcher: (pathname) => pathname === '/frontdesk' || pathname === '/frontdesk/', requiredFeature: 'CORE_POS' },
  { matcher: (pathname) => pathname.startsWith('/frontdesk/menu') || pathname.startsWith('/menu'), requiredFeature: 'CORE_POS' },
  { matcher: (pathname) => pathname.startsWith('/frontdesk/order') || pathname.startsWith('/orders'), requiredFeature: 'CORE_POS' },
  { matcher: (pathname) => pathname.startsWith('/admin/settings/printing'), requiredFeature: 'PRINTING' },
  { matcher: (pathname) => pathname.startsWith('/pickup'), requiredFeature: 'KDS' },
  { matcher: (pathname) => pathname.startsWith('/kds/'), requiredFeature: 'KDS' },
  { matcher: (pathname) => pathname.startsWith('/admin/dashboard'), requiredFeature: 'ADMIN' },
  { matcher: (pathname) => pathname.startsWith('/admin/menu/items'), requiredFeature: 'ADMIN' },
  { matcher: (pathname) => pathname.startsWith('/admin/settings/tables'), requiredFeature: 'ADMIN' },
  { matcher: (pathname) => pathname.startsWith('/admin/reports/'), requiredFeature: 'ANALYTICS' },
  { matcher: (pathname) => pathname.startsWith('/admin/platform'), requiredFeature: 'PLATFORM' },
]

export function getRequiredFeatureForPath(pathname: string) {
  return routeFeatureMetadata.find((entry) => entry.matcher(pathname))?.requiredFeature ?? 'CORE_POS'
}
