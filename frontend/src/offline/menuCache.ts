import { matchesOfflineScope, offlineScopeKey, type OfflineScope } from './environmentScope'
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

export const MENU_CACHE_SCHEMA_VERSION = 2

export type MenuCacheScope = OfflineScope

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
  return offlineScopeKey(scope)
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

function appendCategories(parts: string[], categories: BackendMenuCategory[], includeItemSortOrder: boolean, includeComboEggDefault: boolean) {
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
      if (includeItemSortOrder) {
        appendValue(parts, item.sort_order)
      }
      if (includeComboEggDefault) {
        appendValue(parts, item.default_combo_egg_component_code)
      }
      appendOptions(parts, item.options)
    })
  })
}

function appendComboConfiguration(parts: string[], catalog: BackendMenuCatalog) {
  appendValue(parts, catalog.combo_configuration?.store_id)
  appendValue(parts, catalog.combo_configuration?.menu_revision)
  const comboGroups = catalog.combo_configuration?.groups ?? []
  appendValue(parts, comboGroups.length)
  comboGroups.forEach((group) => {
    appendValue(parts, group.group_id)
    appendValue(parts, group.group_code)
    appendValue(parts, group.component_group)
    appendValue(parts, group.name_zh)
    appendValue(parts, group.name_en)
    appendValue(parts, group.selection_rule)
    appendValue(parts, group.required)
    appendValue(parts, group.enabled)
    appendValue(parts, group.display_order)
    appendValue(parts, group.default_component_code)
    const components = group.components ?? []
    appendValue(parts, components.length)
    components.forEach((component) => {
      appendValue(parts, component.id)
      appendValue(parts, component.group_id)
      appendValue(parts, component.component_group)
      appendValue(parts, component.component_code)
      appendValue(parts, component.name_zh)
      appendValue(parts, component.name_en)
      appendValue(parts, component.enabled)
      appendValue(parts, component.display_order)
      appendValue(parts, component.is_default)
      appendValue(parts, component.linked_menu_item_id)
      appendValue(parts, component.linked_menu_item_sku)
      appendValue(parts, component.linked_menu_item_name_zh)
      appendValue(parts, component.linked_menu_item_name_en)
      appendValue(parts, component.business_behavior)
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

function hasFrenchOptions(options: BackendMenuOption[] = []): boolean {
  return options.some((option) => option.name_fr != null || hasFrenchOptions(option.side_item_remove_options))
}

function appendFrenchOptions(parts: string[], options: BackendMenuOption[] = []) {
  appendValue(parts, options.length)
  options.forEach((option) => {
    appendValue(parts, option.id)
    appendValue(parts, option.name_fr)
    appendFrenchOptions(parts, option.side_item_remove_options)
  })
}

// Matches MenuCatalogHashService. Keep old canonical bytes and cache records intact.
function appendCustomizationExtension(parts: string[], catalog: BackendMenuCatalog) {
  const groups = catalog.combo_configuration?.groups ?? []
  const extended = catalog.categories.some((category) => category.name_fr != null
    || category.items.some((item) => item.name_fr != null || item.customization_config != null
      || item.sellable === false || hasFrenchOptions(item.options)))
    || groups.some((group) => group.name_fr != null
      || group.components.some((component) => component.name_fr != null || component.linked_menu_item_name_fr != null))
  if (!extended) return
  appendValue(parts, 'menu-customization-v1')
  appendValue(parts, catalog.categories.length)
  catalog.categories.forEach((category) => {
    appendValue(parts, category.id)
    appendValue(parts, category.name_fr)
    appendValue(parts, category.items.length)
    category.items.forEach((item) => {
      appendValue(parts, item.id)
      appendValue(parts, item.name_fr)
      appendValue(parts, item.sellable !== false)
      const config = item.customization_config
      appendValue(parts, config != null)
      if (config) {
        appendValue(parts, config.schema_version)
        appendValue(parts, config.option_groups.length)
        config.option_groups.forEach((group) => {
          appendValue(parts, group.option_group)
          appendValue(parts, group.min_select)
          appendValue(parts, group.max_select)
          appendValue(parts, group.distinct)
          appendValue(parts, group.default_option_codes.length)
          group.default_option_codes.forEach((code) => appendValue(parts, code))
        })
        const codes = Object.keys(config.addon_max_quantities).sort()
        appendValue(parts, codes.length)
        codes.forEach((code) => {
          appendValue(parts, code)
          appendValue(parts, config.addon_max_quantities[code])
        })
        appendValue(parts, config.combo != null)
        if (config.combo) {
          appendValue(parts, config.combo.fixed_price)
          appendValue(parts, config.combo.group_codes.length)
          config.combo.group_codes.forEach((code) => appendValue(parts, code))
        }
      }
      appendFrenchOptions(parts, item.options)
    })
  })
  appendValue(parts, groups.length)
  groups.forEach((group) => {
    appendValue(parts, group.group_id)
    appendValue(parts, group.name_fr)
    appendValue(parts, group.components.length)
    group.components.forEach((component) => {
      appendValue(parts, component.id)
      appendValue(parts, component.name_fr)
      appendValue(parts, component.linked_menu_item_name_fr)
      appendValue(parts, component.synthetic_option_id)
    })
  })
}

export function calculateMenuContentHash(catalog: BackendMenuCatalog) {
  const parts: string[] = []
  appendValue(parts, catalog.store_id)
  appendValue(parts, catalog.organization_id)
  appendValue(parts, catalog.menu_revision)
  appendValue(parts, catalog.catalog_version)
  appendValue(parts, catalog.combo_metadata_version)
  if (catalog.safe_summary) {
    const summary = catalog.safe_summary
    appendValue(parts, summary.schema_version)
    appendValue(parts, summary.config_revision)
    appendValue(parts, summary.brand_name)
    appendValue(parts, summary.locale)
    appendValue(parts, summary.currency)
    appendValue(parts, summary.timezone)
    appendValue(parts, summary.tax_policy_reference)
    appendValue(parts, summary.profile_code)
    appendValue(parts, summary.compatibility_mode)
  }
  appendValue(parts, catalog.tax_policy?.rate)
  appendValue(parts, catalog.tax_policy?.label)
  appendValue(parts, catalog.tax_policy?.version)
  appendValue(parts, catalog.pricing_policy?.store_id)
  appendValue(parts, catalog.pricing_policy?.policy_revision)
  appendValue(parts, catalog.pricing_policy?.size_small_delta)
  appendValue(parts, catalog.pricing_policy?.size_regular_delta)
  appendValue(parts, catalog.pricing_policy?.size_large_delta)
  appendValue(parts, catalog.pricing_policy?.combo_delta)
  appendComboConfiguration(parts, catalog)
  appendCategories(parts, catalog.categories, catalog.catalog_version !== 'menu-catalog-v2', catalog.catalog_version === 'menu-catalog-v4')
  appendCustomizationExtension(parts, catalog)
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
  return matchesOfflineScope(record, scope)
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

export async function readMenuSnapshot(
  scope: MenuCacheScope,
  revision: number,
): Promise<MenuSnapshotRecord | null> {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.menuSnapshots, 'readonly')
  const completed = transactionComplete(transaction)
  const snapshot = await requestResult<MenuSnapshotRecord | undefined>(
    transaction.objectStore(OFFLINE_STORES.menuSnapshots).get(menuSnapshotKey(scope, revision)),
  )
  await completed
  if (!snapshot
    || snapshot.schemaVersion !== MENU_CACHE_SCHEMA_VERSION
    || !recordMatchesScope(snapshot, scope)
    || snapshot.revision !== revision) {
    return null
  }
  try {
    validateMenuCatalog(snapshot.catalog, scope, revision)
  } catch {
    return null
  }
  return snapshot
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
