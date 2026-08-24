import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiRequestError, apiRequest } from './apiClient'
import {
  fetchOwnerStoreProvisioningCatalog,
  provisionOwnerStore,
  resolveOwnerStoreProvisioningAttempt,
  shouldRotateOwnerStoreProvisioningAttempt,
} from './ownerStoreProvisioningService'

vi.mock('./apiClient', async (importOriginal) => ({
  ...await importOriginal<typeof import('./apiClient')>(),
  apiRequest: vi.fn(),
}))

const mockedApiRequest = vi.mocked(apiRequest)

describe('owner store provisioning service', () => {
  beforeEach(() => {
    mockedApiRequest.mockReset()
  })

  it('loads the organization-scoped business Store catalog', async () => {
    mockedApiRequest.mockResolvedValueOnce({
      enabled: true,
      profile_code: 'ST_DENIS_CANONICAL_PROFILE',
      profile_version: 'v2',
      master_menu_key: 'LANZHOU_CHAIN_MASTER_MENU',
      master_menu_version: 'v1',
      master_menu_fingerprint_sha256: 'a'.repeat(64),
    })

    await fetchOwnerStoreProvisioningCatalog(100)

    expect(mockedApiRequest).toHaveBeenCalledWith(
      '/api/v1/owner/organizations/100/stores/create-catalog',
    )
  })

  it('creates a Store through the canonical backend with the supplied stable idempotency key', async () => {
    mockedApiRequest.mockResolvedValueOnce({
      request_id: 1,
      store_id: 2,
      status: 'COMPLETED',
      replayed: false,
      validation_status: 'PASS',
      result_code: 'PHASE_B_STORE_PROVISIONED',
      error_code: null,
      counts: {
        station_count: 5,
        category_count: 6,
        item_count: 39,
        option_count: 380,
        pricing_policy_count: 1,
        combo_component_count: 5,
        printing_rule_count: 1,
      },
    })

    await provisionOwnerStore(100, {
      store_name: 'Phase B Test Store',
      store_code: 'PHASE_B_TEST_STORE',
      profile_code: 'ST_DENIS_CANONICAL_PROFILE',
      profile_version: 'v2',
      master_menu_key: 'LANZHOU_CHAIN_MASTER_MENU',
      master_menu_version: 'v1',
    }, 'stable-create-key')

    const [, init] = mockedApiRequest.mock.calls[0]
    expect(mockedApiRequest.mock.calls[0][0]).toBe('/api/v1/owner/organizations/100/stores')
    expect(init?.method).toBe('POST')
    expect((init?.headers as Record<string, string>)['Idempotency-Key']).toBe('stable-create-key')
    expect(JSON.parse(String(init?.body))).toMatchObject({
      store_name: 'Phase B Test Store',
      store_code: 'PHASE_B_TEST_STORE',
    })
  })

  it('retains the same key after an ambiguous failure and rotates it when content changes', () => {
    const request = { store_name: 'Chinatown', store_code: 'CHINATOWN' }
    const first = resolveOwnerStoreProvisioningAttempt(null, 100, request)
    const retry = resolveOwnerStoreProvisioningAttempt(first, 100, { ...request })
    const changed = resolveOwnerStoreProvisioningAttempt(retry, 100, { ...request, store_name: 'Chinatown East' })

    expect(retry).toBe(first)
    expect(changed.idempotencyKey).not.toBe(first.idempotencyKey)
  })

  it('rotates only after backend confirms that a failed ledger requires a new key', () => {
    expect(shouldRotateOwnerStoreProvisioningAttempt(
      new ApiRequestError(409, 'retry', 'retry', null, 'STORE_PROVISIONING_RETRY_REQUIRES_NEW_KEY'),
    )).toBe(true)
    expect(shouldRotateOwnerStoreProvisioningAttempt(
      new ApiRequestError(0, 'timeout', 'timeout', null, 'REQUEST_TIMEOUT'),
    )).toBe(false)
    expect(shouldRotateOwnerStoreProvisioningAttempt(
      new ApiRequestError(409, 'processing', 'processing', null, 'STORE_PROVISIONING_IN_PROGRESS'),
    )).toBe(false)
  })
})
