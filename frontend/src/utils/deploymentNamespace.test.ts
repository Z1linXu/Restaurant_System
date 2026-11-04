import { describe, expect, it } from 'vitest'
import { deploymentStorageKey, normalizeDeploymentNamespace } from './deploymentNamespace'

describe('deployment storage boundary', () => {
  it('keeps legacy keys and namespace stable across releases and locales', () => {
    expect(deploymentStorageKey('restaurant-pos-offline', '')).toBe('restaurant-pos-offline')
    const namespace = 'allo-nouilles-test'
    expect(deploymentStorageKey('restaurant-pos-offline', namespace)).toBe('allo-nouilles-test:restaurant-pos-offline')
    expect(deploymentStorageKey('restaurant_pos_access_token', namespace)).not.toBe('restaurant_pos_access_token')
    expect(deploymentStorageKey('restaurant_pos_refresh_token', namespace)).not.toBe('restaurant_pos_refresh_token')
    expect(normalizeDeploymentNamespace(namespace)).toBe(namespace)
  })
  it('rejects unsafe identifiers instead of silently sharing legacy storage', () => {
    expect(() => normalizeDeploymentNamespace('../legacy')).toThrow()
    expect(() => normalizeDeploymentNamespace('allo:test')).toThrow()
  })
})
