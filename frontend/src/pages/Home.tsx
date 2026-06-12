import { isFeatureEnabled, type FeaturePackage } from '../features/feature-flags/featureConfig'

function navigateTo(path: string) {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

const sections = [
  {
    title: 'Frontdesk',
    feature: 'CORE_POS',
    links: [
      { label: 'Table Board', path: '/frontdesk', feature: 'CORE_POS' },
      { label: 'Menu A', path: '/frontdesk/menu/a', feature: 'CORE_POS' },
      { label: 'Menu B', path: '/frontdesk/menu/b', feature: 'CORE_POS' },
      { label: 'Orders', path: '/frontdesk/order', feature: 'CORE_POS' },
      { label: 'Pickup Board', path: '/pickup', feature: 'KDS' },
    ],
  },
  {
    title: 'Kitchen',
    feature: 'KDS',
    links: [
      { label: 'Grab / Assembling', path: '/kds/grab', feature: 'KDS' },
      { label: 'Hot Kitchen', path: '/kds/hot-kitchen', feature: 'KDS' },
      { label: 'Noodle Monitor', path: '/kds/noodle', feature: 'KDS' },
      { label: 'KDS History', path: '/kds/history', feature: 'KDS' },
    ],
  },
  {
    title: 'Admin',
    feature: 'ADMIN',
    links: [
      { label: 'Owner Dashboard', path: '/admin/dashboard', feature: 'ADMIN' },
      { label: 'Dining Tables', path: '/admin/settings/tables', feature: 'ADMIN' },
      { label: 'Menu Management', path: '/admin/menu/items', feature: 'ADMIN' },
      { label: 'Printing Settings', path: '/admin/settings/printing', feature: 'PRINTING' },
      { label: 'Sales Report', path: '/admin/reports/sales', feature: 'ANALYTICS' },
      { label: 'Item Sales Report', path: '/admin/reports/items', feature: 'ANALYTICS' },
      { label: 'Profit Report', path: '/admin/reports/profit', feature: 'ANALYTICS' },
      { label: 'Store Comparison Report', path: '/admin/reports/stores', feature: 'ANALYTICS' },
      { label: 'Platform Admin', path: '/admin/platform', feature: 'PLATFORM' },
    ],
  },
] as const satisfies Array<{
  title: string
  feature: FeaturePackage
  links: Array<{ label: string; path: string; feature: FeaturePackage }>
}>

export default function Home() {
  return (
    <div className="min-h-screen bg-[var(--surface)] px-6 py-6 text-[var(--on-surface)]">
      <div className="mx-auto max-w-[1200px] space-y-6">
        <div className="rounded-[28px] bg-[rgba(255,255,255,0.82)] px-6 py-6 shadow-[0_16px_32px_rgba(26,28,25,0.06)]">
          <div className="text-[2.3rem] font-black tracking-[-0.06em] text-[var(--primary)]">Restaurant System</div>
          <div className="mt-2 text-[1rem] font-medium text-[var(--muted)]">
            Quick entry page for frontdesk, KDS, and pickup workflows.
          </div>
          <button
            type="button"
            onClick={() => navigateTo('/login')}
            className="mt-5 rounded-[18px] bg-[var(--primary)] px-5 py-3 text-[0.98rem] font-black text-white shadow-[0_12px_24px_rgba(97,0,0,0.18)]"
          >
            Staff Login
          </button>
        </div>

        <div className="grid gap-5 md:grid-cols-2">
          {sections
            .filter((section) => isFeatureEnabled(section.feature) || section.links.some((link) => isFeatureEnabled(link.feature)))
            .map((section) => (
            <div
              key={section.title}
              className="rounded-[26px] bg-[rgba(255,255,255,0.84)] px-5 py-5 shadow-[0_16px_32px_rgba(26,28,25,0.05)]"
            >
              <div className="text-[1.35rem] font-extrabold tracking-[-0.04em] text-[var(--on-surface)]">{section.title}</div>
              <div className="mt-4 space-y-3">
                {section.links.filter((link) => isFeatureEnabled(link.feature)).map((link) => (
                  <button
                    key={link.path}
                    type="button"
                    onClick={() => navigateTo(link.path)}
                    className="flex w-full items-center justify-between rounded-[18px] bg-[rgba(26,28,25,0.04)] px-4 py-3 text-left text-[0.98rem] font-semibold text-[var(--on-surface)] transition hover:bg-[rgba(97,0,0,0.06)] hover:text-[var(--primary)]"
                  >
                    <span>{link.label}</span>
                    <span className="text-[var(--muted)]">→</span>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
