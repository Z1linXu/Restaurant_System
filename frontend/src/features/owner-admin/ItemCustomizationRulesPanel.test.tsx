import { act, create } from 'react-test-renderer'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ItemCustomizationRulesPanel } from './ItemCustomizationRulesPanel'
import { updateItemCustomization } from '../../services/itemCustomizationService'
import type { ItemCustomizationConfiguration } from '../../types/ordering'

vi.mock('../../services/itemCustomizationService', () => ({ updateItemCustomization: vi.fn() }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)

describe('item customization settings', () => {
  afterEach(() => vi.clearAllMocks())

  it('saves a configured noodle default while preserving Combo and free quantity settings', async () => {
    const configuration: ItemCustomizationConfiguration = {
      schema_version: 1,
      option_groups: [{ option_group: 'NOODLE_TYPE', min_select: 1, max_select: 1, distinct: true, default_option_codes: ['thin'] }],
      addon_max_quantities: { extra_noodles: 1 }, combo: { fixed_price: 19.99, group_codes: ['APPETIZER'] },
    }
    const options = ['thin', 'thick', 'udon'].map((code, index) => ({ id: index + 1, menu_item_id: 10, name_zh: code, name_en: code, option_code: code, option_group: 'NOODLE_TYPE', option_type: 'noodle_type', parent_option_id: null, sort_order: index, price_delta: 0, is_active: true }))
    vi.mocked(updateItemCustomization).mockImplementation(async (_id, value) => value)
    const onSaved = vi.fn()
    let view: ReturnType<typeof create>
    await act(async () => { view = create(<ItemCustomizationRulesPanel itemId={10} configuration={configuration} options={options} onSaved={onSaved} />) })
    const udon = view!.root.findAllByType('input').find((node) => node.props.type === 'radio' && node.parent?.children.includes('udon'))!
    await act(async () => udon.props.onChange({ target: { checked: true } }))
    const save = view!.root.findAllByType('button').find((node) => node.children.includes('保存选项设置'))!
    await act(async () => save.props.onClick())
    expect(updateItemCustomization).toHaveBeenCalledWith(10, {
      ...configuration, option_groups: [{ ...configuration.option_groups[0], default_option_codes: ['udon'] }],
    })
    expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ addon_max_quantities: { extra_noodles: 1 }, combo: configuration.combo }))
    await act(async () => view!.unmount())
  })
})
