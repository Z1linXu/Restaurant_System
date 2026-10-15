import { useContext } from 'react'
import { StoreContext } from './StoreContextCore'
import { NEUTRAL_STORE_PRESENTATION } from './storePresentation'

export function useStorePresentation() {
  return useContext(StoreContext)?.presentation ?? NEUTRAL_STORE_PRESENTATION
}
