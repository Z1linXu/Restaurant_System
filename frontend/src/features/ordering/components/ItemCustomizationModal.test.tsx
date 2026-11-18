import { act, create } from 'react-test-renderer'
import { describe, expect, it, vi } from 'vitest'
import type { ItemCustomizationDraft, MenuItem } from '../../../types/ordering'
import { buildDefaultDraft } from '../../../hooks/useOrderSessions'
import { buildLocalLineItem } from '../../../hooks/useDraftOrder'
import { calculateDraftLineSubtotal } from '../../../utils/orderPricing'
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

it('defaults broth to the large portion and retains spice and chili-on-side when changing size and quantity', async () => {
  const broth: MenuItem = {
    id: '30', categoryId: '2', categoryCode: 'CATEGORY_ADD_ON',
    nameEn: 'Beef Broth', nameZh: '牛肉汤', descriptionEn: '', descriptionZh: '', price: 3,
    customization: {
      sizes: { required: true, options: [
        { id: '31', labelEn: 'Large', labelZh: '大份', optionGroup: 'SIZE', optionCode: 'size_large', priceDelta: 0, portionPrice: 3 },
        { id: '32', labelEn: 'Small', labelZh: '小份', optionGroup: 'SIZE', optionCode: 'size_small', priceDelta: -1, portionPrice: 2 },
      ] },
      spicyLevels: [
        { id: '33', labelEn: 'No Spice', labelZh: '不辣', optionGroup: 'SPICY_LEVEL', optionCode: 'spicy_none' },
        { id: '34', labelEn: 'Regular Spice', labelZh: '正常辣', optionGroup: 'SPICY_LEVEL', optionCode: 'spicy_regular' },
      ],
      removeOptions: [{ id: '35', labelEn: 'Chili on the Side', labelZh: '辣单放', optionGroup: 'REMOVE', optionCode: 'chili_on_side' }],
    },
  }
  let draft = buildDefaultDraft(broth)
  expect(draft).toMatchObject({ sizeId: '31', spicyLevelId: '33', quantity: 1 })
  expect(calculateDraftLineSubtotal(broth, draft)).toBe(3)
  let view: ReturnType<typeof create>
  const render = () => <ItemCustomizationModal item={broth} draft={draft} mode="add"
    subtotal={calculateDraftLineSubtotal(broth, draft)} onClose={vi.fn()} onSubmit={vi.fn()}
    onChange={(next) => { draft = next }} />
  await act(async () => { view = create(render()) })
  for (const label of ['小份 · $2.00', '正常辣', '辣单放', '+', '+']) {
    const button = view!.root.findAllByType('button')
      .find((entry) => entry.children.includes(label) || (['div', 'span'] as const).some((tag) =>
        entry.findAllByType(tag).some((node) => node.children.join('').includes(label))))
    expect(button, label).toBeDefined()
    await act(async () => button!.props.onClick())
    await act(async () => view!.update(render()))
  }
  expect(draft).toMatchObject({ sizeId: '32', spicyLevelId: '34', removeIds: ['35'], quantity: 3 })
  const line = buildLocalLineItem(broth, draft)
  expect(line.lineSubtotal).toBe(6)
  expect(line.optionSnapshots?.map((option) => option.optionCode)).toEqual(['size_small', 'spicy_regular', 'chili_on_side'])
  await act(async () => view!.unmount())
})

describe('noodle type presentation labels', () => {
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
