import { describe, expect, it } from 'vitest'
import type { ItemCustomizationDraft } from '../types/ordering'
import { mapOptions } from './orderService'

describe('order option payload mapping', () => {
  it('submits the noodle type explicitly selected by the user', () => {
    const draft: ItemCustomizationDraft = {
      noodleTypeId: '21',
      comboEnabled: false,
      comboSideRemoveIds: [],
      addOnQuantities: {},
      removeIds: [],
      quantity: 1,
      notes: '',
    }

    expect(mapOptions(draft)).toContainEqual({ option_id: 21, quantity: 1 })
    expect(mapOptions(draft)).not.toContainEqual({ option_id: 22, quantity: 1 })
  })
})
