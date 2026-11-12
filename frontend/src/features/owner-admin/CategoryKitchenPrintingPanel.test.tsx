import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { appI18n } from '../../i18n/engine'
import { fetchReceiptPresentation, saveReceiptPresentation } from '../../services/printingAdminService'
import { CategoryKitchenPrintingPanel } from './CategoryKitchenPrintingPanel'

vi.mock('../../services/printingAdminService', () => ({fetchReceiptPresentation:vi.fn(),saveReceiptPresentation:vi.fn()}))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
let view: ReactTestRenderer | undefined
const initial={enabled:true,default_language:'en' as const,kitchen_station_codes:['BACK_KITCHEN'],revision:3}
beforeEach(async () => { vi.clearAllMocks();await appI18n.changeLanguage('en');vi.mocked(fetchReceiptPresentation).mockResolvedValue(initial) })
afterEach(async () => { if(view)await act(async()=>view!.unmount());view=undefined })
it('keeps Drinks direct-serve and persists an independent Dessert switch without changing language', async () => {
  vi.mocked(saveReceiptPresentation).mockImplementation(async (_id,settings)=>({...settings,revision:4}))
  await act(async()=>{view=create(<CategoryKitchenPrintingPanel storeId={1} categories={[{code:'DRINK',label:'Drinks'},{code:'DESSERT',label:'Desserts'}]} />)})
  const drink=view!.root.findByProps({'aria-label':'Drinks Kitchen ticket'});expect(drink.props.disabled).toBe(true);expect(drink.props.checked).toBe(false)
  await act(async()=>view!.root.findByProps({'aria-label':'Desserts Kitchen ticket'}).props.onChange({target:{checked:false}}))
  await act(async()=>view!.root.findAllByType('button').find(x=>x.children.join('')==='Save kitchen routing')!.props.onClick())
  expect(saveReceiptPresentation).toHaveBeenCalledWith(1,{...initial,kitchen_category_enabled:{DESSERT:false}})
})
it('does not expose merchant receipt routing for a store that has not enabled it', async () => {
  vi.mocked(fetchReceiptPresentation).mockResolvedValue({...initial,enabled:false})
  await act(async()=>{view=create(<CategoryKitchenPrintingPanel storeId={2} categories={[]} />)})
  expect(view!.toJSON()).toBeNull()
})
