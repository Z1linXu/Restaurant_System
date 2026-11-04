import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './apiClient'
import { fetchUberInbox, fetchUberOrders } from './uberEatsService'
vi.mock('./apiClient', () => ({ apiRequest: vi.fn() }))
vi.mock('./manualReprintService', () => ({ manualReprint: vi.fn() }))
describe('Uber grouped Inbox read contract', () => {
  beforeEach(() => vi.clearAllMocks())
  it('sends independent cursors/history date and the cancellation signal through a GET', async () => {
    const signal = new AbortController().signal
    await fetchUberInbox(12, { limit: 100, today_before_id: 8, attention_before_id: 4, history_date: '2026-11-01', history_before_id: 2 }, signal)
    expect(apiRequest).toHaveBeenCalledExactlyOnceWith('/api/v1/stores/12/integrations/uber-eats/inbox?limit=100&today_before_id=8&attention_before_id=4&history_date=2026-11-01&history_before_id=2', { signal })
  })
  it('does not invent a historical request or alter the compatibility endpoint', async () => {
    await fetchUberInbox(12)
    expect(apiRequest).toHaveBeenCalledWith('/api/v1/stores/12/integrations/uber-eats/inbox', { signal: undefined })
    await fetchUberOrders(12)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/stores/12/integrations/uber-eats/orders')
  })
})
