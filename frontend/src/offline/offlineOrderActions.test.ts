import { describe, expect, it } from 'vitest'
import { canReturnOfflineOrderToDraft } from './offlineOrderActions'

describe('offline order edit actions', () => {
  it('only allows locally safe states to return to editing', () => {
    expect(canReturnOfflineOrderToDraft('QUEUED')).toBe(true)
    expect(canReturnOfflineOrderToDraft('CONFLICT')).toBe(true)
    expect(canReturnOfflineOrderToDraft('FAILED_VALIDATION')).toBe(true)
  })

  it('does not allow editing while the server result is uncertain', () => {
    expect(canReturnOfflineOrderToDraft('SUBMITTING')).toBe(false)
    expect(canReturnOfflineOrderToDraft('FAILED_RETRYABLE')).toBe(false)
    expect(canReturnOfflineOrderToDraft('SUBMITTED')).toBe(false)
    expect(canReturnOfflineOrderToDraft('CANCELLED_LOCAL')).toBe(false)
  })
})
