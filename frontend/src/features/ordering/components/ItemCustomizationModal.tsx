import { useEffect } from 'react'
import { useLocale } from '../../../i18n/useLocale'
import { Button } from '../../../components/ui/Button'
import { Card } from '../../../components/ui/Card'
import { useIpadLandscape } from '../../../hooks/useIpadLandscape'
import type { ComboChoiceGroup, ItemCustomizationDraft, MenuItem } from '../../../types/ordering'
import {
  resolveComboDraftSelections,
  resolveComboGroupOptionId,
} from '../../../utils/comboSelection'
import { formatNoodleTypeDisplayLabel } from './noodleTypePresentation'
import { selectAddOn, selectRemove } from '../../../utils/modifierSelection'
import { selectTemplateOption, templateSelectionError } from '../../../utils/templateCustomization'
import { businessLabel, missingBusinessTranslation, type BusinessNames } from '../../../i18n/locale'

function isToggleAddOn(labelZh: string, priceDelta?: number) {
  return (priceDelta ?? 0) === 0 && (labelZh === '加香菜' || labelZh === '加葱')
}

const NOODLE_CATEGORY_CODES = new Set(['SOUP_NOODLE', 'DRY_NOODLE', 'FRIED_NOODLE', 'NOODLE', 'NOODLES'])

function normalizeStableCode(value?: string | null) {
  return (value ?? '').trim().toUpperCase()
}

function formatPriceDelta(priceDelta?: number) {
  if (!priceDelta) {
    return ''
  }
  const prefix = priceDelta > 0 ? '+' : '-'
  return ` (${prefix}$${Math.abs(priceDelta).toFixed(2)})`
}

function isNoodleMenuItem(item: MenuItem) {
  const categoryCode = normalizeStableCode(item.categoryCode)
  if (NOODLE_CATEGORY_CODES.has(categoryCode)) {
    return true
  }
  // Structural fallback for older local data that may not expose category codes.
  return Boolean(item.customization?.noodleTypes?.length || item.customization?.soupBases)
}

function comboDraftWithSelection(draft: ItemCustomizationDraft, group: ComboChoiceGroup, optionId: string): ItemCustomizationDraft {
  return {
    ...draft,
    comboEggId: group.groupCode === 'COMBO_EGG' ? optionId : draft.comboEggId,
    comboSideId: group.groupCode === 'COMBO_SIDE' ? optionId : draft.comboSideId,
    comboSelections: {
      ...(draft.comboSelections ?? {}),
      [group.groupCode]: optionId,
    },
    comboSideRemoveIds: group.groupCode === 'COMBO_SIDE' ? [] : draft.comboSideRemoveIds,
  }
}

interface ItemCustomizationModalProps {
  item: MenuItem
  draft: ItemCustomizationDraft
  mode: 'add' | 'edit'
  subtotal: number
  onClose: () => void
  onChange: (nextDraft: ItemCustomizationDraft) => void
  onSubmit: () => void
  submitDisabled?: boolean
  submitDisabledReason?: string
}

function ChoiceButton({
  active,
  label,
  sublabel,
  onClick,
  compact = false,
}: {
  active: boolean
  label: string
  sublabel: string
  onClick: () => void
  compact?: boolean
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`text-center transition ${
        active
          ? 'bg-[var(--primary)] text-[var(--on-primary)] shadow-[0_12px_26px_rgba(97,0,0,0.18)]'
          : 'bg-[var(--surface-container-low)] text-[rgba(83,58,50,0.88)]'
      } ${compact ? 'rounded-[16px] px-3 py-2.5' : 'rounded-[22px] px-5 py-4'}`}
    >
      <div className={compact ? 'text-[0.92rem] font-bold' : 'text-[1.05rem] font-bold'}>{label}</div>
      {sublabel ? <div className={`${compact ? 'mt-0.5 text-[0.76rem]' : 'mt-1 text-sm'} ${active ? 'text-[rgba(255,249,247,0.86)]' : 'text-[var(--muted)]'}`}>{sublabel}</div> : null}
    </button>
  )
}

function CheckboxRow({
  checked,
  label,
  sublabel,
  onClick,
  compact = false,
  disabled = false,
}: {
  checked: boolean
  label: string
  sublabel: string
  onClick: () => void
  compact?: boolean
  disabled?: boolean
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={`flex w-full items-center gap-3 rounded-[18px] text-left disabled:cursor-not-allowed disabled:opacity-40 ${compact ? 'px-1 py-1.5' : 'px-2 py-2'}`}
    >
      <span
        className={`inline-flex items-center justify-center rounded-[10px] border ${
          checked ? 'border-[var(--primary)] bg-[var(--primary)] text-[var(--on-primary)]' : 'border-[rgba(97,0,0,0.18)] bg-white'
        } ${compact ? 'h-6 w-6 text-[0.8rem]' : 'h-7 w-7'}`}
      >
        {checked ? '✓' : ''}
      </span>
      <span className={`${compact ? 'text-[0.9rem]' : 'text-base'} font-medium text-[var(--on-surface)]`}>
        {label}{sublabel ? ` / ${sublabel}` : ''}
      </span>
    </button>
  )
}

function AddOnQuantityRow({
  label,
  sublabel,
  quantity,
  priceDelta,
  onDecrement,
  onIncrement,
  incrementDisabled = false,
  compact = false,
}: {
  label: string
  sublabel: string
  quantity: number
  priceDelta?: number
  onDecrement: () => void
  onIncrement: () => void
  incrementDisabled?: boolean
  compact?: boolean
}) {
  return (
    <div className={`flex items-center justify-between gap-4 rounded-[18px] ${compact ? 'px-1 py-1.5' : 'px-2 py-2'}`}>
      <div className="min-w-0">
        <div className={`${compact ? 'text-[0.9rem]' : 'text-base'} font-medium text-[var(--on-surface)]`}>
          {label}
          {priceDelta ? ` (+$${priceDelta.toFixed(2)})` : ''}
        </div>
        <div className={`${compact ? 'text-[0.76rem]' : 'text-sm'} text-[var(--muted)]`}>{sublabel}</div>
      </div>

      <div className={`inline-flex shrink-0 items-center bg-[var(--surface-container-low)] p-1 ${compact ? 'rounded-[12px]' : 'rounded-[16px]'}`}>
        <button
          type="button"
          className={`inline-flex items-center justify-center text-[var(--on-surface)] ${compact ? 'h-9 w-9 rounded-[10px] text-[1.1rem]' : 'h-10 w-10 rounded-[12px] text-xl'}`}
          onClick={onDecrement}
        >
          −
        </button>
        <span className={`inline-flex items-center justify-center font-bold ${compact ? 'min-w-8 text-[0.92rem]' : 'min-w-10 text-base'}`}>{quantity}</span>
        <button
          type="button"
          className={`inline-flex items-center justify-center bg-[var(--primary)] text-[var(--on-primary)] ${compact ? 'h-9 w-9 rounded-[10px] text-[1.1rem]' : 'h-10 w-10 rounded-[12px] text-xl'}`}
          onClick={onIncrement}
          disabled={incrementDisabled}
        >
          +
        </button>
      </div>
    </div>
  )
}

export function ItemCustomizationModal({
  item,
  draft,
  mode,
  subtotal,
  onClose,
  onChange,
  onSubmit,
  submitDisabled = false,
  submitDisabledReason,
}: ItemCustomizationModalProps) {
  const { t, label: businessName, locale } = useLocale()
  const missingTranslation = missingBusinessTranslation({ en: item.nameEn, zh: item.nameZh, fr: item.nameFr }, locale)
    || (item.optionGroups ?? []).some((group) => group.is_active && (missingBusinessTranslation({ name: group.name, translations: group.name_translations }, locale)
      || group.options.some((option) => option.is_active && missingBusinessTranslation({ en: option.name_en, zh: option.name_zh, fr: option.name_fr }, locale))))
  const displayChoice = (option: { labelEn: string; labelZh: string; labelFr?: string | null }) => businessName({ en: option.labelEn, zh: option.labelZh, fr: option.labelFr })
  const customization = item.customization
  const selectionError = templateSelectionError(item, draft)
  const compact = useIpadLandscape()
  const toggleAddOns = customization?.addOns?.filter((option) => isToggleAddOn(option.labelZh, option.priceDelta)) ?? []
  const quantityAddOns = customization?.addOns?.filter((option) => !isToggleAddOn(option.labelZh, option.priceDelta)) ?? []
  const comboConfig = customization?.combo
  const isNoodleItem = isNoodleMenuItem(item)
  const templateMode = item.optionGroups != null
  const showComboFirst = !templateMode && isNoodleItem && Boolean(comboConfig)
  const templateLabels = (names: BusinessNames) => {
    const label = businessLabel(names, locale === 'fr-CA' ? 'fr-CA' : 'en', t('common.untitled'))
    const chinese = (names.translations?.['zh-CN'] || names.zh)?.trim()
    return { label, sublabel: chinese && chinese !== label ? chinese : '' }
  }
  const activeTemplateGroups = [...(item.optionGroups ?? [])].filter((group) => group.is_active).sort((a, b) => a.sort_order - b.sort_order)
  const isModifierGroup = (group: NonNullable<MenuItem['optionGroups']>[number]) => group.kind === 'ADD_ON' || group.kind === 'REMOVE'
  // Combo keeps its configured placement. Ordinary controls precede modifier cards,
  // matching the mature noodle customization layout without changing menu data.
  const displayedTemplateGroups = activeTemplateGroups.some((group) => group.kind === 'COMBO')
    ? activeTemplateGroups
    : [...activeTemplateGroups.filter((group) => !isModifierGroup(group)), ...activeTemplateGroups.filter(isModifierGroup)]
  const modifierGroupCount = activeTemplateGroups.filter(isModifierGroup).length
  const comboSection = comboConfig ? (
    <section className={`bg-[rgba(26,28,25,0.03)] ${compact ? 'space-y-3 rounded-[20px] p-4' : 'space-y-4 rounded-[28px] p-6'}`}>
      {(() => {
        const comboGroups = comboConfig.groups?.length
          ? comboConfig.groups
          : [
              { groupCode: 'COMBO_EGG', labelEn: 'Egg', labelZh: '鸡蛋', selectionRule: 'EXACTLY_ONE', required: true, options: comboConfig.eggs, defaultOptionId: comboConfig.eggs[0]?.id },
              { groupCode: 'COMBO_SIDE', labelEn: 'Side Dish', labelZh: '配菜', selectionRule: 'EXACTLY_ONE', required: true, options: comboConfig.sides, defaultOptionId: comboConfig.sides[0]?.id },
            ].filter((group) => group.options.length) satisfies ComboChoiceGroup[]
        const selectedComboSideId = resolveComboGroupOptionId(
          draft,
          comboGroups.find((group) => group.groupCode === 'COMBO_SIDE')
            ?? { groupCode: 'COMBO_SIDE', labelEn: 'Side Dish', labelZh: '配菜', selectionRule: 'EXACTLY_ONE', required: true, options: comboConfig.sides, defaultOptionId: comboConfig.sides[0]?.id },
        )
        const sideRemoveOptions = comboConfig.sideRemoveOptions.filter(
          (option) => option.parentOptionId === selectedComboSideId,
        )
        return (
          <>
            <div className="flex items-center justify-between gap-4">
              <button
                type="button"
                aria-label={t('options.makeCombo')}
                aria-pressed={draft.comboEnabled}
                onClick={() => {
                  const resolvedDraft = resolveComboDraftSelections(draft, comboGroups)
                  onChange({
                    ...resolvedDraft,
                    comboEnabled: !draft.comboEnabled,
                    comboSideRemoveIds: !draft.comboEnabled && resolvedDraft.comboSideId === draft.comboSideId
                      ? draft.comboSideRemoveIds
                      : [],
                  })
                }}
                className="flex items-center gap-3 text-left"
              >
                <span
                  className={`inline-flex h-8 w-8 items-center justify-center rounded-[10px] border ${
                    draft.comboEnabled
                      ? 'border-[var(--primary)] bg-[var(--primary)] text-[var(--on-primary)]'
                      : 'border-[rgba(97,0,0,0.16)] bg-white'
                  }`}
                >
                  {draft.comboEnabled ? '✓' : ''}
                </span>
                <span className={`${compact ? 'text-[1.05rem]' : 'text-[1.45rem]'} font-bold tracking-[-0.03em]`}>
                  {t('options.makeCombo')}
                </span>
              </button>
              <span className={`${compact ? 'text-[1rem]' : 'text-[1.35rem]'} font-bold text-[var(--secondary)]`}>
                +${comboConfig.upcharge.toFixed(2)}
              </span>
            </div>

            {draft.comboEnabled ? (
              <div className={compact ? 'space-y-3' : 'space-y-4'}>
                {comboGroups.map((group) => (
                  <div key={group.groupCode} className="space-y-2">
                    <span className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
                      {displayChoice(group)}
                    </span>
                    <div className={`grid ${group.groupCode === 'COMBO_SIDE' ? 'md:grid-cols-3' : 'md:grid-cols-2'} ${compact ? 'gap-2.5' : 'gap-3'}`}>
                      {group.options.map((option) => (
                        <ChoiceButton
                          key={option.id}
                          active={resolveComboGroupOptionId(draft, group) === option.id}
                          label={displayChoice(option)}
                          sublabel=""
                          compact={compact}
                          onClick={() => onChange(comboDraftWithSelection(draft, group, option.id))}
                        />
                      ))}
                    </div>
                  </div>
                ))}

                {sideRemoveOptions.length ? (
                  <div className="space-y-2 rounded-[18px] bg-[rgba(255,255,255,0.62)] p-3">
                    <span className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
                      {t('options.sideRequests')}
                    </span>
                    {sideRemoveOptions.map((option) => {
                      const checked = draft.comboSideRemoveIds.includes(option.id)
                      return (
                        <CheckboxRow
                          key={option.id}
                          checked={checked}
                          label={displayChoice(option)}
                          sublabel=""
                          compact={compact}
                          onClick={() =>
                            onChange({
                              ...draft,
                              comboSideRemoveIds: checked
                                ? draft.comboSideRemoveIds.filter((id) => id !== option.id)
                                : [...draft.comboSideRemoveIds, option.id],
                            })
                          }
                        />
                      )
                    })}
                  </div>
                ) : null}
              </div>
            ) : null}
          </>
        )
      })()}
    </section>
  ) : null

  useEffect(() => {
    const originalOverflow = document.body.style.overflow
    const originalTouchAction = document.body.style.touchAction

    document.body.style.overflow = 'hidden'
    document.body.style.touchAction = 'none'

    return () => {
      document.body.style.overflow = originalOverflow
      document.body.style.touchAction = originalTouchAction
    }
  }, [])

  return (
    <div
      className={`fixed inset-0 z-40 flex items-center justify-center overflow-hidden bg-[rgba(26,28,25,0.18)] backdrop-blur-sm ${compact ? 'p-3' : 'p-6'}`}
      onPointerDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <Card className={`relative flex w-full flex-col overflow-hidden p-0 ${compact ? 'max-h-[94vh] max-w-[960px] rounded-[24px]' : 'max-h-[92vh] max-w-[1120px] rounded-[36px]'}`}>
        <button
          type="button"
          aria-label={t('common.close')}
          onClick={onClose}
          className={`absolute z-20 inline-flex items-center justify-center rounded-full bg-white/95 font-black leading-none text-[rgba(83,58,50,0.78)] shadow-[0_14px_30px_rgba(26,28,25,0.16)] transition hover:bg-white hover:text-[var(--primary)] focus:outline-none focus:ring-4 focus:ring-[rgba(97,0,0,0.16)] ${compact ? 'right-3 top-3 h-12 w-12 text-[2rem]' : 'right-5 top-5 h-14 w-14 text-[2.35rem]'}`}
        >
          ×
        </button>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain [scrollbar-gutter:stable]">
          <div className={`border-b border-[rgba(26,28,25,0.05)] ${compact ? 'px-5 py-4' : 'px-8 py-7'}`}>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-[var(--primary)]">{t('options.title')}</p>
            <div className={`${compact ? 'mt-2.5' : 'mt-3'} flex items-start justify-between gap-6`}>
              <div>
                <h2 className={`${compact ? 'text-[1.8rem]' : 'text-[2.35rem]'} font-extrabold tracking-[-0.05em] text-[var(--on-surface)]`}>
                  {businessName({ en: item.nameEn, zh: item.nameZh, fr: item.nameFr })}
                </h2>
                {missingTranslation ? <p className="mt-1 text-xs text-[var(--muted)]">{t('common.translationFallback')}</p> : null}
              </div>
              <div className={`${compact ? 'text-[1.8rem]' : 'text-[2.2rem]'} font-extrabold tracking-[-0.04em] text-[var(--primary)]`}>
                ${item.price.toFixed(2)}
              </div>
            </div>
          </div>

          <div className={`${compact ? 'space-y-4 px-5 py-4' : 'space-y-6 px-8 py-7'}`}>
            {showComboFirst ? comboSection : null}

            {templateMode ? <div className={`grid grid-cols-1 md:grid-cols-2 ${compact ? 'gap-4' : 'gap-6'}`}>
              {displayedTemplateGroups.map((group) => {
                if (group.kind === 'COMBO') return <div key={group.group_instance_code} className="md:col-span-2" data-option-group={group.group_instance_code}>{comboSection}</div>
                const modifier = isModifierGroup(group)
                const heading = templateLabels({ name: group.name, translations: group.name_translations })
                const options = group.options.filter((option) => option.is_active && option.id != null).sort((a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0))
                const choiceColumns = options.length <= 2 ? 'md:grid-cols-2' : options.length === 3 ? 'md:grid-cols-3' : options.length === 4 ? 'md:grid-cols-4' : 'md:grid-cols-5'
                return <section key={group.group_instance_code}
                  className={`${!modifier || modifierGroupCount === 1 ? 'md:col-span-2' : ''} ${modifier ? `bg-[rgba(26,28,25,0.03)] ${compact ? 'space-y-2 rounded-[20px] p-4' : 'space-y-3 rounded-[24px] p-5'}` : compact ? 'space-y-3' : 'space-y-4'}`}
                  data-option-group={group.group_instance_code}>
                  <h3 className={`${compact ? 'text-[1.1rem]' : 'text-[1.45rem]'} font-bold tracking-[-0.03em]`}>
                    {heading.label}{heading.sublabel ? ` / ${heading.sublabel}` : ''} {group.required ? <span className="ml-2 text-sm text-[var(--primary)]">{t('common.required')}</span> : null}
                  </h3>
                  {group.min_select != null && group.min_select === group.max_select ? <p className="text-sm text-[var(--muted)]">{t('options.exactly', { count: group.min_select })}</p> : <>
                    {group.min_select != null && group.min_select > 0 ? <p className="text-sm text-[var(--muted)]">{t('options.atLeast', { count: group.min_select })}</p> : null}
                    {group.max_select != null ? <p className="text-sm text-[var(--muted)]">{t('options.atMost', { count: group.max_select })}</p> : null}
                  </>}
                  <div className={`grid ${group.selection_mode === 'SINGLE' ? `grid-cols-2 ${choiceColumns}` : !modifier || modifierGroupCount === 1 ? 'md:grid-cols-2' : 'grid-cols-1'} ${compact ? 'gap-2.5' : 'gap-3'}`}>
                    {options.map((option) => {
                      const quantity = draft.groupSelections?.[group.group_instance_code]?.[String(option.id)] ?? 0
                      const selectedCount = Object.values(draft.groupSelections?.[group.group_instance_code] ?? {}).filter((count) => count > 0).length
                      const atLimit = quantity === 0 && group.max_select != null && selectedCount >= group.max_select
                      const select = (next: number) => onChange(selectTemplateOption(draft, group, option.id!, next, item.optionGroups ?? []))
                      const labels = templateLabels({ en: option.name_en, zh: option.name_zh, fr: option.name_fr })
                      if (group.selection_mode === 'SINGLE') return <ChoiceButton key={option.id} compact={compact} active={quantity > 0} label={`${labels.label}${formatPriceDelta(option.price_delta)}`} sublabel={labels.sublabel} onClick={() => select(quantity > 0 && !group.required ? 0 : 1)} />
                      if (group.kind === 'ADD_ON') return <AddOnQuantityRow key={option.id} compact={compact} incrementDisabled={atLimit} label={labels.label} sublabel={labels.sublabel} quantity={quantity} priceDelta={option.price_delta} onIncrement={() => select(quantity + 1)} onDecrement={() => select(Math.max(0, quantity - 1))} />
                      return <CheckboxRow key={option.id} compact={compact} disabled={atLimit} checked={quantity > 0} label={`${labels.label}${formatPriceDelta(option.price_delta)}`} sublabel={labels.sublabel} onClick={() => select(quantity > 0 ? 0 : 1)} />
                    })}
                  </div>
                  {!group.options.some((option) => option.is_active) ? <p className="text-sm text-[var(--muted)]">{t('options.noAvailable')}</p> : null}
                </section>
              })}
            </div> : null}

            {customization?.sizes ? (
              <section className={compact ? 'space-y-3' : 'space-y-4'}>
                <div className="flex items-center gap-3">
                  <h3 className={`${compact ? 'text-[1.1rem]' : 'text-[1.45rem]'} font-bold tracking-[-0.03em]`}>{t('options.size')}</h3>
                  {customization.sizes.required ? (
                    <span className="rounded-full bg-[var(--primary)] px-3 py-1 text-xs font-bold uppercase tracking-[0.08em] text-[var(--on-primary)]">
                      {t('common.required')}
                    </span>
                  ) : null}
                </div>
                {customization.sizes.options.length === 1 ? (
                  <div className={`rounded-[18px] bg-[rgba(26,28,25,0.04)] text-[var(--on-surface)] ${compact ? 'px-4 py-3' : 'px-5 py-4'}`}>
                    <div className={`${compact ? 'text-[0.95rem]' : 'text-[1.05rem]'} font-bold`}>
                      {t('options.autoSelected')}: {displayChoice(customization.sizes.options[0])}{formatPriceDelta(customization.sizes.options[0].priceDelta)}
                    </div>
                  </div>
                ) : (
                  <div className={`grid md:grid-cols-2 ${compact ? 'gap-3' : 'gap-4'}`}>
                    {customization.sizes.options.map((option) => (
                      <ChoiceButton
                        key={option.id}
                        active={draft.sizeId === option.id}
                        label={`${displayChoice(option)}${formatPriceDelta(option.priceDelta)}`}
                        sublabel=""
                        compact={compact}
                        onClick={() => onChange({ ...draft, sizeId: option.id })}
                      />
                    ))}
                  </div>
                )}
              </section>
            ) : null}

            {customization?.soupBases ? (
              <section className={compact ? 'space-y-3' : 'space-y-4'}>
                <div className="flex items-center gap-3">
                  <h3 className={`${compact ? 'text-[1.1rem]' : 'text-[1.45rem]'} font-bold tracking-[-0.03em]`}>{t('options.soupBase')}</h3>
                  {customization.soupBases.required ? (
                    <span className="rounded-full bg-[var(--primary)] px-3 py-1 text-xs font-bold uppercase tracking-[0.08em] text-[var(--on-primary)]">
                      {t('common.required')}
                    </span>
                  ) : null}
                </div>
                <div className={`grid md:grid-cols-2 ${compact ? 'gap-3' : 'gap-4'}`}>
                  {customization.soupBases.options.map((option) => (
                    <ChoiceButton
                      key={option.id}
                      active={draft.soupBaseId === option.id}
                      label={`${displayChoice(option)}${option.priceDelta ? ` (+$${option.priceDelta.toFixed(2)})` : ''}`}
                      sublabel=""
                      compact={compact}
                      onClick={() => onChange({ ...draft, soupBaseId: option.id })}
                    />
                  ))}
                </div>
              </section>
            ) : null}

            {customization?.noodleTypes?.length ? (
              <section className={compact ? 'space-y-3' : 'space-y-4'}>
                <h3 className={`${compact ? 'text-[1.1rem]' : 'text-[1.45rem]'} font-bold tracking-[-0.03em]`}>{t('options.noodleType')}</h3>
                <div className={`grid md:grid-cols-5 ${compact ? 'gap-2.5' : 'gap-3'}`}>
                  {customization.noodleTypes.map((option) => (
                    <ChoiceButton
                      key={option.id}
                      active={draft.noodleTypeId === option.id}
                      label={displayChoice(option)}
                      sublabel={formatNoodleTypeDisplayLabel(option.labelZh, option.optionCode)}
                      compact={compact}
                      onClick={() => onChange({ ...draft, noodleTypeId: option.id })}
                    />
                  ))}
                </div>
              </section>
            ) : null}

            {customization?.spicyLevels?.length ? (
              <section className={compact ? 'space-y-3' : 'space-y-4'}>
                <h3 className={`${compact ? 'text-[1.1rem]' : 'text-[1.45rem]'} font-bold tracking-[-0.03em]`}>{t('options.spicy')}</h3>
                <div className={`grid md:grid-cols-4 ${compact ? 'gap-2.5' : 'gap-3'}`}>
                  {customization.spicyLevels.map((option) => (
                    <ChoiceButton
                      key={option.id}
                      active={draft.spicyLevelId === option.id}
                      label={displayChoice(option)}
                      sublabel=""
                      compact={compact}
                      onClick={() => onChange({ ...draft, spicyLevelId: option.id })}
                    />
                  ))}
                </div>
              </section>
            ) : null}

            {!templateMode && !showComboFirst ? comboSection : null}

            {(customization?.addOns?.length || customization?.removeOptions?.length) ? (
              <section className={`grid md:grid-cols-2 ${compact ? 'gap-4' : 'gap-6'}`}>
                {customization.addOns?.length ? (
                  <div className={`bg-[rgba(26,28,25,0.03)] ${compact ? 'space-y-2 rounded-[20px] p-4' : 'space-y-3 rounded-[24px] p-5'}`}>
                    <h3 className={`${compact ? 'text-[1.05rem]' : 'text-[1.35rem]'} font-bold tracking-[-0.03em]`}>{t('options.addon')}</h3>
                    {toggleAddOns.map((option) => {
                      const checked = (draft.addOnQuantities[option.id] ?? 0) > 0
                      return (
                        <CheckboxRow
                          key={option.id}
                          checked={checked}
                          label={displayChoice(option)}
                          sublabel=""
                          compact={compact}
                          onClick={() =>
                            onChange(selectAddOn(draft, option, checked ? 0 : 1, customization.removeOptions))
                          }
                        />
                      )
                    })}
                    {quantityAddOns.map((option) => {
                      const quantity = draft.addOnQuantities[option.id] ?? 0
                      return (
                        <AddOnQuantityRow
                          key={option.id}
                          label={displayChoice(option)}
                          sublabel=""
                          priceDelta={option.priceDelta}
                          quantity={quantity}
                          compact={compact}
                          onDecrement={() =>
                            onChange(selectAddOn(draft, option, Math.max(0, quantity - 1), customization.removeOptions))
                          }
                          onIncrement={() =>
                            onChange(selectAddOn(draft, option, quantity + 1, customization.removeOptions))
                          }
                        />
                      )
                    })}
                  </div>
                ) : null}

                {customization.removeOptions?.length ? (
                  <div className={`bg-[rgba(26,28,25,0.03)] ${compact ? 'space-y-2 rounded-[20px] p-4' : 'space-y-3 rounded-[24px] p-5'}`}>
                    <h3 className={`${compact ? 'text-[1.05rem]' : 'text-[1.35rem]'} font-bold tracking-[-0.03em]`}>{t('options.remove')}</h3>
                    {customization.removeOptions.map((option) => {
                      const checked = draft.removeIds.includes(option.id)
                      return (
                        <CheckboxRow
                          key={option.id}
                          checked={checked}
                          label={displayChoice(option)}
                          sublabel=""
                          compact={compact}
                          onClick={() =>
                            onChange(selectRemove(draft, option, customization.addOns))
                          }
                        />
                      )
                    })}
                  </div>
                ) : null}
              </section>
            ) : null}

            {isNoodleItem ? (
              <section className={`bg-[rgba(26,28,25,0.03)] ${compact ? 'space-y-2 rounded-[20px] p-4' : 'space-y-3 rounded-[24px] p-5'}`}>
                <div>
                  <h3 className={`${compact ? 'text-[1.05rem]' : 'text-[1.35rem]'} font-bold tracking-[-0.03em]`}>
                    {t('order.note')}
                  </h3>
                  <p className={`${compact ? 'mt-1 text-[0.78rem]' : 'mt-1.5 text-sm'} font-medium text-[var(--muted)]`}>
                    {t('options.noteHint')}
                  </p>
                </div>
                <textarea
                  value={draft.notes}
                  onChange={(event) => onChange({ ...draft, notes: event.target.value })}
                  placeholder={t('order.note')}
                  rows={compact ? 2 : 3}
                  className={`w-full resize-none rounded-[18px] border border-[rgba(26,28,25,0.08)] bg-white/85 px-4 py-3 font-medium text-[var(--on-surface)] outline-none transition focus:border-[rgba(97,0,0,0.38)] focus:ring-4 focus:ring-[rgba(97,0,0,0.10)] ${compact ? 'text-[0.92rem]' : 'text-base'}`}
                />
              </section>
            ) : null}
          </div>
        </div>

        <div className={`border-t border-[rgba(26,28,25,0.05)] ${compact ? 'px-5 py-4' : 'px-8 py-6'}`}>
          <div className={`flex flex-col ${compact ? 'gap-3 xl:flex-row xl:items-center xl:justify-between' : 'gap-4 xl:flex-row xl:items-center xl:justify-between'}`}>
            <div className={`flex items-center ${compact ? 'gap-5' : 'gap-8'}`}>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">{t('common.quantity')}</p>
                <div className={`mt-2 inline-flex items-center bg-[var(--surface-container-low)] p-1 ${compact ? 'rounded-[16px]' : 'rounded-[20px]'}`}>
                  <button
                    type="button"
                    className={`inline-flex items-center justify-center ${compact ? 'h-10 w-10 rounded-[12px] text-[1.45rem]' : 'h-12 w-12 rounded-[16px] text-2xl'}`}
                    onClick={() => onChange({ ...draft, quantity: Math.max(1, draft.quantity - 1) })}
                  >
                    −
                  </button>
                  <span className={`inline-flex items-center justify-center font-bold ${compact ? 'min-w-10 text-[1rem]' : 'min-w-12 text-lg'}`}>{draft.quantity}</span>
                  <button
                    type="button"
                    className={`inline-flex items-center justify-center bg-[var(--primary)] text-[var(--on-primary)] ${compact ? 'h-10 w-10 rounded-[12px] text-[1.45rem]' : 'h-12 w-12 rounded-[16px] text-2xl'}`}
                    onClick={() => onChange({ ...draft, quantity: draft.quantity + 1 })}
                  >
                    +
                  </button>
                </div>
              </div>

              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">{t('order.subtotal')}</p>
                <div className={`mt-2 font-extrabold tracking-[-0.05em] text-[var(--on-surface)] ${compact ? 'text-[2rem]' : 'text-[2.8rem]'}`}>
                  ${subtotal.toFixed(2)}
                </div>
              </div>
            </div>

            {(submitDisabled && submitDisabledReason) || selectionError ? (
              <div className="rounded-[16px] bg-[rgba(151,34,34,0.08)] px-4 py-3 text-[0.9rem] font-bold text-[rgb(116,22,22)]">
                {selectionError ?? submitDisabledReason}
              </div>
            ) : null}
            <Button
              className={compact ? 'min-h-12 rounded-[18px] px-6 text-[0.95rem]' : 'min-h-16 rounded-[24px] px-8 text-lg'}
              onClick={onSubmit}
              disabled={submitDisabled || Boolean(selectionError)}
            >
              {mode === 'add' ? t('order.addItem') : t('order.updateItem')}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  )
}
