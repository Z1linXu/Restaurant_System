import { describe, expect, it } from 'vitest'
import { ApiRequestError, isRefreshCredentialRejection } from './apiClient'

describe('refresh credential failure classification', () => {
  it('clears credentials only for explicit authentication or authorization rejection', () => {
    expect(isRefreshCredentialRejection(new ApiRequestError(401, 'expired'))).toBe(true)
    expect(isRefreshCredentialRejection(new ApiRequestError(403, 'revoked'))).toBe(true)
    expect(isRefreshCredentialRejection(new ApiRequestError(0, 'offline'))).toBe(false)
    expect(isRefreshCredentialRejection(new ApiRequestError(503, 'unavailable'))).toBe(false)
  })
})
