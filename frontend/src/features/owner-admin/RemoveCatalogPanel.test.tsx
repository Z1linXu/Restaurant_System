import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { RemoveCatalogPanel } from './RemoveCatalogPanel'
import { createStoreRemove, fetchRemoveApplicability, fetchStoreRemoves, reorderStoreRemoves, updateRemoveApplicability, updateStoreRemove, type RemoveCatalog } from '../../services/ownerRemoveService'

vi.mock('../../services/ownerRemoveService', () => ({ createStoreRemove: vi.fn(), fetchRemoveApplicability: vi.fn(), fetchStoreRemoves: vi.fn(), reorderStoreRemoves: vi.fn(), updateRemoveApplicability: vi.fn(), updateStoreRemove: vi.fn() }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
const shared = { id: 1, store_id: 18, code: 'remove_cilantro', name_zh: '走香菜', name_en: 'No cilantro', active: true, sort_order: 10, item_ids: [101, 102] }
const catalog: RemoveCatalog = { removes: [shared], conflicts: [{ code: 'remove_carrot', option_ids: [4, 5], names_zh: ['走胡萝卜', '走胡萝卜片'], names_en: [], reason: 'REMOVE_VALUES_AMBIGUOUS' }], legacy_options: [{ option_id: 4, item_id: 104, code: 'remove_carrot', name_zh: '走胡萝卜片', name_en: '', active: true }] }
let view: ReactTestRenderer | undefined
const categories = [{ id: 11, label: '面 / Noodles' }]
const button = (label: string) => view!.root.findAllByType('button').find((node) => node.children.join('') === label)!
async function mount(storeId = 18) { await act(async () => { view = create(<RemoveCatalogPanel storeId={storeId} categories={categories} />) }) }
beforeEach(() => { vi.resetAllMocks(); vi.mocked(fetchStoreRemoves).mockResolvedValue(catalog) })
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })

it('edits one shared record, keeps code immutable, shows legacy conflicts and remains usable after reload', async () => {
  await mount()
  expect(JSON.stringify(view!.toJSON())).toContain('走胡萝卜片')
  await act(async () => view!.root.findByProps({ 'aria-label': 'Edit Remove remove_cilantro' }).props.onClick())
  expect(view!.root.findByProps({ 'aria-label': 'Remove code' }).props.readOnly).toBe(true)
  await act(async () => view!.root.findByProps({ 'aria-label': 'Remove Chinese name' }).props.onChange({ target: { value: '走芫荽' } }))
  vi.mocked(updateStoreRemove).mockResolvedValue({ ...shared, name_zh: '走芫荽' })
  vi.mocked(fetchStoreRemoves).mockResolvedValue({ ...catalog, removes: [{ ...shared, name_zh: '走芫荽' }] })
  await act(async () => view!.root.findByType('form').props.onSubmit({ preventDefault: vi.fn() }))
  expect(updateStoreRemove).toHaveBeenCalledWith(1, 18, { name_zh: '走芫荽', name_en: 'No cilantro', active: true, sort_order: 10 })
  expect(JSON.stringify(view!.toJSON())).toContain('走芫荽')
  expect(button('Add Remove / 添加减料').props.disabled).toBe(false)
})

it('creates a free unassigned Remove, without price input or implicit item assignments', async () => {
  await mount()
  await act(async () => button('Add Remove / 添加减料').props.onClick())
  for (const [label, value] of [['Remove code', 'remove_onion'], ['Remove Chinese name', '走洋葱']]) {
    await act(async () => view!.root.findByProps({ 'aria-label': label }).props.onChange({ target: { value } }))
  }
  await act(async () => view!.root.findByType('form').props.onSubmit({ preventDefault: vi.fn() }))
  expect(createStoreRemove).toHaveBeenCalledWith({ store_id: 18, code: 'remove_onion', name_zh: '走洋葱', name_en: '', active: true, sort_order: 20 })
  expect(updateRemoveApplicability).not.toHaveBeenCalled()
})

it('saves category inheritance plus explicit item denial in one shared applicability request', async () => {
  vi.mocked(fetchRemoveApplicability).mockResolvedValue({ remove_id: 1, store_id: 18, category_ids: [], items: [101, 102].map((id) => ({ item_id: id, name_zh: `商品${id}`, name_en: '', category_id: 11, mode: 'INHERIT', effective: false })) })
  await mount()
  await act(async () => view!.root.findByProps({ 'aria-label': 'Eligible items remove_cilantro' }).props.onClick())
  await act(async () => view!.root.findByProps({ 'aria-label': 'Remove category 11' }).props.onChange({ target: { checked: true } }))
  await act(async () => view!.root.findByProps({ 'aria-label': 'Remove item 102' }).props.onChange({ target: { value: 'DENY' } }))
  await act(async () => button('Save eligibility / 保存适用范围').props.onClick())
  expect(updateRemoveApplicability).toHaveBeenCalledWith(1, 18, expect.objectContaining({ category_ids: [11], items: expect.arrayContaining([expect.objectContaining({ item_id: 102, mode: 'DENY' })]) }))
})

it('does not display a late previous-Store response after switching Store', async () => {
  let finish!: (value: RemoveCatalog) => void
  vi.mocked(fetchStoreRemoves).mockImplementation((id) => id === 18 ? new Promise((resolve) => { finish = resolve }) : Promise.resolve({ removes: [], conflicts: [] }))
  await mount()
  await act(async () => view!.update(<RemoveCatalogPanel storeId={27} categories={[]} />))
  await act(async () => finish(catalog))
  expect(JSON.stringify(view!.toJSON())).not.toContain('走香菜')
  expect(fetchStoreRemoves).toHaveBeenLastCalledWith(27)
})

it('keeps failed edits open and reports the error without publishing a false success', async () => {
  vi.mocked(updateStoreRemove).mockRejectedValue(new Error('REMOVE_SCOPE_MISMATCH'))
  await mount()
  await act(async () => view!.root.findByProps({ 'aria-label': 'Edit Remove remove_cilantro' }).props.onClick())
  await act(async () => view!.root.findByType('form').props.onSubmit({ preventDefault: vi.fn() }))
  expect(view!.root.findByProps({ role: 'alert' }).children.join('')).toContain('REMOVE_SCOPE_MISMATCH')
  expect(view!.root.findAllByType('form')).toHaveLength(1)
  expect(reorderStoreRemoves).not.toHaveBeenCalled()
})

it('summarizes repeated legacy rows inside one collapsed diagnostic without duplicating shared maintenance', async () => {
  vi.mocked(fetchStoreRemoves).mockResolvedValue({ ...catalog, legacy_options: [
    ...catalog.legacy_options!,
    { ...catalog.legacy_options![0], option_id: 5, item_id: 105 },
  ] })
  await mount()
  const details = view!.root.findByProps({ 'aria-label': 'Legacy Remove diagnostics' })
  expect(details.props.open).toBeUndefined()
  const summary = details.findByType('summary').children.join('')
  expect(summary).toContain('2')
  expect(details.findAllByType('ul')[0].findAllByType('li')).toHaveLength(1)
  expect(view!.root.findAllByProps({ 'aria-label': 'Edit Remove remove_cilantro' })).toHaveLength(1)
})
