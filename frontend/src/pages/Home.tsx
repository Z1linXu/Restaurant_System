function navigateTo(path: string) {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

const sections = [
  {
    title: 'Frontdesk',
    links: [
      { label: 'Table Board', path: '/frontdesk' },
      { label: 'Menu', path: '/frontdesk/menu' },
      { label: 'Orders', path: '/frontdesk/order' },
      { label: 'Pickup Board', path: '/pickup' },
    ],
  },
  {
    title: 'Kitchen',
    links: [
      { label: 'Grab / Assembling', path: '/kds/grab' },
      { label: 'Hot Kitchen', path: '/kds/hot-kitchen' },
      { label: 'Noodle Monitor', path: '/kds/noodle' },
      { label: 'KDS History', path: '/kds/history' },
    ],
  },
] as const

export default function Home() {
  return (
    <div className="min-h-screen bg-[var(--surface)] px-6 py-6 text-[var(--on-surface)]">
      <div className="mx-auto max-w-[1200px] space-y-6">
        <div className="rounded-[28px] bg-[rgba(255,255,255,0.82)] px-6 py-6 shadow-[0_16px_32px_rgba(26,28,25,0.06)]">
          <div className="text-[2.3rem] font-black tracking-[-0.06em] text-[var(--primary)]">Restaurant System</div>
          <div className="mt-2 text-[1rem] font-medium text-[var(--muted)]">
            Quick entry page for frontdesk, KDS, and pickup workflows.
          </div>
        </div>

        <div className="grid gap-5 md:grid-cols-2">
          {sections.map((section) => (
            <div
              key={section.title}
              className="rounded-[26px] bg-[rgba(255,255,255,0.84)] px-5 py-5 shadow-[0_16px_32px_rgba(26,28,25,0.05)]"
            >
              <div className="text-[1.35rem] font-extrabold tracking-[-0.04em] text-[var(--on-surface)]">{section.title}</div>
              <div className="mt-4 space-y-3">
                {section.links.map((link) => (
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
