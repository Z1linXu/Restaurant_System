import { act, create } from 'react-test-renderer'
import { describe, expect, it, vi } from 'vitest'
import { ComboConfigurationPanel } from './ComboConfigurationPanel'
import { fetchStoreComboConfiguration, updateStoreComboConfiguration, type StoreComboConfigurationRecord } from '../../services/ownerMenuOptionService'

vi.mock('../../services/ownerMenuOptionService', () => ({ fetchStoreComboConfiguration: vi.fn(), updateStoreComboConfiguration: vi.fn() }))
vi.mock('./ComboEggOverridesPanel', () => ({ ComboEggOverridesPanel: () => null }))

describe('Store Default Egg control', () => {
  it('saves a Store default through existing group configuration, retaining unrelated component mapping', async () => {
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
    const configuration: StoreComboConfigurationRecord = {
      store_id: 1, menu_revision: 4, groups: [{
        group_id: 1, group_code: 'COMBO_EGG', component_group: 'COMBO_EGG', name_zh: '蛋', name_en: 'Egg',
        selection_rule: 'EXACTLY_ONE', required: true, display_order: 1, default_component_code: 'combo_tea_egg',
        components: ['combo_tea_egg', 'combo_fried_egg'].map((code, index) => ({
          id: index + 1, group_id: 1, component_group: 'COMBO_EGG', component_code: code,
          name_zh: code, name_en: code, enabled: true, display_order: index, is_default: index === 0,
          business_behavior: 'NO_KITCHEN_TASK', linked_menu_item_id: null,
        })),
      }],
    }
    vi.mocked(fetchStoreComboConfiguration).mockResolvedValue(configuration)
    vi.mocked(updateStoreComboConfiguration).mockResolvedValue(configuration)
    let renderer: ReturnType<typeof create>
    await act(async () => { renderer = create(<ComboConfigurationPanel storeId={1} />) })
    const select = renderer!.root.findByProps({ 'aria-label': 'Store Default Egg' })
    expect(select.props.value).toBe('combo_tea_egg')
    expect(select.props.disabled).toBe(false)
    await act(async () => select.props.onChange({ target: { value: 'combo_fried_egg' } }))
    await act(async () => renderer!.root.findAllByType('button').find((button) => button.children.join('') === 'Save')!.props.onClick())
    const payload = vi.mocked(updateStoreComboConfiguration).mock.calls[0][0]
    expect(payload.groups?.[0].default_component_code).toBe('combo_fried_egg')
    expect(payload.groups?.[0].components.map((component) => component.is_default)).toEqual([false, true])
    expect(payload.groups?.[0].components.every((component) => component.business_behavior === 'NO_KITCHEN_TASK' && component.linked_menu_item_id === null)).toBe(true)
    expect(payload).not.toHaveProperty('combo_delta')
    await act(async () => renderer!.unmount())
    vi.unstubAllGlobals()
  })
})
