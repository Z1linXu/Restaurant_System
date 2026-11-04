import { afterEach, describe, expect, it } from 'vitest'
import { ApiRequestError, getApiUserMessage, isRefreshCredentialRejection, userMessageForStatus } from './apiClient'

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


describe('receipt errors retain diagnostics and explain the safe next action', () => {
  afterEach(async () => { const { appI18n } = await import('../i18n/engine'); await appI18n.changeLanguage('en') })
  it.each([
    ['zh-CN', '打印中心', '订单仍已保存'],
    ['en', 'Print Center', 'order remains saved'],
    ['fr-CA', 'centre d’impression', 'commande reste enregistrée'],
  ])('shows %s guidance without rewriting the canonical error', async (locale, center, saved) => {
    const { appI18n } = await import('../i18n/engine'); await appI18n.changeLanguage(locale)
    const original = new ApiRequestError(400, 'RECEIPT_USE_ORIGINAL_JOB_REPRINT: added batches', undefined, undefined, 'RECEIPT_USE_ORIGINAL_JOB_REPRINT')
    expect(getApiUserMessage(original)).toContain(center)
    expect(original.code).toBe('RECEIPT_USE_ORIGINAL_JOB_REPRINT')
    expect(userMessageForStatus(400, 'RECEIPT_STATION_NOT_ASSIGNED: SYNTHETIC')).toContain(saved)
  })
})
