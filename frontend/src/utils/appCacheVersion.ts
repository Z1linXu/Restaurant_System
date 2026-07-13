import { OFFLINE_DATABASE_VERSION } from '../offline/offlineDatabase'

const APP_VERSION_KEY = 'restaurant_pos_app_build_version'
const APP_REFRESHED_VERSION_KEY = 'restaurant_pos_app_refreshed_version'
const BUNDLED_SCHEMA_WARNING_KEY = 'restaurant_pos_bundled_schema_warning'

const CACHE_KEYS_TO_CLEAR = [
  'restaurant_pos_menu_catalog',
  'restaurant_pos_feature_config',
  'restaurant_pos_ui_cache',
  'restaurant_pos_owner_dashboard_cache',
]

export function bundledSchemaCompatibility(
  config: RestaurantBundledAppConfig | undefined,
  expectedSchemaVersion = OFFLINE_DATABASE_VERSION,
) {
  if (!config || config.mode !== 'BUNDLED_ASSETS') {
    return 'NOT_BUNDLED' as const
  }
  return config.offlineDatabaseSchemaVersion === expectedSchemaVersion
    ? 'COMPATIBLE' as const
    : 'MISMATCH' as const
}

export function prepareAppCacheVersion() {
  if (typeof window === 'undefined') {
    return true
  }

  const buildVersion = typeof __APP_BUILD_VERSION__ === 'string' && __APP_BUILD_VERSION__
    ? __APP_BUILD_VERSION__
    : 'dev'
  const previousVersion = window.localStorage.getItem(APP_VERSION_KEY)
  const schemaCompatibility = bundledSchemaCompatibility(window.__RESTAURANT_APP_CONFIG__)

  if (schemaCompatibility === 'MISMATCH') {
    const warning = `Bundled offline schema mismatch: APK=${window.__RESTAURANT_APP_CONFIG__?.offlineDatabaseSchemaVersion}, frontend=${OFFLINE_DATABASE_VERSION}`
    window.localStorage.setItem(BUNDLED_SCHEMA_WARNING_KEY, warning)
    console.error(`[appCacheVersion] ${warning}`)
  } else if (schemaCompatibility === 'COMPATIBLE') {
    window.localStorage.removeItem(BUNDLED_SCHEMA_WARNING_KEY)
  }

  if (previousVersion === buildVersion) {
    return true
  }

  CACHE_KEYS_TO_CLEAR.forEach((key) => {
    window.localStorage.removeItem(key)
    window.sessionStorage.removeItem(key)
  })
  window.localStorage.setItem(APP_VERSION_KEY, buildVersion)

  if (window.sessionStorage.getItem(APP_REFRESHED_VERSION_KEY) !== buildVersion) {
    window.sessionStorage.setItem(APP_REFRESHED_VERSION_KEY, buildVersion)
    window.location.reload()
    return false
  }

  return true
}
