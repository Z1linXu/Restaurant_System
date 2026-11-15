import { AlloSalesOverview } from '../features/allo/AlloSalesOverview'
import { useCurrentStore } from '../features/store/useStoreContext'
import { OwnerAdminDashboardPage } from '../features/owner-admin/OwnerAdminDashboardPage'

export default function AdminDashboard() {
  const { storeCode }=useCurrentStore()
  return storeCode==='ALLO_NOUILLES_TEST'?<AlloSalesOverview/>:<OwnerAdminDashboardPage />
}
