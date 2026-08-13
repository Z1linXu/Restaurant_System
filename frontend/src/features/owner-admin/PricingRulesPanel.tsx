import { useEffect, useState } from 'react'
import {
  fetchStorePricingPolicy,
  previewStorePricingPolicy,
  updateStorePricingPolicy,
  type StorePricingPolicyPreviewRecord,
  type StorePricingPolicyRecord,
} from '../../services/ownerMenuOptionService'

interface PricingRulesPanelProps {
  storeId: number
  onSaved?: (message: string) => void
}

interface PricingPolicyDraft {
  size_small_delta: string
  size_regular_delta: string
  size_large_delta: string
  combo_delta: string
}

const MONEY_PATTERN = /^-?\d{1,6}(\.\d{0,2})?$/

function toDraft(policy: StorePricingPolicyRecord): PricingPolicyDraft {
  return {
    size_small_delta: Number(policy.size_small_delta ?? 0).toFixed(2),
    size_regular_delta: Number(policy.size_regular_delta ?? 0).toFixed(2),
    size_large_delta: Number(policy.size_large_delta ?? 0).toFixed(2),
    combo_delta: Number(policy.combo_delta ?? 0).toFixed(2),
  }
}

function validateMoney(value: string, label: string) {
  if (!MONEY_PATTERN.test(value.trim())) {
    throw new Error(`${label} must be a decimal amount with at most two cents digits.`)
  }
  return Number(value).toFixed(2)
}

function formatCurrency(value: number | string) {
  return new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency: 'CAD',
    minimumFractionDigits: 2,
  }).format(Number(value ?? 0))
}

export function PricingRulesPanel({ storeId, onSaved }: PricingRulesPanelProps) {
  const [policy, setPolicy] = useState<StorePricingPolicyRecord | null>(null)
  const [draft, setDraft] = useState<PricingPolicyDraft | null>(null)
  const [preview, setPreview] = useState<StorePricingPolicyPreviewRecord | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      setPreview(null)
      try {
        const nextPolicy = await fetchStorePricingPolicy(storeId)
        if (cancelled) return
        setPolicy(nextPolicy)
        setDraft(toDraft(nextPolicy))
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : 'Failed to load Pricing Rules')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [storeId])

  const buildPayload = () => {
    if (!draft) {
      throw new Error('Pricing Rules are not loaded yet.')
    }
    return {
      store_id: storeId,
      size_small_delta: validateMoney(draft.size_small_delta, 'Small delta'),
      size_regular_delta: validateMoney(draft.size_regular_delta, 'Regular delta'),
      size_large_delta: validateMoney(draft.size_large_delta, 'Large delta'),
      combo_delta: validateMoney(draft.combo_delta, 'Combo delta'),
    }
  }

  const handlePreview = async () => {
    try {
      setSaving(true)
      setError(null)
      setPreview(await previewStorePricingPolicy(buildPayload()))
    } catch (previewError) {
      setError(previewError instanceof Error ? previewError.message : 'Failed to preview Pricing Rules')
    } finally {
      setSaving(false)
    }
  }

  const handleSave = async () => {
    try {
      setSaving(true)
      setError(null)
      const saved = await updateStorePricingPolicy(buildPayload())
      setPolicy(saved)
      setDraft(toDraft(saved))
      setPreview(null)
      onSaved?.(`Pricing Rules saved at policy revision ${saved.policy_revision}. Future drafts use the new Size/Combo deltas.`)
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Failed to save Pricing Rules')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[1.1rem] font-bold text-[var(--on-surface)]">Pricing Rules / 价格规则</div>
          <div className="mt-1 text-[0.85rem] text-[var(--muted)]">
            Store-level canonical deltas for Small / Regular / Large and Combo. Item options only choose support.
          </div>
          {policy ? (
            <div className="mt-1 text-[0.76rem] text-[var(--muted)]">Policy revision {policy.policy_revision}</div>
          ) : null}
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void handlePreview()}
            disabled={loading || saving || !draft}
            className="rounded-[14px] bg-[rgba(26,28,25,0.06)] px-3 py-2 text-[0.82rem] font-semibold disabled:opacity-60"
          >
            Preview Impact
          </button>
          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={loading || saving || !draft || !preview}
            title={!preview ? 'Preview impact before saving.' : undefined}
            className="rounded-[14px] bg-[var(--primary)] px-3 py-2 text-[0.82rem] font-semibold text-white disabled:opacity-60"
          >
            {saving ? 'Saving...' : 'Confirm Save'}
          </button>
        </div>
      </div>

      {error ? (
        <div className="mt-4 rounded-[16px] border border-[rgba(97,0,0,0.18)] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.82rem] font-semibold text-[var(--primary)]">
          {error}
        </div>
      ) : null}

      {loading || !draft ? (
        <div className="mt-4 rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-3 text-[0.84rem] text-[var(--muted)]">
          Loading Pricing Rules...
        </div>
      ) : (
        <div className="mt-4 grid gap-3 md:grid-cols-4">
          {[
            ['小碗 / Small', 'size_small_delta'],
            ['中碗 / Regular', 'size_regular_delta'],
            ['大碗 / Large', 'size_large_delta'],
            ['套餐 / Combo', 'combo_delta'],
          ].map(([label, key]) => (
            <label key={key} className="block rounded-[18px] bg-[rgba(26,28,25,0.035)] px-3 py-3">
              <div className="text-[0.72rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">{label}</div>
              <input
                value={draft[key as keyof PricingPolicyDraft]}
                onChange={(event) => {
                  setDraft({ ...draft, [key]: event.target.value })
                  setPreview(null)
                }}
                inputMode="decimal"
                className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] bg-white px-3 py-2.5 text-[0.9rem] font-semibold outline-none"
              />
            </label>
          ))}
        </div>
      )}

      {preview ? (
        <div className="mt-4 rounded-[18px] border border-[rgba(26,28,25,0.06)] bg-white/70 p-3">
          <div className="text-[0.82rem] font-black uppercase tracking-[0.12em] text-[var(--muted)]">Impact preview</div>
          <div className="mt-2 space-y-3">
            {preview.impact_groups.length ? preview.impact_groups.map((group) => (
              <div key={group.policy_key} className="rounded-[14px] bg-[rgba(26,28,25,0.035)] px-3 py-2">
                <div className="text-[0.86rem] font-semibold text-[var(--on-surface)]">
                  {group.policy_key}: {formatCurrency(group.old_delta)} → {formatCurrency(group.new_delta)}
                </div>
                <div className="mt-1 text-[0.76rem] text-[var(--muted)]">
                  {group.affected_item_count} active item(s) currently use this rule.
                </div>
                {group.sample_items.length ? (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {group.sample_items.slice(0, 8).map((item) => (
                      <span key={`${group.policy_key}-${item.item_id}`} className="rounded-full bg-white px-2 py-1 text-[0.72rem] text-[var(--muted)]">
                        {item.name_zh || item.name_en || item.sku || `Item ${item.item_id}`}: {formatCurrency(item.old_price)} → {formatCurrency(item.new_price)}
                      </span>
                    ))}
                  </div>
                ) : null}
              </div>
            )) : (
              <div className="rounded-[14px] bg-[rgba(64,124,73,0.08)] px-3 py-2 text-[0.82rem] font-semibold text-[rgb(48,96,56)]">
                No active future-order price changes for the current draft.
              </div>
            )}
          </div>
        </div>
      ) : null}
    </section>
  )
}
