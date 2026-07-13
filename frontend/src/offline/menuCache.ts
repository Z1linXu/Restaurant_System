import type {
  BackendMenuCatalog,
  BackendMenuCategory,
  BackendMenuOption,
} from '../types/ordering'
import {
  OFFLINE_STORES,
  openOfflineDatabase,
  requestResult,
  transactionComplete,
} from './offlineDatabase'

export const MENU_CACHE_SCHEMA_VERSION = 1

export interface MenuCacheScope {
  accountId: number
  organizationId: number
  storeId: number
}

export interface MenuHeadRecord extends MenuCacheScope {
  key: string
  activeRevision: number
  lastUpdatedAt: string
  etag: string
  contentHash: string
  schemaVersion: number
}

export interface MenuSnapshotRecord extends MenuCacheScope {
  key: string
  revision: number
  catalog: BackendMenuCatalog
  downloadedAt: string
  contentHash: string
  schemaVersion: number
}

export interface ActiveMenuSnapshot {
  head: MenuHeadRecord
  snapshot: MenuSnapshotRecord
}

export function menuScopeKey(scope: MenuCacheScope) {
  return `account:${scope.accountId}|organization:${scope.organizationId}|store:${scope.storeId}`
}

export function menuSnapshotKey(scope: MenuCacheScope, revision: number) {
  return `${menuScopeKey(scope)}|revision:${revision}`
}

function normalizeValue(value: unknown) {
  if (value == null) {
    return '<null>'
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? String(value) : '<invalid-number>'
  }
  return String(value)
}

function appendValue(parts: string[], value: unknown) {
  const normalized = normalizeValue(value)
  parts.push(`${normalized.length}:${normalized}|`)
}

function appendOptions(parts: string[], options: BackendMenuOption[] | undefined) {
  const safeOptions = options ?? []
  appendValue(parts, safeOptions.length)
  safeOptions.forEach((option) => {
    appendValue(parts, option.id)
    appendValue(parts, option.option_type)
    appendValue(parts, option.option_code)
    appendValue(parts, option.option_group)
    appendValue(parts, option.parent_option_id)
    appendValue(parts, option.sort_order)
    appendValue(parts, option.name_zh)
    appendValue(parts, option.name_en)
    appendValue(parts, option.price_delta)
    appendValue(parts, option.is_active)
    appendOptions(parts, option.side_item_remove_options)
  })
}

function appendCategories(parts: string[], categories: BackendMenuCategory[]) {
  appendValue(parts, categories.length)
  categories.forEach((category) => {
    appendValue(parts, category.id)
    appendValue(parts, category.code)
    appendValue(parts, category.name_zh)
    appendValue(parts, category.name_en)
    appendValue(parts, category.sort_order)
    appendValue(parts, category.is_active)
    appendValue(parts, category.items.length)
    category.items.forEach((item) => {
      appendValue(parts, item.id)
      appendValue(parts, item.category_id)
      appendValue(parts, item.station_id)
      appendValue(parts, item.name_zh)
      appendValue(parts, item.name_en)
      appendValue(parts, item.sku)
      appendValue(parts, item.item_type)
      appendValue(parts, item.base_price)
      appendValue(parts, item.is_active)
      appendValue(parts, item.is_sold_out)
      appendOptions(parts, item.options)
    })
  })
}

function fnv1a32(value: string) {
  let hash = 0x811c9dc5
  const bytes = new TextEncoder().encode(value)
  bytes.forEach((byte) => {
    hash = Math.imul(hash ^ byte, 0x01000193) >>> 0
  })
  return hash.toString(16).padStart(8, '0')
}

export function calculateMenuContentHash(catalog: BackendMenuCatalog) {
  const parts: string[] = []
  appendValue(parts, catalog.store_id)
  appendValue(parts, catalog.organization_id)
  appendValue(parts, catalog.menu_revision)
  appendValue(parts, catalog.catalog_version)
  appendValue(parts, catalog.combo_metadata_version)
  appendValue(parts, catalog.tax_policy?.rate)
  appendValue(parts, catalog.tax_policy?.label)
  appendValue(parts, catalog.tax_policy?.version)
  appendCategories(parts, catalog.categories)
  return `fnv1a32:${fnv1a32(parts.join(''))}`
}

export function validateMenuCatalog(
  catalog: BackendMenuCatalog,
  scope: MenuCacheScope,
  expectedRevision: number,
) {
  if (catalog.store_id !== scope.storeId || catalog.organization_id !== scope.organizationId) {
    throw new Error('MENU_CACHE_SCOPE_MISMATCH')
  }
  if (catalog.menu_revision !== expectedRevision) {
    throw new Error('MENU_CACHE_REVISION_MISMATCH')
  }
  if (!catalog.content_hash || calculateMenuContentHash(catalog) !== catalog.content_hash) {
    throw new Error('MENU_CACHE_HASH_MISMATCH')
  }
}

function recordMatchesScope(record: MenuCacheScope, scope: MenuCacheScope) {
  return record.accountId === scope.accountId
    && record.organizationId === scope.organizationId
    && record.storeId === scope.storeId
}

export async function readActiveMenuSnapshot(scope: MenuCacheScope): Promise<ActiveMenuSnapshot | null> {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(
    [OFFLINE_STORES.menuHeads, OFFLINE_STORES.menuSnapshots],
    'readonly',
  )
  const completed = transactionComplete(transaction)
  const head = await requestResult<MenuHeadRecord | undefined>(
    transaction.objectStore(OFFLINE_STORES.menuHeads).get(menuScopeKey(scope)),
  )
  if (!head) {
    await completed
    return null
  }
  const snapshot = await requestResult<MenuSnapshotRecord | undefined>(
    transaction.objectStore(OFFLINE_STORES.menuSnapshots).get(menuSnapshotKey(scope, head.activeRevision)),
  )
  await completed

  if (!snapshot
    || head.schemaVersion !== MENU_CACHE_SCHEMA_VERSION
    || snapshot.schemaVersion !== MENU_CACHE_SCHEMA_VERSION
    || !recordMatchesScope(head, scope)
    || !recordMatchesScope(snapshot, scope)
    || snapshot.revision !== head.activeRevision) {
    await deleteMenuCacheScope(scope)
    return null
  }

  try {
    validateMenuCatalog(snapshot.catalog, scope, snapshot.revision)
  } catch {
    await deleteMenuCacheScope(scope)
    return null
  }

  return { head, snapshot }
}

export async function replaceActiveMenuSnapshot(
  scope: MenuCacheScope,
  catalog: BackendMenuCatalog,
  etag: string,
) {
  validateMenuCatalog(catalog, scope, catalog.menu_revision)
  const downloadedAt = new Date().toISOString()
  const snapshot: MenuSnapshotRecord = {
    ...scope,
    key: menuSnapshotKey(scope, catalog.menu_revision),
    revision: catalog.menu_revision,
    catalog,
    downloadedAt,
    contentHash: catalog.content_hash,
    schemaVersion: MENU_CACHE_SCHEMA_VERSION,
  }
  const head: MenuHeadRecord = {
    ...scope,
    key: menuScopeKey(scope),
    activeRevision: catalog.menu_revision,
    lastUpdatedAt: downloadedAt,
    etag,
    contentHash: catalog.content_hash,
    schemaVersion: MENU_CACHE_SCHEMA_VERSION,
  }

  const database = await openOfflineDatabase()
  const transaction = database.transaction(
    [OFFLINE_STORES.menuHeads, OFFLINE_STORES.menuSnapshots],
    'readwrite',
  )
  const completed = transactionComplete(transaction)
  transaction.objectStore(OFFLINE_STORES.menuSnapshots).put(snapshot)
  transaction.objectStore(OFFLINE_STORES.menuHeads).put(head)
  await completed
  return { head, snapshot }
}

export async function deleteMenuCacheScope(scope: MenuCacheScope) {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(
    [OFFLINE_STORES.menuHeads, OFFLINE_STORES.menuSnapshots],
    'readwrite',
  )
  const completed = transactionComplete(transaction)
  transaction.objectStore(OFFLINE_STORES.menuHeads).delete(menuScopeKey(scope))
  const snapshots = transaction.objectStore(OFFLINE_STORES.menuSnapshots)
  const prefix = `${menuScopeKey(scope)}|revision:`
  const cursorRequest = snapshots.openCursor()
  cursorRequest.onsuccess = () => {
    const cursor = cursorRequest.result
    if (!cursor) {
      return
    }
    if (String(cursor.key).startsWith(prefix)) {
      cursor.delete()
    }
    cursor.continue()
  }
  await completed
}
