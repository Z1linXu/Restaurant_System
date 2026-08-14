import { createContext } from 'react'
import type { StoreContextResponse, StoreModuleConfiguration } from '../../services/storeWorkspaceService'

export interface StoreContextValue {
  storeId: number
  storeName: string
  storeCode: string | null
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
    organizationId: data?.organization_id ?? null,
    organizationName: data?.organization_name ?? null,
    roleCode: data?.role_code ?? null,
    moduleConfiguration: data?.module_configuration ?? null,
    loading,
    error,
  }
}
