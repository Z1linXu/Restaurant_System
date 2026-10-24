import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { DINING_TABLES_CHANGED_EVENT, requireActiveDiningTable } from '../../services/frontdeskConfigService'

/** Display snapshots are never an ordering permission. Keep any opened workspace mounted to retain its draft. */
export function DiningTableOrderGate({ storeId, tableNo, enabled, onBack, children }: {
  storeId: number; tableNo: string; enabled: boolean; onBack: () => void; children: ReactNode
}) {
  const key = `${storeId}:${tableNo}`
  const [checked, setChecked] = useState<{ key: string; allowed: boolean; message: string } | null>(null)
  const [openedKey, setOpenedKey] = useState<string | null>(null)
  const generation = useRef(0)
  const verify = useCallback(async () => {
    const request = ++generation.current
    await Promise.resolve()
    if (request !== generation.current || !enabled) return
    setChecked({ key, allowed: false, message: '正在确认桌台状态…' })
    try {
      await requireActiveDiningTable(storeId, tableNo)
      if (request !== generation.current) return
      setOpenedKey(key)
      setChecked({ key, allowed: true, message: '' })
    } catch (error) {
      if (request === generation.current) setChecked({ key, allowed: false, message: error instanceof Error ? error.message : '无法确认桌台状态；本机草稿已保留。' })
    }
  }, [enabled, key, storeId, tableNo])
  useEffect(() => {
    if (!enabled) return
    let active = true
    void Promise.resolve().then(() => { if (active) void verify() })
    const changed = (event: Event) => {
      if ((event as CustomEvent<{ storeId: number }>).detail?.storeId === storeId) void verify()
    }
    const visible = () => { if (document.visibilityState === 'visible') void verify() }
    window.addEventListener('focus', verify)
    window.addEventListener('online', verify)
    window.addEventListener('offline', verify)
    window.addEventListener(DINING_TABLES_CHANGED_EVENT, changed)
    document.addEventListener('visibilitychange', visible)
    return () => {
      active = false
      generation.current += 1
      window.removeEventListener('focus', verify)
      window.removeEventListener('online', verify)
      window.removeEventListener('offline', verify)
      window.removeEventListener(DINING_TABLES_CHANGED_EVENT, changed)
      document.removeEventListener('visibilitychange', visible)
    }
  }, [enabled, storeId, verify])
  if (!enabled) return children
  const allowed = checked?.key === key && checked.allowed
  return <>
    {!allowed ? <div className="mx-auto my-8 max-w-xl space-y-4 rounded-[24px] bg-white p-6" role="alert">
      <p className="text-lg font-bold">{checked?.key === key ? checked.message : '正在确认桌台状态…'}</p>
      <p className="text-sm text-stone-600">草稿和待提交记录会保留。请确认网络及桌台配置后重试。</p>
      <button type="button" onClick={() => void verify()} className="min-h-12 rounded-xl bg-stone-100 px-4 font-bold">重试</button>
      <button type="button" onClick={onBack} className="ml-3 min-h-12 rounded-xl bg-stone-100 px-4 font-bold">返回桌台</button>
    </div> : null}
    {openedKey === key ? <div hidden={!allowed}>{children}</div> : null}
  </>
}
