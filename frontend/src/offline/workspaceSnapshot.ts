import type { AuthUser, LoginResponse } from '../services/authService'
import type {
  StoreContextResponse,
  WorkspaceOrganization,
  WorkspaceResponse,
  WorkspaceStore,
} from '../services/storeWorkspaceService'
import {
  OFFLINE_STORES,
  openOfflineDatabase,
  requestResult,
  transactionComplete,
} from './offlineDatabase'

export const WORKSPACE_SNAPSHOT_SCHEMA_VERSION = 1
export const WORKSPACE_SNAPSHOT_MAX_AGE_MS = 24 * 60 * 60 * 1000

const CURRENT_AUTH_KEY = 'auth:current'

interface CurrentAuthSnapshotRecord {
  key: typeof CURRENT_AUTH_KEY
  accountId: number
  user: AuthUser
  permissions: string[]
  features: Record<string, boolean>
  validatedAt: string
  schemaVersion: number
}

interface WorkspaceSnapshotRecord {
  key: string
  accountId: number
  activeStoreId: number
  workspace: WorkspaceResponse
  validatedAt: string
  schemaVersion: number
}

interface StoreContextSnapshotRecord {
  key: string
  accountId: number
  storeId: number
  context: StoreContextResponse
  validatedAt: string
  schemaVersion: number
}

export interface RestrictedAuthSnapshot {
  accountId: number
  user: AuthUser
  permissions: string[]
  features: Record<string, boolean>
  validatedAt: string
}

function workspaceKey(accountId: number) {
  return `workspace:account:${accountId}`
}

function storeContextKey(accountId: number, storeId: number) {
  return `context:account:${accountId}:store:${storeId}`
}

export function isWorkspaceSnapshotFresh(
  validatedAt: string,
  nowMs = Date.now(),
  maxAgeMs = WORKSPACE_SNAPSHOT_MAX_AGE_MS,
) {
  const validatedAtMs = new Date(validatedAt).getTime()
  return Number.isFinite(validatedAtMs)
    && validatedAtMs <= nowMs
    && nowMs - validatedAtMs <= maxAgeMs
}

function isSnapshotRecordUsable(record: { schemaVersion: number; validatedAt: string } | undefined) {
  return Boolean(record)
    && record!.schemaVersion === WORKSPACE_SNAPSHOT_SCHEMA_VERSION
    && isWorkspaceSnapshotFresh(record!.validatedAt)
}

function selectActiveStore(workspace: WorkspaceResponse, preferredStoreId?: number | null) {
  if (preferredStoreId != null && workspace.stores.some((store) => store.id === preferredStoreId)) {
    return preferredStoreId
  }
  if (workspace.default_store_id != null && workspace.stores.some((store) => store.id === workspace.default_store_id)) {
    return workspace.default_store_id
  }
  return workspace.stores[0]?.id ?? null
}

export function restrictWorkspaceToActiveStore(workspace: WorkspaceResponse, activeStoreId: number): WorkspaceResponse | null {
  const activeStore = workspace.stores.find((store) => store.id === activeStoreId)
  if (!activeStore) {
    return null
  }
  const organizations = activeStore.organization_id == null
    ? []
    : workspace.organizations.filter((organization) => organization.id === activeStore.organization_id)
  return {
    default_store_id: activeStore.id,
    organizations,
    stores: [activeStore],
  }
}

async function readSnapshotRecord<T>(key: string) {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.workspaceSnapshots, 'readonly')
  const completed = transactionComplete(transaction)
  const record = await requestResult<T | undefined>(
    transaction.objectStore(OFFLINE_STORES.workspaceSnapshots).get(key),
  )
  await completed
  return record
}

async function writeSnapshotRecord(record: { key: string }) {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.workspaceSnapshots, 'readwrite')
  const completed = transactionComplete(transaction)
  transaction.objectStore(OFFLINE_STORES.workspaceSnapshots).put(record)
  await completed
}

export async function saveRestrictedAuthSnapshot(response: LoginResponse) {
  const record: CurrentAuthSnapshotRecord = {
    key: CURRENT_AUTH_KEY,
    accountId: response.user.id,
    user: response.user,
    permissions: [...(response.permissions ?? [])],
    features: { ...(response.features ?? {}) },
    validatedAt: new Date().toISOString(),
    schemaVersion: WORKSPACE_SNAPSHOT_SCHEMA_VERSION,
  }
  await writeSnapshotRecord(record)
}

export async function readRestrictedAuthSnapshot(): Promise<RestrictedAuthSnapshot | null> {
  const record = await readSnapshotRecord<CurrentAuthSnapshotRecord>(CURRENT_AUTH_KEY)
  if (!isSnapshotRecordUsable(record) || record!.accountId !== record!.user.id) {
    return null
  }
  return {
    accountId: record!.accountId,
    user: record!.user,
    permissions: [...record!.permissions],
    features: { ...record!.features },
    validatedAt: record!.validatedAt,
  }
}

export async function clearRestrictedAuthSnapshot() {
  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.workspaceSnapshots, 'readwrite')
  const completed = transactionComplete(transaction)
  transaction.objectStore(OFFLINE_STORES.workspaceSnapshots).delete(CURRENT_AUTH_KEY)
  await completed
}

export async function saveWorkspaceSnapshot(accountId: number, workspace: WorkspaceResponse) {
  const existing = await readSnapshotRecord<WorkspaceSnapshotRecord>(workspaceKey(accountId))
  const activeStoreId = selectActiveStore(workspace, existing?.activeStoreId)
  if (activeStoreId == null) {
    return
  }
  const record: WorkspaceSnapshotRecord = {
    key: workspaceKey(accountId),
    accountId,
    activeStoreId,
    workspace,
    validatedAt: new Date().toISOString(),
    schemaVersion: WORKSPACE_SNAPSHOT_SCHEMA_VERSION,
  }
  await writeSnapshotRecord(record)
}

export async function readRestrictedWorkspaceSnapshot(accountId: number): Promise<WorkspaceResponse | null> {
  const record = await readSnapshotRecord<WorkspaceSnapshotRecord>(workspaceKey(accountId))
  if (!isSnapshotRecordUsable(record) || record!.accountId !== accountId) {
    return null
  }
  return restrictWorkspaceToActiveStore(record!.workspace, record!.activeStoreId)
}

function workspaceFromContext(context: StoreContextResponse): WorkspaceResponse {
  const store: WorkspaceStore = {
    id: context.id,
    name: context.name,
    code: context.code,
    status: context.status,
    organization_id: context.organization_id,
    role_code: context.role_code,
  }
  const organizations: WorkspaceOrganization[] = context.organization_id == null
    ? []
    : [{
        id: context.organization_id,
        name: context.organization_name ?? `Organization ${context.organization_id}`,
        code: context.organization_code,
        status: null,
        role_code: context.role_code,
      }]
  return {
    default_store_id: context.id,
    organizations,
    stores: [store],
  }
}

export async function saveStoreContextSnapshot(accountId: number, context: StoreContextResponse) {
  const existingWorkspace = await readSnapshotRecord<WorkspaceSnapshotRecord>(workspaceKey(accountId))
  const now = new Date().toISOString()
  const workspace = existingWorkspace?.workspace ?? workspaceFromContext(context)
  const workspaceRecord: WorkspaceSnapshotRecord = {
    key: workspaceKey(accountId),
    accountId,
    activeStoreId: context.id,
    workspace,
    validatedAt: now,
    schemaVersion: WORKSPACE_SNAPSHOT_SCHEMA_VERSION,
  }
  const contextRecord: StoreContextSnapshotRecord = {
    key: storeContextKey(accountId, context.id),
    accountId,
    storeId: context.id,
    context,
    validatedAt: now,
    schemaVersion: WORKSPACE_SNAPSHOT_SCHEMA_VERSION,
  }

  const database = await openOfflineDatabase()
  const transaction = database.transaction(OFFLINE_STORES.workspaceSnapshots, 'readwrite')
  const completed = transactionComplete(transaction)
  const store = transaction.objectStore(OFFLINE_STORES.workspaceSnapshots)
  store.put(workspaceRecord)
  store.put(contextRecord)
  await completed
}

export async function readRestrictedStoreContextSnapshot(accountId: number, storeId: number) {
  const workspace = await readSnapshotRecord<WorkspaceSnapshotRecord>(workspaceKey(accountId))
  if (!isSnapshotRecordUsable(workspace)
    || workspace!.accountId !== accountId
    || workspace!.activeStoreId !== storeId) {
    return null
  }
  const context = await readSnapshotRecord<StoreContextSnapshotRecord>(storeContextKey(accountId, storeId))
  if (!isSnapshotRecordUsable(context)
    || context!.accountId !== accountId
    || context!.storeId !== storeId) {
    return null
  }
  return context!.context
}
