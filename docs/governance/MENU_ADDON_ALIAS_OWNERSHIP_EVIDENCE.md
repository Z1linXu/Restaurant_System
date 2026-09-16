# Add-on identity and print alias ownership

2026-09-16 implementation evidence. Runtime acceptance is recorded separately
below when executed; this file does not authorize Production.

Base: `52a7fe6844850d00c1d7f346d5a35fb07cba03f7`.
PRIMARY_REPAIR_GOAL: Menu owns Add-on identity; Printing consumes optional aliases;
apply explicit Owner price decisions only on Staging, preserving history.
EXPECTED_REPAIR_CLASS: HIGH (money propagation, printing revision/history).

## Repository validation

- Backend: 175 passing tests in 16 classes, including Menu catalog, controller,
  pricing rollback, Store/Organization JDBC isolation, printing semantic/GRAB/HOT
  rendering, Combo defaults, provisioning and order regression.
- Frontend: 43 passing tests across five matching files; alias-only controls,
  null reset, unchanged legacy values, catalog editing and cache/draft regression.
- TypeScript/Vite production build: PASS.
- Existing acceptance-helper tests: 21 PASS.
- Governance validation and whitespace: PASS.
- Schema unchanged; no Flyway migration introduced.

Agent 6: independent review `01a0aafa-cf2a-7950-8ada-d7dc722ab6f3` found P1 stale
JPA active pointer under interleaved publication and P2 legacy draft-only code
compatibility. Both were repaired before merge. Re-review ACCEPT; reviewer
verified 13 lifecycle tests and whitespace, no remaining P0/P1/P2. Added real
two-transaction interleaving and actual JDBC legacy-vocabulary tests.

Product changes use existing Store catalog, option materialization, printing
revision repositories and menu revision mechanism. The only tooling change is
the bounded alias/confirmed-price slice in the existing Staging acceptance
script; no deployment, credential or migration framework was introduced.

## Pre-deployment observation

Staging observed at `8fc7ba8623c3180b35c6e3605a46d0a7d6744ce7`; database healthy.
Production images/start times match the existing release; no mutations.
Disk 67%, about 20 GB available. Build Cache 19.19 GB / 15.7 GB reclaimable.
Reviewed cache dry-run returned NO_GO because records are not clearly eligible.
No cleanup performed; all current/rollback artifacts remain protected.

Owner's Chrome session is available. Combo UI acceptance was already confirmed
by Owner; new alias UI checks still require the newly deployed application.

## Runtime result

Not yet executed. No Staging acceptance PASS or Production authorization claimed.

### Post-deployment append — 2026-09-16

The statement above is the preserved pre-deployment observation. This append
records subsequent execution, not a rewrite of previous acceptance evidence.

- PR #232 merged; reviewed commit `780f01e1ee454726fb5045f70f97075e9303f503`.
- Exact merged/deployed executable: `11996ef919d9b28ee5366c7e40a58a78c074b675`.
- Backend image: `sha256:603f79e0272a3fe83fdd7ac9bb58b72dc2b1facb3ceef720bc636bf5b9c5469d`.
- Frontend image: `sha256:d82650726a7faec62f1270a98bc7e878f0060392d5b6d62fa75ee03bf6e0728f`.
- Reviewed release/env preparation, preflight, deploy, passive readiness and
  OPS001 collect-evidence completed. Health/readiness PASS; no new migration.
- Flyway count/max=28/28; digest unchanged
  `f78e801148aab78f871bbccca3debea90b1737d27e3ec05a3cfc37aee0193e8b`.
- Staging DB container unchanged:
  `b64d3c676dbb4003368279453e5c6b390ac6327c3cf28001ead671155f93f4c5`.

Evidence root: `/srv/restaurant-pos/staging/evidence/`.
All following filenames start `addon-alias-11996ef919d9b28ee5366c7e40a58a78c074b675-`:

| Suffix | SHA-256 |
| --- | --- |
| preflight.txt | 028b7034ec319bf403785a32945ecd00549ea7032f6d049a3fce64a0fa0d1651 |
| readiness-r2.txt | cefa1601db4a24042ab810b1619d72797f3e51413b567c5b6ba8ebaaf81b16bf |
| runtime.txt | 185d6b50326f9687965e587ad7cd5416354d78daf081e7cb6d45e3472ac84573 |
| acceptance.json | f3414b5e14deedf0ff9b75cd5f58d92a13d7d1fdb871ac13f71352a1e3a17c72 |
| owner-prices-r2.json | 4f42efb7d48ae1cbe3d65cdd8b6bc6a341f1995182263f1ceed251ab78edceca |

### Automated runtime acceptance

Existing reviewed helper `--alias-ownership --create-fixtures
--create-isolation-fixtures`, run `alias1601`: 26.05 seconds, no failed checks.
Synthetic Stores 26/27 (`STG005_MENU_ALIAS1601_A/B`); items 916/917/918;
synthetic order 66, MOCK print proof job 164. Store-scoped synthetic OWNER 42
on control Store 27 was denied read/write on Store 26; credentials private.

Passed new Store creation/replay/LIVE materialization; code immutability;
alias-only edit/default/reset/rename; preservation of legacy aliases;
normal/Combo identity separation; name/price propagation; active versus
eligibility; confirmed price dry-run/apply/replay; Store isolation; menu refresh;
frozen order and print snapshots; endpoint-free MOCK rendering. Master catalog
advertised fingerprint and Profile/artifact fingerprint remained unchanged.

The original automated report is deliberately PARTIAL: browser checks are
separate, foreign-Organization Owner credential was not supplied, and the
supported Master API only advertises selection fingerprint rather than raw
Master content. Backend Organization isolation tests PASS, but do not replace
the missing independent runtime identity proof. No SQL/token workaround used.

### Browser acceptance supplement

Authenticated Staging Chrome Owner UI, 2026-09-16 ~16:25–16:31 UTC:

- Store 1 Printing shows readonly codes/names/default text and alias inputs;
  no Add/Remove identity controls in the Add-on section. Existing `+面面`,
  `+海带`, `+牛筋`, normal/Combo egg aliases preserved.
- Store 26 Menu Add-ons visibly lists normal catalog only; Combo-only eggs
  absent. Created `stg_alias1601_ui_cheese`, 加验收芝士 / Synthetic UI Cheese,
  explicit $2.25. Save succeeded; subsequent edit shows code readonly.
- Normal navigation to Printing automatically shows the new code/name/default
  and blank alias. Set `+验收芝`, Save Draft/Publish => v6 PUBLISHED.
- Reset to Default, Save Draft/Publish => v7 PUBLISHED, alias empty.
- Return to Menu Add-ons: original name and $2.25 unchanged; using fallback.
- This supplements `alias_owner_browser_controls`; original JSON was not edited.
- Owner previously confirmed Combo Default Egg UI acceptance. This run did not
  repeat the complete Frontdesk UI pricing/eligibility/cache/Combo interaction
  matrix; API proof above is not a substitute for every interactive UI check.

Overall result: PARTIAL, waiting for remaining Owner UI/data decisions and
authorized foreign-Organization runtime proof. No overall PASS claimed.

### Current Store 1 reconciliation and decision boundary

Validated exact Store 1 `STG005_SRC_20260809_R01`, Organization 1. Invoked the
reviewed authenticated reconciliation endpoint with the explicit 16 supplied
code/price pairs, dry-run then apply/replay. Result PASS; 13 deterministic
groups linked, 48 existing current options linked, row identities preserved.
Every pre-existing explicit printing alias and advertised Master/Profile
fingerprint remained unchanged. Synthetic history and cross-Store checks passed.
This is Store-local current data, not a modification of shared Master/Profile.

| Remaining data | Exact boundary |
| --- | --- |
| extra_meat | Price $6.99 applied; English Extra Beef / Extra Meat unresolved |
| tea_egg | Price $1.99 applied; Chinese 加蛋 / 加卤蛋 unresolved |
| Missing code | 20 current options (IDs in private sanitized report); no label-inferred mapping or repricing |
| Beef tendon | Menu `addon_beef_tendons` already $6.99 retained separately from alias-only `addon_beef_tendon` => +牛筋; no remap inferred |
| cabbage | Alias-only code exists; missing-code cabbage labels not enough authority to create/link identity |

Confirmed current prices are NOT a new default for future Add-ons, and do not
authorize Production reconciliation. Production price/name decision state was
not re-audited or changed in this task. No claimed complete 16-code catalog.

The first local API invocation imported Python bytecode into the exact remote
release and correctly failed the clean-release guard before business mutation.
Moved only that run-created cache to Staging state, disabled bytecode generation,
then reran successfully as r2. Failed evidence preserved, no guard bypass,
no code repair/redeploy required.

### Safety and resource closure

Production backend/frontend image IDs and all three container IDs/start times
match baseline; no Production action was performed. No Printer/Pad mutation.
Post-build disk 70%, ~18 GB free; Build Cache 20.66 GB / 17.06 GB reclaimable.
No cache pruning or volume deletion. Existing rollback images retained.
Phase C NOT_STARTED; Production NOT_AUTHORIZED.
