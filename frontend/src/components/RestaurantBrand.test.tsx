import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, expect, it, vi } from 'vitest'
import { RestaurantBrand } from './RestaurantBrand'

vi.mock('../features/store/useStoreContext', () => ({ useOptionalCurrentStore: () => null }))
vi.mock('../i18n/useLocale', () => ({ useLocale: () => ({ t: () => 'Restaurant POS' }) }))
let view: ReactTestRenderer | undefined
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined; vi.unstubAllGlobals() })
it('shows the bundled merchant before login without shipping credentials', async () => {
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', { __RESTAURANT_APP_CONFIG__: { brandName: 'Allô Nouilles', brandLogo: '/merchant/logo.png' } })
  await act(async () => { view = create(<RestaurantBrand />) })
  expect(view!.root.findByType('img').props).toMatchObject({ src: '/merchant/logo.png', alt: 'Allô Nouilles' })
})
it('keeps the existing browser and configurable Pad fallback unchanged', async () => {
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('window', {})
  await act(async () => { view = create(<RestaurantBrand />) })
  expect(JSON.stringify(view!.toJSON())).toContain('Restaurant POS')
  expect(view!.root.findAllByType('img')).toHaveLength(0)
})
