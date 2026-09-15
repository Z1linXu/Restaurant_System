# Menu Simplification — Staging Evidence

Observation: 2026-09-15. This is partial evidence, **not** integrated acceptance
PASS or Production authorization. Current Gate belongs only to
[`CURRENT_STATE.yml`](CURRENT_STATE.yml).

## Exact application identity

- Implementation PR: [230](https://github.com/Z1linXu/Restaurant_System/pull/230).
- Merge/deployed SHA: `8fc7ba8623c3180b35c6e3605a46d0a7d6744ce7`.
- Backend: `sha256:255e5da67cbcf4421cd8632385fb1c90e641a3bcb8bfe78c35e2ad2cbad6d067`.
- Frontend: `sha256:f57c7bc61bf20bf72c100d9ca6da056481bbb533ce76c540fe68af41a6c2d189`.
- Reviewed OPS001 exact-release preparation/preflight/deploy/readiness/runtime
  collection used. No application rebuild for assertion repair or evidence sync.
- Flyway clean validated ledger: V28 / 28 successful migrations; V27 and V28
  were applied on Staging. Ledger digest:
  `f78e801148aab78f871bbccca3debea90b1737d27e3ec05a3cfc37aee0193e8b`.
- Database container unchanged:
  `b64d3c676dbb4003368279453e5c6b390ac6327c3cf28001ead671155f93f4c5`.

Runtime records reside in `/srv/restaurant-pos/staging/evidence/` with prefix
`menu-simplification-8fc7ba8623c3180b35c6e3605a46d0a7d6744ce7-`:

| Record | SHA-256 |
| --- | --- |
| preflight.txt | `d615ee901053842155fb403271bacb82950b1c97eed7436464fd329914e5a494` |
| readiness.txt | `09e11066363f82f84ec476387e2e3e4a561f049239256c73b8684248e02a3e8f` |
| runtime.txt | `d68073424510a363fd40385d597612b0ac75fd82dae7314801a67d28ea7c30cb` |

Startup took 40.672 seconds. Immediate HTTP checks observed startup 000/502;
the existing bounded health wait then passed. No extra build/redeploy or
rollback was needed. At 18:59 UTC both environments' health was UP, Staging
frontend HTTP 200 and both PostgreSQL containers healthy.

## Actual synthetic API acceptance

The exact released acceptance script ran with run ID `menu1501` and the
existing private Staging Organization Owner credential through normal APIs.
No secret value is included here.

- Created Stores 24 `STG005_MENU_MENU1501_A` and 25 `STG005_MENU_MENU1501_B`
  using normal Owner Store creation, with successful idempotent replay and LIVE
  materialization. Existing Stores were not used as mutation targets.
- Each received 5 stations, 6 categories, 39 items, 380 option rows,
  5 Combo components, 9 deterministic catalog Add-ons and 6 preserved
  unresolved conflict groups. No conflicting business winner was selected.
- Test items 835/836/837; catalog Add-ons 19/20 with code
  `stg_menu_menu1501_egg`; synthetic order 64; synthetic isolation Owner 41.
- API checks passed for Store tea-egg default, explicit item fried-egg override,
  missing Printing coverage fallback, removal to null, and explicit submitted
  employee selection. Browser initial-default/manual-choice behavior is not
  inferred from submission API success.
- Store 24 catalog name/price changed from Synthetic egg / $1.25 to Synthetic
  egg renamed / $2.75 and propagated to both linked current option rows.
  Store 25 remained at the original name/price/revision. Unrelated item 837
  remained ineligible. Immutable-code edit was rejected.
- Item 836 eligibility was disabled independently. Catalog deactivate/reactivate
  preserved item-specific eligibility and did not change Store lifecycle.
- Reconciliation dry-run/apply/replay preserved unresolved values, option IDs
  and eligibility. Cross-Store catalog linking was rejected.
- Historical order 64 snapshots remained unchanged after catalog edits:
  `096259741e172d20c765d444bf3e118fdfa3c9d08c7badec65496aeb855d2a13`.
- Normal Owner 41 can read its control Store 25, but target Store 24 catalog
  read, item eligibility read, catalog write and eligibility write returned 403;
  the denied operations did not change the catalog.

### Printing assertion repair and preserved evidence

Original `menu-simplification-menu1501.json` remains FAIL and was not rewritten.
Its last assertion incorrectly required PrintJob `execution_mode=MOCK`.
The unchanged dispatcher also uses Store MOCK mode when job execution mode is
null. This was an acceptance-tool contract error, not a Printing runtime bug.

Supplemental checks reused the exact release API client, authorization negative
checks, runtime validator and acceptance mutex on the same Stores/order. They
did not resubmit the order or create replacement fixtures. Job 159 was PRINTED,
GRAB, Store 24/order 64, with null execution mode, no printer endpoint/ID/device,
and actual Store mode MOCK with endpoint-free printers. It contained both
`+TESTADD` and `+TESTCOMBO` in the same item line. Rendered-text digest:
`91efb215701e6ad34e0509ca2e8ffe1c13a9f3ba9ed13531a1f9cb5dfcf925f4`.

Supplement record: `menu-simplification-menu1501-supplement.json`, digest
`15a8532b295877698f4f299003564f83eed0dc47a869c73eb14c5c9e188c131c`.
It is PARTIAL, not PASS, because no foreign-Organization Owner fixture was
supplied. The bounded verifier correction additionally rejects REAL/PAD_DIRECT,
physical binding, wrong Store/order/module/status and missing distinct tokens;
21 acceptance-tool tests passed. Application code/resolver was not changed.
Independent focused Agent 6 re-review returned ACCEPT, no P0/P1/P2, after
checking the four-file verifier/test/technical-clarification diff against
`8fc7ba8623c3180b35c6e3605a46d0a7d6744ce7`. It ran the new focused test and
whitespace check; it did not claim to repeat runtime acceptance. The later
evidence/state updates describe observations, not new runtime code.

## Master / Profile evidence scope

Before and after fixture creation/menu edits/negative authorization checks:

- advertised Master catalog fingerprint unchanged:
  `ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c`;
- actual Profile version fingerprint unchanged:
  `51ddf408755ef476ac99abd9ab7498f48995431c5d5a52d98a77704ab71b23ae`;
- Profile plus returned artifacts digest unchanged:
  `d2bc60a40ed879bf45d51d1bfdaa15f000f2b4ad4312d491484c525005b5dab3`.

The supported Master selection API advertises its fingerprint but does not
return Master artifact bytes; that API alone is not content proof. A separate
19:02:22 UTC Staging database audit used `BEGIN READ ONLY`, 15-second timeout,
Organization 1 / Master version 50 only. Actual `content_json` was canonicalized
with sorted keys, preserved arrays and compact UTF-8 JSON; its SHA-256 equalled
the pre-acceptance advertised fingerprint above. The stored fingerprint also
matched; `updated_at` remained `2026-08-16T09:49:12.883696`. No content or secret
was printed. This supplies actual current canonical-content agreement with the
before-run fingerprint, not a claim of an independently captured before-run raw
byte snapshot. The earlier script's NOT_RUN record remains unchanged.

## Remaining acceptance evidence

1. Authenticated Staging Owner browser: current in-app browser awaits Owner
   login at `http://127.0.0.1:18080/login` through the isolated SSH tunnel.
   The previous browser principal was Manager and was logged out normally.
   No Owner UI acceptance is claimed. Do not transmit passwords through chat.
2. Real foreign-Organization control Owner/Store: current fixtures provide only
   same-Organization Store isolation. PostgreSQL composite-FK and authorization
   tests passed, but are not a substitute for this requested runtime slice.
   Do not invent direct membership SQL or a new provisioning engine to obtain it.

Once the Owner browser session is available, continue Combo exception add/edit/
remove and initial default/manual choice, Add-ons CRUD/code-readonly/coverage,
Item eligibility-only UX with other groups unchanged, and normal ordering
refresh. Do not rerun fixture creation or submit another order merely to repair
an evidence assertion. No successful integrated-acceptance Stop Marker is issued.

## Production and disk safety

Production remains `b4d0350c15cf777ff0ee53e9acb0d4e4127d9b99`, Flyway V26.
Backend/nginx/database IDs and start times match the pre-run baseline. Database:
`c2ab37fec6ac966e77a1d8ab7aa41d1da294b9ac57b1a21ce18904d04cfae91e`.
No Production deploy, restart, migration, reconciliation, credential or hardware
mutation. No historical orders were rewritten. Phase C was not started.

Disk changed 64% / about 21 GB free → 67% / about 20 GB free. Build Cache changed
17.48 GB total / 14.47 GB reclaimable → 19.19 GB / 15.7 GB. Reviewed pre-build
hygiene dry-run could not establish safe candidate eligibility; no cleanup was
performed, reclaimed space 0. Post-build review retains the same fail-closed
decision: disk remains below 70%, and no evidence makes previously ambiguous
old-cache candidates safe. Current/rollback images, both runtimes, databases,
configuration and historical evidence remain protected.

Production's 15 actual business-value conflicts (five per Store) are recorded
in [`MENU_ADDON_PRODUCTION_DECISIONS.md`](MENU_ADDON_PRODUCTION_DECISIONS.md).
No majority/highest/latest/Master/source-Store value was selected.

Repository tests and consolidated Agent 6 review are recorded in
[`MENU_SIMPLIFICATION_IMPLEMENTATION_EVIDENCE.md`](MENU_SIMPLIFICATION_IMPLEMENTATION_EVIDENCE.md).
