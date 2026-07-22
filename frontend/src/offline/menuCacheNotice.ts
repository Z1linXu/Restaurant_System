export interface MenuCacheNoticeScope {
  accountId: number | null
  organizationId: number | null
  storeId: number
}

export function menuCacheNoticeDismissalKey(scope: MenuCacheNoticeScope, menuRevision: number | null) {
  return `restaurant_menu_cache_notice_dismissed:${scope.accountId ?? 'anonymous'}:${scope.organizationId ?? 'unknown'}:${scope.storeId}:${menuRevision ?? 'unknown'}`
}
