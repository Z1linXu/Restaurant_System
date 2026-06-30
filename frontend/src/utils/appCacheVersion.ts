const APP_VERSION_KEY = 'restaurant_pos_app_build_version'
const APP_REFRESHED_VERSION_KEY = 'restaurant_pos_app_refreshed_version'

const CACHE_KEYS_TO_CLEAR = [
  'restaurant_pos_menu_catalog',
  'restaurant_pos_feature_config',
  'restaurant_pos_ui_cache',
  'restaurant_pos_owner_dashboard_cache',
]

export function prepareAppCacheVersion() {
  if (typeof window === 'undefined') {
    return true
  }

  const buildVersion = typeof __APP_BUILD_VERSION__ === 'string' && __APP_BUILD_VERSION__
    ? __APP_BUILD_VERSION__
    : 'dev'
  const previousVersion = window.localStorage.getItem(APP_VERSION_KEY)

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
