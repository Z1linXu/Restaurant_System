import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'
import { prepareAppCacheVersion } from './utils/appCacheVersion'
import { AuthProvider } from './features/auth/AuthProvider'
import { ConnectionHealthProvider } from './features/network/ConnectionHealthProvider'
import { OrderOutboxProvider } from './features/offline/OrderOutboxProvider'
import { LocaleProvider } from './i18n/LocaleProvider'

if (prepareAppCacheVersion()) {
  createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <LocaleProvider>
        <ConnectionHealthProvider>
          <AuthProvider>
            <OrderOutboxProvider>
              <App />
            </OrderOutboxProvider>
          </AuthProvider>
        </ConnectionHealthProvider>
        </LocaleProvider>
      </StrictMode>,
  )
}
