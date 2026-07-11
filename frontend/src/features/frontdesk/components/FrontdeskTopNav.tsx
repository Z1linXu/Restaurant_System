import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { navigateTo } from '../navigation'
import { useAuth } from '../../auth/useAuth'
import { isFeatureEnabled, type FeaturePackage } from '../../feature-flags/featureConfig'
import { StoreSwitcher } from '../../store/StoreSwitcher'
import { buildStorePath } from '../../store/storeRoutes'
import { useOptionalCurrentStore } from '../../store/StoreContext'
import {
  getAndroidPadDeviceBridge,
  parseAndroidBridgeJson,
  type AndroidPadDeviceStatus,
  type AndroidPadPrintWorkerStatus,
} from '../../../types/androidPadBridge'
import type { AuthUser } from '../../../services/authService'

interface FrontdeskTopNavProps {
  activeItem?: 'menu' | 'orders' | 'pickup' | 'stations' | 'dashboard' | null
}

const navItems = [
  { id: 'orders', label: 'Orders', icon: '▤', feature: 'CORE_POS' },
  { id: 'menu', label: 'Menu', icon: '✕', feature: 'CORE_POS' },
  { id: 'pickup', label: 'Pickup', icon: '◉', feature: 'KDS' },
  { id: 'dashboard', label: 'Dashboard', icon: '◫', feature: 'ADMIN' },
] as const satisfies Array<{
  id: 'menu' | 'orders' | 'pickup' | 'dashboard'
  label: string
  icon: string
  feature: FeaturePackage
}>

export function FrontdeskTopNav({ activeItem = null }: FrontdeskTopNavProps) {
  const currentStore = useOptionalCurrentStore()
  const { user, signOut, permissions, features } = useAuth()
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false)
  const [userMenuPosition, setUserMenuPosition] = useState({ top: 0, left: 0 })
  const userMenuButtonRef = useRef<HTMLButtonElement | null>(null)
  const userMenuPanelRef = useRef<HTMLDivElement | null>(null)
  const path = (target: string) => currentStore ? buildStorePath(currentStore.storeId, target) : target
  const role = user?.role_code?.toUpperCase()
  const canSeeAdminDashboard = role === 'OWNER' || role === 'ADMIN' || role === 'MANAGER'

  const updateUserMenuPosition = () => {
    const rect = userMenuButtonRef.current?.getBoundingClientRect()
    if (!rect) {
      return
    }
    const menuWidth = 288
    setUserMenuPosition({
      top: rect.bottom + 8,
      left: Math.min(Math.max(12, rect.left), Math.max(12, window.innerWidth - menuWidth - 12)),
    })
  }

  useEffect(() => {
    if (!userMenuOpen) {
      return
    }
    updateUserMenuPosition()
    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node
      if (!userMenuButtonRef.current?.contains(target) && !userMenuPanelRef.current?.contains(target)) {
        setUserMenuOpen(false)
      }
    }
    const handleReposition = () => updateUserMenuPosition()
    window.addEventListener('pointerdown', handlePointerDown)
    window.addEventListener('resize', handleReposition)
    window.addEventListener('scroll', handleReposition, true)
    return () => {
      window.removeEventListener('pointerdown', handlePointerDown)
      window.removeEventListener('resize', handleReposition)
      window.removeEventListener('scroll', handleReposition, true)
    }
  }, [userMenuOpen])

  const openTool = (target: string) => {
    setUserMenuOpen(false)
    navigateTo(path(target))
  }

  const handleLogout = () => {
    setUserMenuOpen(false)
    setLogoutConfirmOpen(true)
  }

  const openProfile = () => {
    setUserMenuOpen(false)
    setProfileOpen(true)
  }

  const confirmLogout = () => {
    setLogoutConfirmOpen(false)
    void signOut().finally(() => navigateTo('/login'))
  }

  return (
    <div className="flex items-center justify-between gap-3 rounded-[22px] bg-[rgba(255,255,255,0.78)] px-4 py-2.5 shadow-[0_10px_22px_rgba(26,28,25,0.05)] backdrop-blur-sm">
      <div className="flex items-center gap-3">
        <div className="relative">
          <button
            ref={userMenuButtonRef}
            type="button"
            aria-label="用户菜单 / User menu"
            aria-expanded={userMenuOpen}
            className="flex min-h-11 min-w-11 items-center justify-center rounded-full bg-[rgba(97,0,0,0.12)] text-[1.15rem] transition hover:bg-[rgba(97,0,0,0.18)] focus:outline-none focus:ring-2 focus:ring-[rgba(97,0,0,0.28)]"
            onClick={() => {
              updateUserMenuPosition()
              setUserMenuOpen((open) => !open)
            }}
          >
            👨🏻‍🍳
          </button>
          {userMenuOpen ? createPortal(
            <div
              ref={userMenuPanelRef}
              className="fixed z-[9999] w-72 rounded-[22px] border border-[rgba(97,0,0,0.08)] bg-white p-3 text-[var(--on-surface)] shadow-[0_22px_54px_rgba(26,28,25,0.18)]"
              style={{ top: userMenuPosition.top, left: userMenuPosition.left }}
            >
              <div className="rounded-[18px] bg-[rgba(97,0,0,0.05)] px-3 py-3">
                <div className="text-[0.95rem] font-black">{user?.full_name || user?.username || '当前用户'}</div>
                <div className="mt-1 text-[0.78rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">
                  {user?.role_code || 'UNKNOWN'}
                </div>
              </div>
              <div className="mt-2 space-y-1">
                <button
                  type="button"
                  className="flex min-h-12 w-full items-center justify-between rounded-[16px] px-3 text-left text-[0.98rem] font-bold text-[rgba(26,28,25,0.84)] transition hover:bg-[rgba(97,0,0,0.06)]"
                  onClick={openProfile}
                >
                  <span>个人信息</span>
                  <span className="text-[0.78rem] font-semibold text-[var(--muted)]">Profile</span>
                </button>
                <button
                  type="button"
                  className="flex min-h-12 w-full items-center justify-between rounded-[16px] px-3 text-left text-[0.98rem] font-bold text-[rgba(26,28,25,0.84)] transition hover:bg-[rgba(97,0,0,0.06)]"
                  onClick={() => openTool('/admin/settings/printing')}
                >
                  <span>打印设置</span>
                  <span className="text-[0.78rem] font-semibold text-[var(--muted)]">Printing</span>
                </button>
                <button
                  type="button"
                  className="flex min-h-12 w-full items-center justify-between rounded-[16px] px-3 text-left text-[0.98rem] font-bold text-[rgba(26,28,25,0.84)] transition hover:bg-[rgba(97,0,0,0.06)]"
                  onClick={() => openTool('/admin/menu/items')}
                >
                  <span>菜单管理</span>
                  <span className="text-[0.78rem] font-semibold text-[var(--muted)]">Menu</span>
                </button>
                <button
                  type="button"
                  className="flex min-h-12 w-full items-center justify-between rounded-[16px] px-3 text-left text-[0.98rem] font-black text-[var(--primary)] transition hover:bg-[rgba(97,0,0,0.08)]"
                  onClick={handleLogout}
                >
                  <span>退出登录</span>
                  <span className="text-[0.78rem] font-semibold text-[var(--muted)]">Logout</span>
                </button>
              </div>
            </div>,
            document.body,
          ) : null}
          {logoutConfirmOpen ? createPortal(
            <div
              className="fixed inset-0 z-[10000] flex items-center justify-center bg-[rgba(26,28,25,0.34)] p-4 backdrop-blur-sm"
              onClick={() => setLogoutConfirmOpen(false)}
            >
              <div
                className="w-full max-w-[360px] rounded-[26px] bg-white p-5 text-[var(--on-surface)] shadow-[0_24px_64px_rgba(26,28,25,0.2)]"
                onClick={(event) => event.stopPropagation()}
              >
                <div className="text-[1.35rem] font-black tracking-[-0.04em]">退出登录</div>
                <div className="mt-2 text-[0.95rem] font-semibold text-[var(--muted)]">确定要退出当前账号吗？</div>
                <div className="mt-5 grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    className="min-h-12 rounded-[18px] bg-[rgba(26,28,25,0.06)] px-4 text-[0.98rem] font-bold text-[var(--muted)]"
                    onClick={() => setLogoutConfirmOpen(false)}
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    className="min-h-12 rounded-[18px] bg-[var(--primary)] px-4 text-[0.98rem] font-black text-white shadow-[0_14px_28px_rgba(97,0,0,0.2)]"
                    onClick={confirmLogout}
                  >
                    退出登录
                  </button>
                </div>
              </div>
            </div>,
            document.body,
          ) : null}
          {profileOpen ? createPortal(
            <StaffProfileModal
              currentStoreName={currentStore?.storeName ?? null}
              features={features}
              onClose={() => setProfileOpen(false)}
              permissions={permissions}
              user={user}
            />,
            document.body,
          ) : null}
        </div>
        <div>
          <p className="font-display text-[1.2rem] font-extrabold tracking-[-0.04em] text-[var(--primary)]">蘭</p>
          <p className="text-[0.72rem] text-[var(--muted)]">{currentStore?.storeName ?? 'Frontdesk Workstation'}</p>
        </div>
      </div>

      <nav className="flex items-center gap-2">
        <StoreSwitcher compact />
        {navItems.filter((item) => isFeatureEnabled(item.feature) && (item.id !== 'dashboard' || canSeeAdminDashboard)).map((item) => {
          const active = item.id === activeItem
          return (
            <button
              key={item.id}
              type="button"
              className={`inline-flex min-h-10 items-center gap-2 rounded-[16px] px-3.5 text-[0.88rem] font-semibold transition ${
                active
                  ? 'bg-[var(--surface-container-lowest)] text-[var(--primary)] shadow-[0_10px_22px_rgba(26,28,25,0.05)]'
                  : 'text-[rgba(26,28,25,0.7)] hover:bg-[rgba(26,28,25,0.04)]'
              }`}
              onClick={() => {
                if (item.id === 'orders') {
                  navigateTo(path('/frontdesk/order'))
                  return
                }
                if (item.id === 'pickup') {
                  navigateTo(path('/pickup'))
                  return
                }
                if (item.id === 'menu') {
                  navigateTo(path('/frontdesk'))
                  return
                }
                if (item.id === 'dashboard') {
                  navigateTo(path('/admin/dashboard'))
                }
              }}
            >
              <span className="text-[1.05rem] leading-none">{item.icon}</span>
              <span>{item.label}</span>
            </button>
          )
        })}
      </nav>
    </div>
  )
}

function StaffProfileModal({
  currentStoreName,
  features,
  onClose,
  permissions,
  user,
}: {
  currentStoreName: string | null
  features: Record<string, boolean>
  onClose: () => void
  permissions: string[]
  user: AuthUser | null
}) {
  const [bridgeAvailable, setBridgeAvailable] = useState(false)
  const [deviceStatus, setDeviceStatus] = useState<AndroidPadDeviceStatus | null>(null)
  const [workerStatus, setWorkerStatus] = useState<AndroidPadPrintWorkerStatus | null>(null)

  const refreshAndroidState = () => {
    const bridge = getAndroidPadDeviceBridge()
    setBridgeAvailable(Boolean(bridge))
    setDeviceStatus(bridge ? parseAndroidBridgeJson<AndroidPadDeviceStatus>(bridge.getDeviceStatus()) : null)
    setWorkerStatus(bridge?.getPrintWorkerStatus ? parseAndroidBridgeJson<AndroidPadPrintWorkerStatus>(bridge.getPrintWorkerStatus()) : null)
  }

  useEffect(() => {
    refreshAndroidState()
    const intervalId = window.setInterval(refreshAndroidState, 5000)
    return () => window.clearInterval(intervalId)
  }, [])

  const enabledFeatures = Object.entries(features ?? {})
    .filter(([, enabled]) => enabled)
    .map(([feature]) => feature)
  const storeName = user?.store_name ?? currentStoreName ?? '-'
  const androidStoreId = deviceStatus?.store_id ?? deviceStatus?.device_store_id ?? null

  return (
    <div
      className="fixed inset-0 z-[10000] flex items-center justify-center bg-[rgba(26,28,25,0.34)] p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="max-h-[88vh] w-full max-w-[620px] overflow-auto rounded-[26px] bg-white p-5 text-[var(--on-surface)] shadow-[0_24px_64px_rgba(26,28,25,0.22)]"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="text-[1.35rem] font-black tracking-[-0.04em]">个人信息</div>
            <div className="mt-1 text-[0.9rem] font-semibold text-[var(--muted)]">
              {user?.full_name || user?.username || '当前用户'}
            </div>
          </div>
          <button
            type="button"
            className="min-h-10 rounded-[14px] bg-[rgba(26,28,25,0.06)] px-3 text-[0.86rem] font-bold text-[var(--on-surface)]"
            onClick={onClose}
          >
            关闭
          </button>
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          <ProfileRow label="User ID" value={user?.id ?? '-'} />
          <ProfileRow label="Username" value={user?.username ?? '-'} />
          <ProfileRow label="Full name" value={user?.full_name ?? '-'} />
          <ProfileRow label="Role" value={user?.role_code ?? '-'} />
          <ProfileRow label="Store" value={`${user?.store_id ?? '-'} · ${storeName}`} />
          <ProfileRow label="Organization" value={user?.organization_id ?? '-'} />
        </div>

        <div className="mt-5">
          <div className="text-[0.82rem] font-black uppercase tracking-[0.14em] text-[var(--muted)]">Capabilities</div>
          <div className="mt-2 flex max-h-32 flex-wrap gap-2 overflow-auto">
            {permissions.length ? permissions.map((permission) => (
              <span key={permission} className="rounded-full bg-[rgba(26,28,25,0.06)] px-2.5 py-1 text-[0.72rem] font-bold text-[rgba(26,28,25,0.74)]">
                {permission}
              </span>
            )) : (
              <span className="text-[0.84rem] font-semibold text-[var(--muted)]">-</span>
            )}
          </div>
        </div>

        <div className="mt-5">
          <div className="text-[0.82rem] font-black uppercase tracking-[0.14em] text-[var(--muted)]">Features</div>
          <div className="mt-2 flex flex-wrap gap-2">
            {enabledFeatures.length ? enabledFeatures.map((feature) => (
              <span key={feature} className="rounded-full bg-[rgba(18,141,77,0.1)] px-2.5 py-1 text-[0.72rem] font-bold text-[rgb(25,112,69)]">
                {feature}
              </span>
            )) : (
              <span className="text-[0.84rem] font-semibold text-[var(--muted)]">-</span>
            )}
          </div>
        </div>

        <div className="mt-5 border-t border-[rgba(26,28,25,0.08)] pt-4">
          <div className="text-[0.82rem] font-black uppercase tracking-[0.14em] text-[var(--muted)]">Android Pad</div>
          {!bridgeAvailable ? (
            <div className="mt-2 rounded-[16px] bg-[rgba(26,28,25,0.05)] px-4 py-3 text-[0.86rem] font-semibold text-[var(--muted)]">
              当前是普通浏览器，没有 Android Pad 原生设备状态。
            </div>
          ) : (
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <ProfileRow label="Paired" value={deviceStatus?.paired ? 'yes' : 'no'} />
              <ProfileRow label="Device" value={deviceStatus?.device_id ?? '-'} />
              <ProfileRow label="Device name" value={deviceStatus?.device_name ?? '-'} />
              <ProfileRow label="Store" value={androidStoreId ?? '-'} />
              <ProfileRow label="Token" value={deviceStatus?.token_last4 ? `****${deviceStatus.token_last4}` : '-'} />
              <ProfileRow label="Registered" value={deviceStatus?.registered_at ?? '-'} />
              <ProfileRow label="App" value={deviceStatus?.app_version ?? '-'} />
              <ProfileRow label="Platform" value={deviceStatus?.platform ?? '-'} />
              <ProfileRow label="Worker" value={workerStatus?.worker_state_label ?? workerStatus?.worker_state ?? '-'} />
              <ProfileRow label="Auto print" value={workerStatus?.auto_enabled == null ? '-' : workerStatus.auto_enabled ? 'on' : 'off'} />
              <ProfileRow label="Worker running" value={workerStatus?.worker_running == null ? '-' : workerStatus.worker_running ? 'yes' : 'no'} />
              <ProfileRow label="Last error" value={workerStatus?.last_error ?? '-'} />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function ProfileRow({ label, value }: { label: string; value: string | number | boolean | null | undefined }) {
  return (
    <div className="min-w-0 rounded-[16px] bg-[rgba(26,28,25,0.04)] px-3 py-2.5">
      <div className="text-[0.72rem] font-bold uppercase tracking-[0.12em] text-[var(--muted)]">{label}</div>
      <div className="mt-1 break-words text-[0.94rem] font-black text-[var(--on-surface)]">{String(value ?? '-')}</div>
    </div>
  )
}
