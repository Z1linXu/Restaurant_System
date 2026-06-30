import { useEffect, useMemo, useState } from 'react'
import {
  createStoreFromTemplate,
  fetchPlatformOverview,
  savePlatformEntity,
  type PlatformAdminOverview,
} from '../../services/platformAdminService'
import { isFeatureEnabled } from '../feature-flags/featureConfig'
import { FrontdeskTopNav } from '../frontdesk/components/FrontdeskTopNav'
import { useCurrentStore } from '../store/StoreContext'

type SectionKey =
  | 'organizations'
  | 'templates'
  | 'stores'
  | 'stations'
  | 'dining_tables'
  | 'menu_categories'
  | 'menu_items'
  | 'menu_item_options'
  | 'kds_display_configs'
  | 'users'
  | 'roles'

interface EditorState {
  section: SectionKey
  path: string
  title: string
  value: string
  id?: number
}

const sectionMeta: {
  key: SectionKey
  title: string
  description: string
  path: string
}[] = [
  { key: 'organizations', title: 'Organizations', description: 'Tenant layer and lifecycle state.', path: 'organizations' },
  { key: 'templates', title: 'Templates', description: 'Reusable onboarding presets for future restaurants.', path: 'templates' },
  { key: 'stores', title: 'Stores', description: 'Store records linked to organizations.', path: 'stores' },
  { key: 'stations', title: 'Stations', description: 'Kitchen and production station setup.', path: 'stations' },
  { key: 'dining_tables', title: 'Dining Tables', description: 'Frontdesk table layout and split-table rules.', path: 'dining-tables' },
  { key: 'menu_categories', title: 'Menu Categories', description: 'Database-driven menu structure.', path: 'menu/categories' },
  { key: 'menu_items', title: 'Menu Items', description: 'Sellable items tied to store/category/station.', path: 'menu/items' },
  { key: 'menu_item_options', title: 'Menu Item Options', description: 'Options, modifiers, addons, and removes.', path: 'menu/item-options' },
  { key: 'kds_display_configs', title: 'KDS Display Config', description: 'Per-screen density and layout config.', path: 'kds-configs' },
  { key: 'users', title: 'Users', description: 'Store users and operator records.', path: 'users' },
  { key: 'roles', title: 'Roles', description: 'Role setup preserved from the original restaurant.', path: 'roles' },
] as const

function navigateTo(path: string) {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

function getSummary(record: Record<string, unknown>) {
  return String(
    record.table_name ??
      record.table_code ??
      record.name ??
      record.code ??
      record.username ??
      record.screen_code ??
      record.id ??
      'Untitled',
  )
}

function makeDraft(section: SectionKey, overview: PlatformAdminOverview): Record<string, unknown> {
  const storeId = Number(overview.stores[0]?.id ?? 1)
  const organizationId = Number(overview.organizations[0]?.id ?? 1)

  switch (section) {
    case 'organizations':
      return { name: '', code: '', status: 'active' }
    case 'templates':
      return {
        organization_id: organizationId,
        name: '',
        code: '',
        description: '',
        source_store_id: storeId,
        default_station_setup_json: '[]',
        default_kds_display_rules_json: '[]',
        default_menu_category_structure_json: '[]',
        default_dining_table_layout_rules_json: '[]',
        default_role_setup_json: '[]',
        is_active: true,
      }
    case 'stores':
      return { organization_id: organizationId, name: '', code: '', status: 'active', enable_bar_kitchen_tasks: false }
    case 'stations':
      return { store_id: storeId, name: '', code: '', sort_order: 1, is_active: true }
    case 'dining_tables':
      return {
        store_id: storeId,
        table_code: '',
        table_name: '',
        area_name: '',
        table_config: 'single_only',
        capacity: 4,
        supports_split: false,
        sort_order: 1,
        is_active: true,
      }
    case 'menu_categories':
      return { store_id: storeId, code: '', name_zh: '', name_en: '', sort_order: 1, is_active: true }
    case 'menu_items':
      return {
        store_id: storeId,
        category_id: Number(overview.menu_categories[0]?.id ?? 1),
        station_id: Number(overview.stations[0]?.id ?? 1),
        name_zh: '',
        name_en: '',
        sku: '',
        item_type: 'menu_item',
        base_price: 0,
        is_active: true,
        is_sold_out: false,
      }
    case 'menu_item_options':
      return {
        menu_item_id: Number(overview.menu_items[0]?.id ?? 1),
        option_type: 'addon',
        name_zh: '',
        name_en: '',
        price_delta: 0,
        is_active: true,
      }
    case 'kds_display_configs':
      return {
        store_id: storeId,
        screen_code: '',
        header_layout: 'top_bar',
        density_mode: 'compact',
        card_size_mode: 'standard',
        config_json: '{}',
      }
    case 'users':
      return {
        store_id: storeId,
        role_id: Number(overview.roles[0]?.id ?? 1),
        username: '',
        full_name: '',
        phone: '',
        status: 'active',
      }
    case 'roles':
      return { name: '', code: '' }
  }
}

export function PlatformAdminPage() {
  const { storeId } = useCurrentStore()
  const [overview, setOverview] = useState<PlatformAdminOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [saving, setSaving] = useState(false)
  const [templateForm, setTemplateForm] = useState({
    organization_id: 1,
    template_id: 1,
    name: '',
    code: '',
  })

  const loadOverview = async () => {
    setLoading(true)
    setError(null)
    try {
      const nextOverview = await fetchPlatformOverview(storeId)
      setOverview(nextOverview)
      setTemplateForm((current) => ({
        ...current,
        organization_id: Number(nextOverview.organizations[0]?.id ?? 1),
        template_id: Number(nextOverview.templates[0]?.id ?? 1),
      }))
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load platform admin overview')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadOverview()
  }, [storeId])

  const sectionData = useMemo(() => {
    if (!overview) {
      return []
    }

    return sectionMeta.map((section) => ({
      ...section,
      items: overview[section.key],
    }))
  }, [overview])

  const openEditor = (section: SectionKey, path: string, title: string, record?: Record<string, unknown>) => {
    if (!overview) {
      return
    }
    const nextRecord = record ? { ...record } : makeDraft(section, overview)
    setEditor({
      section,
      path,
      title,
      id: typeof nextRecord.id === 'number' ? nextRecord.id : undefined,
      value: JSON.stringify(nextRecord, null, 2),
    })
  }

  const handleSave = async () => {
    if (!editor) {
      return
    }

    try {
      setSaving(true)
      const payload = JSON.parse(editor.value) as Record<string, unknown>
      await savePlatformEntity(editor.path, payload, editor.id)
      await loadOverview()
      setEditor(null)
    } catch (saveError) {
      window.alert(saveError instanceof Error ? saveError.message : 'Failed to save entity')
    } finally {
      setSaving(false)
    }
  }

  const handleCreateStoreFromTemplate = async () => {
    try {
      setSaving(true)
      await createStoreFromTemplate({
        organization_id: templateForm.organization_id,
        template_id: templateForm.template_id,
        name: templateForm.name,
        code: templateForm.code,
        status: 'active',
      })
      setTemplateForm((current) => ({
        ...current,
        name: '',
        code: '',
      }))
      await loadOverview()
    } catch (createError) {
      window.alert(createError instanceof Error ? createError.message : 'Failed to create store from template')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="min-h-screen bg-[var(--surface)] px-4 py-4 text-[var(--on-surface)]">
      <div className="mx-auto max-w-[1600px] space-y-4">
        <FrontdeskTopNav activeItem="dashboard" />

        <div className="rounded-[26px] bg-[rgba(255,255,255,0.84)] px-5 py-5 shadow-[0_16px_32px_rgba(26,28,25,0.05)]">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div className="text-[1.9rem] font-black tracking-[-0.05em] text-[var(--primary)]">Platform Admin</div>
              <div className="mt-1 text-[0.95rem] text-[var(--muted)]">
                Organization-first configuration layer for the current restaurant and future template-based onboarding.
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => navigateTo('/frontdesk')}
                className="rounded-[14px] border border-[rgba(97,0,0,0.12)] px-3 py-2 text-[0.9rem] font-semibold text-[var(--primary)]"
              >
                Back to Frontdesk
              </button>
              <button
                type="button"
                onClick={() => void loadOverview()}
                className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.9rem] font-semibold text-white"
              >
                Refresh
              </button>
            </div>
          </div>
        </div>

        <div className="rounded-[24px] bg-[rgba(255,255,255,0.82)] p-4 shadow-[0_14px_28px_rgba(26,28,25,0.05)]">
          <div className="text-[1.05rem] font-bold text-[var(--on-surface)]">Create Store From Template</div>
          <div className="mt-3 grid gap-3 md:grid-cols-4">
            <input
              value={templateForm.name}
              onChange={(event) => setTemplateForm((current) => ({ ...current, name: event.target.value }))}
              placeholder="Store name"
              className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2.5 text-[0.92rem]"
            />
            <input
              value={templateForm.code}
              onChange={(event) => setTemplateForm((current) => ({ ...current, code: event.target.value }))}
              placeholder="Store code"
              className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2.5 text-[0.92rem]"
            />
            <select
              value={templateForm.organization_id}
              onChange={(event) => setTemplateForm((current) => ({ ...current, organization_id: Number(event.target.value) }))}
              className="rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2.5 text-[0.92rem]"
            >
              {(overview?.organizations ?? []).map((organization) => (
                <option key={String(organization.id)} value={String(organization.id)}>
                  {String(organization.name)}
                </option>
              ))}
            </select>
            <div className="flex gap-3">
              <select
                value={templateForm.template_id}
                onChange={(event) => setTemplateForm((current) => ({ ...current, template_id: Number(event.target.value) }))}
                className="min-w-0 flex-1 rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2.5 text-[0.92rem]"
              >
                {(overview?.templates ?? []).map((template) => (
                  <option key={String(template.id)} value={String(template.id)}>
                    {String(template.name)}
                  </option>
                ))}
              </select>
              <button
                type="button"
                disabled={saving || !templateForm.name.trim() || !templateForm.code.trim()}
                onClick={() => void handleCreateStoreFromTemplate()}
                className="rounded-[14px] bg-[var(--primary)] px-4 py-2.5 text-[0.92rem] font-semibold text-white disabled:opacity-50"
              >
                Create
              </button>
            </div>
          </div>
        </div>

        {error ? (
          <div className="rounded-[20px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.95rem] font-semibold text-[var(--primary)]">
            {error}
          </div>
        ) : null}

        {loading || !overview ? (
          <div className="rounded-[24px] bg-[rgba(255,255,255,0.82)] px-5 py-5 text-[0.95rem] text-[var(--muted)] shadow-[0_14px_28px_rgba(26,28,25,0.05)]">
            Loading platform configuration...
          </div>
        ) : (
          <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_420px]">
            <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">
              {sectionData.map((section) => (
                <div key={section.key} className="rounded-[24px] bg-[rgba(255,255,255,0.82)] p-4 shadow-[0_14px_28px_rgba(26,28,25,0.05)]">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="text-[1.05rem] font-bold text-[var(--on-surface)]">{section.title}</div>
                      <div className="mt-1 text-[0.84rem] leading-5 text-[var(--muted)]">{section.description}</div>
                    </div>
                    <button
                      type="button"
                      onClick={() => openEditor(section.key, section.path, `New ${section.title.slice(0, -1)}`)}
                      className="rounded-[12px] bg-[rgba(97,0,0,0.08)] px-2.5 py-1.5 text-[0.82rem] font-semibold text-[var(--primary)]"
                    >
                      New
                    </button>
                  </div>

                  <div className="mt-3 text-[0.82rem] font-semibold uppercase tracking-[0.18em] text-[var(--muted)]">
                    {section.items.length} records
                  </div>

                  <div className="mt-3 space-y-2">
                    {section.items.slice(0, 8).map((item) => (
                      <button
                        key={`${section.key}-${String(item.id)}`}
                        type="button"
                        onClick={() => openEditor(section.key, section.path, section.title, item)}
                        className="flex w-full items-start justify-between gap-3 rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-2.5 text-left transition hover:bg-[rgba(97,0,0,0.06)]"
                      >
                        <div className="min-w-0">
                          <div className="truncate text-[0.92rem] font-semibold text-[var(--on-surface)]">{getSummary(item)}</div>
                          <div className="mt-0.5 text-[0.76rem] text-[var(--muted)]">ID {String(item.id ?? 'new')}</div>
                        </div>
                        <span className="text-[0.8rem] text-[var(--muted)]">Edit</span>
                      </button>
                    ))}
                    {section.items.length > 8 ? (
                      <div className="px-1 pt-1 text-[0.8rem] text-[var(--muted)]">Showing first 8. Use Refresh after edits to inspect the latest state.</div>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>

            <div className="rounded-[24px] bg-[rgba(255,255,255,0.84)] p-4 shadow-[0_14px_28px_rgba(26,28,25,0.05)]">
              <div className="text-[1.08rem] font-bold text-[var(--on-surface)]">
                {editor ? editor.title : 'JSON Editor'}
              </div>
              <div className="mt-1 text-[0.84rem] text-[var(--muted)]">
                Minimal platform management editor. Existing restaurant data stays intact; edits apply directly to the live configuration tables.
              </div>

              {!isFeatureEnabled('DEVELOPER_TOOLS') ? (
                <div className="mt-5 rounded-[18px] bg-[rgba(97,0,0,0.06)] px-4 py-5 text-[0.9rem] font-medium text-[var(--primary)]">
                  JSON editing is part of the DEVELOPER_TOOLS package and is disabled for this configuration.
                </div>
              ) : editor ? (
                <div className="mt-4 space-y-3">
                  <textarea
                    value={editor.value}
                    onChange={(event) => setEditor((current) => (current ? { ...current, value: event.target.value } : current))}
                    className="h-[720px] w-full rounded-[18px] border border-[rgba(26,28,25,0.08)] bg-[rgba(244,244,239,0.72)] px-3 py-3 font-mono text-[0.82rem] leading-6 text-[var(--on-surface)]"
                  />
                  <div className="flex gap-3">
                    <button
                      type="button"
                      disabled={saving}
                      onClick={() => void handleSave()}
                      className="rounded-[14px] bg-[var(--primary)] px-4 py-2.5 text-[0.92rem] font-semibold text-white disabled:opacity-50"
                    >
                      Save
                    </button>
                    <button
                      type="button"
                      onClick={() => setEditor(null)}
                      className="rounded-[14px] border border-[rgba(26,28,25,0.12)] px-4 py-2.5 text-[0.92rem] font-semibold text-[var(--on-surface)]"
                    >
                      Close
                    </button>
                  </div>
                </div>
              ) : (
                <div className="mt-5 rounded-[18px] bg-[rgba(244,244,239,0.72)] px-4 py-5 text-[0.9rem] text-[var(--muted)]">
                  Pick any entity card on the left to inspect or edit its live JSON payload.
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
