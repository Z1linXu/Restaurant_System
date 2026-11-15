import { AlloSalesOverview } from '../features/allo/AlloSalesOverview'
import { useCurrentStore } from '../features/store/useStoreContext'
import { SalesReportPage } from '../features/reports/SalesReportPage'

export default function AdminReportsSales() {
  const { storeCode }=useCurrentStore()
  return storeCode==='ALLO_NOUILLES_TEST'?<AlloSalesOverview/>:<SalesReportPage />
}
