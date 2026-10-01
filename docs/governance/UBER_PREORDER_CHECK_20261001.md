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
- Private config and runtime `UBER_EATS_WEBHOOK_SIGNING_KEY`: **PRESENT**.
- Public probes at **2026-10-01T17:42:56Z**: wrong key **401 / 29 ms**;
  current explicit key **200 / 54 ms** against identical raw bytes.
  Event `internal-signing-preorder-20261001-final` is `IGNORED`; Uber inbox
  order rows **0**, local UBER_EATS orders **0**. Runtime key matches the
  privately configured Dashboard Primary key. **WEBHOOK_SIGNING_VERIFICATION=PASS**.

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
Live browser refresh after deployment: selected target Store, Uber Inbox loaded,
no connection error, empty inbox. Health and public ingress verification PASS.
Earlier runtime authorization probes: Store 1 FRONTDESK can read Store 1 and
receives 403 for Store 18/24 context/connection/orders; OWNER sees no binding
in those other stores. Cross-org live testing unavailable: all Staging stores
are org 1. This is scope evidence, not real-order isolation/E2E evidence.

## C. Menu and mapping

Fresh real Testing token granted `eats.store`; menu GET HTTP 200 at
**2026-10-01T17:15:56Z**. API returns no menu version, ETag or Last-Modified.
Local authenticated mapping-catalog GET HTTP 200; Store/org 1/1, revision 215,
catalog `menu-catalog-v4`, content hash `fnv1a32:562b32ff`.

| Metric | Current |
|---|---:|
| Uber API item objects | 74 |
| UBER_ROOT_ITEMS | 41 (earlier report's 31 was a counting omission) |
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
Re-reading the earlier private snapshot also yields 41 roots and 258 contexts;
the old/new root, modifier and context identity sets are identical. The earlier
31/238/10-unreferenced report is explicitly corrected; no menu change is inferred.
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
- Reviewer and read-only preflight reconciled historical COMPLETED and current
  DISPATCHED outbox terminal records; both are accepted, pending/unknown states
  remain blocked. Bytecode cache generation is disabled before helper imports.
  Early preflight attempts stopped before runtime mutation. A subsequent update
  rolled back because inherited Compose labels differed from the normalized
  model. Filtering management labels resolved this; Agent 6 accepted the repair
  without P0/P1/P2. No data repair was performed.

## F. Readiness

READY_FOR_REAL_SANDBOX_ORDER_TEST = **NO**.

Exact mapping blocker: **NO_HIGH_CONFIDENCE_STABLE_ID_MAPPING**; no complete
test combination is mapped. All webhook/binding/Inbox pre-order gates passed;
the mapping gate remains blocked.
Do not place an order or click Accept/Deny based on the proposal above.

## Deployment evidence

- Backend exact reviewed source: `21826ea5b2dc9297dd98bc8be47f5223446d11c6`.
- Runtime image: `sha256:1cafba0b58575ffa93e66d95622700c88ced7ad313da8eec4085ca5743938d3e`.
- Tested jar SHA256: `90dc663508abfc9c5b39827850e7e97b70da2b6e69aab90e2095994dde2dab82`; application source unchanged across helper-only commits.
- Frontend remains `7b70a4fac4e350444bcd37687ca88a8636ff8f2a`.
- Private DB backup/rollback: `/srv/restaurant-pos/staging/uber-signing-key-20261001-attempt2`; original attempt evidence retained separately.
- Strict runtime model, unchanged DB/frontend/Production container fingerprints, unchanged Flyway V30 and Production read-only health: PASS.
- Public HTTPS/frontend/assets/CORS/forwarding/WebSocket checks: **42/42 PASS**.
- No real Sandbox order, Accept/Deny, menu mutation or physical printing performed.
