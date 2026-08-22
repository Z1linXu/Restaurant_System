import { describe, expect, it } from 'vitest'
import type { StoreModuleConfiguration, StoreModuleState } from '../../services/storeWorkspaceService'
import {
  evaluateStoreModuleAccess,
  evaluateStoreModuleManagementAccess,
  getRequiredStoreModuleForPath,
  isStoreModuleEnabled,
  type StoreModuleKey,
} from './storeModuleAccess'

const moduleKeys: StoreModuleKey[] = [
  'ORDERING_POS',
  'MENU',
  'MENU_MANAGEMENT',
  'TABLE_MANAGEMENT',
  'PRINTING',
  'ORDER_HISTORY',
  'REPORTING_CORE',
  'STAFF_ACCESS',
  'STORE_ADMINISTRATION',
  'KDS',
  'ANALYTICS_ADVANCED',
]

function moduleState(moduleKey: StoreModuleKey, enabled: boolean): StoreModuleState {
  return {
    module_key: moduleKey,
    display_name: moduleKey,
    classification: moduleKey === 'KDS' || moduleKey === 'ANALYTICS_ADVANCED' ? 'OPTIONAL_MODULE' : 'CORE_MODULE',
    category: 'STORE_OPERATION',
    enabled,
    default_enabled: enabled,
    core_required: moduleKey !== 'KDS' && moduleKey !== 'ANALYTICS_ADVANCED',
    active_normal_store_required: moduleKey !== 'KDS' && moduleKey !== 'ANALYTICS_ADVANCED',
    activation_blocking: moduleKey !== 'KDS' && moduleKey !== 'ANALYTICS_ADVANCED',
    persisted: true,
    source: 'STORE_PROFILE',
    configuration_status: 'VALID',
    profile_code: 'NORMAL_LANZHOU_STORE',
    profile_version: 'v1',
    legacy_runtime_mode: moduleKey === 'PRINTING' ? 'MOCK' : null,
    legacy_store_flag: moduleKey === 'PRINTING' ? true : null,
  }
}

function moduleConfiguration(overrides: Partial<Record<StoreModuleKey, boolean>> = {}): StoreModuleConfiguration {
  return {
    store_id: 1,
    catalog_version: 'PHASE_A1_MODULE_CATALOG_V1',
    dependency_graph_version: 'PHASE_A2_MODULE_DEPENDENCY_GRAPH_V1',
    valid: true,
    validation_status: 'VALID',
    environment_capability_source: 'CURRENT_ENVIRONMENT',
    hardware_capability_source: 'A8_DEFERRED',
    legacy_compatibility_status: 'COMPATIBLE',
    legacy_precedence: 'STORE_MODULES_CANONICAL',
    environment_capabilities: ['CORE_POS_RUNTIME', 'AUTH_RUNTIME', 'DATABASE', 'WEBSOCKET_RUNTIME', 'PRINTING_FEATURE_FLAG', 'PRINT_MODE_RUNTIME', 'ANALYTICS_FEATURE_FLAG'],
    hardware_capabilities: ['TOUCH_CLIENT'],
    hardware_readiness: [],
    modules: moduleKeys.map((moduleKey) => moduleState(moduleKey, overrides[moduleKey] ?? true)),
    validation_issues: [],
  }
}

describe('Store module access contract', () => {
  it('fails closed when Store Context has no module configuration', () => {
    expect(evaluateStoreModuleAccess(null, 'ORDERING_POS')).toMatchObject({
      allowed: false,
      status: 'MODULE_CONFIGURATION_INVALID',
    })
  })

  it('treats disabled KDS as a Store module disabled state', () => {
    const configuration = moduleConfiguration({ KDS: false })
    expect(evaluateStoreModuleAccess(configuration, 'KDS')).toMatchObject({
      allowed: false,
      status: 'MODULE_DISABLED',
    })
  })

  it('allows Reporting Core without requiring Analytics Advanced', () => {
    const configuration = moduleConfiguration({ ANALYTICS_ADVANCED: false })
    expect(isStoreModuleEnabled(configuration, 'REPORTING_CORE')).toBe(true)
    expect(evaluateStoreModuleAccess(configuration, 'ANALYTICS_ADVANCED')).toMatchObject({
      allowed: false,
      status: 'MODULE_DISABLED',
    })
  })

  it('surfaces module environment capability gaps separately from disabled modules', () => {
    const configuration = moduleConfiguration()
    configuration.valid = false
    configuration.validation_status = 'INVALID'
    configuration.validation_issues = [{
      code: 'ENVIRONMENT_CAPABILITY_MISSING',
      module_key: 'PRINTING',
      target: 'PRINTING_FEATURE_FLAG',
      message: 'PRINTING_FEATURE_FLAG is missing.',
    }]
    expect(evaluateStoreModuleAccess(configuration, 'PRINTING')).toMatchObject({
      allowed: false,
      status: 'MODULE_ENVIRONMENT_CAPABILITY_MISSING',
    })
  })

  it('surfaces module hardware capability gaps separately from disabled modules', () => {
    const configuration = moduleConfiguration()
    configuration.valid = false
    configuration.validation_status = 'INVALID'
    configuration.validation_issues = [{
      code: 'HARDWARE_CAPABILITY_MISSING',
      module_key: 'PRINTING',
      target: 'PRINT_HOT_KITCHEN',
      message: 'PRINT_HOT_KITCHEN is missing.',
    }]
    expect(evaluateStoreModuleAccess(configuration, 'PRINTING')).toMatchObject({
      allowed: false,
      status: 'MODULE_HARDWARE_CAPABILITY_MISSING',
    })
    expect(evaluateStoreModuleManagementAccess(configuration, 'PRINTING')).toMatchObject({
      allowed: true,
      status: 'ALLOWED',
    })
  })

  it('maps store-scoped routes to canonical Store modules', () => {
    expect(getRequiredStoreModuleForPath('/stores/1/frontdesk')).toBe('ORDERING_POS')
    expect(getRequiredStoreModuleForPath('/stores/1/frontdesk/order')).toBe('ORDER_HISTORY')
    expect(getRequiredStoreModuleForPath('/stores/1/admin/settings/printing')).toBe('PRINTING')
    expect(getRequiredStoreModuleForPath('/stores/1/admin/reports/sales')).toBe('REPORTING_CORE')
    expect(getRequiredStoreModuleForPath('/stores/1/admin/reports/profit')).toBe('REPORTING_CORE')
    expect(getRequiredStoreModuleForPath('/stores/1/pickup')).toBe('KDS')
    expect(getRequiredStoreModuleForPath('/stores/1/kds/hot-kitchen')).toBe('KDS')
    expect(getRequiredStoreModuleForPath('/stores/1/admin/platform')).toBeNull()
  })
})
