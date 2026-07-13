/// <reference types="vite/client" />

declare const __APP_BUILD_VERSION__: string

interface RestaurantBundledAppConfig {
  mode: 'BUNDLED_ASSETS'
  appVersion: string
  buildVersion: string
  assetManifestSha256: string
  offlineDatabaseSchemaVersion: number
  storeId: number | null
}

interface Window {
  __RESTAURANT_API_BASE_URL__?: string
  __RESTAURANT_WS_BASE_URL__?: string
  __RESTAURANT_APP_CONFIG__?: RestaurantBundledAppConfig
}
