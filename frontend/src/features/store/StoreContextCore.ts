import { createContext } from 'react'
import type { StoreContextResponse, StoreModuleConfiguration } from '../../services/storeWorkspaceService'
import { isStoreLive } from './storeOperationalState'

export interface StoreContextValue {
  storeId: number
  storeName: string
  storeCode: string | null
  storeStatus: string | null
  storeKind: string | null
  lifecycleStatus: string | null
  operationalState: string | null
  isLive: boolean
  provisioningSource: string | null
  organizationId: number | null
  organizationName: string | null
  roleCode: string | null
  moduleConfiguration: StoreModuleConfiguration | null
  loading: boolean
  error: string | null
}

export const StoreContext = createContext<StoreContextValue | null>(null)

export function mapStoreContext(
  storeId: number,
  data: StoreContextResponse | null,
  loading: boolean,
  error: string | null,
): StoreContextValue {
  return {
    storeId,
    storeName: data?.name ?? `门店 ${storeId}`,
    storeCode: data?.code ?? null,
    storeStatus: data?.status ?? null,
    storeKind: data?.store_kind ?? null,
    lifecycleStatus: data?.lifecycle_status ?? null,
    operationalState: data?.operational_state ?? null,
    isLive: isStoreLive(data),
    provisioningSource: data?.provisioning_source ?? null,
    organizationId: data?.organization_id ?? null,
    organizationName: data?.organization_name ?? null,
    roleCode: data?.role_code ?? null,
    moduleConfiguration: data?.module_configuration ?? null,
    loading,
    error,
  }
}
