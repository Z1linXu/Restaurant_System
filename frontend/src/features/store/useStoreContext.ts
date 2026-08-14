import { useContext } from 'react'
import { StoreContext } from './StoreContextCore'

export function useCurrentStore() {
  const value = useContext(StoreContext)
  if (!value) {
    throw new Error('useCurrentStore must be used inside StoreContextProvider')
  }
  return value
}

export function useOptionalCurrentStore() {
  return useContext(StoreContext)
}
