import { useEffect, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { fetchAuditLogs, type AuditLogPage } from '../../services/auditLogService'
import { fetchStaffStores, type StaffStoreRecord } from '../../services/staffAdminService'
import { useCurrentStore } from '../store/StoreContext'

function today() {
  return new Date().toISOString().slice(0, 10)
}

export function AuditLogsPage() {
  const { isOwner } = useAuth()
  const { storeId } = useCurrentStore()
  const [stores, setStores] = useState<StaffStoreRecord[]>([])
  const [selectedStoreId, setSelectedStoreId] = useState<string>(String(storeId))
  const [date, setDate] = useState(today())
  const [actor, setActor] = useState('')
  const [action, setAction] = useState('')
  const [data, setData] = useState<AuditLogPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const loadStores = async () => {
      const nextStores = await fetchStaffStores()
      setStores(nextStores)
      setSelectedStoreId(String(nextStores.find((store) => store.id === storeId)?.id ?? storeId))
    }
    void loadStores().catch((exception) => setError(exception instanceof Error ? exception.message : 'Failed to load stores'))
  }, [isOwner, storeId])

  useEffect(() => {
    setSelectedStoreId(String(storeId))
  }, [storeId])

  const loadLogs = async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await fetchAuditLogs({
        storeId: selectedStoreId ? Number(selectedStoreId) : null,
        date,
        actor: actor.trim() || undefined,
        action: action.trim() || undefined,
      }))
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Failed to load audit logs')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadLogs()
  }, [selectedStoreId, date])

  return (
    <div className="space-y-5 text-[var(--on-surface)]">
        <div className="rounded-[28px] bg-white px-6 py-5 shadow-[0_18px_34px_rgba(26,28,25,0.06)]">
          <div className="text-[2rem] font-black tracking-[-0.05em]">Audit Logs</div>
          <div className="mt-4 flex flex-wrap gap-3">
            <select disabled={!isOwner || stores.length <= 1} value={selectedStoreId} onChange={(event) => setSelectedStoreId(event.target.value)} className="h-12 rounded-[16px] border bg-white px-4 font-bold">
              {stores.map((store) => <option key={store.id} value={store.id}>{store.name}</option>)}
            </select>
            <input type="date" value={date} onChange={(event) => setDate(event.target.value)} className="h-12 rounded-[16px] border px-4 font-bold" />
            <input value={actor} onChange={(event) => setActor(event.target.value)} placeholder="Actor" className="h-12 rounded-[16px] border px-4 font-semibold" />
            <input value={action} onChange={(event) => setAction(event.target.value)} placeholder="Action" className="h-12 rounded-[16px] border px-4 font-semibold" />
            <button type="button" onClick={() => void loadLogs()} className="h-12 rounded-[16px] bg-[var(--primary)] px-5 font-black text-white">Search</button>
          </div>
          {error ? <div className="mt-4 rounded-[16px] bg-red-50 px-4 py-3 font-bold text-red-700">{error}</div> : null}
        </div>

        <div className="rounded-[28px] bg-white p-4 shadow-[0_18px_34px_rgba(26,28,25,0.06)]">
          {loading ? (
            <div className="px-4 py-8 font-bold text-[var(--muted)]">Loading audit logs...</div>
          ) : (
            <div className="space-y-3">
              {(data?.records ?? []).map((record) => (
                <div key={record.id} className="rounded-[20px] bg-[rgba(26,28,25,0.04)] px-4 py-4">
                  <div className="flex flex-wrap justify-between gap-3">
                    <div className="font-black">{record.action}</div>
                    <div className="font-semibold text-[var(--muted)]">{new Date(record.created_at).toLocaleString()}</div>
                  </div>
                  <div className="mt-1 text-[0.95rem] font-semibold">{record.summary}</div>
                  <div className="mt-2 text-[0.82rem] text-[var(--muted)]">
                    Actor: {record.actor_name_snapshot ?? record.actor_user_id ?? 'system'} · Role: {record.actor_role_snapshot ?? 'unknown'} · Entity: {record.entity_type ?? '-'} #{record.entity_id ?? '-'}
                  </div>
                </div>
              ))}
              {!data?.records?.length ? <div className="px-4 py-8 font-bold text-[var(--muted)]">No audit logs for this filter.</div> : null}
            </div>
          )}
        </div>
    </div>
  )
}
