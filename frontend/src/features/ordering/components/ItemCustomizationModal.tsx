import { useEffect } from 'react'
import { Button } from '../../../components/ui/Button'
import { Card } from '../../../components/ui/Card'
import { useIpadLandscape } from '../../../hooks/useIpadLandscape'
import type { ItemCustomizationDraft, MenuItem } from '../../../types/ordering'

function isToggleAddOn(labelZh: string, priceDelta?: number) {
  return (priceDelta ?? 0) === 0 && (labelZh === '加香菜' || labelZh === '加葱')
}

const NOODLE_CATEGORY_CODES = new Set(['SOUP_NOODLE', 'DRY_NOODLE', 'FRIED_NOODLE', 'NOODLE', 'NOODLES'])

function normalizeStableCode(value?: string | null) {
  return (value ?? '').trim().toUpperCase()
}

function isNoodleMenuItem(item: MenuItem) {
  const categoryCode = normalizeStableCode(item.categoryCode)
  if (NOODLE_CATEGORY_CODES.has(categoryCode)) {
    return true
  }
  // Structural fallback for older local data that may not expose category codes.
  return Boolean(item.customization?.noodleTypes?.length || item.customization?.soupBases)
}

interface ItemCustomizationModalProps {
  item: MenuItem
  draft: ItemCustomizationDraft
  mode: 'add' | 'edit'
  subtotal: number
  onClose: () => void
  onChange: (nextDraft: ItemCustomizationDraft) => void
  onSubmit: () => void
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
      <div className={`${compact ? 'mt-0.5 text-[0.76rem]' : 'mt-1 text-sm'} ${active ? 'text-[rgba(255,249,247,0.86)]' : 'text-[var(--muted)]'}`}>{sublabel}</div>
    </button>
  )
}

function CheckboxRow({
  checked,
  label,
  sublabel,
  onClick,
  compact = false,
}: {
  checked: boolean
  label: string
  sublabel: string
  onClick: () => void
  compact?: boolean
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex w-full items-center gap-3 rounded-[18px] text-left ${compact ? 'px-1 py-1.5' : 'px-2 py-2'}`}
    >
      <span
        className={`inline-flex items-center justify-center rounded-[10px] border ${
          checked ? 'border-[var(--primary)] bg-[var(--primary)] text-[var(--on-primary)]' : 'border-[rgba(97,0,0,0.18)] bg-white'
        } ${compact ? 'h-6 w-6 text-[0.8rem]' : 'h-7 w-7'}`}
      >
        {checked ? '✓' : ''}
      </span>
      <span className={`${compact ? 'text-[0.9rem]' : 'text-base'} font-medium text-[var(--on-surface)]`}>
        {label} / {sublabel}
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
  compact = false,
}: {
  label: string
  sublabel: string
  quantity: number
  priceDelta?: number
  onDecrement: () => void
  onIncrement: () => void
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
}: ItemCustomizationModalProps) {
  const customization = item.customization
  const compact = useIpadLandscape()
  const toggleAddOns = customization?.addOns?.filter((option) => isToggleAddOn(option.labelZh, option.priceDelta)) ?? []
  const quantityAddOns = customization?.addOns?.filter((option) => !isToggleAddOn(option.labelZh, option.priceDelta)) ?? []
  const comboConfig = customization?.combo
  const showComboFirst = isNoodleMenuItem(item) && Boolean(comboConfig)
  const comboSection = comboConfig ? (
    <section className={`bg-[rgba(26,28,25,0.03)] ${compact ? 'space-y-3 rounded-[20px] p-4' : 'space-y-4 rounded-[28px] p-6'}`}>
      {(() => {
        const selectedComboSideId = draft.comboSideId ?? comboConfig.sides[0]?.id
        const sideRemoveOptions = comboConfig.sideRemoveOptions.filter(
          (option) => option.parentOptionId === selectedComboSideId,
        )
        return (
          <>
            <div className="flex items-center justify-between gap-4">
              <button
                type="button"
                onClick={() =>
                  onChange({
                    ...draft,
                    comboEnabled: !draft.comboEnabled,
                    comboEggId: !draft.comboEnabled ? draft.comboEggId ?? comboConfig.eggs[0]?.id : draft.comboEggId,
                    comboSideId: !draft.comboEnabled ? draft.comboSideId ?? comboConfig.sides[0]?.id : draft.comboSideId,
                    comboSideRemoveIds: !draft.comboEnabled ? draft.comboSideRemoveIds : [],
                  })
                }
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
                  Make it a Combo / 设为套餐
                </span>
              </button>
              <span className={`${compact ? 'text-[1rem]' : 'text-[1.35rem]'} font-bold text-[var(--secondary)]`}>
                +${comboConfig.upcharge.toFixed(2)}
              </span>
            </div>

            {draft.comboEnabled ? (
              <div className={compact ? 'space-y-3' : 'space-y-4'}>
                <div className="space-y-2">
                  <span className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
                    Egg / 鸡蛋
                  </span>
                  <div className={`grid md:grid-cols-2 ${compact ? 'gap-2.5' : 'gap-3'}`}>
                    {comboConfig.eggs.map((option) => (
                      <ChoiceButton
                        key={option.id}
                        active={(draft.comboEggId ?? comboConfig.eggs[0]?.id) === option.id}
                        label={option.labelEn}
                        sublabel={option.labelZh}
                        compact={compact}
                        onClick={() => onChange({ ...draft, comboEggId: option.id })}
                      />
                    ))}
                  </div>
                </div>

                <div className="space-y-2">
                  <span className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
                    Side Dish / 配菜
                  </span>
                  <div className={`grid md:grid-cols-3 ${compact ? 'gap-2.5' : 'gap-3'}`}>
                    {comboConfig.sides.map((option) => (
                      <ChoiceButton
                        key={option.id}
                        active={selectedComboSideId === option.id}
                        label={option.labelEn}
                        sublabel={option.labelZh}
                        compact={compact}
                        onClick={() =>
                          onChange({
                            ...draft,
                            comboSideId: option.id,
                            comboSideRemoveIds: [],
                          })
                        }
                      />
                    ))}
                  </div>
                </div>

                {sideRemoveOptions.length ? (
                  <div className="space-y-2 rounded-[18px] bg-[rgba(255,255,255,0.62)] p-3">
                    <span className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
                      Side Requests / 配菜需求
                    </span>
                    {sideRemoveOptions.map((option) => {
                      const checked = draft.comboSideRemoveIds.includes(option.id)
                      return (
                        <CheckboxRow
                          key={option.id}
                          checked={checked}
                          label={option.labelEn}
                          sublabel={option.labelZh}
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
          aria-label="Close customization modal"
          onClick={onClose}
          className={`absolute z-20 inline-flex items-center justify-center rounded-full bg-white/95 font-black leading-none text-[rgba(83,58,50,0.78)] shadow-[0_14px_30px_rgba(26,28,25,0.16)] transition hover:bg-white hover:text-[var(--primary)] focus:outline-none focus:ring-4 focus:ring-[rgba(97,0,0,0.16)] ${compact ? 'right-3 top-3 h-12 w-12 text-[2rem]' : 'right-5 top-5 h-14 w-14 text-[2.35rem]'}`}
        >
          ×
        </button>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain [scrollbar-gutter:stable]">
          <div className={`border-b border-[rgba(26,28,25,0.05)] ${compact ? 'px-5 py-4' : 'px-8 py-7'}`}>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-[var(--primary)]">Noodle Craft</p>
            <div className={`${compact ? 'mt-2.5' : 'mt-3'} flex items-start justify-between gap-6`}>
              <div>
                <h2 className={`${compact ? 'text-[1.8rem]' : 'text-[2.35rem]'} font-extrabold tracking-[-0.05em] text-[var(--on-surface)]`}>
                  {item.nameEn}
                </h2>
                <p className={`mt-1 ${compact ? 'text-[1.45rem]' : 'text-[2rem]'} font-bold text-[rgba(53,43,38,0.9)]`}>{item.nameZh}</p>
              </div>
              <div className={`${compact ? 'text-[1.8rem]' : 'text-[2.2rem]'} font-extrabold tracking-[-0.04em] text-[var(--primary)]`}>
                ${item.price.toFixed(2)}
              </div>
            </div>
          </div>

          <div className={`${compact ? 'space-y-4 px-5 py-4' : 'space-y-6 px-8 py-7'}`}>
            {showComboFirst ? comboSection : null}

            {customization?.sizes ? (
              <section className={compact ? 'space-y-3' : 'space-y-4'}>
                <div className="flex items-center gap-3">
                  <h3 className={`${compact ? 'text-[1.1rem]' : 'text-[1.45rem]'} font-bold tracking-[-0.03em]`}>Size / 规格</h3>
                  {customization.sizes.required ? (
                    <span className="rounded-full bg-[var(--primary)] px-3 py-1 text-xs font-bold uppercase tracking-[0.08em] text-[var(--on-primary)]">
                      Required
                    </span>
                  ) : null}
                </div>
                <div className={`grid md:grid-cols-2 ${compact ? 'gap-3' : 'gap-4'}`}>
                  {customization.sizes.options.map((option) => (
                    <ChoiceButton
                      key={option.id}
                      active={draft.sizeId === option.id}
                      label={`${option.labelEn}${option.priceDelta ? ` (+$${option.priceDelta.toFixed(2)})` : ''}`}
                      sublabel={`${option.labelZh}${option.priceDelta ? ` (+$${option.priceDelta.toFixed(2)})` : ''}`}
                      compact={compact}
                      onClick={() => onChange({ ...draft, sizeId: option.id })}
                    />
                  ))}
                </div>
              </section>
            ) : null}

            {customization?.soupBases ? (
              <section className={compact ? 'space-y-3' : 'space-y-4'}>
                <div className="flex items-center gap-3">
                  <h3 className={`${compact ? 'text-[1.1rem]' : 'text-[1.45rem]'} font-bold tracking-[-0.03em]`}>Soup Base / 汤底</h3>
                  {customization.soupBases.required ? (
                    <span className="rounded-full bg-[var(--primary)] px-3 py-1 text-xs font-bold uppercase tracking-[0.08em] text-[var(--on-primary)]">
                      Required
                    </span>
                  ) : null}
                </div>
                <div className={`grid md:grid-cols-2 ${compact ? 'gap-3' : 'gap-4'}`}>
                  {customization.soupBases.options.map((option) => (
                    <ChoiceButton
                      key={option.id}
                      active={draft.soupBaseId === option.id}
                      label={`${option.labelEn}${option.priceDelta ? ` (+$${option.priceDelta.toFixed(2)})` : ''}`}
                      sublabel={`${option.labelZh}${option.priceDelta ? ` (+$${option.priceDelta.toFixed(2)})` : ''}`}
                      compact={compact}
                      onClick={() => onChange({ ...draft, soupBaseId: option.id })}
                    />
                  ))}
                </div>
              </section>
            ) : null}

            {customization?.noodleTypes?.length ? (
              <section className={compact ? 'space-y-3' : 'space-y-4'}>
                <h3 className={`${compact ? 'text-[1.1rem]' : 'text-[1.45rem]'} font-bold tracking-[-0.03em]`}>Noodle Type / 面型</h3>
                <div className={`grid md:grid-cols-5 ${compact ? 'gap-2.5' : 'gap-3'}`}>
                  {customization.noodleTypes.map((option) => (
                    <ChoiceButton
                      key={option.id}
                      active={draft.noodleTypeId === option.id}
                      label={option.labelEn}
                      sublabel={option.labelZh}
                      compact={compact}
                      onClick={() => onChange({ ...draft, noodleTypeId: option.id })}
                    />
                  ))}
                </div>
              </section>
            ) : null}

            {customization?.spicyLevels?.length ? (
              <section className={compact ? 'space-y-3' : 'space-y-4'}>
                <h3 className={`${compact ? 'text-[1.1rem]' : 'text-[1.45rem]'} font-bold tracking-[-0.03em]`}>Spicy Level / 辣度</h3>
                <div className={`grid md:grid-cols-4 ${compact ? 'gap-2.5' : 'gap-3'}`}>
                  {customization.spicyLevels.map((option) => (
                    <ChoiceButton
                      key={option.id}
                      active={draft.spicyLevelId === option.id}
                      label={option.labelEn}
                      sublabel={option.labelZh}
                      compact={compact}
                      onClick={() => onChange({ ...draft, spicyLevelId: option.id })}
                    />
                  ))}
                </div>
              </section>
            ) : null}

            {!showComboFirst ? comboSection : null}

            {(customization?.addOns?.length || customization?.removeOptions?.length) ? (
              <section className={`grid md:grid-cols-2 ${compact ? 'gap-4' : 'gap-6'}`}>
                {customization.addOns?.length ? (
                  <div className={`bg-[rgba(26,28,25,0.03)] ${compact ? 'space-y-2 rounded-[20px] p-4' : 'space-y-3 rounded-[24px] p-5'}`}>
                    <h3 className={`${compact ? 'text-[1.05rem]' : 'text-[1.35rem]'} font-bold tracking-[-0.03em]`}>Add-ons / 加料</h3>
                    {toggleAddOns.map((option) => {
                      const checked = (draft.addOnQuantities[option.id] ?? 0) > 0
                      return (
                        <CheckboxRow
                          key={option.id}
                          checked={checked}
                          label={option.labelEn}
                          sublabel={option.labelZh}
                          compact={compact}
                          onClick={() =>
                            onChange({
                              ...draft,
                              addOnQuantities: {
                                ...draft.addOnQuantities,
                                [option.id]: checked ? 0 : 1,
                              },
                            })
                          }
                        />
                      )
                    })}
                    {quantityAddOns.map((option) => {
                      const quantity = draft.addOnQuantities[option.id] ?? 0
                      return (
                        <AddOnQuantityRow
                          key={option.id}
                          label={option.labelEn}
                          sublabel={option.labelZh}
                          priceDelta={option.priceDelta}
                          quantity={quantity}
                          compact={compact}
                          onDecrement={() =>
                            onChange({
                              ...draft,
                              addOnQuantities: {
                                ...draft.addOnQuantities,
                                [option.id]: Math.max(0, quantity - 1),
                              },
                            })
                          }
                          onIncrement={() =>
                            onChange({
                              ...draft,
                              addOnQuantities: {
                                ...draft.addOnQuantities,
                                [option.id]: quantity + 1,
                              },
                            })
                          }
                        />
                      )
                    })}
                  </div>
                ) : null}

                {customization.removeOptions?.length ? (
                  <div className={`bg-[rgba(26,28,25,0.03)] ${compact ? 'space-y-2 rounded-[20px] p-4' : 'space-y-3 rounded-[24px] p-5'}`}>
                    <h3 className={`${compact ? 'text-[1.05rem]' : 'text-[1.35rem]'} font-bold tracking-[-0.03em]`}>Remove / 免除</h3>
                    {customization.removeOptions.map((option) => {
                      const checked = draft.removeIds.includes(option.id)
                      return (
                        <CheckboxRow
                          key={option.id}
                          checked={checked}
                          label={option.labelEn}
                          sublabel={option.labelZh}
                          compact={compact}
                          onClick={() =>
                            onChange({
                              ...draft,
                              removeIds: checked
                                ? draft.removeIds.filter((id) => id !== option.id)
                                : [...draft.removeIds, option.id],
                            })
                          }
                        />
                      )
                    })}
                  </div>
                ) : null}
              </section>
            ) : null}
          </div>
        </div>

        <div className={`border-t border-[rgba(26,28,25,0.05)] ${compact ? 'px-5 py-4' : 'px-8 py-6'}`}>
          <div className={`flex flex-col ${compact ? 'gap-3 xl:flex-row xl:items-center xl:justify-between' : 'gap-4 xl:flex-row xl:items-center xl:justify-between'}`}>
            <div className={`flex items-center ${compact ? 'gap-5' : 'gap-8'}`}>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">Quantity / 数量</p>
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
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">Subtotal</p>
                <div className={`mt-2 font-extrabold tracking-[-0.05em] text-[var(--on-surface)] ${compact ? 'text-[2rem]' : 'text-[2.8rem]'}`}>
                  ${subtotal.toFixed(2)}
                </div>
              </div>
            </div>

            <Button className={compact ? 'min-h-12 rounded-[18px] px-6 text-[0.95rem]' : 'min-h-16 rounded-[24px] px-8 text-lg'} onClick={onSubmit}>
              {mode === 'add' ? 'Add to Order / 添加到订单' : 'Update Item / 更新菜品'}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  )
}
