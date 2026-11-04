import { ApiRequestError } from '../../services/apiClient'
import { fetchUberInbox, type UberInboxFeed, type UberInboxPage, type UberOrder } from '../../services/uberEatsService'
import { subscribeToFrontdeskOrders } from '../../services/orderService'

export type InboxBucket = 'today' | 'attention'
interface Snapshot { feed: UberInboxFeed | null; loading: boolean; error: string | null }
const empty: Snapshot = { feed: null, loading: true, error: null }
export const disabledInbox: Snapshot = { feed: null, loading: false, error: null }
export const emptyInboxPage: UberInboxPage = { orders: [], has_more: false, next_before_id: null }
const entries = new Map<string, InboxSubscription>()
export const uniqueUberOrders = (rows: UberOrder[]) => [...new Map(rows.map(row => [row.id, row])).values()]

/** One request stream, fallback timer and realtime client per authenticated Store workspace. */
class InboxSubscription {
  snapshot: Snapshot = empty
  private listeners = new Set<() => void>()
  private generation = 0
  private active = false
  private inFlight = false
  private queued = false
  private controller: AbortController | null = null
  private timer: number | undefined
  private unsubscribeRealtime: (() => void) | undefined
  private depths = { today: 1, attention: 1 }

  private key: string
  private storeId: number
  constructor(key: string, storeId: number) { this.key = key; this.storeId = storeId }
  getSnapshot = () => this.snapshot
  private publish(snapshot: Snapshot) {
    this.snapshot = snapshot
    this.listeners.forEach(listener => listener())
  }
  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    if (!this.active) this.start()
    return () => {
      this.listeners.delete(listener)
      if (this.listeners.size === 0) {
        this.stop()
        queueMicrotask(() => { if (!this.active && entries.get(this.key) === this) entries.delete(this.key) })
      }
    }
  }
  private start() {
    entries.set(this.key, this)
    this.active = true
    this.generation++
    this.inFlight = false
    void this.load()
    this.timer = window.setInterval(() => { if (!document.hidden) void this.load() }, 5000)
    this.unsubscribeRealtime = subscribeToFrontdeskOrders(this.storeId, this.refresh, {
      onLifecycle: event => { if (event.phase === 'CONNECTED') this.refresh() },
    })
    window.addEventListener('online', this.refresh)
    window.addEventListener('restaurant-auth-expired', this.expire)
    document.addEventListener('visibilitychange', this.visible)
  }
  private stop() {
    this.active = false
    this.generation++
    this.controller?.abort()
    this.controller = null
    this.queued = false
    if (this.timer !== undefined) window.clearInterval(this.timer)
    this.timer = undefined
    this.unsubscribeRealtime?.()
    this.unsubscribeRealtime = undefined
    window.removeEventListener('online', this.refresh)
    window.removeEventListener('restaurant-auth-expired', this.expire)
    document.removeEventListener('visibilitychange', this.visible)
    this.snapshot = empty
    this.depths = { today: 1, attention: 1 }
  }
  private visible = () => { if (!document.hidden) this.refresh() }
  private expire = () => {
    this.stop()
    this.publish({ feed: null, loading: false, error: '登录已过期，请重新登录。' })
  }
  refresh = () => {
    if (!this.active) return
    if (this.inFlight) { this.queued = true; return }
    void this.load()
  }
  loadMore = (bucket: InboxBucket) => {
    if (this.inFlight || !this.snapshot.feed?.[bucket].has_more) return
    this.depths[bucket]++
    this.refresh()
  }
  private async load() {
    if (!this.active || this.inFlight) return
    this.inFlight = true
    const generation = this.generation
    const controller = new AbortController()
    this.controller = controller
    try {
      const feed = await fetchUberInbox(this.storeId, { limit: 100 }, controller.signal)
      if (!this.active || generation !== this.generation || controller.signal.aborted) return
      // Rebuild explicit pagination after inserts/status changes so older visible work remains live.
      for (let page = 1; page < Math.max(this.depths.today, this.depths.attention); page++) {
        const todayCursor = page < this.depths.today && feed.today.has_more ? feed.today.next_before_id : null
        const attentionCursor = page < this.depths.attention && feed.attention.has_more ? feed.attention.next_before_id : null
        if (todayCursor == null && attentionCursor == null) break
        const next = await fetchUberInbox(this.storeId, {
          limit: 100, today_before_id: todayCursor ?? undefined, attention_before_id: attentionCursor ?? undefined,
        }, controller.signal)
        if (!this.active || generation !== this.generation || controller.signal.aborted) return
        if (next.business_date !== feed.business_date) {
          this.depths = { today: 1, attention: 1 }
          if (this.active && generation === this.generation) this.publish({ feed: next, loading: false, error: null })
          return
        }
        for (const bucket of ['today', 'attention'] as const) {
          if ((bucket === 'today' ? todayCursor : attentionCursor) != null) {
            feed[bucket] = { ...next[bucket], orders: uniqueUberOrders([...feed[bucket].orders, ...next[bucket].orders]) }
          }
        }
      }
      if (this.active && generation === this.generation) this.publish({ feed, loading: false, error: null })
    } catch (error) {
      if (!this.active || generation !== this.generation || controller.signal.aborted) return
      const denied = error instanceof ApiRequestError && (error.status === 401 || error.status === 403)
      this.publish({ feed: denied ? null : this.snapshot.feed, loading: false, error: error instanceof Error ? error.message : '无法加载 Uber 订单' })
    } finally {
      if (generation === this.generation) {
        this.inFlight = false
        this.controller = null
        if (this.queued) { this.queued = false; void this.load() }
      }
    }
  }
}

export function sharedUberInbox(key: string, storeId: number) {
  let entry = entries.get(key)
  if (!entry) { entry = new InboxSubscription(key, storeId); entries.set(key, entry) }
  return entry
}
