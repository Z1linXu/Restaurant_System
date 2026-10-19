import { act, create } from 'react-test-renderer'
import { describe, expect, it, vi } from 'vitest'
import type { ItemCustomizationDraft, MenuItem } from '../../../types/ordering'
import { ItemCustomizationModal } from './ItemCustomizationModal'
import { formatNoodleTypeDisplayLabel } from './noodleTypePresentation'

vi.mock('../../../hooks/useIpadLandscape', () => ({ useIpadLandscape: () => false }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
vi.stubGlobal('document', { body: { style: { overflow: '', touchAction: '' } } })

const noodleTypes = [
  ['capillary', 'Capillary', '毛细', 'noodle_capillary', '毛细（1）'],
  ['thin', 'Thin', '细', 'noodle_thin', '细（2）'],
  ['sanxi', 'Sanxi', '三细', 'noodle_sanxi', '三细（3）'],
  ['erxi', 'Erxi', '二细', 'noodle_erxi', '二细（4）'],
  ['leek-leaf', 'Leek Leaf', '韭叶', 'noodle_leek_leaf', '韭叶（5）'],
  ['wide', 'Wide', '宽', 'noodle_wide', '宽（6）'],
  ['extra-wide', 'Extra Wide', '大宽', 'noodle_extra_wide', '大宽（7）'],
] as const

describe('noodle type presentation labels', () => {
  it('requires exactly two distinct modifier choices before enabling Add to Order', async () => {
    const item: MenuItem = {
      id: '1', categoryId: '1', nameEn: 'Mixed Grill', nameZh: '双拼饭', descriptionEn: '', descriptionZh: '', price: 19.99,
      customization: { modifierGroups: [{ groupCode: 'PROTEIN', labelZh: '蛋白选择', labelEn: 'Protein', minSelect: 2, maxSelect: 2, distinct: true, defaultOptionIds: [], options: ['Beef', 'Chicken', 'Pork', 'Shrimp'].map((label, index) => ({ id: String(index + 1), labelEn: label, labelZh: label, priceDelta: 0 })) }] },
    }
    let draft: ItemCustomizationDraft = { comboEnabled: false, comboSelections: {}, comboSideRemoveIds: [], addOnQuantities: {}, modifierSelections: {}, removeIds: [], quantity: 1, notes: '' }
    let view: ReturnType<typeof create>
    const render = () => <ItemCustomizationModal item={item} draft={draft} mode="add" subtotal={19.99} onClose={vi.fn()} onChange={(next) => { draft = next }} onSubmit={vi.fn()} />
    await act(async () => { view = create(render()) })
    const submit = () => view!.root.findAllByType('button').find((button) => button.findAllByType('span').some((span) => span.children.includes('Add to Order / 添加到订单')))!
    const select = (name: string) => view!.root.findAllByType('button').find((button) => button.findAllByType('div').some((node) => node.children.includes(name)))!
    expect(submit().props.disabled).toBe(true)
    for (const name of ['Beef', 'Shrimp']) {
      await act(async () => select(name).props.onClick())
      await act(async () => view!.update(render()))
    }
    expect(submit().props.disabled).toBe(false)
    expect(draft.modifierSelections?.PROTEIN).toEqual(['1', '4'])
    await act(async () => select('Chicken').props.onClick())
    expect(draft.modifierSelections?.PROTEIN).toEqual(['1', '4'])
    await act(async () => select('Beef').props.onClick())
    await act(async () => view!.update(render()))
    expect(submit().props.disabled).toBe(true)
    await act(async () => view!.unmount())
  })

  it('renders fixed Combo total and a configuration-driven free checkbox without a Size section', async () => {
    const item: MenuItem = {
      id: '1', categoryId: '1', nameEn: 'Dish', nameZh: '菜', nameFr: 'Plat', descriptionEn: '', descriptionZh: '', price: 16.99,
      customization: { addOns: [{ id: '1', labelEn: 'Extra Rice', labelZh: '加饭', priceDelta: 0, maxQuantity: 1 }], combo: { upcharge: 3, fixedPrice: 19.99, groups: [], eggs: [], sides: [], sideRemoveOptions: [] } },
    }
    const draft: ItemCustomizationDraft = { comboEnabled: false, comboSelections: {}, comboSideRemoveIds: [], addOnQuantities: {}, removeIds: [], quantity: 1, notes: '' }
    const onChange = vi.fn()
    let view: ReturnType<typeof create>
    await act(async () => { view = create(<ItemCustomizationModal item={item} draft={draft} mode="add" subtotal={16.99} onClose={vi.fn()} onChange={onChange} onSubmit={vi.fn()} />) })
    const text = JSON.stringify(view!.toJSON())
    expect(text).toContain('套餐总价 $19.99')
    expect(text).not.toContain('Size / 规格')
    expect(text).toContain('Plat')
    const rice = view!.root.findAllByType('button').find((button) => button.findAllByType('span').some((span) => span.children.includes('Extra Rice')))!
    await act(async () => rice.props.onClick())
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ addOnQuantities: { '1': 1 } }))
    await act(async () => view!.unmount())
  })

  it('preserves a restored manual egg when Combo is turned off and on after the default changes', async () => {
    const eggs = [
      { id: 'tea', labelEn: 'Tea Egg', labelZh: '卤蛋', optionCode: 'combo_tea_egg', optionGroup: 'COMBO_EGG' },
      { id: 'fried', labelEn: 'Fried Egg', labelZh: '煎蛋', optionCode: 'combo_fried_egg', optionGroup: 'COMBO_EGG' },
    ]
    const item: MenuItem = {
      id: '1', categoryId: '1', categoryCode: 'NOODLES', nameEn: 'Dish', nameZh: '菜', descriptionEn: '', descriptionZh: '', price: 10,
      customization: { combo: { upcharge: 5, eggs, sides: [], sideRemoveOptions: [], groups: [{
        groupCode: 'COMBO_EGG', labelEn: 'Egg', labelZh: '蛋', required: true, selectionRule: 'EXACTLY_ONE', defaultOptionId: 'tea', options: eggs,
      }] } },
    }
    let draft: ItemCustomizationDraft = { comboEnabled: false, comboEggId: 'fried', comboSelections: {}, comboSideRemoveIds: [], addOnQuantities: {}, removeIds: [], quantity: 1, notes: '' }
    const onChange = vi.fn((next: ItemCustomizationDraft) => { draft = next })
    let view: ReturnType<typeof create>
    const render = () => <ItemCustomizationModal item={item} draft={draft} mode="add" subtotal={15} onClose={vi.fn()} onChange={onChange} onSubmit={vi.fn()} />
    await act(async () => { view = create(render()) })
    const toggle = () => view!.root.findAllByType('button').find((button) => button.findAllByType('span').some((node) => node.children.includes('Make it a Combo / 设为套餐')))!
    for (const enabled of [true, false, true]) {
      await act(async () => toggle().props.onClick())
      expect(draft.comboEnabled).toBe(enabled)
      expect(draft.comboEggId).toBe('fried')
      expect(draft.comboSelections.COMBO_EGG).toBe('fried')
      await act(async () => view!.update(render()))
    }
    await act(async () => view!.unmount())
  })

  it.each(noodleTypes)('formats %s by stable option code', (_id, _en, zh, code, expected) => {
    expect(formatNoodleTypeDisplayLabel(zh, code)).toBe(expected)
  })

  it.each([undefined, null, '', 'legacy_thin', 'noodle_future'])('keeps the original label for unmapped code %s', (code) => {
    expect(formatNoodleTypeDisplayLabel('原始中文', code)).toBe('原始中文')
  })

  it('keeps English labels and selection identity unchanged', async () => {
    const item: MenuItem = {
      id: 'item-1',
      categoryId: 'category-1',
      categoryCode: 'SOUP_NOODLE',
      nameEn: 'Beef Noodle',
      nameZh: '牛肉面',
      descriptionEn: '',
      descriptionZh: '',
      price: 16,
      customization: {
        noodleTypes: noodleTypes.map(([id, labelEn, labelZh, optionCode]) => ({
          id,
          labelEn,
          labelZh,
          optionCode,
          optionType: 'noodle_type',
          optionGroup: 'NOODLE_TYPE',
        })),
      },
    }
    const draft: ItemCustomizationDraft = {
      noodleTypeId: 'capillary',
      comboEnabled: false,
      comboSelections: {},
      comboSideRemoveIds: [],
      addOnQuantities: {},
      removeIds: [],
      quantity: 1,
      notes: '',
    }
    const onChange = vi.fn()
    let view: ReturnType<typeof create>

    await act(async () => {
      view = create(
        <ItemCustomizationModal
          item={item}
          draft={draft}
          mode="add"
          subtotal={16}
          onClose={vi.fn()}
          onChange={onChange}
          onSubmit={vi.fn()}
        />,
      )
    })

    const text = view!.root.findAllByType('div').map((node) => node.children.join(' '))
    noodleTypes.forEach((noodleType) => {
      const english = noodleType[1]
      const displayZh = noodleType[4]
      expect(text).toContain(english)
      expect(text).toContain(displayZh)
    })

    await act(async () => {
      view!.root.findAllByType('button').find((button) => button.findAllByType('div').some((node) => node.children.includes('Thin')))!.props.onClick()
    })
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ noodleTypeId: 'thin' }))
  })
})
