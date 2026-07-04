import type { FeaturePackage } from './featureConfig'

interface FeatureDisabledPageProps {
  feature: FeaturePackage
}

function navigateToHome() {
  window.history.pushState({}, '', '/')
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export function FeatureDisabledPage({ feature }: FeatureDisabledPageProps) {
  return (
    <div className="min-h-screen bg-[var(--surface)] px-6 py-6 text-[var(--on-surface)]">
      <div className="mx-auto flex min-h-[calc(100vh-3rem)] max-w-[960px] items-center justify-center">
        <div className="w-full rounded-[30px] bg-[rgba(255,255,255,0.86)] px-7 py-8 text-center shadow-[0_18px_42px_rgba(26,28,25,0.07)]">
          <div className="text-[0.78rem] font-black uppercase tracking-[0.2em] text-[var(--primary)]">
            功能未启用
          </div>
          <h1 className="mt-3 font-display text-[2.5rem] font-extrabold tracking-[-0.07em] text-[var(--on-surface)]">
            暂不可用
          </h1>
          <p className="mx-auto mt-3 max-w-[560px] text-[1rem] leading-7 text-[var(--muted)]">
            当前页面属于 <span className="font-semibold text-[var(--on-surface)]">{feature}</span> 功能包，但这家餐厅暂未启用该功能。
          </p>
          <button
            type="button"
            onClick={navigateToHome}
            className="mt-6 rounded-full bg-[var(--primary)] px-5 py-3 text-[0.92rem] font-semibold text-white shadow-[0_14px_26px_rgba(97,0,0,0.16)]"
          >
            返回首页
          </button>
        </div>
      </div>
    </div>
  )
}
