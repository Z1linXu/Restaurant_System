import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import {
  createStaff,
  deactivateStaff,
  fetchStaff,
  fetchStaffStores,
  reactivateStaff,
  resetStaffPassword,
  updateStaff,
  type StaffStoreRecord,
  type StaffUserPayload,
  type StaffUserRecord,
} from '../../services/staffAdminService'
import { useCurrentStore } from '../store/StoreContext'

type StaffDraft = StaffUserPayload & { id?: number }

const blankDraft = (storeId: number, role = 'FRONTDESK'): StaffDraft => ({
  store_id: storeId,
  username: '',
  full_name: '',
  phone: '',
  role_code: role,
  password: '',
})

export function StaffManagementPage() {
  const { isOwner, isManager } = useAuth()
  const { storeId } = useCurrentStore()
  const [stores, setStores] = useState<StaffStoreRecord[]>([])
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(storeId)
  const [staff, setStaff] = useState<StaffUserRecord[]>([])
  const [draft, setDraft] = useState<StaffDraft | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const roleOptions = useMemo(() => (isOwner ? ['MANAGER', 'FRONTDESK'] : ['FRONTDESK']), [isOwner])

  const loadStores = async () => {
    const nextStores = await fetchStaffStores()
    setStores(nextStores)
    setSelectedStoreId((current) => current ?? nextStores.find((store) => store.id === storeId)?.id ?? nextStores[0]?.id ?? storeId)
    if (!nextStores.length) {
      setStaff([])
    }
  }

  const loadStaff = async (storeId: number) => {
    setStaff(await fetchStaff(storeId))
  }

  useEffect(() => {
    setSelectedStoreId(storeId)
  }, [storeId])

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        await loadStores()
      } catch (exception) {
        setError(exception instanceof Error ? exception.message : 'Failed to load staff stores')
      } finally {
        setLoading(false)
      }
    }
    void load()
  }, [])

  useEffect(() => {
    if (!selectedStoreId) {
      return
    }
    void loadStaff(selectedStoreId).catch((exception) => setError(exception instanceof Error ? exception.message : 'Failed to load staff'))
  }, [selectedStoreId])

  const saveDraft = async () => {
    if (!draft) {
      return
    }
    setError(null)
    try {
      if (draft.id) {
        await updateStaff(draft.id, { ...draft, password: undefined })
        setMessage('Staff updated')
      } else {
        await createStaff(draft)
        setMessage('Staff created')
      }
      setDraft(null)
      if (selectedStoreId) {
        await loadStaff(selectedStoreId)
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Failed to save staff')
    }
  }

  const handleStatus = async (record: StaffUserRecord) => {
    try {
      if (record.status === 'active') {
        await deactivateStaff(record.id)
        setMessage('Staff deactivated')
      } else {
        await reactivateStaff(record.id)
        setMessage('Staff reactivated')
      }
      if (selectedStoreId) {
        await loadStaff(selectedStoreId)
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Failed to update status')
    }
  }

  const handleResetPassword = async (record: StaffUserRecord) => {
    const nextPassword = window.prompt(`New password for ${record.username}`)
    if (!nextPassword) {
      return
    }
    try {
      await resetStaffPassword(record.id, nextPassword)
      setMessage('Password reset')
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Failed to reset password')
    }
  }

  return (
    <div className="space-y-5 text-[var(--on-surface)]">
        <div className="rounded-[28px] bg-white px-6 py-5 shadow-[0_18px_34px_rgba(26,28,25,0.06)]">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <div className="text-[2rem] font-black tracking-[-0.05em]">Staff Management</div>
              <div className="text-[0.95rem] font-medium text-[var(--muted)]">
                {isOwner ? 'Owner can manage manager and frontdesk staff.' : 'Manager can manage frontdesk staff in this store.'}
              </div>
            </div>
            <div className="flex flex-wrap gap-3">
              <select
                disabled={!isOwner || stores.length <= 1}
                value={selectedStoreId ?? ''}
                onChange={(event) => setSelectedStoreId(Number(event.target.value))}
                className="h-12 rounded-[16px] border border-[rgba(26,28,25,0.12)] bg-white px-4 font-bold"
              >
                {stores.map((store) => (
                  <option key={store.id} value={store.id}>
                    {store.name}
                  </option>
                ))}
              </select>
              <button
                type="button"
                disabled={!selectedStoreId}
                onClick={() => selectedStoreId && setDraft(blankDraft(selectedStoreId, roleOptions[0]))}
                className="h-12 rounded-[16px] bg-[var(--primary)] px-5 font-black text-white disabled:opacity-50"
              >
                New Staff
              </button>
            </div>
          </div>
          {message ? <div className="mt-4 rounded-[16px] bg-emerald-50 px-4 py-3 font-bold text-emerald-700">{message}</div> : null}
          {error ? <div className="mt-4 rounded-[16px] bg-red-50 px-4 py-3 font-bold text-red-700">{error}</div> : null}
        </div>

        {draft ? (
          <div className="rounded-[28px] bg-white px-6 py-5 shadow-[0_18px_34px_rgba(26,28,25,0.06)]">
            <div className="text-[1.2rem] font-black">{draft.id ? 'Edit Staff' : 'Create Staff'}</div>
            <div className="mt-4 grid gap-3 md:grid-cols-2">
              <input className="h-12 rounded-[16px] border px-4 font-semibold" placeholder="Username" value={draft.username} onChange={(event) => setDraft({ ...draft, username: event.target.value })} />
              <input className="h-12 rounded-[16px] border px-4 font-semibold" placeholder="Display name" value={draft.full_name ?? ''} onChange={(event) => setDraft({ ...draft, full_name: event.target.value })} />
              <input className="h-12 rounded-[16px] border px-4 font-semibold" placeholder="Phone" value={draft.phone ?? ''} onChange={(event) => setDraft({ ...draft, phone: event.target.value })} />
              <select className="h-12 rounded-[16px] border px-4 font-semibold" value={draft.role_code} onChange={(event) => setDraft({ ...draft, role_code: event.target.value })}>
                {roleOptions.map((role) => (
                  <option key={role} value={role}>{role}</option>
                ))}
              </select>
              {!draft.id ? (
                <input className="h-12 rounded-[16px] border px-4 font-semibold md:col-span-2" placeholder="Temporary password" type="password" value={draft.password ?? ''} onChange={(event) => setDraft({ ...draft, password: event.target.value })} />
              ) : null}
            </div>
            <div className="mt-4 flex gap-3">
              <button type="button" onClick={() => void saveDraft()} className="h-11 rounded-[15px] bg-[var(--primary)] px-5 font-black text-white">Save</button>
              <button type="button" onClick={() => setDraft(null)} className="h-11 rounded-[15px] bg-[rgba(26,28,25,0.06)] px-5 font-bold">Cancel</button>
            </div>
          </div>
        ) : null}

        <div className="rounded-[28px] bg-white p-4 shadow-[0_18px_34px_rgba(26,28,25,0.06)]">
          {loading ? (
            <div className="px-4 py-8 font-bold text-[var(--muted)]">Loading staff...</div>
          ) : !stores.length ? (
            <div className="px-4 py-8 font-bold text-[var(--muted)]">No stores are available for this account.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[820px] border-separate border-spacing-y-2">
                <thead className="text-left text-[0.8rem] uppercase tracking-[0.14em] text-[var(--muted)]">
                  <tr><th className="px-4 py-2">Name</th><th>Username</th><th>Role</th><th>Status</th><th>Actions</th></tr>
                </thead>
                <tbody>
                  {staff.map((record) => (
                    <tr key={record.id} className="bg-[rgba(26,28,25,0.04)]">
                      <td className="rounded-l-[18px] px-4 py-3 font-black">{record.full_name || record.username}</td>
                      <td className="font-semibold">{record.username}</td>
                      <td className="font-semibold">{record.role_code}</td>
                      <td className={record.status === 'active' ? 'font-bold text-emerald-700' : 'font-bold text-red-700'}>{record.status}</td>
                      <td className="rounded-r-[18px] py-3">
                        <div className="flex flex-wrap gap-2">
                          <button type="button" className="rounded-[12px] bg-white px-3 py-2 font-bold" onClick={() => setDraft({ id: record.id, store_id: record.store_id, username: record.username, full_name: record.full_name, phone: record.phone, role_code: isManager ? 'FRONTDESK' : record.role_code })}>Edit</button>
                          <button type="button" className="rounded-[12px] bg-white px-3 py-2 font-bold" onClick={() => void handleResetPassword(record)}>Reset Password</button>
                          <button type="button" className="rounded-[12px] bg-[rgba(97,0,0,0.08)] px-3 py-2 font-bold text-[var(--primary)]" onClick={() => void handleStatus(record)}>{record.status === 'active' ? 'Deactivate' : 'Reactivate'}</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!staff.length ? (
                <div className="px-4 py-8 font-bold text-[var(--muted)]">No staff accounts for this store yet.</div>
              ) : null}
            </div>
          )}
        </div>
    </div>
  )
}
