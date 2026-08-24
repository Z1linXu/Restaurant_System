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
