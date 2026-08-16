import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './apiClient'
import {
  fetchOwnerStoreProvisioningCatalog,
  provisionOwnerStore,
} from './ownerStoreProvisioningService'

vi.mock('./apiClient', () => ({
  apiRequest: vi.fn(),
}))

const mockedApiRequest = vi.mocked(apiRequest)

describe('owner store provisioning service', () => {
  beforeEach(() => {
    mockedApiRequest.mockReset()
  })

  it('loads the organization-scoped Phase B catalog', async () => {
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
      '/api/v1/owner/organizations/100/phase-b/store-provisioning/catalog',
    )
  })

  it('creates a Store through the canonical backend with an automatic idempotency key', async () => {
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
    })

    const [, init] = mockedApiRequest.mock.calls[0]
    expect(mockedApiRequest.mock.calls[0][0]).toBe('/api/v1/owner/organizations/100/phase-b/store-provisioning')
    expect(init?.method).toBe('POST')
    expect((init?.headers as Record<string, string>)['Idempotency-Key']).toBeTruthy()
    expect(JSON.parse(String(init?.body))).toMatchObject({
      store_name: 'Phase B Test Store',
      store_code: 'PHASE_B_TEST_STORE',
    })
  })
})
