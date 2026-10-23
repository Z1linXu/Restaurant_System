import { act, create, type ReactTestRenderer } from 'react-test-renderer'
import { afterEach, expect, it, vi } from 'vitest'
import { ReceiptPreview } from './ReceiptPreview'

vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
let view: ReactTestRenderer | undefined
afterEach(async () => { if (view) await act(async () => view!.unmount()); view = undefined })

it.each(['1', '1 (UPDATE)', '1里', '1-左 (UPDATE)', '1-右 (UPDATE)'])(
  'shows an additional 16mm spacer before %s and retains original padding/blank lines', async (header) => {
    const body = `\n\n${header}\n----------------\nitem`
    await act(async () => { view = create(<ReceiptPreview content={`[[TOP_FEED_16MM]]\n${body}`} />) })
    const pre = view!.root.findByType('pre')
    const spacer = pre.findByType('span')
    expect(spacer.props.style).toEqual({ display: 'block', height: '16mm' })
    expect(pre.props.className).toContain('py-4')
    expect(pre.children[0]).toBe(spacer)
    expect(pre.children[1]).toBe(body)
  },
)

it('collapses duplicate leading layout markers to one spacer', async () => {
  await act(async () => { view = create(<ReceiptPreview content={'[[TOP_FEED_16MM]]\n[[TOP_FEED_16MM]]\n1'} />) })
  expect(view!.root.findAllByType('span')).toHaveLength(1)
  expect(view!.root.findByType('pre').children[1]).toBe('1')
})

it('keeps takeout and frozen unmarked previews unchanged', async () => {
  await act(async () => { view = create(<ReceiptPreview content={'\n\nTAKEOUT\nitem'} />) })
  expect(view!.root.findAllByType('span')).toHaveLength(0)
  expect(view!.root.findByType('pre').children).toEqual(['\n\nTAKEOUT\nitem'])
})
