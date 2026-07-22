import { describe, expect, it } from 'vitest'
import { menuCacheNoticeDismissalKey } from './menuCacheNotice'

describe('menu cache notice dismissal scope', () => {
  it('isolates the dismissal by account, organization, store, and revision', () => {
    const base = { accountId: 5, organizationId: 9, storeId: 1 }
    expect(menuCacheNoticeDismissalKey(base, 12))
      .toBe('restaurant_menu_cache_notice_dismissed:5:9:1:12')
    expect(menuCacheNoticeDismissalKey({ ...base, storeId: 2 }, 12))
      .not.toBe(menuCacheNoticeDismissalKey(base, 12))
    expect(menuCacheNoticeDismissalKey(base, 13))
      .not.toBe(menuCacheNoticeDismissalKey(base, 12))
  })
})
