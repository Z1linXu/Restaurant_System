import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'
import { prepareAppCacheVersion } from './utils/appCacheVersion'
import { AuthProvider } from './features/auth/AuthProvider'
import { ConnectionHealthProvider } from './features/network/ConnectionHealthProvider'

if (prepareAppCacheVersion()) {
  createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <ConnectionHealthProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </ConnectionHealthProvider>
      </StrictMode>,
  )
}
