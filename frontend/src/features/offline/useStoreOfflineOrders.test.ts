import { describe, expect, it } from 'vitest'
import {
  OFFLINE_RECONCILIATION_CONCURRENCY,
  runBoundedReconciliation,
} from './useStoreOfflineOrders'

describe('offline order reconciliation scheduling', () => {
  it('bounds concurrent server reads and visits every unique value', async () => {
    let active = 0
    let maximumActive = 0
    const visited: number[] = []

    await runBoundedReconciliation(
      [1, 2, 3, 4, 5, 6, 7],
      async (value) => {
        active += 1
        maximumActive = Math.max(maximumActive, active)
        visited.push(value)
        await Promise.resolve()
        active -= 1
      },
    )

    expect(maximumActive).toBeLessThanOrEqual(OFFLINE_RECONCILIATION_CONCURRENCY)
    expect(visited.sort((left, right) => left - right)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('stops taking new work after the scope becomes inactive', async () => {
    let active = true
    const visited: number[] = []

    await runBoundedReconciliation(
      [1, 2, 3],
      async (value) => {
        visited.push(value)
        active = false
      },
      () => active,
    )

    expect(visited).toEqual([1])
  })
})
