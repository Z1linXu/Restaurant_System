export const OFFLINE_DATABASE_NAME = 'restaurant-pos-offline'
export const OFFLINE_DATABASE_VERSION = 3

export const OFFLINE_STORES = {
  menuHeads: 'menuHeads',
  menuSnapshots: 'menuSnapshots',
  localDrafts: 'localDrafts',
  orderOutbox: 'orderOutbox',
} as const

let databasePromise: Promise<IDBDatabase> | null = null

export class OfflineStorageUnavailableError extends Error {
  constructor(message = 'IndexedDB is not available on this device') {
    super(message)
    this.name = 'OfflineStorageUnavailableError'
  }
}

export function openOfflineDatabase() {
  if (databasePromise) {
    return databasePromise
  }
  if (typeof indexedDB === 'undefined') {
    return Promise.reject(new OfflineStorageUnavailableError())
  }

  databasePromise = new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(OFFLINE_DATABASE_NAME, OFFLINE_DATABASE_VERSION)
    request.onupgradeneeded = () => {
      const database = request.result
      if (!database.objectStoreNames.contains(OFFLINE_STORES.menuHeads)) {
        database.createObjectStore(OFFLINE_STORES.menuHeads, { keyPath: 'key' })
      }
      if (!database.objectStoreNames.contains(OFFLINE_STORES.menuSnapshots)) {
        database.createObjectStore(OFFLINE_STORES.menuSnapshots, { keyPath: 'key' })
      }
      if (!database.objectStoreNames.contains(OFFLINE_STORES.localDrafts)) {
        const drafts = database.createObjectStore(OFFLINE_STORES.localDrafts, { keyPath: 'key' })
        drafts.createIndex('byUpdatedAt', 'updatedAt')
        drafts.createIndex('bySubmitState', 'submitState')
      }
      if (!database.objectStoreNames.contains(OFFLINE_STORES.orderOutbox)) {
        const outbox = database.createObjectStore(OFFLINE_STORES.orderOutbox, { keyPath: 'key' })
        outbox.createIndex('byAccountId', 'accountId')
        outbox.createIndex('byState', 'state')
        outbox.createIndex('byNextRetryAt', 'nextRetryAt')
      }
    }
    request.onsuccess = () => {
      const database = request.result
      database.onversionchange = () => {
        database.close()
        databasePromise = null
      }
      resolve(database)
    }
    request.onerror = () => {
      databasePromise = null
      reject(request.error ?? new OfflineStorageUnavailableError('Unable to open offline storage'))
    }
    request.onblocked = () => {
      databasePromise = null
      reject(new OfflineStorageUnavailableError('Offline storage upgrade is blocked by another app window'))
    }
  })

  return databasePromise
}

export function requestResult<T>(request: IDBRequest<T>) {
  return new Promise<T>((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('IndexedDB request failed'))
  })
}

export function transactionComplete(transaction: IDBTransaction) {
  return new Promise<void>((resolve, reject) => {
    transaction.oncomplete = () => resolve()
    transaction.onerror = () => reject(transaction.error ?? new Error('IndexedDB transaction failed'))
    transaction.onabort = () => reject(transaction.error ?? new Error('IndexedDB transaction was aborted'))
  })
}
