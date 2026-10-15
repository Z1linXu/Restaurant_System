import { OFFLINE_STORES, openOfflineDatabase, requestResult, transactionComplete } from './offlineDatabase'

// Read-only discovery. Unknown provenance is never inferred from the current URL.
export async function hasLegacyOfflineOrders() {
  const database = await openOfflineDatabase()
  const transaction = database.transaction([OFFLINE_STORES.localDrafts, OFFLINE_STORES.orderOutbox], 'readonly')
  const completed = transactionComplete(transaction)
  const records = await Promise.all([OFFLINE_STORES.localDrafts, OFFLINE_STORES.orderOutbox].map((name) => (
    requestResult<Array<{ environmentId?: string; schemaVersion?: number }>>(transaction.objectStore(name).getAll())
  )))
  await completed
  return records.flat().some((record) => !record.environmentId || record.schemaVersion !== 2)
}
