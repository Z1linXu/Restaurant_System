import { useEffect, useMemo, useState } from 'react'
import {
  fetchStoreComboConfiguration,
  updateStoreComboConfiguration,
  type StoreComboConfigurationRecord,
} from '../../services/ownerMenuOptionService'

interface ComboConfigurationPanelProps {
  storeId: number
  onSaved?: (message: string) => void
}

type ErrorState = string | null

function cloneConfiguration(configuration: StoreComboConfigurationRecord | null): StoreComboConfigurationRecord | null {
  if (!configuration) return null
  return {
    ...configuration,
    groups: configuration.groups.map((group) => ({
      ...group,
      components: group.components.map((component) => ({ ...component })),
    })),
  }
}

function sameConfiguration(left: StoreComboConfigurationRecord | null, right: StoreComboConfigurationRecord | null) {
  if (!left || !right) return false
  const leftComponents = left.groups.flatMap((group) => group.components)
  const rightEnabled = new Map(
    right.groups.flatMap((group) => group.components)
      .map((component) => [`${component.component_group}:${component.component_code}`, component.enabled]),
  )
  return leftComponents.every((component) => (
    rightEnabled.get(`${component.component_group}:${component.component_code}`) === component.enabled
  ))
}

export function ComboConfigurationPanel({ storeId, onSaved }: ComboConfigurationPanelProps) {
  const [configuration, setConfiguration] = useState<StoreComboConfigurationRecord | null>(null)
  const [draft, setDraft] = useState<StoreComboConfigurationRecord | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<ErrorState>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const nextConfiguration = await fetchStoreComboConfiguration(storeId)
        if (cancelled) return
        setConfiguration(nextConfiguration)
        setDraft(cloneConfiguration(nextConfiguration))
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : 'Failed to load Combo Configuration')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [storeId])

  const dirty = useMemo(() => !sameConfiguration(configuration, draft), [configuration, draft])

  const toggleComponent = (groupCode: string, componentCode: string) => {
    if (!draft) return
    setDraft({
      ...draft,
      groups: draft.groups.map((group) => (
        group.component_group !== groupCode
          ? group
          : {
              ...group,
              components: group.components.map((component) => (
                component.component_code === componentCode
                  ? { ...component, enabled: !component.enabled }
                  : component
              )),
            }
      )),
    })
  }

  const handleSave = async () => {
    if (!draft) return
    try {
      setSaving(true)
      setError(null)
      const saved = await updateStoreComboConfiguration({
        store_id: storeId,
        components: draft.groups.flatMap((group) => group.components.map((component) => ({
          component_group: component.component_group,
          component_code: component.component_code,
          enabled: component.enabled,
        }))),
      })
      setConfiguration(saved)
      setDraft(cloneConfiguration(saved))
      onSaved?.(`Combo Configuration saved at menu revision ${saved.menu_revision}. Pads will refresh the complete menu snapshot.`)
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Failed to save Combo Configuration')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Combo Configuration / 套餐配置</div>
          <div className="mt-1 text-[0.85rem] text-[var(--muted)]">
            Store-level combo contents. Combo price still comes from Pricing Rules.
          </div>
          {configuration ? (
            <div className="mt-1 text-[0.76rem] text-[var(--muted)]">Menu revision {configuration.menu_revision}</div>
          ) : null}
        </div>
        <button
          type="button"
          onClick={() => void handleSave()}
          disabled={loading || saving || !dirty || !draft}
          className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white disabled:opacity-60"
        >
          {saving ? 'Saving...' : 'Save'}
        </button>
      </div>

      {error ? (
        <div className="mt-4 rounded-[16px] border border-[rgba(97,0,0,0.18)] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.82rem] font-semibold text-[var(--primary)]">
          {error}
        </div>
      ) : null}

      {loading || !draft ? (
        <div className="mt-4 rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-3 text-[0.84rem] text-[var(--muted)]">
          Loading Combo Configuration...
        </div>
      ) : (
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          {draft.groups.map((group) => {
            const draftDefaultCode = group.components.find((component) => component.enabled)?.component_code ?? null
            return (
              <div key={group.component_group} className="rounded-[20px] bg-[rgba(26,28,25,0.035)] px-4 py-4">
                <div className="text-[0.78rem] font-black uppercase tracking-[0.14em] text-[var(--muted)]">
                  {group.name_en} / {group.name_zh}
                </div>
                <div className="mt-3 space-y-2">
                  {group.components.map((component) => (
                    <button
                      type="button"
                      role="checkbox"
                      aria-checked={component.enabled}
                      key={`${component.component_group}:${component.component_code}`}
                      onClick={() => toggleComponent(group.component_group, component.component_code)}
                      className="flex min-h-12 w-full items-center gap-3 rounded-[16px] bg-white px-3 py-2 text-left shadow-sm"
                    >
                      <span
                        className={`inline-flex h-7 w-7 items-center justify-center rounded-[10px] border ${
                          component.enabled
                            ? 'border-[var(--primary)] bg-[var(--primary)] text-white'
                            : 'border-[rgba(97,0,0,0.18)] bg-white text-transparent'
                        }`}
                      >
                        ✓
                      </span>
                      <span>
                        <span className="block text-[0.92rem] font-semibold text-[var(--on-surface)]">
                          {component.name_zh} / {component.name_en}
                        </span>
                        {component.component_code === draftDefaultCode ? (
                          <span className="mt-0.5 block text-[0.72rem] font-semibold text-[var(--muted)]">
                            Default when Combo is enabled
                          </span>
                        ) : null}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}
