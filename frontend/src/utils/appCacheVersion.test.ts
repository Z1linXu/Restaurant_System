import { describe, expect, it } from 'vitest'
import { OFFLINE_DATABASE_VERSION, OFFLINE_STORES } from '../offline/offlineDatabase'
import { bundledSchemaCompatibility } from './appCacheVersion'

const bundledConfig: RestaurantBundledAppConfig = {
  mode: 'BUNDLED_ASSETS',
  appVersion: '0.2.0',
  buildVersion: 'test-build',
  assetManifestSha256: 'abc',
  offlineDatabaseSchemaVersion: OFFLINE_DATABASE_VERSION,
  storeId: 1,
}

describe('bundled app compatibility', () => {
  it('requires the APK manifest and frontend IndexedDB schema to match', () => {
    expect(bundledSchemaCompatibility(bundledConfig)).toBe('COMPATIBLE')
    expect(bundledSchemaCompatibility({
      ...bundledConfig,
      offlineDatabaseSchemaVersion: OFFLINE_DATABASE_VERSION - 1,
    })).toBe('MISMATCH')
    expect(bundledSchemaCompatibility(undefined)).toBe('NOT_BUNDLED')
  })

  it('keeps existing draft and outbox stores during the additive schema upgrade', () => {
    expect(OFFLINE_STORES.localDrafts).toBe('localDrafts')
    expect(OFFLINE_STORES.orderOutbox).toBe('orderOutbox')
    expect(OFFLINE_STORES.workspaceSnapshots).toBe('workspaceSnapshots')
  })
})
