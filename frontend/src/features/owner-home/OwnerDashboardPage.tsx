import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { navigateTo } from '../frontdesk/navigation'
import { buildStorePath } from '../store/storeRoutes'
import { getApiUserMessage } from '../../services/apiClient'
import {
  fetchOwnerStoreProvisioningCatalog,
  provisionOwnerStore,
  type OwnerStoreProvisioningCatalog,
  type OwnerStoreProvisioningResult,
} from '../../services/ownerStoreProvisioningService'
import {
  fetchOwnerOverview,
  type OwnerOverviewOrganization,
  type OwnerOverviewResponse,
  type OwnerOverviewStore,
} from '../../services/ownerWorkspaceService'

function formatMoney(value: number | null | undefined) {
  if (value == null) return 'N/A'
  return `$${Number(value).toFixed(2)}`
}

function formatTime(value: string | null | undefined) {
  if (!value) return 'N/A'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function normalizeFeature(features: Record<string, boolean>, key: string) {
  return Boolean(features[key] ?? features[key.toLowerCase()] ?? features[key.toUpperCase()])
}

function normalizeStatus(value: string | null | undefined, fallback = 'N/A') {
  if (!value) return fallback
  return value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function normalizeStoreCode(value: string) {
  const normalized = value
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .replace(/_{2,}/g, '_')
    .slice(0, 64)
  if (!normalized) return ''
  if (/^[A-Z][A-Z0-9_]{1,63}$/.test(normalized)) {
    return normalized
  }
  return `STORE_${normalized}`.slice(0, 64)
}

function storeCodeFromName(name: string) {
  return normalizeStoreCode(name) || 'PHASE_B_TEST_STORE'
}

function isOperationalStore(store: OwnerOverviewStore) {
  const status = (store.status ?? 'active').toLowerCase()
  const lifecycle = (store.lifecycle_status ?? 'ACTIVE').toUpperCase()
  const kind = (store.store_kind ?? 'BUSINESS').toUpperCase()
  return status === 'active' && lifecycle === 'ACTIVE' && kind === 'BUSINESS'
}

function isPhaseBProvisionedStore(store: OwnerOverviewStore) {
  return (store.provisioning_source ?? '').toUpperCase() === 'PHASE_B_OWNER_PROVISIONING'
}

function isVisibleOwnerStore(store: OwnerOverviewStore) {
  return (store.store_kind ?? 'BUSINESS').toUpperCase() !== 'VALIDATION_FIXTURE'
    || isPhaseBProvisionedStore(store)
}

function countValue(result: OwnerStoreProvisioningResult | null, key: keyof OwnerStoreProvisioningResult['counts']) {
  return result?.counts?.[key] ?? 0
}

function Metric({ label, value, tone = 'default' }: { label: string; value: string | number; tone?: 'default' | 'danger' | 'muted' }) {
  const toneClass = tone === 'danger'
    ? 'bg-red-50 text-red-800'
    : tone === 'muted'
      ? 'bg-stone-100 text-stone-600'
      : 'bg-[rgba(97,0,0,0.06)] text-[var(--on-surface)]'
  return (
    <div className={`rounded-[18px] px-4 py-3 ${toneClass}`}>
      <div className="text-[0.72rem] font-black uppercase tracking-[0.12em] opacity-70">{label}</div>
      <div className="mt-1 text-[1.25rem] font-black tracking-[-0.04em]">{value}</div>
    </div>
  )
}

function FeatureChip({ enabled, label }: { enabled: boolean; label: string }) {
  return (
    <span
      className={`rounded-full px-3 py-1 text-[0.72rem] font-black uppercase tracking-[0.08em] ${
        enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-stone-100 text-stone-500'
      }`}
    >
      {label}: {enabled ? 'On' : 'Off'}
    </span>
  )
}

function StoreStateBadge({ store }: { store: OwnerOverviewStore }) {
  const operational = isOperationalStore(store)
  const phaseB = isPhaseBProvisionedStore(store)
  const tone = operational
    ? 'bg-emerald-50 text-emerald-700'
    : phaseB
      ? 'bg-amber-50 text-amber-800'
      : 'bg-stone-100 text-stone-600'
  const label = operational
    ? 'Live'
    : phaseB
      ? 'Not Live'
      : normalizeStatus(store.store_kind ?? store.status)

  return (
    <span className={`rounded-full px-3 py-1 text-[0.72rem] font-black uppercase ${tone}`}>
      {label}: {normalizeStatus(store.lifecycle_status ?? store.status, 'Unknown')}
    </span>
  )
}

function StoreAction({
  store,
  path,
  label,
  enabled = true,
  reason,
}: {
  store: OwnerOverviewStore
  path: string
  label: string
  enabled?: boolean
  reason?: string
}) {
  return (
    <button
      type="button"
      disabled={!enabled}
      title={!enabled ? reason : undefined}
      onClick={() => navigateTo(buildStorePath(store.id, path))}
      className={`rounded-[16px] px-4 py-3 text-left text-[0.9rem] font-black transition ${
        enabled
          ? 'bg-[var(--primary)] text-white shadow-[0_14px_28px_rgba(97,0,0,0.16)] hover:translate-y-[-1px]'
          : 'cursor-not-allowed bg-stone-100 text-stone-400'
      }`}
    >
      {label}
      {!enabled && reason ? <span className="mt-1 block text-[0.7rem] font-semibold normal-case">{reason}</span> : null}
    </button>
  )
}

function StoreCard({ store }: { store: OwnerOverviewStore }) {
  const features = {
    core: normalizeFeature(store.features, 'core_pos'),
    printing: normalizeFeature(store.features, 'printing'),
    kds: normalizeFeature(store.features, 'kds'),
    admin: normalizeFeature(store.features, 'admin'),
    analytics: normalizeFeature(store.features, 'analytics'),
  }
  const summary = store.summary
  const failedPrintTone = summary.failed_print_jobs > 0 ? 'danger' : 'default'
  const operational = isOperationalStore(store)
  const profileLabel = store.provisioned_profile_code && store.provisioned_profile_version
    ? `${store.provisioned_profile_code} ${store.provisioned_profile_version}`
    : null
  const masterLabel = store.provisioned_master_menu_key && store.provisioned_master_menu_version
    ? `${store.provisioned_master_menu_key} ${store.provisioned_master_menu_version}`
    : null

  return (
    <section className="rounded-[30px] bg-white p-5 shadow-[0_18px_44px_rgba(26,28,25,0.08)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[1.55rem] font-black tracking-[-0.05em]">{store.name}</div>
          <div className="mt-1 text-[0.88rem] font-bold text-[var(--muted)]">
            {store.code || `Store ${store.id}`} · {normalizeStatus(store.status, 'Active')} · {store.role_code || 'store access'}
          </div>
        </div>
        <div className="rounded-[16px] bg-[rgba(26,28,25,0.05)] px-3 py-2 text-[0.78rem] font-black uppercase tracking-[0.08em] text-[var(--muted)]">
          Updated {formatTime(summary.last_updated_at)}
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        <StoreStateBadge store={store} />
        <FeatureChip enabled={features.core} label="Core POS" />
        <FeatureChip enabled={features.printing} label="Printing" />
        <FeatureChip enabled={features.kds} label="KDS" />
        <FeatureChip enabled={features.admin} label="Admin" />
        <FeatureChip enabled={features.analytics} label="Analytics" />
      </div>

      {profileLabel || masterLabel ? (
        <div className="mt-3 flex flex-wrap gap-2 text-[0.78rem] font-bold text-[var(--muted)]">
          {profileLabel ? <span className="rounded-full bg-[rgba(26,28,25,0.05)] px-3 py-1">Profile {profileLabel}</span> : null}
          {masterLabel ? <span className="rounded-full bg-[rgba(26,28,25,0.05)] px-3 py-1">Menu {masterLabel}</span> : null}
        </div>
      ) : null}

      <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Today Orders" value={summary.today_orders} />
        <Metric label="Today Sales" value={formatMoney(summary.today_sales)} />
        <Metric label="Active Orders" value={summary.active_orders} />
        <Metric label="Tables" value={`${summary.occupied_tables}/${summary.open_tables + summary.occupied_tables}`} />
        <Metric label="Failed Prints" value={summary.failed_print_jobs} tone={failedPrintTone} />
        <Metric label="Printing Mode" value={summary.printing_mode || 'N/A'} />
        <Metric label="Last Print Failure" value={formatTime(summary.last_failed_print_at)} tone="muted" />
        <Metric label="KDS Active" value={features.kds ? (summary.kds_active_count ?? 0) : 'Disabled'} tone={features.kds ? 'default' : 'muted'} />
      </div>

      <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <StoreAction store={store} path="/admin/dashboard" label="Admin Dashboard" enabled={features.admin} reason="Admin disabled" />
        <StoreAction store={store} path="/frontdesk" label="Frontdesk" enabled={features.core && operational} reason={operational ? 'Core POS disabled' : 'Store not live'} />
        <StoreAction store={store} path="/admin/settings/printing" label="Printing" enabled={features.printing} reason="Printing disabled" />
        <StoreAction store={store} path="/admin/menu/items" label="Menu" enabled={features.admin} reason="Admin disabled" />
        <StoreAction store={store} path="/admin/reports/sales" label="Reports" enabled={features.analytics} reason="Analytics disabled" />
        <StoreAction store={store} path="/admin/settings/tables" label="Tables" enabled={features.admin} reason="Admin disabled" />
      </div>
    </section>
  )
}

function CreateStorePanel({
  organizations,
  onProvisioned,
}: {
  organizations: OwnerOverviewOrganization[]
  onProvisioned: (result: OwnerStoreProvisioningResult) => void
}) {
  const eligibleOrganizations = useMemo(
    () => organizations.filter((organization) => (organization.role_code ?? '').toUpperCase() === 'OWNER'),
    [organizations],
  )
  const [selectedOrganizationId, setSelectedOrganizationId] = useState('')
  const [catalog, setCatalog] = useState<OwnerStoreProvisioningCatalog | null>(null)
  const [catalogError, setCatalogError] = useState<string | null>(null)
  const [catalogLoading, setCatalogLoading] = useState(false)
  const [storeName, setStoreName] = useState('')
  const [storeCode, setStoreCode] = useState('')
  const [codeTouched, setCodeTouched] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<OwnerStoreProvisioningResult | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    if (!selectedOrganizationId && eligibleOrganizations.length) {
      setSelectedOrganizationId(String(eligibleOrganizations[0].id))
    }
  }, [eligibleOrganizations, selectedOrganizationId])

  useEffect(() => {
    const organizationId = Number(selectedOrganizationId)
    if (!organizationId) {
      setCatalog(null)
      setCatalogError(null)
      return
    }
    let active = true
    setCatalog(null)
    setCatalogError(null)
    setCatalogLoading(true)
    fetchOwnerStoreProvisioningCatalog(organizationId)
      .then((nextCatalog) => {
        if (!active) return
        setCatalog(nextCatalog)
      })
      .catch((error) => {
        if (!active) return
        setCatalogError(getApiUserMessage(error, 'Store provisioning is not available.'))
      })
      .finally(() => {
        if (active) setCatalogLoading(false)
      })
    return () => {
      active = false
    }
  }, [selectedOrganizationId])

  if (!eligibleOrganizations.length) {
    return null
  }

  const selectedOrganization = eligibleOrganizations.find((organization) => String(organization.id) === selectedOrganizationId)
  const canSubmit = Boolean(
    selectedOrganization
    && catalog
    && storeName.trim()
    && normalizeStoreCode(storeCode).length >= 2
    && !catalogLoading
    && !submitting,
  )

  const handleStoreNameChange = (value: string) => {
    setStoreName(value)
    if (!codeTouched) {
      setStoreCode(storeCodeFromName(value))
    }
  }

  const handleSubmit = async () => {
    const organizationId = Number(selectedOrganizationId)
    const normalizedCode = normalizeStoreCode(storeCode)
    if (!organizationId || !catalog || !storeName.trim() || normalizedCode.length < 2) {
      return
    }

    try {
      setSubmitting(true)
      setSubmitError(null)
      setResult(null)
      const nextResult = await provisionOwnerStore(organizationId, {
        store_name: storeName.trim(),
        store_code: normalizedCode,
        profile_code: catalog.profile_code,
        profile_version: catalog.profile_version,
        master_menu_key: catalog.master_menu_key,
        master_menu_version: catalog.master_menu_version,
        master_menu_fingerprint_sha256: catalog.master_menu_fingerprint_sha256,
      })
      setResult(nextResult)
      onProvisioned(nextResult)
    } catch (error) {
      setSubmitError(getApiUserMessage(error, 'Store provisioning failed.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="rounded-[30px] bg-white p-5 shadow-[0_18px_44px_rgba(26,28,25,0.08)]">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="text-[1.45rem] font-black">Create New Store</div>
          <div className="mt-1 text-[0.9rem] font-semibold text-[var(--muted)]">
            Phase B Store provisioning
          </div>
        </div>
        {catalog ? (
          <div className="rounded-[16px] bg-emerald-50 px-3 py-2 text-[0.78rem] font-black uppercase text-emerald-700">
            Enabled
          </div>
        ) : null}
      </div>

      <div className="mt-5 grid gap-3 lg:grid-cols-[minmax(220px,0.8fr)_minmax(220px,1fr)_minmax(180px,0.7fr)]">
        <label className="grid gap-1">
          <span className="text-[0.72rem] font-black uppercase text-[var(--muted)]">Organization</span>
          <select
            value={selectedOrganizationId}
            onChange={(event) => setSelectedOrganizationId(event.target.value)}
            className="min-h-12 rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-3 text-[0.92rem] font-bold outline-none"
          >
            {eligibleOrganizations.map((organization) => (
              <option key={organization.id} value={organization.id}>
                {organization.name}
              </option>
            ))}
          </select>
        </label>

        <label className="grid gap-1">
          <span className="text-[0.72rem] font-black uppercase text-[var(--muted)]">Store Name</span>
          <input
            value={storeName}
            onChange={(event) => handleStoreNameChange(event.target.value)}
            placeholder="PHASE_B_VALIDATION_STORE..."
            className="min-h-12 rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-3 text-[0.92rem] font-bold outline-none"
          />
        </label>

        <label className="grid gap-1">
          <span className="text-[0.72rem] font-black uppercase text-[var(--muted)]">Store Code</span>
          <input
            value={storeCode}
            onChange={(event) => {
              setCodeTouched(true)
              setStoreCode(normalizeStoreCode(event.target.value))
            }}
            placeholder="PHASE_B_TEST"
            className="min-h-12 rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-3 text-[0.92rem] font-bold uppercase outline-none"
          />
        </label>
      </div>

      <div className="mt-4 grid gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
        <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-3">
          <div className="text-[0.72rem] font-black uppercase text-[var(--muted)]">Store Profile</div>
          <div className="mt-1 text-[0.95rem] font-black">
            {catalogLoading ? 'Loading...' : catalog ? `${catalog.profile_code} ${catalog.profile_version}` : 'Unavailable'}
          </div>
        </div>
        <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-3">
          <div className="text-[0.72rem] font-black uppercase text-[var(--muted)]">Master Menu</div>
          <div className="mt-1 text-[0.95rem] font-black">
            {catalogLoading ? 'Loading...' : catalog ? `${catalog.master_menu_key} ${catalog.master_menu_version}` : 'Unavailable'}
          </div>
        </div>
        <button
          type="button"
          disabled={!canSubmit}
          onClick={() => void handleSubmit()}
          className="min-h-12 rounded-[18px] bg-[var(--primary)] px-5 text-[0.92rem] font-black text-white shadow-[0_14px_28px_rgba(97,0,0,0.16)] disabled:cursor-not-allowed disabled:bg-stone-200 disabled:text-stone-500 disabled:shadow-none"
        >
          {submitting ? 'Creating...' : 'Create Store'}
        </button>
      </div>

      {catalogError ? (
        <div className="mt-4 rounded-[18px] bg-amber-50 px-4 py-3 text-[0.9rem] font-bold text-amber-800">
          {catalogError}
        </div>
      ) : null}

      {submitError ? (
        <div className="mt-4 rounded-[18px] bg-red-50 px-4 py-3 text-[0.9rem] font-bold text-red-700">
          {submitError}
        </div>
      ) : null}

      {result ? (
        <div className="mt-4 rounded-[20px] border border-emerald-100 bg-emerald-50 px-4 py-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div className="text-[1rem] font-black text-emerald-800">
                {result.status} · {result.validation_status}
              </div>
              <div className="mt-1 text-[0.82rem] font-bold text-emerald-700">
                {countValue(result, 'category_count')} categories · {countValue(result, 'item_count')} items · {countValue(result, 'option_count')} options · {countValue(result, 'printing_rule_count')} print rules
              </div>
            </div>
            {result.store_id ? (
              <button
                type="button"
                onClick={() => navigateTo(buildStorePath(result.store_id!, '/admin/menu/items'))}
                className="rounded-[16px] bg-emerald-700 px-4 py-3 text-[0.88rem] font-black text-white"
              >
                Open Menu
              </button>
            ) : null}
          </div>
        </div>
      ) : null}
    </section>
  )
}

function OrganizationSection({ organization }: { organization: OwnerOverviewOrganization }) {
  const visibleStores = organization.stores.filter(
    (store) => isVisibleOwnerStore(store),
  )
  const hiddenFixtureCount = organization.stores.length - visibleStores.length

  return (
    <div className="space-y-4">
      <div className="rounded-[24px] bg-[rgba(255,255,255,0.78)] px-5 py-4">
        <div className="text-[0.78rem] font-black uppercase tracking-[0.14em] text-[var(--muted)]">Organization</div>
        <div className="mt-1 text-[1.45rem] font-black tracking-[-0.05em]">{organization.name}</div>
        <div className="mt-1 text-[0.9rem] font-semibold text-[var(--muted)]">
          {organization.code || `Organization ${organization.id}`} · {organization.role_code || 'member'} · {visibleStores.length} store{visibleStores.length === 1 ? '' : 's'}
        </div>
        {hiddenFixtureCount > 0 ? (
          <div className="mt-2 inline-flex rounded-full bg-[rgba(26,28,25,0.05)] px-3 py-1 text-[0.74rem] font-bold text-[var(--muted)]">
            {hiddenFixtureCount} validation fixture{hiddenFixtureCount === 1 ? '' : 's'} hidden
          </div>
        ) : null}
      </div>
      <div className="grid gap-5">
        {visibleStores.map((store) => (
          <StoreCard key={store.id} store={store} />
        ))}
      </div>
    </div>
  )
}

export function OwnerDashboardPage() {
  const { user, isOwner } = useAuth()
  const [overview, setOverview] = useState<OwnerOverviewResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadOverview = () => {
    setLoading(true)
    setError(null)
    fetchOwnerOverview()
      .then((response) => setOverview(response))
      .catch((exception) => setError(getApiUserMessage(exception, 'Unable to load Owner Home.')))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    void Promise.resolve().then(() => loadOverview())
  }, [])

  const totalStores = useMemo(
    () => overview?.organizations.reduce(
      (total, organization) => total + organization.stores.filter(isVisibleOwnerStore).length,
      0,
    ) ?? 0,
    [overview],
  )
  const totalOrganizations = overview?.organizations.length ?? 0
  const title = user?.role_code === 'MANAGER' ? 'Store Selection / 门店选择' : 'Owner Home / 多店总览'

  if (loading && !overview) {
    return (
      <div className="min-h-screen bg-[linear-gradient(180deg,#f6f3ec_0%,#efe9dd_100%)] px-6 py-8 text-[var(--on-surface)]">
        <div className="mx-auto max-w-[1100px] rounded-[30px] bg-white px-7 py-8 shadow-[0_22px_54px_rgba(26,28,25,0.1)]">
          <div className="text-[1rem] font-bold text-[var(--muted)]">Loading Owner Home...</div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-[linear-gradient(180deg,#f6f3ec_0%,#efe9dd_100%)] px-4 py-5 text-[var(--on-surface)] sm:px-6">
      <div className="mx-auto max-w-[1500px] space-y-5">
        <header className="rounded-[32px] bg-white px-6 py-6 shadow-[0_18px_44px_rgba(26,28,25,0.08)]">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <div className="text-[2rem] font-black tracking-[-0.06em]">{title}</div>
              <div className="mt-2 max-w-[760px] text-[0.98rem] font-semibold leading-6 text-[var(--muted)]">
                Review accessible restaurants, check printing and order health, then enter a single-store workspace.
              </div>
              <div className="mt-3 text-[0.88rem] font-bold text-[var(--muted)]">
                {user?.full_name || user?.username || 'Current user'} · {user?.role_code || 'role'} · {totalStores} store{totalStores === 1 ? '' : 's'}
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <div className="rounded-[18px] bg-[rgba(26,28,25,0.05)] px-4 py-3 text-[0.84rem] font-bold text-[var(--muted)]">
                Last refreshed: {formatTime(overview?.generated_at)}
              </div>
              <button
                type="button"
                onClick={loadOverview}
                disabled={loading}
                className="rounded-[18px] bg-[var(--primary)] px-5 py-3 text-[0.92rem] font-black text-white shadow-[0_14px_28px_rgba(97,0,0,0.16)] disabled:cursor-wait disabled:opacity-60"
              >
                {loading ? 'Refreshing...' : 'Refresh'}
              </button>
            </div>
          </div>
        </header>

        {error ? (
          <div className="rounded-[24px] bg-red-50 px-5 py-4 text-[0.95rem] font-bold text-red-700">
            {error}
          </div>
        ) : null}

        {!error && overview && isOwner ? (
          <CreateStorePanel
            organizations={overview.organizations}
            onProvisioned={() => loadOverview()}
          />
        ) : null}

        {!error && overview && totalStores === 0 ? (
          <div className="rounded-[30px] bg-white px-7 py-8 shadow-[0_18px_44px_rgba(26,28,25,0.08)]">
            <div className="text-[1.5rem] font-black tracking-[-0.04em]">
              {totalOrganizations > 0 ? 'No Stores Yet' : 'No Store Access'}
            </div>
            <div className="mt-2 text-[0.98rem] font-semibold text-[var(--muted)]">
              {totalOrganizations > 0
                ? 'Use Create New Store to provision the first non-live test Store for this Organization.'
                : 'This account has no active store membership. Please ask an owner or platform administrator to assign store access.'}
            </div>
          </div>
        ) : null}

        {overview?.organizations.map((organization) => (
          <OrganizationSection key={organization.id} organization={organization} />
        ))}
      </div>
    </div>
  )
}
