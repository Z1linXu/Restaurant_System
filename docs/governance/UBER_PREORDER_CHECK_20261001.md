# Uber Sandbox pre-order check — 2026-10-01

Scope: TEST APP / Staging only. PRIMARY_REPAIR_GOAL: verify Dashboard HMAC key
compatibility and one fully mapped test combination. EXPECTED_REPAIR_CLASS: SMALL.
No Uber order, Accept/Deny, printing, Production operation or menu upload allowed.

## A. Webhook

Dashboard independently re-read after account access recovered:

- URL: `https://staging-pos.lanzhounoodlesmtl.com/api/v1/integrations/uber-eats/webhook`
- Type **PRIMARY**, authentication **BASIC_HMAC**; TEST APP `Restaruant Pos`.
- Current Primary Signing Key copied through the UI directly into the private
  Staging env over SSH stdin. No value emitted, clipboard cleared. It matches
  the existing OAuth secret at this observation; no key rotation was performed.
- Before repair, current runtime used `UBER_EATS_CLIENT_SECRET` exclusively;
  `UBER_EATS_WEBHOOK_SIGNING_KEY` was MISSING in both env file and container.
- Prepared repair prefers `UBER_EATS_WEBHOOK_SIGNING_KEY`; only when absent
  does legacy fallback use client secret. Configured independent key is the
  single HMAC trust source, not an additional accepted key. Secondary key
  rollover is not implemented. OAuth continues using CLIENT_SECRET unchanged.
- Raw body bytes, HMAC SHA-256, `X-Uber-Signature`, constant-time comparison and
  signature-before-parsing/environment guards are preserved.
- Private config: PRESENT; runtime deployment and fresh probes **PENDING**.

All inspected runtime flags/client credentials were PRESENT; sandbox/enabled
values verified internally. No values are included here. No client secret,
OAuth token or signing key is in this repository/build context.

## B. Store binding / Inbox

Fresh DB read confirms mapping id 1, sandbox, enabled=true:
`bd993244-5589-4b19-8f0d-dc2ba73d4273` → Store **1**, code
`STG005_SRC_20260809_R01`, Organization **1** matching current Store,
printing **MOCK**. Correct binding was not modified. Uber rows remain 0.

Target route: `https://staging-pos.lanzhounoodlesmtl.com/stores/1/frontdesk/uber-eats`.
The existing route/hook uses active Store context and scoped `/stores/1/.../orders`,
clears obsolete Store responses on switch, and does not use a global inbox.
Live browser refresh and post-deploy health are pending final verification.

## C. Menu and mapping

Fresh real Testing token granted `eats.store`; menu GET HTTP 200 at
**2026-10-01T17:15:56Z**. API returns no menu version, ETag or Last-Modified.
Local authenticated mapping-catalog GET HTTP 200; Store/org 1/1, revision 215,
catalog `menu-catalog-v4`, content hash `fnv1a32:562b32ff`.

| Metric | Current |
|---|---:|
| Uber API item objects | 74 |
| UBER_ROOT_ITEMS | 41 (previous observation 31 is superseded) |
| UBER_MODIFIER_GROUPS | 11 |
| UBER_UNIQUE_MODIFIERS | 33 |
| Root/modifier contexts | 258 |
| LOCAL_ACTIVE_ITEMS | 35 |
| LOCAL_ACTIVE_OPTIONS | 270 distinct option rows |
| MAPPED_ITEMS_BEFORE / AFTER | 0 / 0 |
| UNMAPPED_ITEMS | 41 |
| MAPPED_MODIFIERS_BEFORE / AFTER | 0 / 0 |
| UNMAPPED_MODIFIERS | 33 unique IDs / 258 root contexts |
| HIGH-confidence correspondences | 0 |

All Uber external_data values are blank/missing; external_id is absent.
Actual Uber stable IDs have no confirmed exact local SKU/option_code contract.
Names alone cannot establish HIGH confidence, even when exact display names match.
**No mapping was saved or overwritten. No menu was changed.**

Full [item/modifier mapping diff](UBER_TEST_MENU_MAPPING_DIFF_20261001.md):
32 root items REVIEW_REQUIRED, 9 NO_MATCH; 23 unique modifiers REVIEW_REQUIRED,
10 NO_MATCH. The diff includes every stable ID, candidate local identity/Chinese
name, modifier group/parent requirements and confidence. Local source details:
[items](UBER_TEST_LOCAL_ITEMS_20261001.csv), [options](UBER_TEST_LOCAL_OPTIONS_20261001.csv).

## D. First-order proposal — not an order instruction

Candidate only: Traditional Lanzhou Hand-pull Beef Noodle
(`b940caa7-6e37-4b3b-b964-3e10151e7903`) → local item 1,
`traditional_beef_noodle` / 传统牛肉面.

Proposed modifiers: Large, Extra Fried Egg, Non Coriander. The actual menu also
requires **one spicy level and one noodle type**, in addition to Size; these
cannot be silently omitted. All selected stable-ID correspondences require
explicit identity confirmation. Full candidates are in the mapping diff.

TEST_COMBINATION_FULLY_MAPPED = **NO**. Under the Owner's HIGH-only save rule,
there is no fully mapped dish to recommend for checkout this round.

## E. Tests and repair

- Full backend `mvn verify` with isolated PostgreSQL: **830 tests, 823 passed,
  7 environment-gated skipped, 0 failures**. Uber tests **27/27 executed**,
  including 3 new independent-key/raw-byte/malformed-key tests and 20 actual
  PostgreSQL mapping/store/webhook integration tests using distinct OAuth/HMAC
  fixture secrets. These are local tests, not real Uber orders.
- Frontend full tests **234/234**, including Uber Inbox; production build PASS.
- Application code delta: one private configuration field/accessor and webhook
  key selection; no order/menu/printing/payment behavior changes.
- Staging Compose forwards the optional independent key; example has no value.
- Bounded backend-only update reuses reviewed ingress model/health/rollback;
  exact image, DB backup, zero pending work, unchanged DB/frontend/Production
  container fingerprints and unchanged Flyway are required by the helper.
- Exact current Staging key/client-secret scan over 16 task-changed files: PASS.
- Reviewer found incorrect outbox terminal label COMPLETED in the helper;
  corrected to actual DISPATCHED before runtime execution. No data repair.

## F. Readiness

READY_FOR_REAL_SANDBOX_ORDER_TEST = **NO**.

Exact mapping blocker: **NO_HIGH_CONFIDENCE_STABLE_ID_MAPPING**; no complete
test combination is mapped. Independent-key runtime repair/probes are pending
at this source checkpoint and must not be inferred from unit-test success.
Do not place an order or click Accept/Deny based on the proposal above.
