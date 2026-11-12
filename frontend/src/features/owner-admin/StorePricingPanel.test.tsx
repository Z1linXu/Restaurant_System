import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { fetchStorePricingCapabilities } from '../../services/ownerMenuPricingService'
import { StorePricingPanel } from './StorePricingPanel'

vi.mock('../../services/ownerMenuPricingService', () => ({ fetchStorePricingCapabilities: vi.fn() }))
vi.mock('./PricingRulesPanel', () => ({ PricingRulesPanel: () => <div data-testid="legacy-size" /> }))
vi.mock('./CategoryBulkPriceAdjustmentPanel', () => ({ CategoryBulkPriceAdjustmentPanel: () => <div data-testid="category-bulk" /> }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
let view: ReactTestRenderer | undefined
beforeEach(() => vi.clearAllMocks())
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })
const mount = async () => { await act(async () => { view = create(<StorePricingPanel storeId={1} categories={[{id:2,label:'Noodles'}]} />) }) }
it('Allo capability uses bulk without showing irrelevant standard sizes', async () => {
  vi.mocked(fetchStorePricingCapabilities).mockResolvedValue({store_id:1,category_bulk_enabled:true,standard_size_enabled:false})
  await mount(); expect(view!.root.findAllByProps({'data-testid':'category-bulk'})).toHaveLength(1)
  expect(view!.root.findAllByProps({'data-testid':'legacy-size'})).toHaveLength(0)
})
it('preserves the existing standard Size engine for stores that use it', async () => {
  vi.mocked(fetchStorePricingCapabilities).mockResolvedValue({store_id:1,category_bulk_enabled:false,standard_size_enabled:true})
  await mount(); expect(view!.root.findAllByProps({'data-testid':'legacy-size'})).toHaveLength(1)
  expect(view!.root.findAllByProps({'data-testid':'category-bulk'})).toHaveLength(0)
})
it('does not guess pricing capabilities on a failed request', async () => {
  vi.mocked(fetchStorePricingCapabilities).mockRejectedValue(new Error('temporarily unavailable'))
  await mount(); expect(view!.root.findAllByProps({role:'alert'})).toHaveLength(1)
  expect(view!.root.findAllByProps({'data-testid':'legacy-size'})).toHaveLength(0)
  expect(view!.root.findAllByProps({'data-testid':'category-bulk'})).toHaveLength(0)
})
