import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { DiningTablesManagementPage } from './DiningTablesManagementPage'
import { deleteDiningTable, fetchPlatformOverview, savePlatformEntity, type DiningTableRecord, type PlatformAdminOverview } from '../../services/platformAdminService'
import { notifyDiningTablesChanged } from '../../services/frontdeskConfigService'

vi.mock('../../services/platformAdminService', () => ({ fetchPlatformOverview: vi.fn(), savePlatformEntity: vi.fn(), deleteDiningTable: vi.fn() }))
vi.mock('../../services/frontdeskConfigService', () => ({ notifyDiningTablesChanged: vi.fn() }))
vi.mock('../store/useStoreContext', () => ({ useCurrentStore: () => ({ storeId: 1 }) }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
let view: ReactTestRenderer | undefined
const table = (storeId = 1): DiningTableRecord => ({ id: storeId, store_id: storeId, table_code: `T${storeId}`, table_name: '1', area_name: 'Main', table_config: 'split_supported', capacity: 4, supports_split: true, sort_order: 1, is_active: true, split_label_mode: 'LEFT_RIGHT' })
const overview = (tables: DiningTableRecord[]) => ({ stores: [{ id: 1, name: 'Saint-Denis' }, { id: 2, name: 'CHINATOWN' }], dining_tables: tables }) as unknown as PlatformAdminOverview
const button = (text: string) => view!.root.findAllByType('button').find((node) => node.children.join('') === text)!
const row = (code: string) => view!.root.findAllByType('button').find((node) => node.findAllByType('span').some((span) => span.children.join('') === code))!
const field = (label: string) => view!.root.findByProps({ 'aria-label': label })
beforeEach(() => { vi.resetAllMocks(); vi.stubGlobal('window', { confirm: vi.fn(() => true) }); vi.mocked(fetchPlatformOverview).mockResolvedValue(overview([table()])) })
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })

it('saves a table-specific mode, reloads it, and leaves other table properties unchanged', async () => {
  await act(async () => { view = create(<DiningTablesManagementPage />) })
  await act(async () => row('T1').props.onClick())
  expect(field('Split Label Mode').props.value).toBe('LEFT_RIGHT')
  expect(view!.root.findAllByType('span').some((node) => node.children.join('') === '1-左 / 1-右')).toBe(true)
  await act(async () => field('Split Label Mode').props.onChange({ target: { value: 'INSIDE_OUTSIDE' } }))
  expect(view!.root.findAllByType('span').some((node) => node.children.join('') === '1里 / 1外')).toBe(true)
  const changed = { ...table(), split_label_mode: 'INSIDE_OUTSIDE' as const }
  vi.mocked(savePlatformEntity).mockResolvedValue(changed as unknown as Record<string, unknown>)
  vi.mocked(fetchPlatformOverview).mockResolvedValue(overview([changed]))
  await act(async () => button('Save Table').props.onClick())
  expect(savePlatformEntity).toHaveBeenCalledWith('dining-tables', changed, 1)
  await act(async () => row('T1').props.onClick())
  expect(field('Split Label Mode').props.value).toBe('INSIDE_OUTSIDE')
  expect(JSON.stringify(view!.toJSON())).not.toContain('verification failed')
})

it('defaults a new table to LEFT_RIGHT and preserves the selected mode on creation', async () => {
  await act(async () => { view = create(<DiningTablesManagementPage />) })
  await act(async () => button('Add Table').props.onClick())
  expect(field('Split Label Mode').props.value).toBe('LEFT_RIGHT')
  expect(field('Split Label Mode').props.disabled).toBe(true)
  await act(async () => field('Table Code').props.onChange({ target: { value: 'T3' } }))
  await act(async () => field('Table Name').props.onChange({ target: { value: '3' } }))
  await act(async () => field('Table Config').props.onChange({ target: { value: 'split_supported' } }))
  await act(async () => field('Split Label Mode').props.onChange({ target: { value: 'INSIDE_OUTSIDE' } }))
  const created = { ...table(), id: 3, table_code: 'T3', table_name: '3', area_name: 'Main Hall', split_label_mode: 'INSIDE_OUTSIDE' as const }
  vi.mocked(savePlatformEntity).mockResolvedValue(created as unknown as Record<string, unknown>)
  vi.mocked(fetchPlatformOverview).mockResolvedValue(overview([table(), created]))
  await act(async () => button('Create Table').props.onClick())
  expect(savePlatformEntity).toHaveBeenCalledWith('dining-tables', expect.objectContaining({ store_id: 1, table_code: 'T3', table_name: '3', supports_split: true, split_label_mode: 'INSIDE_OUTSIDE' }), undefined)
  await act(async () => row('T3').props.onClick())
  expect(field('Split Label Mode').props.value).toBe('INSIDE_OUTSIDE')
})

it('ignores a late old-Store load and opens Chinatown records with its own mode', async () => {
  let old!: (result: PlatformAdminOverview) => void
  vi.mocked(fetchPlatformOverview).mockImplementation((id) => id === 1 ? new Promise((resolve) => { old = resolve }) : Promise.resolve(overview([{ ...table(2), split_label_mode: 'INSIDE_OUTSIDE' }])))
  await act(async () => { view = create(<DiningTablesManagementPage />) })
  await act(async () => field('Dining table Store').props.onChange({ target: { value: '2' } }))
  await act(async () => old(overview([table()])))
  expect(row('T1')).toBeUndefined()
  await act(async () => row('T2').props.onClick())
  expect(field('Split Label Mode').props.value).toBe('INSIDE_OUTSIDE')
  expect(field('Table Code').props.value).toBe('T2')
})

it('removes Active editing and Status display, confirms the table identity before soft deletion, and closes after reload', async () => {
  vi.mocked(fetchPlatformOverview).mockResolvedValue(overview([table(), { ...table(), id: 9, table_code: 'deleted', is_active: false }]))
  await act(async () => { view = create(<DiningTablesManagementPage />) })
  expect(row('deleted')).toBeUndefined()
  expect(view!.root.findAllByType('span').some((node) => node.children.join('') === 'Status')).toBe(false)
  await act(async () => row('T1').props.onClick())
  expect(view!.root.findAllByType('input').filter((node) => node.props.type === 'checkbox')).toHaveLength(0)
  vi.mocked(window.confirm).mockReturnValue(false)
  await act(async () => button('Delete Table / 删除桌台').props.onClick())
  expect(deleteDiningTable).not.toHaveBeenCalled()
  expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('1（T1）'))
  vi.mocked(window.confirm).mockReturnValue(true)
  vi.mocked(deleteDiningTable).mockResolvedValue({ ...table(), is_active: false })
  vi.mocked(fetchPlatformOverview).mockResolvedValue(overview([]))
  await act(async () => button('Delete Table / 删除桌台').props.onClick())
  expect(deleteDiningTable).toHaveBeenCalledWith(1, 1)
  expect(notifyDiningTablesChanged).toHaveBeenCalledWith(1)
  expect(row('T1')).toBeUndefined()
  expect(view!.root.findAllByProps({ 'aria-label': 'Table Code' })).toHaveLength(0)
})

it('keeps the editor and table when deletion is rejected for an active order', async () => {
  await act(async () => { view = create(<DiningTablesManagementPage />) })
  await act(async () => row('T1').props.onClick())
  vi.mocked(deleteDiningTable).mockRejectedValue(new Error('桌台有进行中的订单，暂时不能删除 / Table has active orders'))
  await act(async () => button('Delete Table / 删除桌台').props.onClick())
  expect(JSON.stringify(view!.toJSON())).toContain('Table has active orders')
  expect(field('Table Code').props.value).toBe('T1')
  expect(row('T1')).toBeDefined()
  expect(notifyDiningTablesChanged).not.toHaveBeenCalled()
})

it('confirms and reports the persisted table identity when name and code edits have not been saved', async () => {
  await act(async () => { view = create(<DiningTablesManagementPage />) })
  await act(async () => row('T1').props.onClick())
  await act(async () => field('Table Name').props.onChange({ target: { value: 'UNSAVED NAME' } }))
  await act(async () => field('Table Code').props.onChange({ target: { value: 'UNSAVED CODE' } }))
  vi.mocked(deleteDiningTable).mockResolvedValue({ ...table(), is_active: false })
  vi.mocked(fetchPlatformOverview).mockResolvedValue(overview([]))
  await act(async () => button('Delete Table / 删除桌台').props.onClick())
  const confirmation = vi.mocked(window.confirm).mock.calls[0][0]
  expect(confirmation).toContain('1（T1）')
  expect(confirmation).not.toContain('UNSAVED')
  expect(deleteDiningTable).toHaveBeenCalledWith(1, 1)
  expect(JSON.stringify(view!.toJSON())).toContain('已删除桌台 1')
  expect(JSON.stringify(view!.toJSON())).not.toContain('UNSAVED')
  expect(savePlatformEntity).not.toHaveBeenCalled()
})
