import { apiRequest } from './apiClient'

export interface StoreReadinessCheck {
  code: string
  status: string
  message: string
}

export interface StoreReadinessCounts {
  station_count: number
  table_count: number
  staff_count: number
  printer_role_count: number
  device_count: number
}

export interface StoreReadinessResponse {
  evidence_id: number | null
  organization_id: number
  store_id: number
  readiness_status: string
  ready: boolean
  store_status: string | null
  lifecycle_status: string | null
  readiness_fingerprint: string | null
  checked_at: string | null
  expires_at: string | null
  checks: StoreReadinessCheck[]
  counts: StoreReadinessCounts
}

export interface StorePart2Counts {
  station_count: number
  table_count: number
  staff_count: number
  printer_role_count: number
  device_count: number
}

export interface StorePart2StaffResult {
  user_id: number
  login_identifier: string
  role_code: string
}

export interface StorePart2StaffCredential {
  login_identifier: string
  temporary_password: string
  role_code: string
  one_time: boolean
}

export interface StorePart2DeviceCredential {
  device_id: number
  device_name: string
  device_token: string
  one_time: boolean
}

export interface StorePart2ProvisioningResponse {
  request_id: number
  store_id: number
  status: string
  readiness_status: string
  replayed: boolean
  result_code: string | null
  error_code: string | null
  counts: StorePart2Counts
  staff: StorePart2StaffResult[]
  synthetic_staff_credentials: StorePart2StaffCredential[]
  synthetic_device_credentials: StorePart2DeviceCredential[]
  readiness: StoreReadinessResponse
}

export interface StoreActivationResponse {
  request_id: number
  organization_id: number
  store_id: number
  status: string
  target_state: string
  replayed: boolean
  result_code: string | null
  error_code: string | null
  readiness: StoreReadinessResponse
}

export interface StorePart2ProvisioningRequest {
  stations?: Array<Record<string, unknown>>
  tables?: Array<Record<string, unknown>>
  staff?: Array<Record<string, unknown>>
  printer_roles?: Array<Record<string, unknown>>
  devices?: Array<Record<string, unknown>>
}

function createIdempotencyKey(prefix: string) {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `${prefix}-${crypto.randomUUID()}`
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function part2Path(organizationId: number, storeId: number) {
  return `/api/v1/owner/organizations/${organizationId}/stores/${storeId}/phase-b/part2`
}

export function fetchOwnerStorePart2Readiness(organizationId: number, storeId: number) {
  return apiRequest<StoreReadinessResponse>(`${part2Path(organizationId, storeId)}/readiness`)
}

export function provisionOwnerStorePart2(
  organizationId: number,
  storeId: number,
  request: StorePart2ProvisioningRequest = {},
) {
  return apiRequest<StorePart2ProvisioningResponse>(`${part2Path(organizationId, storeId)}/provision`, {
    method: 'POST',
    headers: {
      'Idempotency-Key': createIdempotencyKey('phase-b-part2-provision'),
    },
    body: JSON.stringify(request),
  })
}

export function activateOwnerStorePart2(
  organizationId: number,
  storeId: number,
  expectedReadinessFingerprint: string | null,
) {
  return apiRequest<StoreActivationResponse>(`${part2Path(organizationId, storeId)}/activate`, {
    method: 'POST',
    headers: {
      'Idempotency-Key': createIdempotencyKey('phase-b-part2-activate'),
    },
    body: JSON.stringify({
      expected_readiness_fingerprint: expectedReadinessFingerprint,
    }),
  })
}
