import { Button } from '../../components/ui/Button'
import type { LocalDraftSubmitState } from '../../offline/localDrafts'
import { offlineOrderStatusView } from './offlineOrderStatus'

interface OfflineOrderStatusPanelProps {
  state: LocalDraftSubmitState
  lastBackendSuccessAt: string | null
  lastErrorCode?: string | null
  nextRetryAt?: string | null
  compact?: boolean
  onRetry?: () => void
  onReturnToDraft?: () => void
  onCancelLocal?: () => void
}

const toneClasses = {
  neutral: 'border-[rgba(83,58,50,0.14)] bg-[rgba(83,58,50,0.06)] text-[rgba(53,43,38,0.94)]',
  warning: 'border-[rgba(177,111,21,0.28)] bg-[rgba(220,153,54,0.13)] text-[rgb(112,65,8)]',
  progress: 'border-[rgba(35,91,140,0.24)] bg-[rgba(61,126,184,0.11)] text-[rgb(29,76,116)]',
  success: 'border-[rgba(55,119,78,0.24)] bg-[rgba(70,145,95,0.11)] text-[rgb(37,91,55)]',
  danger: 'border-[rgba(151,34,34,0.3)] bg-[rgba(151,34,34,0.1)] text-[rgb(116,22,22)]',
} as const

function displayTime(value: string | null | undefined) {
  if (!value) return '暂无'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '暂无' : date.toLocaleString()
}

export function OfflineOrderStatusPanel({
  state,
  lastBackendSuccessAt,
  lastErrorCode,
  nextRetryAt,
  compact = false,
  onRetry,
  onReturnToDraft,
  onCancelLocal,
}: OfflineOrderStatusPanelProps) {
  const view = offlineOrderStatusView(state)
  const showActions = (view.canRetry && onRetry) || (view.canReturnToDraft && onReturnToDraft)

  return (
    <section
      aria-live="polite"
      className={`shrink-0 rounded-[18px] border ${toneClasses[view.tone]} ${compact ? 'space-y-2 px-3 py-2.5' : 'space-y-3 px-4 py-3.5'}`}
    >
      <div>
        <p className={`${compact ? 'text-[0.82rem]' : 'text-sm'} font-black`}>{view.title}</p>
        <p className={`${compact ? 'mt-0.5 text-[0.76rem]' : 'mt-1 text-sm'} font-semibold`}>{view.description}</p>
      </div>
      <div className={`${compact ? 'text-[0.68rem]' : 'text-xs'} font-semibold opacity-75`}>
        最近连接服务器成功：{displayTime(lastBackendSuccessAt)}
        {nextRetryAt ? ` · 下次重试：${displayTime(nextRetryAt)}` : ''}
      </div>
      {lastErrorCode ? (
        <details className={`${compact ? 'text-[0.7rem]' : 'text-xs'} font-semibold`}>
          <summary className="cursor-pointer select-none underline">查看错误</summary>
          <p className="mt-1 break-all">错误码：{lastErrorCode}</p>
        </details>
      ) : null}
      {showActions ? (
        <div className="grid grid-cols-2 gap-2">
          {view.canRetry && onRetry ? (
            <Button variant="secondary" className="min-h-11 rounded-[14px] px-2 text-[0.78rem]" onClick={onRetry}>
              立即重试
            </Button>
          ) : <span />}
          {view.canReturnToDraft && onReturnToDraft ? (
            <Button variant="secondary" className="min-h-11 rounded-[14px] px-2 text-[0.78rem]" onClick={onReturnToDraft}>
              返回修改
            </Button>
          ) : null}
        </div>
      ) : null}
      {view.canCancelLocal && state !== 'LOCAL_DRAFT' && onCancelLocal ? (
        <button type="button" className="min-h-11 w-full rounded-[14px] px-3 text-left text-xs font-black underline" onClick={onCancelLocal}>
          取消本机订单
        </button>
      ) : null}
    </section>
  )
}
