const TOP_FEED_16MM = '[[TOP_FEED_16MM]]\n'

export function ReceiptPreview({ content }: { content?: string | null }) {
  let text = content ?? ''
  const topFeed = text.startsWith(TOP_FEED_16MM)
  while (text.startsWith(TOP_FEED_16MM)) text = text.slice(TOP_FEED_16MM.length)

  return (
    <pre className="max-h-[70vh] overflow-auto whitespace-pre-wrap px-5 py-4 font-mono text-[0.92rem] leading-6 text-[var(--on-surface)]">
      {topFeed && <span aria-hidden="true" data-receipt-top-feed="16mm" style={{ display: 'block', height: '16mm' }} />}
      {text || '该打印任务没有保存小票预览。'}
    </pre>
  )
}
