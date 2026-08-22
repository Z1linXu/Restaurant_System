import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './apiClient'
import {
  activateOwnerStorePart2,
  fetchOwnerStorePart2Readiness,
  provisionOwnerStorePart2,
} from './ownerStorePart2Service'

vi.mock('./apiClient', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = vi.mocked(apiRequest)

describe('owner Store Part 2 service', () => {
  beforeEach(() => {
    mockedApiRequest.mockReset()
  })

  it('loads Store-scoped readiness', async () => {
    mockedApiRequest.mockResolvedValueOnce({ readiness_status: 'NOT_READY', ready: false })

    await fetchOwnerStorePart2Readiness(7, 11)

    expect(mockedApiRequest).toHaveBeenCalledWith(
      '/api/v1/owner/organizations/7/stores/11/phase-b/part2/readiness',
    )
  })

  it('sends an idempotent synthetic provisioning request', async () => {
    mockedApiRequest.mockResolvedValueOnce({ status: 'COMPLETED' })

    await provisionOwnerStorePart2(7, 11)

    const [path, init] = mockedApiRequest.mock.calls[0]
    expect(path).toBe('/api/v1/owner/organizations/7/stores/11/phase-b/part2/provision')
    expect(init?.method).toBe('POST')
    expect((init?.headers as Record<string, string>)['Idempotency-Key']).toContain('phase-b-part2-provision-')
    expect(JSON.parse(String(init?.body))).toEqual({})
  })

  it('sends the readiness fingerprint to Owner activation', async () => {
    mockedApiRequest.mockResolvedValueOnce({ status: 'COMPLETED', target_state: 'LIVE' })

    await activateOwnerStorePart2(7, 11, 'fingerprint-123')

    const [path, init] = mockedApiRequest.mock.calls[0]
    expect(path).toBe('/api/v1/owner/organizations/7/stores/11/phase-b/part2/activate')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      expected_readiness_fingerprint: 'fingerprint-123',
    })
  })
})
