import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TemplatePrintDiagnosticsPanel } from './TemplatePrintDiagnosticsPanel'
import { fetchTemplatePrintDiagnostics, type TemplatePrintTicket } from '../../services/templatePlatformService'
import { ApiRequestError } from '../../services/apiClient'

vi.mock('../../services/templatePlatformService', async () => ({ ...await vi.importActual<typeof import('../../services/templatePlatformService')>('../../services/templatePlatformService'), fetchTemplatePrintDiagnostics: vi.fn() }))
vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
const ticket: TemplatePrintTicket = { intent_id: 4, source_key: 'order-30-C', order_id: 30, order_update_batch_id: null, group_code: 'C', group_name: 'Cold C', logical_target: 'cold', job_id: null, state: 'UNRESOLVED', reason: 'No MOCK printer assigned', error_code: 'PRINTER_MISSING', rendered_text: 'SAVED SYNTHETIC C CONTENT', menu_revision: 2, routing_revision: 3, execution_mode: 'MOCK' }
let view: ReactTestRenderer | undefined
beforeEach(() => { vi.clearAllMocks(); vi.mocked(fetchTemplatePrintDiagnostics).mockResolvedValue({ tickets: [ticket] }) })
afterEach(() => { act(() => view?.unmount()); view = undefined; vi.useRealTimers() })
const content = () => JSON.stringify(view!.toJSON())

describe('persistent per-ticket diagnostics', () => {
  it('shows pre-Job warnings and re-queries the saved record after remount', async () => {
    await act(async () => { view = create(<TemplatePrintDiagnosticsPanel storeId={2} orderId={30} />) })
    expect(content()).toContain('Order saved')
    expect(content()).toContain('No MOCK printer assigned')
    expect(content()).toContain('Not created')
    expect(content()).toContain('SAVED SYNTHETIC C CONTENT')
    expect(content()).not.toContain('Mock processing completed')
    act(() => view!.unmount())
    await act(async () => { view = create(<TemplatePrintDiagnosticsPanel storeId={2} orderId={30} />) })
    expect(fetchTemplatePrintDiagnostics).toHaveBeenCalledTimes(2)
    expect(fetchTemplatePrintDiagnostics).toHaveBeenLastCalledWith(2, expect.objectContaining({ orderId: 30 }))
    expect(content()).toContain('No MOCK printer assigned')
  })

  it('refreshes a later asynchronous failure after successful server confirmation', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchTemplatePrintDiagnostics).mockResolvedValueOnce({ tickets: [{ ...ticket, job_id: 8, state: 'PENDING', reason: null, error_code: null }] })
      .mockResolvedValue({ tickets: [{ ...ticket, job_id: 8, state: 'FAILED', reason: 'MOCK connection failure' }] })
    await act(async () => { view = create(<TemplatePrintDiagnosticsPanel storeId={2} orderId={30} poll />) })
    expect(content()).not.toContain('MOCK connection failure')
    await act(async () => { await vi.advanceTimersByTimeAsync(3000) })
    expect(content()).toContain('MOCK connection failure')
    expect(content()).toContain('Order saved')
  })

  it('isolates the Update batch and does not call missing jobs printed', async () => {
    vi.mocked(fetchTemplatePrintDiagnostics).mockResolvedValue({ tickets: [{ ...ticket, source_key: 'old', order_update_batch_id: null }, { ...ticket, source_key: 'batch-7', order_update_batch_id: 7, state: 'PRINTED', reason: 'Job not created', job_id: null }] })
    await act(async () => { view = create(<TemplatePrintDiagnosticsPanel storeId={2} orderId={30} batchId={7} />) })
    expect(content()).not.toContain('order-30-C')
    expect(content()).not.toContain('Mock processing completed')
    expect(content()).toContain('Not created')
    expect(fetchTemplatePrintDiagnostics).toHaveBeenCalledWith(2, expect.objectContaining({ batchId: 7 }))
  })

  it('keeps order success distinct from a status-read failure and never writes a job', async () => {
    vi.mocked(fetchTemplatePrintDiagnostics).mockRejectedValue(new Error('Status unavailable'))
    await act(async () => { view = create(<TemplatePrintDiagnosticsPanel storeId={2} orderId={30} />) })
    expect(content()).toContain('Order saved; ticket status is temporarily unavailable')
    expect(content()).not.toContain('PRINTED')
  })

  it('keeps an unsupported legacy backend silent and preserves the original print coordinator path', async () => {
    vi.mocked(fetchTemplatePrintDiagnostics).mockRejectedValue(new ApiRequestError(404, 'Not found'))
    await act(async () => { view = create(<TemplatePrintDiagnosticsPanel storeId={2} orderId={30} hideWhenEmpty poll />) })
    expect(view!.toJSON()).toBeNull()
    expect(fetchTemplatePrintDiagnostics).toHaveBeenCalledTimes(1)
  })
})
