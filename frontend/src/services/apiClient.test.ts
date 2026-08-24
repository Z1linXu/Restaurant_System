import { describe, expect, it } from 'vitest'
import { ApiRequestError, getApiUserMessage, isRefreshCredentialRejection } from './apiClient'

describe('refresh credential failure classification', () => {
  it('clears credentials only for explicit authentication or authorization rejection', () => {
    expect(isRefreshCredentialRejection(new ApiRequestError(401, 'expired'))).toBe(true)
    expect(isRefreshCredentialRejection(new ApiRequestError(403, 'revoked'))).toBe(true)
    expect(isRefreshCredentialRejection(new ApiRequestError(0, 'offline'))).toBe(false)
    expect(isRefreshCredentialRejection(new ApiRequestError(503, 'unavailable'))).toBe(false)
  })
})

describe('business Store create error classification', () => {
  it('distinguishes Organization membership denial from runtime feature denial', () => {
    expect(getApiUserMessage(new ApiRequestError(
      403,
      'denied',
      'denied',
      null,
      'BUSINESS_STORE_CREATE_ORGANIZATION_DENIED',
    ))).toContain('Organization')
    expect(getApiUserMessage(new ApiRequestError(
      403,
      'disabled',
      'disabled',
      null,
      'FEATURE_DISABLED',
    ))).toContain('unavailable')
  })
})
