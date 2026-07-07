# PAD_DIRECT Restaurant Pilot Checklist

This checklist is for restaurant field testing before PAD_DIRECT is treated as
stable production printing. It does not change printing semantics: failed jobs
are not automatically reprinted or requeued, and Android still runs a foreground
semi-auto worker rather than a background daemon.

## Before Going To The Restaurant

1. Confirm each printer has a static IP or DHCP reservation.
2. Confirm Print Center assignments:
   - `GRAB` -> kitchen printer
   - `HOT_KITCHEN` -> hot kitchen printer, or the same kitchen printer if that is the pilot setup
   - `FRONTDESK_RECEIPT` -> receipt printer
3. Confirm the Android Pad is paired in Print Center.
4. Confirm store printing mode is `PAD_DIRECT`.
5. Open Android Local Control Panel and confirm:
   - Auto processing is enabled.
   - Worker state is `WAITING` or `POLLING`.
   - `lastPollAt` keeps updating.
   - `pollScheduled=true` when idle.
   - `consecutiveErrors=0`.
6. Use Local Control Panel printer testing from the same Pad for every assigned printer endpoint.
7. In Print Center, confirm there are no old `PENDING` jobs and no stale `PRINTING` jobs.
8. Confirm backend logs do not spam empty queue INFO logs; only non-empty queue polls should be INFO.

## On-Site Test Script

1. Keep the Android app open and in the foreground.
2. Submit 20 orders in a row.
3. For each order, record:
   - submit time
   - first claim time
   - printed time
   - module that printed slowest
   - assigned printer endpoint
4. Confirm Print Center shows queue age, queue delay, total time, claimed device, printed device, printer endpoint, and retry count.
5. Test screen sleep / foreground return.
6. Test printer power off / restore.
7. Test `ERROR_STOPPED` is visible in Control Panel.
8. Tap Stop, restart the app, and confirm it does not secretly print.
9. Tap Start, background/foreground the app, and confirm it safely resumes.
10. Confirm `PENDING` over 30 seconds shows warning and over 2 minutes shows danger messaging.
11. Confirm failed jobs do not auto-reprint.

## How To Read Print Center State

- `PENDING + retry 0 + no attempts`: no Pad has consumed the job. Check worker state, pairing, auto enabled, and last poll.
- `CLAIMED/PRINTING for a long time`: a Pad claimed the job but did not complete/fail. Check the claiming Pad and physical printer output before reprinting.
- `FAILED + error_code`: a Pad consumed the job and reported a failure. Fix the displayed printer/device/network issue before reprint.
- Slow `created -> claimed`: worker, polling, lifecycle, queue, or backend pending query issue.
- Slow `claimed -> printed`: assigned printer network/TCP issue.

## Index / Query Follow-Up

Current PR11D-14 does not add database migrations. For PR11D-15, review a
low-risk index for the PAD_DIRECT pending query:

```sql
where store_id = ?
  and execution_mode = 'PAD_DIRECT'
  and (
    status = 'PENDING'
    or (status = 'CLAIMED' and claim_expires_at < now())
  )
order by created_at asc, id asc
limit ?
```

Candidate indexes to evaluate with `EXPLAIN ANALYZE` on real pilot data:

- Composite: `(store_id, execution_mode, status, created_at, id)`
- Claim expiry helper: `(store_id, execution_mode, status, claim_expires_at, created_at, id)`
- Partial indexes for active PAD_DIRECT `PENDING` and expired `CLAIMED` jobs if PostgreSQL statistics show the composite index is not enough.

Do not add the index blindly during service hours. Measure query plans first.
