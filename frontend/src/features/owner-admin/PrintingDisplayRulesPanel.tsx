import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  fetchPrintingDisplayRules,
  previewPrintingDisplayRules,
  publishPrintingDisplayRuleDraft,
  savePrintingDisplayRuleDraft,
  validatePrintingDisplayRules,
  type PrintingDisplayRuleContent,
  type PrintingDisplayRulePreviewResponse,
  type PrintingDisplayRuleRevision,
  type PrintingDisplayRuleSettings,
  type PrintingDisplayRuleValidationResponse,
} from '../../services/printingAdminService'

type ToastKind = 'success' | 'error'
type ToastHandler = (message: string, kind?: ToastKind) => void

type OutputMap = Record<string, string>

interface ItemAliasRule {
  item_sku: string
  outputs?: OutputMap
}

interface DictionaryObjectEntry {
  semantic_code?: string
  match_codes?: string[]
  match_zh?: string[]
  match_en?: string[]
  outputs?: OutputMap
}

interface ConditionalOverride {
  condition?: {
    item_sku?: string | string[]
    dictionary?: string
    semantic_code?: string
  }
  omit?: boolean
}

type RuleDictionaries = Record<string, Array<DictionaryObjectEntry | [string, string]>>

interface StructuredRuleContent extends PrintingDisplayRuleContent {
  schema_version?: string
  outputs?: string[]
  item_aliases?: ItemAliasRule[]
  dictionaries?: RuleDictionaries
  conditional_overrides?: ConditionalOverride[]
  formatting?: Record<string, string>
}

const STRUCTURED_DICTIONARIES = [
  { key: 'SIZE', label: 'Size / 大小', receiptMode: 'ZH_EN' },
  { key: 'NOODLE_TYPE', label: 'Noodle / 面型', receiptMode: 'NONE' },
  { key: 'SPICINESS', label: 'Spicy / 辣度', receiptMode: 'SINGLE' },
] as const

const MODIFIER_DICTIONARIES = [
  { key: 'MODIFIER_ADD', label: 'Modifier Add / 加料' },
  { key: 'MODIFIER_REMOVE', label: 'Modifier Remove / 去除' },
] as const

const OUTPUT_TYPES = ['GRAB', 'FRONTDESK_RECEIPT', 'HOT_KITCHEN'] as const
const CONDITION_DICTIONARIES = ['SIZE', 'NOODLE_TYPE', 'SPICINESS', 'SOUP_BASE'] as const

const EMPTY_CONTENT: StructuredRuleContent = {
  schema_version: 'PRINTING_DISPLAY_RULES_V1',
  outputs: ['GRAB', 'FRONTDESK_RECEIPT', 'HOT_KITCHEN'],
  item_aliases: [],
  dictionaries: {},
  conditional_overrides: [],
  formatting: {},
}

function cloneContent(content?: PrintingDisplayRuleContent | null): StructuredRuleContent {
  if (!content) {
    return structuredCloneSafe(EMPTY_CONTENT)
  }
  const cloned = structuredCloneSafe(content) as StructuredRuleContent
  cloned.schema_version = cloned.schema_version ?? 'PRINTING_DISPLAY_RULES_V1'
  cloned.outputs = Array.isArray(cloned.outputs) ? cloned.outputs : ['GRAB', 'FRONTDESK_RECEIPT', 'HOT_KITCHEN']
  cloned.item_aliases = Array.isArray(cloned.item_aliases) ? cloned.item_aliases : []
  cloned.dictionaries = isRecord(cloned.dictionaries) ? cloned.dictionaries as RuleDictionaries : {}
  cloned.conditional_overrides = Array.isArray(cloned.conditional_overrides) ? cloned.conditional_overrides : []
  cloned.formatting = isRecord(cloned.formatting) ? cloned.formatting as Record<string, string> : {}
  return cloned
}

function structuredCloneSafe<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function revisionLabel(revision?: PrintingDisplayRuleRevision | null) {
  if (!revision) {
    return 'none'
  }
  return `v${revision.revision_number} · ${revision.status}`
}

function shortFingerprint(value?: string | null) {
  return value ? `${value.slice(0, 12)}…${value.slice(-8)}` : '—'
}

function splitCsv(value: string) {
  return value
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean)
}

function itemSkuCsv(value?: string | string[]) {
  if (Array.isArray(value)) {
    return value.join(', ')
  }
  return value ?? ''
}

function matchSummary(entry: DictionaryObjectEntry) {
  return [
    ...(entry.match_codes ?? []),
    ...(entry.match_zh ?? []),
    ...(entry.match_en ?? []),
  ].filter(Boolean).join(' / ') || '—'
}

function updateContent(content: StructuredRuleContent, updater: (draft: StructuredRuleContent) => void) {
  const next = cloneContent(content)
  updater(next)
  return next
}

function dictionaryEntries(content: StructuredRuleContent, key: string) {
  const entries = content.dictionaries?.[key]
  return Array.isArray(entries) ? entries : []
}

function objectDictionaryEntries(content: StructuredRuleContent, key: string) {
  return dictionaryEntries(content, key).filter(isRecord) as DictionaryObjectEntry[]
}

function modifierDictionaryEntries(content: StructuredRuleContent, key: string) {
  return dictionaryEntries(content, key).filter((entry): entry is [string, string] => Array.isArray(entry) && entry.length >= 2)
}

function activeContentFrom(settings: PrintingDisplayRuleSettings | null) {
  return cloneContent(settings?.draft_revision?.content ?? settings?.active_revision?.content ?? null)
}

function validationTone(validation: PrintingDisplayRuleValidationResponse | null) {
  if (!validation) {
    return 'bg-[rgba(26,28,25,0.04)] text-[var(--muted)]'
  }
  return validation.valid
    ? 'bg-[rgba(64,124,73,0.1)] text-[rgb(48,96,56)]'
    : 'bg-[rgba(97,0,0,0.08)] text-[var(--primary)]'
}

interface PrintingDisplayRulesPanelProps {
  storeId: number
  onToast?: ToastHandler
}

export function PrintingDisplayRulesPanel({ storeId, onToast }: PrintingDisplayRulesPanelProps) {
  const [settings, setSettings] = useState<PrintingDisplayRuleSettings | null>(null)
  const [content, setContent] = useState<StructuredRuleContent>(() => cloneContent(null))
  const [summary, setSummary] = useState('Owner edited printing display rules')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [validation, setValidation] = useState<PrintingDisplayRuleValidationResponse | null>(null)
  const [preview, setPreview] = useState<PrintingDisplayRulePreviewResponse | null>(null)
  const [previewInput, setPreviewInput] = useState({
    item_sku: 'traditional_beef_noodle',
    item_name_zh: '传统牛肉面',
    item_name_en: 'Traditional Beef Noodle',
    size_zh: '大碗',
    noodle_type_zh: '三细',
    spiciness_zh: '少辣',
    modifier_add_codes: 'tea_egg',
    modifier_remove_codes: 'cilantro',
    combo: true,
  })

  const loadRules = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const next = await fetchPrintingDisplayRules(storeId)
      setSettings(next)
      setContent(activeContentFrom(next))
      setValidation(null)
      setPreview(null)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Printing display rules failed to load')
    } finally {
      setLoading(false)
    }
  }, [storeId])

  useEffect(() => {
    void loadRules()
  }, [loadRules])

  const draftRevisionId = settings?.draft_revision?.id ?? null
  const activeRevision = settings?.active_revision ?? null

  const handleValidate = async () => {
    try {
      const result = await validatePrintingDisplayRules(storeId, content)
      setValidation(result)
      onToast?.(result.valid ? 'Printing display rules validated.' : 'Printing display rules have validation errors.', result.valid ? 'success' : 'error')
    } catch (validateError) {
      onToast?.(validateError instanceof Error ? validateError.message : 'Validation failed', 'error')
    }
  }

  const handleSaveDraft = async () => {
    try {
      setSaving(true)
      const saved = await savePrintingDisplayRuleDraft(storeId, content, summary)
      onToast?.(
        saved.lifecycle_result === 'ALREADY_ACTIVE'
          ? `Printing display rules are already active as v${saved.revision_number}.`
          : `Printing display rule draft saved: v${saved.revision_number}.`,
        'success',
      )
      const next = await fetchPrintingDisplayRules(storeId)
      setSettings(next)
      setContent(activeContentFrom(next))
      setValidation(null)
    } catch (saveError) {
      onToast?.(saveError instanceof Error ? saveError.message : 'Draft save failed', 'error')
    } finally {
      setSaving(false)
    }
  }

  const handlePublish = async () => {
    if (!draftRevisionId) {
      onToast?.('No draft revision to publish.', 'error')
      return
    }
    try {
      setPublishing(true)
      const result = await publishPrintingDisplayRuleDraft(storeId, draftRevisionId)
      onToast?.(`Printing display rules published as v${result.revision_number}.`, 'success')
      const next = await fetchPrintingDisplayRules(storeId)
      setSettings(next)
      setContent(activeContentFrom(next))
      setValidation(null)
    } catch (publishError) {
      onToast?.(publishError instanceof Error ? publishError.message : 'Publish failed', 'error')
    } finally {
      setPublishing(false)
    }
  }

  const handlePreview = async () => {
    try {
      const result = await previewPrintingDisplayRules({
        store_id: storeId,
        content,
        item_sku: previewInput.item_sku,
        item_name_zh: previewInput.item_name_zh,
        item_name_en: previewInput.item_name_en,
        size_zh: previewInput.size_zh,
        noodle_type_zh: previewInput.noodle_type_zh,
        spiciness_zh: previewInput.spiciness_zh,
        modifier_add_codes: splitCsv(previewInput.modifier_add_codes),
        modifier_remove_codes: splitCsv(previewInput.modifier_remove_codes),
        combo: previewInput.combo,
      })
      setPreview(result)
    } catch (previewError) {
      onToast?.(previewError instanceof Error ? previewError.message : 'Preview failed', 'error')
    }
  }

  const updateObjectDictionaryOutput = (dictionaryKey: string, index: number, outputKey: string, value: string) => {
    setContent((current) => updateContent(current, (draft) => {
      const entries = objectDictionaryEntries(draft, dictionaryKey)
      entries[index] = {
        ...entries[index],
        outputs: {
          ...(entries[index]?.outputs ?? {}),
          [outputKey]: value,
        },
      }
      draft.dictionaries = {
        ...(draft.dictionaries ?? {}),
        [dictionaryKey]: entries,
      }
    }))
  }

  const updateModifierPair = (dictionaryKey: string, index: number, pair: [string, string]) => {
    setContent((current) => updateContent(current, (draft) => {
      const entries = modifierDictionaryEntries(draft, dictionaryKey)
      entries[index] = pair
      draft.dictionaries = {
        ...(draft.dictionaries ?? {}),
        [dictionaryKey]: entries,
      }
    }))
  }

  const addModifierPair = (dictionaryKey: string) => {
    setContent((current) => updateContent(current, (draft) => {
      const entries = modifierDictionaryEntries(draft, dictionaryKey)
      draft.dictionaries = {
        ...(draft.dictionaries ?? {}),
        [dictionaryKey]: [...entries, ['new_modifier_code', '显示文本']],
      }
    }))
  }

  const removeModifierPair = (dictionaryKey: string, index: number) => {
    setContent((current) => updateContent(current, (draft) => {
      const entries = modifierDictionaryEntries(draft, dictionaryKey)
      draft.dictionaries = {
        ...(draft.dictionaries ?? {}),
        [dictionaryKey]: entries.filter((_, entryIndex) => entryIndex !== index),
      }
    }))
  }

  const updateConditional = (index: number, updater: (entry: ConditionalOverride) => ConditionalOverride) => {
    setContent((current) => updateContent(current, (draft) => {
      const entries = [...(draft.conditional_overrides ?? [])]
      entries[index] = updater(entries[index] ?? { condition: {}, omit: true })
      draft.conditional_overrides = entries
    }))
  }

  const addConditional = () => {
    setContent((current) => updateContent(current, (draft) => {
      draft.conditional_overrides = [
        ...(draft.conditional_overrides ?? []),
        {
          condition: {
            item_sku: '',
            dictionary: 'NOODLE_TYPE',
            semantic_code: '',
          },
          omit: true,
        },
      ]
    }))
  }

  const removeConditional = (index: number) => {
    setContent((current) => updateContent(current, (draft) => {
      draft.conditional_overrides = (draft.conditional_overrides ?? []).filter((_, entryIndex) => entryIndex !== index)
    }))
  }

  return (
    <section className="rounded-[26px] bg-[rgba(255,255,255,0.84)] p-5 shadow-[0_18px_34px_rgba(26,28,25,0.05)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[1.15rem] font-bold text-[var(--on-surface)]">Printing Display Rules</div>
          <div className="mt-1 max-w-3xl text-[0.86rem] leading-5 text-[var(--muted)]">
            Store-scoped aliases, dictionaries, constrained conditional display rules, preview, and revision publishing.
            These rules affect future print rendering only; historical print snapshots remain frozen.
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void loadRules()}
            className="rounded-full bg-[rgba(26,28,25,0.06)] px-4 py-2 text-[0.84rem] font-semibold text-[var(--on-surface)]"
          >
            {loading ? 'Loading...' : 'Reload'}
          </button>
          <button
            type="button"
            onClick={() => void handleValidate()}
            className="rounded-full bg-[rgba(38,86,160,0.12)] px-4 py-2 text-[0.84rem] font-semibold text-[rgb(38,86,160)]"
          >
            Validate
          </button>
          <button
            type="button"
            onClick={() => void handleSaveDraft()}
            disabled={saving}
            className="rounded-full bg-[rgba(191,104,32,0.14)] px-4 py-2 text-[0.84rem] font-semibold text-[rgb(140,76,17)] disabled:opacity-60"
          >
            {saving ? 'Saving...' : 'Save Draft'}
          </button>
          <button
            type="button"
            onClick={() => void handlePublish()}
            disabled={publishing || !draftRevisionId}
            className="rounded-full bg-[var(--primary)] px-4 py-2 text-[0.84rem] font-semibold text-white disabled:bg-[rgba(26,28,25,0.12)] disabled:text-[var(--muted)]"
          >
            {publishing ? 'Publishing...' : 'Publish Draft'}
          </button>
        </div>
      </div>

      {error ? (
        <div className="mt-4 rounded-[18px] bg-[rgba(97,0,0,0.08)] px-4 py-3 text-[0.9rem] font-semibold text-[var(--primary)]">
          {error}
        </div>
      ) : null}

      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <RevisionBadge title="Active" revision={activeRevision} />
        <RevisionBadge title="Draft" revision={settings?.draft_revision ?? null} />
        <div className={`rounded-[18px] px-4 py-3 text-[0.84rem] font-semibold ${validationTone(validation)}`}>
          <div className="text-[0.72rem] uppercase tracking-[0.14em] opacity-70">Validation</div>
          <div className="mt-1">
            {validation ? (validation.valid ? 'PASS' : 'FAILED') : 'Not checked'}
          </div>
          {validation?.fingerprint_sha256 ? (
            <div className="mt-1 font-mono text-[0.72rem]">{shortFingerprint(validation.fingerprint_sha256)}</div>
          ) : null}
        </div>
      </div>

      <label className="mt-4 block">
        <div className="text-[0.78rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Draft summary</div>
        <input
          value={summary}
          onChange={(event) => setSummary(event.target.value)}
          className="mt-1 w-full rounded-[16px] border border-[rgba(26,28,25,0.08)] bg-white px-4 py-3 text-[0.92rem] outline-none"
        />
      </label>

      {validation && !validation.valid ? (
        <div className="mt-4 rounded-[18px] bg-[rgba(97,0,0,0.06)] px-4 py-3">
          <div className="text-[0.86rem] font-bold text-[var(--primary)]">Validation issues</div>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-[0.82rem] text-[var(--primary)]">
            {validation.issues.map((issue, index) => (
              <li key={`${issue.path}-${index}`}>{issue.path}: {issue.message}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="mt-5 space-y-5">
        {STRUCTURED_DICTIONARIES.map((dictionary) => (
          <DictionarySection
            key={dictionary.key}
            title={dictionary.label}
            entries={objectDictionaryEntries(content, dictionary.key)}
            receiptMode={dictionary.receiptMode}
            onChange={(index, outputKey, value) => updateObjectDictionaryOutput(dictionary.key, index, outputKey, value)}
          />
        ))}

        <div className="grid gap-5 lg:grid-cols-2">
          {MODIFIER_DICTIONARIES.map((dictionary) => (
            <ModifierDictionarySection
              key={dictionary.key}
              title={dictionary.label}
              entries={modifierDictionaryEntries(content, dictionary.key)}
              onChange={(index, pair) => updateModifierPair(dictionary.key, index, pair)}
              onAdd={() => addModifierPair(dictionary.key)}
              onRemove={(index) => removeModifierPair(dictionary.key, index)}
            />
          ))}
        </div>

        <section className="rounded-[22px] border border-[rgba(26,28,25,0.08)] bg-[rgba(26,28,25,0.03)] p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div className="text-[1rem] font-bold text-[var(--on-surface)]">Conditional display rules</div>
              <div className="mt-1 text-[0.8rem] text-[var(--muted)]">
                Constrained item/dictionary/semantic conditions only. No scripts, regex, or raw template expressions.
              </div>
            </div>
            <button
              type="button"
              onClick={addConditional}
              className="rounded-full bg-[rgba(26,28,25,0.06)] px-4 py-2 text-[0.82rem] font-semibold text-[var(--on-surface)]"
            >
              Add rule
            </button>
          </div>
          <div className="mt-3 space-y-3">
            {(content.conditional_overrides ?? []).map((entry, index) => (
              <div key={index} className="grid gap-3 rounded-[18px] bg-white p-3 md:grid-cols-[minmax(0,1.3fr)_180px_minmax(0,1fr)_96px_96px]">
                <label className="text-[0.78rem] font-semibold text-[var(--muted)]">
                  Item SKU(s)
                  <input
                    value={itemSkuCsv(entry.condition?.item_sku)}
                    onChange={(event) => updateConditional(index, (current) => ({
                      ...current,
                      condition: {
                        ...(current.condition ?? {}),
                        item_sku: splitCsv(event.target.value),
                      },
                    }))}
                    placeholder="sku_a, sku_b"
                    className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.88rem] font-normal outline-none"
                  />
                </label>
                <label className="text-[0.78rem] font-semibold text-[var(--muted)]">
                  Dictionary
                  <select
                    value={entry.condition?.dictionary ?? 'NOODLE_TYPE'}
                    onChange={(event) => updateConditional(index, (current) => ({
                      ...current,
                      condition: {
                        ...(current.condition ?? {}),
                        dictionary: event.target.value,
                      },
                    }))}
                    className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.88rem] font-normal outline-none"
                  >
                    {CONDITION_DICTIONARIES.map((dictionary) => (
                      <option key={dictionary} value={dictionary}>{dictionary}</option>
                    ))}
                  </select>
                </label>
                <label className="text-[0.78rem] font-semibold text-[var(--muted)]">
                  Semantic code
                  <input
                    value={entry.condition?.semantic_code ?? ''}
                    onChange={(event) => updateConditional(index, (current) => ({
                      ...current,
                      condition: {
                        ...(current.condition ?? {}),
                        semantic_code: event.target.value,
                      },
                    }))}
                    className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.88rem] font-normal outline-none"
                  />
                </label>
                <label className="flex items-center justify-center gap-2 rounded-[14px] bg-[rgba(26,28,25,0.04)] px-3 py-2 text-[0.82rem] font-semibold text-[var(--on-surface)]">
                  <input
                    type="checkbox"
                    checked={entry.omit ?? true}
                    onChange={(event) => updateConditional(index, (current) => ({ ...current, omit: event.target.checked }))}
                    className="h-4 w-4 accent-[var(--primary)]"
                  />
                  Omit
                </label>
                <button
                  type="button"
                  onClick={() => removeConditional(index)}
                  className="rounded-[14px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.82rem] font-semibold text-[var(--primary)]"
                >
                  Remove
                </button>
              </div>
            ))}
          </div>
        </section>

        <PreviewSection
          previewInput={previewInput}
          setPreviewInput={setPreviewInput}
          onPreview={handlePreview}
          preview={preview}
        />

        <section className="rounded-[22px] border border-[rgba(26,28,25,0.08)] bg-white p-4">
          <div className="text-[1rem] font-bold text-[var(--on-surface)]">Revision history</div>
          <div className="mt-3 overflow-auto rounded-[16px] border border-[rgba(26,28,25,0.06)]">
            <table className="min-w-full text-left text-[0.84rem]">
              <thead className="bg-[rgba(246,243,236,0.94)] text-[0.72rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                <tr>
                  <th className="px-3 py-2">Revision</th>
                  <th className="px-3 py-2">Status</th>
                  <th className="px-3 py-2">Fingerprint</th>
                  <th className="px-3 py-2">Summary</th>
                  <th className="px-3 py-2">Published</th>
                </tr>
              </thead>
              <tbody>
                {(settings?.revisions ?? []).map((revision) => (
                  <tr key={revision.id} className="border-t border-[rgba(26,28,25,0.06)]">
                    <td className="px-3 py-2 font-semibold">v{revision.revision_number}</td>
                    <td className="px-3 py-2">{revision.status}</td>
                    <td className="px-3 py-2 font-mono text-[0.75rem]">{shortFingerprint(revision.fingerprint_sha256)}</td>
                    <td className="px-3 py-2 text-[var(--muted)]">{revision.summary ?? '—'}</td>
                    <td className="px-3 py-2 text-[var(--muted)]">{revision.published_at ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </section>
  )
}

function RevisionBadge({ title, revision }: { title: string; revision?: PrintingDisplayRuleRevision | null }) {
  return (
    <div className="rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-3 text-[0.84rem]">
      <div className="text-[0.72rem] font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">{title}</div>
      <div className="mt-1 font-bold text-[var(--on-surface)]">{revisionLabel(revision)}</div>
      <div className="mt-1 font-mono text-[0.72rem] text-[var(--muted)]">{shortFingerprint(revision?.fingerprint_sha256)}</div>
    </div>
  )
}

function DictionarySection({
  title,
  entries,
  receiptMode,
  onChange,
}: {
  title: string
  entries: DictionaryObjectEntry[]
  receiptMode: 'ZH_EN' | 'SINGLE' | 'NONE'
  onChange: (index: number, outputKey: string, value: string) => void
}) {
  return (
    <section className="rounded-[22px] border border-[rgba(26,28,25,0.08)] bg-[rgba(26,28,25,0.03)] p-4">
      <div className="text-[1rem] font-bold text-[var(--on-surface)]">{title}</div>
      <div className="mt-3 space-y-3">
        {entries.map((entry, index) => (
          <div
            key={`${entry.semantic_code ?? title}-${index}`}
            className="grid gap-3 rounded-[18px] bg-white p-3 lg:grid-cols-[160px_minmax(0,1.2fr)_120px_140px_140px_minmax(0,1fr)]"
          >
            <div>
              <div className="text-[0.72rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Semantic</div>
              <div className="mt-1 text-[0.88rem] font-bold text-[var(--on-surface)]">{entry.semantic_code ?? '—'}</div>
            </div>
            <div>
              <div className="text-[0.72rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">Matches</div>
              <div className="mt-1 text-[0.8rem] text-[var(--muted)]">{matchSummary(entry)}</div>
            </div>
            <RuleTextInput label="GRAB" value={entry.outputs?.GRAB ?? ''} onChange={(value) => onChange(index, 'GRAB', value)} />
            <RuleTextInput label="HOT Kitchen" value={entry.outputs?.HOT_KITCHEN ?? ''} onChange={(value) => onChange(index, 'HOT_KITCHEN', value)} />
            {receiptMode === 'ZH_EN' ? (
              <div className="grid gap-2">
                <RuleTextInput label="Receipt ZH" value={entry.outputs?.FRONTDESK_RECEIPT_ZH ?? ''} onChange={(value) => onChange(index, 'FRONTDESK_RECEIPT_ZH', value)} />
                <RuleTextInput label="Receipt EN" value={entry.outputs?.FRONTDESK_RECEIPT_EN ?? ''} onChange={(value) => onChange(index, 'FRONTDESK_RECEIPT_EN', value)} />
              </div>
            ) : receiptMode === 'SINGLE' ? (
              <RuleTextInput label="Receipt" value={entry.outputs?.FRONTDESK_RECEIPT ?? ''} onChange={(value) => onChange(index, 'FRONTDESK_RECEIPT', value)} />
            ) : (
              <div className="rounded-[14px] bg-[rgba(26,28,25,0.04)] px-3 py-2 text-[0.78rem] text-[var(--muted)]">No receipt output</div>
            )}
          </div>
        ))}
      </div>
    </section>
  )
}

function RuleTextInput({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="block text-[0.72rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">
      {label}
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 w-full rounded-[14px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.88rem] font-normal text-[var(--on-surface)] outline-none"
      />
    </label>
  )
}

function ModifierDictionarySection({
  title,
  entries,
  onChange,
  onAdd,
  onRemove,
}: {
  title: string
  entries: [string, string][]
  onChange: (index: number, pair: [string, string]) => void
  onAdd: () => void
  onRemove: (index: number) => void
}) {
  return (
    <section className="rounded-[22px] border border-[rgba(26,28,25,0.08)] bg-[rgba(26,28,25,0.03)] p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="text-[1rem] font-bold text-[var(--on-surface)]">{title}</div>
        <button
          type="button"
          onClick={onAdd}
          className="rounded-full bg-[rgba(26,28,25,0.06)] px-3 py-1.5 text-[0.78rem] font-semibold text-[var(--on-surface)]"
        >
          Add
        </button>
      </div>
      <div className="mt-3 space-y-2">
        {entries.map((entry, index) => (
          <div key={`${entry[0]}-${index}`} className="grid gap-2 rounded-[16px] bg-white p-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_80px]">
            <input
              value={entry[0]}
              onChange={(event) => onChange(index, [event.target.value, entry[1]])}
              aria-label={`${title} code`}
              className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.86rem] outline-none"
            />
            <input
              value={entry[1]}
              onChange={(event) => onChange(index, [entry[0], event.target.value])}
              aria-label={`${title} output`}
              className="rounded-[12px] border border-[rgba(26,28,25,0.08)] px-3 py-2 text-[0.86rem] outline-none"
            />
            <button
              type="button"
              onClick={() => onRemove(index)}
              className="rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 text-[0.78rem] font-semibold text-[var(--primary)]"
            >
              Remove
            </button>
          </div>
        ))}
      </div>
    </section>
  )
}

function PreviewSection({
  previewInput,
  setPreviewInput,
  onPreview,
  preview,
}: {
  previewInput: {
    item_sku: string
    item_name_zh: string
    item_name_en: string
    size_zh: string
    noodle_type_zh: string
    spiciness_zh: string
    modifier_add_codes: string
    modifier_remove_codes: string
    combo: boolean
  }
  setPreviewInput: (value: {
    item_sku: string
    item_name_zh: string
    item_name_en: string
    size_zh: string
    noodle_type_zh: string
    spiciness_zh: string
    modifier_add_codes: string
    modifier_remove_codes: string
    combo: boolean
  }) => void
  onPreview: () => void
  preview: PrintingDisplayRulePreviewResponse | null
}) {
  const setField = (field: keyof typeof previewInput, value: string | boolean) => {
    setPreviewInput({ ...previewInput, [field]: value })
  }

  return (
    <section className="rounded-[22px] border border-[rgba(38,86,160,0.14)] bg-[rgba(38,86,160,0.05)] p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[1rem] font-bold text-[var(--on-surface)]">Preview</div>
          <div className="mt-1 text-[0.8rem] text-[var(--muted)]">Shows what future GRAB / receipt / HOT kitchen rendering will display.</div>
        </div>
        <button
          type="button"
          onClick={onPreview}
          className="rounded-full bg-[rgba(38,86,160,0.14)] px-4 py-2 text-[0.84rem] font-semibold text-[rgb(38,86,160)]"
        >
          Generate Preview
        </button>
      </div>
      <div className="mt-3 grid gap-3 md:grid-cols-3">
        <RuleTextInput label="Item SKU" value={previewInput.item_sku} onChange={(value) => setField('item_sku', value)} />
        <RuleTextInput label="Name ZH" value={previewInput.item_name_zh} onChange={(value) => setField('item_name_zh', value)} />
        <RuleTextInput label="Name EN" value={previewInput.item_name_en} onChange={(value) => setField('item_name_en', value)} />
        <RuleTextInput label="Size ZH" value={previewInput.size_zh} onChange={(value) => setField('size_zh', value)} />
        <RuleTextInput label="Noodle ZH" value={previewInput.noodle_type_zh} onChange={(value) => setField('noodle_type_zh', value)} />
        <RuleTextInput label="Spicy ZH" value={previewInput.spiciness_zh} onChange={(value) => setField('spiciness_zh', value)} />
        <RuleTextInput label="Add codes CSV" value={previewInput.modifier_add_codes} onChange={(value) => setField('modifier_add_codes', value)} />
        <RuleTextInput label="Remove codes CSV" value={previewInput.modifier_remove_codes} onChange={(value) => setField('modifier_remove_codes', value)} />
        <label className="flex items-center gap-3 rounded-[16px] bg-white px-4 py-3 text-[0.86rem] font-semibold text-[var(--on-surface)]">
          <input
            type="checkbox"
            checked={previewInput.combo}
            onChange={(event) => setField('combo', event.target.checked)}
            className="h-4 w-4 accent-[var(--primary)]"
          />
          Combo
        </label>
      </div>
      {preview ? (
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <PreviewCard title="GRAB" value={preview.grab_preview} />
          <PreviewCard title="Frontdesk receipt" value={preview.frontdesk_receipt_preview} />
          <PreviewCard title="HOT kitchen" value={preview.hot_kitchen_preview} />
        </div>
      ) : null}
    </section>
  )
}

function PreviewCard({ title, value }: { title: string; value: string }) {
  return (
    <div className="rounded-[18px] bg-white px-4 py-3">
      <div className="text-[0.72rem] font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">{title}</div>
      <pre className="mt-2 whitespace-pre-wrap text-[0.9rem] font-semibold text-[var(--on-surface)]">{value || '—'}</pre>
    </div>
  )
}

interface ItemPrintingRuleAliasPanelProps {
  storeId: number
  itemSku: string
  itemNameZh: string
  itemNameEn: string
  onToast?: ToastHandler
}

export function ItemPrintingRuleAliasPanel({
  storeId,
  itemSku,
  itemNameZh,
  itemNameEn,
  onToast,
}: ItemPrintingRuleAliasPanelProps) {
  const [settings, setSettings] = useState<PrintingDisplayRuleSettings | null>(null)
  const [outputs, setOutputs] = useState<OutputMap>({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [publishing, setPublishing] = useState(false)

  const loadAlias = useCallback(async () => {
    if (!itemSku.trim()) {
      setSettings(null)
      setOutputs({})
      return
    }
    setLoading(true)
    try {
      const next = await fetchPrintingDisplayRules(storeId)
      const content = activeContentFrom(next)
      const alias = (content.item_aliases ?? []).find((entry) => entry.item_sku === itemSku)
      setSettings(next)
      setOutputs(alias?.outputs ?? {})
    } catch (loadError) {
      onToast?.(loadError instanceof Error ? loadError.message : 'Item printing alias load failed', 'error')
    } finally {
      setLoading(false)
    }
  }, [itemSku, onToast, storeId])

  useEffect(() => {
    void loadAlias()
  }, [loadAlias])

  const draftRevisionId = settings?.draft_revision?.id ?? null
  const fallbackName = itemNameZh || itemNameEn || itemSku

  const updatedContentWithAlias = useMemo(() => {
    const content = activeContentFrom(settings)
    const aliases = [...(content.item_aliases ?? [])].filter((entry) => entry.item_sku !== itemSku)
    const cleanedOutputs = Object.fromEntries(
      Object.entries(outputs)
        .map(([key, value]) => [key, value.trim()])
        .filter(([, value]) => value),
    ) as OutputMap
    if (itemSku.trim() && Object.keys(cleanedOutputs).length) {
      aliases.push({
        item_sku: itemSku.trim(),
        outputs: cleanedOutputs,
      })
    }
    content.item_aliases = aliases
    return content
  }, [itemSku, outputs, settings])

  const handleSave = async () => {
    if (!itemSku.trim()) {
      onToast?.('SKU is required before saving printing aliases.', 'error')
      return
    }
    try {
      setSaving(true)
      const saved = await savePrintingDisplayRuleDraft(
        storeId,
        updatedContentWithAlias,
        `Updated item printing aliases for ${itemSku}`,
      )
      onToast?.(`Item printing alias draft saved: v${saved.revision_number}.`, 'success')
      const next = await fetchPrintingDisplayRules(storeId)
      setSettings(next)
    } catch (saveError) {
      onToast?.(saveError instanceof Error ? saveError.message : 'Item printing alias save failed', 'error')
    } finally {
      setSaving(false)
    }
  }

  const handlePublish = async () => {
    if (!draftRevisionId) {
      onToast?.('Save a draft before publishing item printing aliases.', 'error')
      return
    }
    try {
      setPublishing(true)
      const published = await publishPrintingDisplayRuleDraft(storeId, draftRevisionId)
      onToast?.(`Printing display rules published as v${published.revision_number}.`, 'success')
      const next = await fetchPrintingDisplayRules(storeId)
      setSettings(next)
    } catch (publishError) {
      onToast?.(publishError instanceof Error ? publishError.message : 'Publish failed', 'error')
    } finally {
      setPublishing(false)
    }
  }

  return (
    <div className="rounded-[18px] border border-[rgba(38,86,160,0.14)] bg-[rgba(38,86,160,0.05)] px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[0.9rem] font-bold text-[var(--on-surface)]">Item → Printing Rules</div>
          <div className="mt-1 text-[0.78rem] leading-5 text-[var(--muted)]">
            Optional item-specific display aliases. Blank values fall back to the menu name / Store dictionary.
          </div>
        </div>
        <div className="text-[0.72rem] font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
          {loading ? 'Loading…' : `Active ${revisionLabel(settings?.active_revision)}`}
        </div>
      </div>
      <div className="mt-3 grid gap-3 md:grid-cols-3">
        {OUTPUT_TYPES.map((outputType) => (
          <RuleTextInput
            key={outputType}
            label={outputType}
            value={outputs[outputType] ?? ''}
            onChange={(value) => setOutputs((current) => ({ ...current, [outputType]: value }))}
          />
        ))}
      </div>
      <div className="mt-3 rounded-[14px] bg-white/80 px-3 py-2 text-[0.78rem] text-[var(--muted)]">
        Fallback preview: {fallbackName}
      </div>
      <div className="mt-3 flex flex-wrap justify-end gap-2">
        <button
          type="button"
          onClick={() => void loadAlias()}
          className="rounded-[14px] bg-white px-3.5 py-2 text-[0.82rem] font-semibold text-[var(--on-surface)]"
        >
          Reload alias
        </button>
        <button
          type="button"
          onClick={() => void handleSave()}
          disabled={saving || !itemSku.trim()}
          className="rounded-[14px] bg-[rgba(38,86,160,0.14)] px-3.5 py-2 text-[0.82rem] font-semibold text-[rgb(38,86,160)] disabled:opacity-60"
        >
          {saving ? 'Saving...' : 'Save rule draft'}
        </button>
        <button
          type="button"
          onClick={() => void handlePublish()}
          disabled={publishing || !draftRevisionId}
          className="rounded-[14px] bg-[var(--primary)] px-3.5 py-2 text-[0.82rem] font-semibold text-white disabled:bg-[rgba(26,28,25,0.12)] disabled:text-[var(--muted)]"
        >
          {publishing ? 'Publishing...' : 'Publish draft'}
        </button>
      </div>
    </div>
  )
}
