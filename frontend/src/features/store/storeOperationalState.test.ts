import { describe, expect, it } from 'vitest'
import { isStoreLive } from './storeOperationalState'

describe('canonical Store operational lifecycle', () => {
  it('uses the backend is_live contract when present', () => {
    expect(isStoreLive({ status: 'inactive', lifecycle_status: 'READY_FOR_REVIEW', is_live: true })).toBe(true)
    expect(isStoreLive({ status: 'active', lifecycle_status: 'ACTIVE', is_live: false })).toBe(false)
  })

  it('falls back fail-closed to active plus ACTIVE without using store kind', () => {
    expect(isStoreLive({ status: 'active', lifecycle_status: 'ACTIVE' })).toBe(true)
    expect(isStoreLive({ status: 'active', lifecycle_status: 'READY_FOR_REVIEW' })).toBe(false)
    expect(isStoreLive({ status: null, lifecycle_status: null })).toBe(false)
  })
})
