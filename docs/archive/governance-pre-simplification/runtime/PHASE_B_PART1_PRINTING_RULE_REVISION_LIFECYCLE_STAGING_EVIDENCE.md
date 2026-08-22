# Phase B Part 1 Printing Display Rule Revision Lifecycle — Staging Evidence

Date: 2026-08-21 (America/Toronto)

This evidence records the bounded Printing Display Rule revision/fingerprint
lifecycle repair inside the existing Phase B Part 1 Owner manual acceptance
gate. It does not authorize Phase B Part 2, Store activation, a real
printer/Pad binding, Production deployment, Production mutation, or the
separate `OPTION-CODE-STABILITY` backlog item.

## Reviewed release

- implementation PR: `#181`
- implementation commit: `4e2e636f9c925f3efcd3e4dab38a9b3c37d41451`
- merged and deployed SHA: `dc2836f6805aa97c24454a27371f88d307db2c5e`
- Staging environment: `restaurant-pos-staging`
- Flyway: `count=22`, `max_version=22`
- Production deployed SHA remains:
  `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`
- Production mutation: none

## Repair contract

Revision identity remains `(rule_set_id, revision_number)`.
`fingerprint_sha256` is a content checksum and query key, not a unique
historical identity. V22 removes
`uq_printing_display_rule_revisions_fingerprint`, adds the ordinary
`idx_printing_display_rule_revisions_set_fingerprint` lookup index, and adds
the partial unique index
`uq_printing_display_rule_revisions_single_draft` for one DRAFT per rule set.
V22 contains no historical row deletion or rewrite.

`saveDraft` now updates the one existing DRAFT, permits historical content to
become a new rollback revision, and returns explicit `ALREADY_ACTIVE` without
creating a revision when content already equals active. `publishDraft` uses
the existing rule-set pessimistic lock and one transaction to publish the
DRAFT and advance `active_revision_id`. Published-row immutability remains
enforced by the V17 trigger. Persistence conflicts are exposed as stable HTTP
409 business errors instead of generic HTTP 500.

## Repository verification

- focused lifecycle/conflict/migration/handler tests: PASS
- complete backend regression: `605` tests, `0` failures, `0` errors,
  `4` environment-skipped, PASS
- actual local PostgreSQL V1-to-V22 migration/invariant test: PASS
- backend package: PASS
- frontend Vitest: `23` files / `114` tests, PASS
- frontend production build: PASS
- targeted lint for both changed frontend files: PASS
- repository-wide frontend lint: pre-existing unrelated failures only; no
  changed-file lint finding
- governance validation and `git diff --check`: PASS
- Agent 6 final review: `ACCEPT`, P0/P1/P2 all zero. Its initial transaction
  rollback test gap was repaired; the new proxied-service test proves a failed
  active-pointer flush restores the revision to DRAFT, clears `published_at`,
  and preserves the prior active pointer.

## Exact-SHA Staging evidence

All listed files are private mode-`0600` evidence under
`/srv/restaurant-pos/staging/evidence`.

| Check | Evidence file | SHA-256 / result |
| --- | --- | --- |
| candidate import | `printing-rule-lifecycle-candidate-import-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `44b7d049e1badb995e47bc34227e04108f5bbdcbd65214d6dffe51b4e631fd05`; PASS |
| release environment | `printing-rule-lifecycle-release-env-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `63e4921a3b0a2488dfd5430a005e0cb1d925094bac82c02f74daf3a25977eda6`; resulting env digest `12fecdcc8e820b2a3b38a965952990a8ea0ebfe86fbe43a8f9812fbe6f07dddf` |
| preflight | `printing-rule-lifecycle-preflight-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `3db2dfea04d5cda8468bb0f852c03dc807f41979bb69c800e75e09dfd09824b3`; PASS |
| deploy | `printing-rule-lifecycle-deploy-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `57b3af85289e65a426a80763dfe5b22f309a6ba82d67ef3eba0883cd9b9866fa`; PASS |
| health | `printing-rule-lifecycle-health-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14`; PASS |
| readiness | `printing-rule-lifecycle-readiness-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `b04dbe2800d4a64e4e729ef1230b589f0c854796696d027211b9c5692138faf1`; PASS |
| runtime | `printing-rule-lifecycle-runtime-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `f7c9080d62efe81de53ef6405b3e09a1196e5a8427faf5581d495183f9dfc6b9`; exact SHA, Flyway V22, restart counts zero, PASS |
| affected lifecycle | `printing-rule-lifecycle-affected-acceptance-r2-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `4a13c8f48561a77cc70f19398ad04964eec2976415417080b811389276292251`; PASS |
| database invariants | `printing-rule-lifecycle-db-verification-r2-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `bd034449cd8cc2af395c4da5b769af997df3e17b03eada3285afcb2704527f64`; PASS |
| post-acceptance health | `printing-rule-lifecycle-post-acceptance-health-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14`; PASS |
| post-acceptance readiness | `printing-rule-lifecycle-post-acceptance-readiness-dc2836f6805aa97c24454a27371f88d307db2c5e.txt` | `54b6f2508e6e7d375a272f78a374a857c3154583900fbf0da400726601452dfd`; PASS |

Pre-migration inspection found exactly one DRAFT for rule set 3 and no rule
set with more than one DRAFT, so V22's partial unique index could apply without
cleanup. The first affected-acceptance attempt stopped at HTTP 401 before any
rule mutation because the older private twin credential JSON retained the
pre-rotation password. The retained failure evidence is
`printing-rule-lifecycle-affected-acceptance-dc2836f6805aa97c24454a27371f88d307db2c5e.txt`.
The R2 run used the existing separately stored current mode-`0600` Owner
credential and passed. A first read-only DB evidence query used `code` instead
of the actual `option_code` column and was retained; R2 corrected only that
query and passed.

## Affected Staging acceptance

Store 1 rule set 3 began with active v8 and one existing v9 DRAFT. Through the
normal authenticated Owner API, acceptance performed:

1. updated and published v9 as `addon_beef_tendon -> +牛筋`;
2. saved and published v10 as `addon_beef_tendon -> +牛筋鸡`;
3. saved and published v11 as `addon_beef_tendon -> +牛筋`;
4. saved the already-active v11 content and received HTTP 200 with
   `lifecycle_result=ALREADY_ACTIVE`, without a new revision or DRAFT;
5. previewed the active content and received `+牛筋` in both GRAB and
   HOT_KITCHEN preview.

v7, v9 and v11 all have fingerprint
`a1c319d8dccbfabf4c6e3eeb8234f4cf08a6721535edaf66fbe2c628661169fb`
but distinct revision numbers and IDs. v8 and v10 likewise share their content
fingerprint while remaining distinct revisions. Active pointer 53 identifies
v11; DRAFT count is zero. Historical v6/v7/v8 identity, status and fingerprint
were unchanged across the acceptance.

Post-acceptance DB evidence confirms the old fingerprint constraint is absent,
both V22 indexes have the required definitions, and three PUBLISHED revisions
may safely share the A fingerprint. The Store-local `加牛筋` option remains
`id=3437, option_code=addon_beef_tendons`; it was not changed by this repair.
That mismatch remains the separate deferred `OPTION-CODE-STABILITY` debt.

Post-acceptance health/readiness passed. The Staging project fingerprint stayed
`f255c05a71faa261b14fec3302a1edfe7bf0b76e415c5bb06f4bf6906688260e`.
The passively observed Production fingerprint stayed
`df8fe7cc36fbfb0909fcfe6528f855bab69190271f586adc31d4794a1d8bbc91`;
Production runtime and data were not mutated.

## Gate

Repository repair and exact-SHA Staging automated acceptance are PASS. The
current gate remains `WAITING_FOR_OWNER_MANUAL_ACCEPTANCE`; the next action is
Owner manual retest on Staging. Phase B Part 2 remains unauthorized.
