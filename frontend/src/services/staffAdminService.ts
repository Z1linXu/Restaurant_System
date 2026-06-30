import { apiRequest } from './apiClient'

export interface StaffStoreRecord {
  id: number
  organization_id?: number | null
  name: string
  code: string
  status: string
}

export interface StaffUserRecord {
  id: number
  store_id: number
  role_id: number
  role_code: 'OWNER' | 'ADMIN' | 'MANAGER' | 'FRONTDESK' | string
  username: string
  full_name?: string | null
  phone?: string | null
  status: string
  created_at?: string
  updated_at?: string
}

export interface StaffUserPayload {
  store_id: number
  username: string
  full_name?: string | null
  phone?: string | null
  role_code: string
  password?: string | null
}

export function fetchStaffStores() {
  return apiRequest<StaffStoreRecord[]>('/api/v1/admin/staff/stores')
}

export function fetchStaff(storeId: number) {
  return apiRequest<StaffUserRecord[]>(`/api/v1/admin/staff?store_id=${storeId}`)
}

export function createStaff(payload: StaffUserPayload) {
  return apiRequest<StaffUserRecord>('/api/v1/admin/staff', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateStaff(userId: number, payload: StaffUserPayload) {
  return apiRequest<StaffUserRecord>(`/api/v1/admin/staff/${userId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deactivateStaff(userId: number) {
  return apiRequest<StaffUserRecord>(`/api/v1/admin/staff/${userId}/deactivate`, { method: 'POST' })
}

export function reactivateStaff(userId: number) {
  return apiRequest<StaffUserRecord>(`/api/v1/admin/staff/${userId}/reactivate`, { method: 'POST' })
}

export function resetStaffPassword(userId: number, newPassword: string) {
  return apiRequest<StaffUserRecord>(`/api/v1/admin/staff/${userId}/reset-password`, {
    method: 'POST',
    body: JSON.stringify({ new_password: newPassword }),
  })
}
