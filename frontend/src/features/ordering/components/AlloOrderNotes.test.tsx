import { useState } from 'react'
import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ItemCustomizationModal } from './ItemCustomizationModal'
import { OrderSummaryPanel } from './OrderSummaryPanel'
import { buildDefaultDraft } from '../../../hooks/useOrderSessions'
import { calculateDraftLineSubtotal } from '../../../utils/orderPricing'
import { isQuickAddItem } from '../orderingCustomizationRules'
import { appI18n } from '../../../i18n/engine'
import type { MenuItem, ItemCustomizationDraft, OrderLineItem } from '../../../types/ordering'
vi.mock('../../../hooks/useIpadLandscape', () => ({ useIpadLandscape: () => false }))
vi.mock('../../offline/OfflineOrderStatusPanel', () => ({ OfflineOrderStatusPanel: () => null }))
let view: ReactTestRenderer | undefined
let captured: ItemCustomizationDraft
const item = (sku: string): MenuItem => ({ id: '10', sku, categoryId: '1', categoryCode: sku.startsWith('AN_B') ? 'NOODLE' : 'RICE', nameZh: '合成菜', nameEn: 'Synthetic dish', descriptionZh: '', descriptionEn: '', price: 16.99, optionGroups: [] })
function Modal({ sku, allowed = true }: { sku: string; allowed?: boolean }) {
 const dish = item(sku); const [draft, setDraft] = useState(buildDefaultDraft(dish)); captured = draft
 return <ItemCustomizationModal allowRiceNotes={allowed} item={dish} draft={draft} subtotal={calculateDraftLineSubtotal(dish, draft)} mode="edit" onChange={setDraft} onSubmit={() => {}} onClose={() => {}} />
}
beforeEach(async () => { vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true); vi.stubGlobal('document', { body: { style: { overflow: '', touchAction: '' } } }); await appI18n.changeLanguage('en') })
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.unstubAllGlobals() })
it.each(['AN_C1','AN_C2','AN_C3','AN_C4','AN_C5','AN_C6','AN_B1'])('reuses optional item-note control for %s and preserves quantity pricing', async (sku) => {
 await act(async () => { view = create(<Modal sku={sku} />) })
 const field = view!.root.findByType('textarea')
 await act(async () => field.props.onChange({ target: { value: '不要酱汁\nNo sauce' } }))
 expect(captured.notes).toBe('不要酱汁\nNo sauce')
 expect(calculateDraftLineSubtotal(item(sku), captured)).toBe(16.99)
 expect(isQuickAddItem(item('AN_C4'), true)).toBe(false)
})
it.each(['AN_A1','AN_D1','AN_E1'])('does not add a note input for %s', async (sku) => {
 await act(async () => { view = create(<Modal sku={sku} />) })
 expect(view!.root.findAllByType('textarea')).toHaveLength(0)
})
it('keeps other Stores rice modal unchanged', async () => {
 await act(async () => { view = create(<Modal sku="AN_C4" allowed={false} />) })
 expect(view!.root.findAllByType('textarea')).toHaveLength(0)
})
it('uses exactly one whole-order field while retaining readonly item notes', async () => {
 const line = (id: string, notes: string): OrderLineItem => ({ id, menuItemId: id, nameZh: id, nameEn: id, quantity: 1, unitPrice: 1, lineSubtotal: 1, selection: { ...buildDefaultDraft(item('AN_C4')), notes }, summaryTags: [], notes })
 const changed = vi.fn(), noop = () => {}
 await act(async () => { view = create(<OrderSummaryPanel unifiedOrderNotes session={{ orderId: 'test', slotLabel: 'TEST 1', tableLabel: 'TEST 1', status: 'draft', isModifiedAfterSubmit: false, items: [line('B1','少辣'),line('C4','不要酱汁')], orderNotes: 'Serve together' }} subtotal={2} tax={0.3} total={2.3} localSubmitState="LOCAL_DRAFT" lastBackendSuccessAt={null} onIncrementItem={noop} onDecrementItem={noop} onEditItem={noop} onRemoveItem={noop} onUpdateItemNote={noop} onUpdateOrderNotes={changed} onSaveDraft={noop} onCancelOrder={noop} onSubmitOrder={noop} />) })
 expect(view!.root.findAllByType('textarea')).toHaveLength(1)
 expect(view!.root.findByType('textarea').props.value).toBe('Serve together')
 expect(JSON.stringify(view!.toJSON())).toContain('不要酱汁')
 await act(async () => view!.root.findByType('textarea').props.onChange({ target: { value: '请一起上菜\nServe together' } }))
 expect(changed).toHaveBeenCalledWith('请一起上菜\nServe together')
})
