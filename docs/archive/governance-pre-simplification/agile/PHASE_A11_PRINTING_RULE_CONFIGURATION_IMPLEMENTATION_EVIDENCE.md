# Phase A11 Printing Rule Configuration Implementation Evidence

## Status

```text
PHASE_A11_PRINTING_RULE_CONFIGURATION_IMPLEMENTATION = ACCEPTED_BY_OWNER
A11_OWNER_5_ANSWERS = CLOSED
PHASE_A11_OWNER_ACCEPTANCE = PASS
PHASE_B_IMPLEMENTATION = WAITING_FOR_A11_5_AND_EXPLICIT_OWNER_APPROVAL
PRODUCTION = NO_MUTATION
STAGING_OWNER_ACCEPTANCE = PASS_OWNER_DECLARED_2026_08_15
LATEST_CODE_AUTHORITY = origin/main@0de03c773ef04594e7d737c6bccdf6f607692eca
```

Fresh implementation authority:

```text
origin/main@0de03c773ef04594e7d737c6bccdf6f607692eca
```

Owner acceptance update:

The Owner has completed A11 manual Staging acceptance and declared
`PHASE_A11_OWNER_ACCEPTANCE = PASS`. This loop must not rerun A11 manual
acceptance, redesign A11 or require Owner retest. Checked-in machine runtime
evidence still preserves the historical V17 startup-repair trail below; the
Owner verdict is the latest Owner acceptance authority.

## Owner decisions implemented

- `HYBRID_UI`: item-specific aliases live in Menu Management; Store-global
  dictionaries, rules, history and preview live in Printing Settings.
- `INDEPENDENT_OUTPUT_ALIASES_WITH_MENU_NAME_FALLBACK`: `GRAB`,
  `FRONTDESK_RECEIPT` and `HOT_KITCHEN` each resolve their own display alias
  and fall back to menu/order snapshots.
- `STORE_GLOBAL_DICTIONARY_PLUS_ITEM_OVERRIDE`: Store-wide Size/noodle/spicy
  and modifier dictionaries are canonical, with item aliases/overrides.
- `FROZEN_HISTORICAL_RULE_REVISION`: new print renders capture the active rule
  revision/fingerprint and retain the existing rendered snapshot.
- `STRUCTURED_FIELDS_CONSTRAINED_CONDITIONS_ONLY`: rule content is data, not
  scripts, arbitrary regex, raw ESC/POS, or executable templates.

## Schema

Additive Flyway:

```text
V17__add_printing_display_rules.sql
```

New canonical tables:

- `printing_display_rule_sets`
  - one Store-scoped rule set per Store;
  - points to the active revision;
  - has no physical printer endpoint, device credential or source Store ID.
- `printing_display_rule_revisions`
  - versioned structured `content_json`;
  - deterministic `fingerprint_sha256`;
  - draft/published lifecycle;
  - database trigger protects published revisions from content rewrite.

Extended table:

- `print_jobs.printing_rule_revision_id`
- `print_jobs.printing_rule_fingerprint`

Backfill behavior:

- materializes one code-equivalent default published revision per existing
  Store;
- preserves existing historical `rendered_text_snapshot`;
- does not reinterpret submitted orders, completed orders, receipts, reports or
  old print snapshots.

## Application contract

Canonical A11 source:

```text
printing_display_rule_sets.active_revision_id
→ printing_display_rule_revisions.content_json
```

Resolver precedence:

```text
1. frozen rendered_text_snapshot for single job reprint
2. captured print_jobs.printing_rule_revision_id when re-rendering historical jobs/orders
3. active Store rule revision for new renders
4. item-specific output alias
5. Store-global output dictionary / constrained condition
6. submitted order/menu snapshot fallback
```

Protected system logic remains outside A11:

- routing and HOT_KITCHEN eligibility;
- printer assignment and logical topology;
- print mode and physical printer endpoints;
- device credentials, pairing and Pad worker state;
- PrintJob state machine, lease, retry and queueing;
- order totals, pricing, tax, reports and payment;
- Store/Organization authorization.

## API/UI

Owner Printing APIs:

- `GET /api/v1/admin/printing/display-rules?store_id={storeId}`
- `POST /api/v1/admin/printing/display-rules/draft`
- `POST /api/v1/admin/printing/display-rules/validate`
- `POST /api/v1/admin/printing/display-rules/preview`
- `POST /api/v1/admin/printing/display-rules/publish`

Frontend:

- `Menu Management` exposes item-specific aliases for `GRAB`,
  `FRONTDESK_RECEIPT` and `HOT_KITCHEN`.
- `Printing Settings` exposes Store-global dictionaries, constrained rules,
  preview, validation, active/draft revision, fingerprint and history.

## Profile contract

Post-A11 profile versions must include a `PRINTING_DISPLAY_RULES` artifact.
Materialization must create target Store-owned rule rows/revisions. Profiles
must not live-link a source Store, copy source Store database IDs, or carry
physical endpoints, printer secrets, device credentials or PrintJob runtime
data.

Historical `ST_DENIS_CANONICAL_PROFILE/v1` remains a valid immutable historical
profile and is not rewritten.

## Validation

Repository validation completed before PR:

```text
backend mvn -q -DskipTests compile = PASS
backend mvn -q -Dtest=PrintDispatcherServiceImplTest test = PASS
backend targeted A11/profile tests = PASS
backend mvn test = PASS (527 run, 0 failures, 0 errors, 3 skipped)
frontend npm test = PASS (21 files, 109 tests)
frontend npm run build = PASS
changed-file eslint = PASS
git diff --check = PASS
```

Full frontend lint is not the A11 gating signal because the repository already
contains unrelated legacy lint findings outside the A11 changed files. The A11
changed-file lint gate passed.

## Agent 6 focused repair closure

Initial Agent 6 implementation review returned `REJECT` with four bounded
blockers. The implementation candidate was repaired without changing runtime
configuration, Production, printer endpoints, physical devices or broad A11
scope:

- Post-A11 Store Profile enforcement now requires
  `template_references.printing_display_rules` for every profile except the
  immutable historical `ST_DENIS_CANONICAL_PROFILE/v1`, and every template
  reference must have a matching artifact with the same code, version and
  fingerprint.
- Rule validation is now whitelist-based for top-level fields, item alias
  entries, dictionary entries, dictionary output keys, modifier dictionaries,
  formatting fields and constrained conditional overrides. Unknown structured
  fields, non-canonical output keys, regex/script/template/expression fields,
  operational/security keys and executable-like values fail closed.
- `HOT_KITCHEN` preview and renderer paths now honor independent HOT_KITCHEN
  dictionary outputs instead of reusing GRAB dictionary values.
- `doc/API.md` was synchronized to the implemented `/api/v1/admin/printing`
  endpoints and the canonical `item_sku`, `outputs`, uppercase dictionary and
  `conditional_overrides` rule shape.

Repair validation:

```text
backend mvn -q -DskipTests compile = PASS
backend focused A11/profile/HOT renderer tests = PASS
backend mvn -q test = PASS (527 run, 0 failures, 0 errors, 3 skipped)
changed-file eslint = PASS
frontend npm test -- --run = PASS (21 files, 109 tests)
frontend npm run build = PASS
git diff --check = PASS
Agent 6 final follow-up review = ACCEPT
```

Prohibited-data scan note: A11 scans show only documentation safety language and
validator/test constants for prohibited keys such as credentials/tokens/printer
endpoints. No A11 runtime value, secret, printer endpoint, device credential,
payment/customer payload or raw environment value is introduced.

## Safety confirmation

- Production was not read for runtime values and was not mutated.
- No Production deployment, restart, Flyway or configuration change occurred.
- No physical printer endpoint, printer secret, device credential, token,
  cookie, session secret, payment data, customer PII or raw environment secret
  is part of the A11 rule contract.
- A11 display rules cannot change printer routing, assignment, print mode,
  PrintJob state, pricing, reporting, payment or authorization.
- A11 Owner acceptance is now `PASS_OWNER_DECLARED`; Phase B remains stopped
  only until explicit Phase B Owner approval after A11.5 design closure.

## Staging startup repair

During exact-SHA Staging deployment of merged PR #159
(`9c5bc05912e565c0c4e8cb1b82eae88d15d0fa0a`), Flyway V17 applied
successfully but backend startup failed schema validation because
`print_jobs.printing_rule_fingerprint` is a PostgreSQL `CHAR(64)` / `bpchar`
column while the `PrintJob` entity lacked the explicit Hibernate `CHAR` JDBC
mapping already used by other fixed-length fingerprint entities.

Repair scope:

- no Flyway history edit;
- no Staging reset or downgrade;
- no Production mutation;
- no printer endpoint/device/secret change;
- add the missing `@JdbcTypeCode(SqlTypes.CHAR)` mapping to
  `PrintJob.printingRuleFingerprint`;
- add a focused entity schema-contract regression test.

Repair validation:

```text
backend mvn -q -Dtest=PrintJobEntitySchemaContractTest test = PASS
backend mvn -q -DskipTests compile = PASS
backend mvn -q test = PASS (528 run, 0 failures, 0 errors, 3 skipped)
git diff --check = PASS
Agent 6 focused startup-repair review = ACCEPT
```

## Next runtime gate

Superseded by Owner acceptance update:

```text
PHASE_A11_OWNER_ACCEPTANCE = PASS
```

Next gate:

```text
PHASE_A11_5_CHAIN_MASTER_MENU_AND_STORE_MATERIALIZATION_DESIGN
```
