import type {
  StoreModuleConfiguration,
  StoreModuleState,
  StoreModuleValidationIssue,
} from '../../services/storeWorkspaceService'

export const STORE_MODULE_KEYS = [
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
] as const

export type StoreModuleKey = typeof STORE_MODULE_KEYS[number]

export type StoreModuleAccessStatus =
  | 'ALLOWED'
  | 'MODULE_DISABLED'
  | 'MODULE_CONFIGURATION_INVALID'
  | 'MODULE_ENVIRONMENT_CAPABILITY_MISSING'
  | 'MODULE_HARDWARE_CAPABILITY_MISSING'

export interface StoreModuleAccessResult {
  allowed: boolean
  moduleKey: StoreModuleKey
  status: StoreModuleAccessStatus
  displayName: string
  module: StoreModuleState | null
  issues: StoreModuleValidationIssue[]
  message: string
}

export const STORE_MODULE_DISPLAY_NAMES: Record<StoreModuleKey, string> = {
  ORDERING_POS: 'Ordering / POS',
  MENU: 'Menu Catalog',
  MENU_MANAGEMENT: 'Menu Management',
  TABLE_MANAGEMENT: 'Table Management',
  PRINTING: 'Printing',
  ORDER_HISTORY: 'Order History',
  REPORTING_CORE: 'Reporting Core',
  STAFF_ACCESS: 'Staff and Access',
  STORE_ADMINISTRATION: 'Store Administration',
  KDS: 'Kitchen Display System',
  ANALYTICS_ADVANCED: 'Advanced Analytics',
}

export function getStoreModuleDisplayName(moduleKey: StoreModuleKey) {
  return STORE_MODULE_DISPLAY_NAMES[moduleKey]
}

export function findStoreModule(
  moduleConfiguration: StoreModuleConfiguration | null | undefined,
  moduleKey: StoreModuleKey,
) {
  return moduleConfiguration?.modules?.find((module) => module.module_key === moduleKey) ?? null
}

export function getStoreModuleIssues(
  moduleConfiguration: StoreModuleConfiguration | null | undefined,
  moduleKey: StoreModuleKey,
) {
  return (moduleConfiguration?.validation_issues ?? []).filter((issue) => (
    issue.module_key === moduleKey || issue.target === moduleKey
  ))
}

export function evaluateStoreModuleAccess(
  moduleConfiguration: StoreModuleConfiguration | null | undefined,
  moduleKey: StoreModuleKey,
): StoreModuleAccessResult {
  const module = findStoreModule(moduleConfiguration, moduleKey)
  const displayName = module?.display_name ?? getStoreModuleDisplayName(moduleKey)
  if (!moduleConfiguration) {
    return denied(moduleKey, 'MODULE_CONFIGURATION_INVALID', displayName, null, [], 'Store module configuration is unavailable.')
  }
  if (!module) {
    return denied(moduleKey, 'MODULE_CONFIGURATION_INVALID', displayName, null, [], `Store module configuration is missing: ${moduleKey}.`)
  }
  if (!module.persisted) {
    return denied(moduleKey, 'MODULE_CONFIGURATION_INVALID', displayName, module, [], `Store module is not materialized for this Store: ${moduleKey}.`)
  }
  if (module.enabled !== true) {
    return denied(moduleKey, 'MODULE_DISABLED', displayName, module, [], `Module disabled for this Store: ${moduleKey}.`)
  }

  const issues = getStoreModuleIssues(moduleConfiguration, moduleKey)
  if (issues.some((issue) => issue.code === 'ENVIRONMENT_CAPABILITY_MISSING')) {
    return denied(
      moduleKey,
      'MODULE_ENVIRONMENT_CAPABILITY_MISSING',
      displayName,
      module,
      issues,
      `Environment capability missing for ${moduleKey}.`,
    )
  }
  if (issues.some((issue) => issue.code === 'HARDWARE_CAPABILITY_MISSING')) {
    return denied(
      moduleKey,
      'MODULE_HARDWARE_CAPABILITY_MISSING',
      displayName,
      module,
      issues,
      `Hardware capability missing for ${moduleKey}.`,
    )
  }
  if (issues.length > 0) {
    return denied(
      moduleKey,
      'MODULE_CONFIGURATION_INVALID',
      displayName,
      module,
      issues,
      `Store module configuration is invalid for ${moduleKey}.`,
    )
  }

  return {
    allowed: true,
    moduleKey,
    status: 'ALLOWED',
    displayName,
    module,
    issues: [],
    message: 'Module capability allowed.',
  }
}

export function isStoreModuleEnabled(
  moduleConfiguration: StoreModuleConfiguration | null | undefined,
  moduleKey: StoreModuleKey,
) {
  return evaluateStoreModuleAccess(moduleConfiguration, moduleKey).allowed
}

export function evaluateStoreModuleManagementAccess(
  moduleConfiguration: StoreModuleConfiguration | null | undefined,
  moduleKey: StoreModuleKey,
): StoreModuleAccessResult {
  const module = findStoreModule(moduleConfiguration, moduleKey)
  const displayName = module?.display_name ?? getStoreModuleDisplayName(moduleKey)
  if (!moduleConfiguration || !module || !module.persisted) {
    return denied(moduleKey, 'MODULE_CONFIGURATION_INVALID', displayName, module, [], `Store module configuration is unavailable: ${moduleKey}.`)
  }
  if (module.enabled !== true) {
    return denied(moduleKey, 'MODULE_DISABLED', displayName, module, [], `Module disabled for this Store: ${moduleKey}.`)
  }
  const issues = getStoreModuleIssues(moduleConfiguration, moduleKey)
  const environmentIssues = issues.filter((issue) => issue.code === 'ENVIRONMENT_CAPABILITY_MISSING')
  if (environmentIssues.length > 0) {
    return denied(
      moduleKey,
      'MODULE_ENVIRONMENT_CAPABILITY_MISSING',
      displayName,
      module,
      environmentIssues,
      `Environment capability missing for ${moduleKey}.`,
    )
  }
  return {
    allowed: true,
    moduleKey,
    status: 'ALLOWED',
    displayName,
    module,
    issues,
    message: 'Module management allowed; runtime capability is evaluated separately.',
  }
}

export function isStoreModuleManagementEnabled(
  moduleConfiguration: StoreModuleConfiguration | null | undefined,
  moduleKey: StoreModuleKey,
) {
  return evaluateStoreModuleManagementAccess(moduleConfiguration, moduleKey).allowed
}

export function getRequiredStoreModuleForPath(pathname: string): StoreModuleKey | null {
  const normalized = normalizeStoreRoutePath(pathname)
  if (normalized.startsWith('/admin/platform')) {
    return null
  }
  if (normalized.startsWith('/admin/reports/')) {
    return 'REPORTING_CORE'
  }
  if (normalized === '/admin' || normalized === '/admin/' || normalized.startsWith('/admin/dashboard')) {
    return 'STORE_ADMINISTRATION'
  }
  if (normalized.startsWith('/admin/staff')) {
    return 'STAFF_ACCESS'
  }
  if (normalized.startsWith('/admin/audit-logs') || normalized.startsWith('/admin/audit')) {
    return 'STORE_ADMINISTRATION'
  }
  if (normalized.startsWith('/admin/settings/tables')) {
    return 'TABLE_MANAGEMENT'
  }
  if (normalized.startsWith('/admin/menu/items')) {
    return 'MENU_MANAGEMENT'
  }
  if (normalized.startsWith('/admin/settings/printing')) {
    return 'PRINTING'
  }
  if (normalized.startsWith('/pickup') || normalized.startsWith('/kds/')) {
    return 'KDS'
  }
  if (normalized.startsWith('/frontdesk/order') || normalized.startsWith('/orders')) {
    return 'ORDER_HISTORY'
  }
  if (
    normalized === '/frontdesk'
    || normalized === '/frontdesk/'
    || normalized.startsWith('/frontdesk/menu')
    || normalized.startsWith('/menu')
  ) {
    return 'ORDERING_POS'
  }
  return null
}

function normalizeStoreRoutePath(pathname: string) {
  return pathname.replace(/^\/stores\/\d+/, '') || '/'
}

function denied(
  moduleKey: StoreModuleKey,
  status: Exclude<StoreModuleAccessStatus, 'ALLOWED'>,
  displayName: string,
  module: StoreModuleState | null,
  issues: StoreModuleValidationIssue[],
  message: string,
): StoreModuleAccessResult {
  return {
    allowed: false,
    moduleKey,
    status,
    displayName,
    module,
    issues,
    message,
  }
}
