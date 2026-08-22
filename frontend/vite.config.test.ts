import { describe, expect, it } from 'vitest'
import viteConfig from './vite.config'

describe('browser bundle compatibility', () => {
  it('maps Node-style global references from SockJS dependencies to globalThis', () => {
    expect(viteConfig).toMatchObject({
      define: {
        global: 'globalThis',
      },
    })
  })
})
