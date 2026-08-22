import { ApiRequestError, apiRequest } from './apiClient'

export interface OwnerStoreProvisioningCatalog {
  enabled: boolean
  profile_code: string
  profile_version: string
  master_menu_key: string
  master_menu_version: string
  master_menu_fingerprint_sha256: string
}

export interface OwnerStoreProvisioningRequest {
  store_name: string
  store_code: string
  profile_code?: string | null
  profile_version?: string | null
  profile_fingerprint_sha256?: string | null
  master_menu_key?: string | null
  master_menu_version?: string | null
  master_menu_fingerprint_sha256?: string | null
}

export interface OwnerStoreProvisioningCounts {
  station_count: number
  category_count: number
  item_count: number
  option_count: number
  pricing_policy_count: number
  combo_component_count: number
  printing_rule_count: number
}

export interface OwnerStoreProvisioningResult {
  request_id: number
  store_id: number | null
  status: string
  replayed: boolean
  validation_status: string
  result_code: string | null
  error_code: string | null
  counts: OwnerStoreProvisioningCounts
}

function createIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `phase-b-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export interface OwnerStoreProvisioningAttempt {
  payloadSignature: string
  idempotencyKey: string
}

export function resolveOwnerStoreProvisioningAttempt(
  previous: OwnerStoreProvisioningAttempt | null,
  organizationId: number,
  request: OwnerStoreProvisioningRequest,
): OwnerStoreProvisioningAttempt {
  const payloadSignature = JSON.stringify({ organizationId, request })
  if (previous?.payloadSignature === payloadSignature) {
    return previous
  }
  return { payloadSignature, idempotencyKey: createIdempotencyKey() }
}

export function shouldRotateOwnerStoreProvisioningAttempt(error: unknown) {
  return error instanceof ApiRequestError
    && error.code === 'STORE_PROVISIONING_RETRY_REQUIRES_NEW_KEY'
}

export function fetchOwnerStoreProvisioningCatalog(organizationId: number) {
  return apiRequest<OwnerStoreProvisioningCatalog>(
    `/api/v1/owner/organizations/${organizationId}/phase-b/store-provisioning/catalog`,
  )
}

export function provisionOwnerStore(
  organizationId: number,
  request: OwnerStoreProvisioningRequest,
  idempotencyKey: string,
) {
  return apiRequest<OwnerStoreProvisioningResult>(
    `/api/v1/owner/organizations/${organizationId}/phase-b/store-provisioning`,
    {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(request),
    },
  )
}
