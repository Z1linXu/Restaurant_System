import { DineInPage } from '../features/dinein/DineInPage'

export default function DineIn() {
  return <DineInPage routePath={window.location.pathname} routeSearch={window.location.search} />
}
