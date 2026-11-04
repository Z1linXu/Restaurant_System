# Uber Analytics Pending repair

PRIMARY_REPAIR_GOAL: Recognize reliable post-promotion merchant merchandise amounts, preserve unknown amounts and original snapshots, and correct only the 14 approved Saint-Denis orders dated 2026-11-03 through an idempotent, compensatable financial journal.

EXPECTED_REPAIR_CLASS: MEDIUM.

Baseline: verified Production `028d4971740889a0ac0d784a43497bc3e5b54b3d`, Flyway V39. Fresh origin/main is `80e888fcdad74e98326fcbeaa5df14bc0d9baed6`; it is not the release input. Dedicated branch: `codex/uber-analytics-pending-production`. Unrelated Owner work is untouched.

Owner authorizes local isolated tests, independent review, backup/restore verification and this bounded Production release without Staging/Test deployment. Staging/Test must remain stopped. Owner explicitly exempts only the four previously identified expired historical PAD_DIRECT PRINTING jobs from this release's active-work gate; their states must remain unchanged. New active work, financial validation, resource, backup and rollback gates remain mandatory.

Product changes: financial parser, financial correction journal/read integration, Analytics completeness and counts. No printing execution, polling, webhook/import/dispatch, Inbox/performance, Android, inventory or archiving change. Internal correction tooling has no Owner-facing workflow.

The six unexplained one-cent discrepancies remain blocked. An original snapshot is never overwritten. APPLY/REVOKE journal events provide data compensation independently of application rollback.

## Focused scope and validation

PRODUCT REPAIR: financial capture/policy, versioned correction/read path, shared Analytics completeness fields and UI, focused financial/PG/UI tests. Eight strict candidates total29993 cents; six ±1 cent mismatches remain Pending. Official source: https://developer.uber.com/docs/eats/references/api/v2/get-eats-order-orderid (charges, Promotions, PromoFundingSplit). No generic cent tolerance or payout inference.

BLOCKING TOOLING: the reviewed dine-in-headers promotion path assumes active Staging and no Flyway change. Owner explicitly prohibits restoring Staging. This case helper reuses its private Compose modeling, exact config comparison, health, inventory, backup and rollback primitives, replacing only case-specific gates with local restore proof, V39.1 additive scope and the fourteen-target compensation. No second release architecture or credential lifecycle. Scope expansion check: roughly260 lines of bounded orchestration are required to carry the authorized direct Production release safely; reusable/new generalized tooling is deferred. Production backup is custom pg_dump, checksum/TOC plus full isolated restore validation before replacement. Single Backend/nginx replacement can interrupt HTTP and must not be called zero downtime.

A pre-existing MenuRevisionLockIntegrationTest JPA fixture lacked the existing RealtimeEventPublisher constructor mock. Only the test fixture is repaired so full regression can run; table/product behavior is unchanged.

Application rollback uses the saved private Compose/images with unchanged runtime settings; additive journal schema is retained. Data compensation is independent: the same exact new image CLI `--mode revoke --preview-sha <batch>` appends REVOKE events, without deleting APPLY or changing original snapshots. Restore of full Production backup is an emergency separately controlled action, not automatic rollback.
