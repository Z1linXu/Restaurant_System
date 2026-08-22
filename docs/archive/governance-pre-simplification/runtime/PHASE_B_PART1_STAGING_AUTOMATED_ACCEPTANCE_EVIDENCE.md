# Phase B Part 1 Staging Automated Acceptance Evidence

> Status: `PASS_WAITING_FOR_OWNER_MANUAL_ACCEPTANCE`
>
> Date: 2026-08-21, America/Toronto

## Authority and repository

- Standing Staging workflow policy entered `main` through PR #174, merge
  `42b5a15647b83a3f13af85198c60a865bbc68fa7`.
- The bounded Display Rule configuration-access repair entered `main` through
  PR #175, merge `96c81cf4fab8e771187ceeddeed28e5fc3e87f4a`.
- Agent 6 returned `AGENT_6_ACCEPT`; full backend regression passed 575 tests,
  0 failures and 0 errors, with 3 skipped.

## Exact Staging runtime

| Evidence | Result |
| --- | --- |
| deployed SHA | `96c81cf4fab8e771187ceeddeed28e5fc3e87f4a` |
| environment digest | `1ee93bdad179b5c833c400f1431022ccc88f61f1e3393437696ea319269d4ed3` |
| preflight | `PASS`; SHA-256 `bfdd280da1d01a5bc72705f583552f519c3246d8285e24ba57bc4c8e5af24034` |
| deploy | `PASS`; SHA-256 `a93899b25af16707ce5e289fe059fa1054a78a670ee896df3994f5b9ddc3e978` |
| health | `PASS`; final retry SHA-256 `e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14` |
| readiness | `PASS`; SHA-256 `57b1e1737739b1209c71e365a2e3589977309d24f88eb609f122c72e6bdfce65` |
| runtime evidence | `PASS`; SHA-256 `09c2746dfb5828ca267ba37655ce2d87721aecdd1b0a0782cb3b6cc85383050f` |
| Flyway | 21 successful rows, max version V21, digest `f78ba6a05f0e99a2d80eec1db6fe6405569853af27db08dc726cd7aaff44d139` |
| containers | db/backend/nginx running, restart count 0 |

The first two immediate health probes observed transient HTTP 502 while Spring
Boot was still starting. The application completed normally, the final health
probe passed, and no restart or repair was required.

## Part 1 automated acceptance

Canonical acceptance evidence SHA-256:
`98664247bb3c368208c962866ede5ac6f2fe3bbf1149e7eb8270af62dc0b88e4`.

The unique fixture is Store 11,
`PHASE_B_VALIDATION_STORE_96C81CF_R1`. It is a non-active
`VALIDATION_FIXTURE`, lifecycle `READY_FOR_REVIEW`, provisioned by
`PHASE_B_OWNER_PROVISIONING`.

The canonical harness passed Owner login/workspace, provisioning catalog,
create/replay, lifecycle/provenance, ledger uniqueness, Master mapping counts,
independent local IDs and parent remap, Store context/catalog, item and category
isolation, Store-only item isolation, pricing independence, Combo independence,
Printing Display Rule independence, source/Master/Profile immutability and
logout.

Supplemental evidence SHA-256
`fd48bcac79104d2390d35f984e8e06f2a5e35c58d28bdd24003c82f3a57b2476`
records wrong-Organization HTTP 403, no active/invalid validation fixtures, no
duplicate validation Store code and the exact target Store ready for review.

## Combo Printing MOCK verification

Endpoint-free MOCK evidence SHA-256:
`963f71b3e48863399db2a8808964d1337114c8fc516d156ab7e32f5201c40a52`.

A synthetic Store 1 order created one GRAB, one HOT_KITCHEN and one
FRONTDESK_RECEIPT job; all reached `PRINTED`, all printer endpoints remained
blank, and the order was cancelled after verification. Runtime facts:

- GRAB printed `毛豆 x1` before `大×1 | +煎 | 备注：这是个备注`;
- GRAB did not contain `+毛豆`, and the parent note appeared exactly once;
- the COLD synthetic side task carried no parent note;
- HOT_KITCHEN retained the parent `combo_fried_egg` line but contained no
  `毛豆` side leakage;
- FRONTDESK_RECEIPT remained nonblank and retained its existing Combo receipt
  representation.

The repository and persisted Display Rule contract map `combo_fried_egg` to
`+煎`, not `+蛋`. Changing that published label would alter the Display Rule
fingerprint/master-data contract and was not performed under this no-migration,
no-real-Store-mutation package.

An earlier custom smoke evidence file with SHA-256
`33ee33da686b755946f89f060ff1ce8eb66c1de4329a39255ab93e7bf4580cc9`
is explicitly invalid and excluded: its temporary cleanup trap swallowed a
failed assertion. The corrected evidence above is fail-closed and was derived
from the retained, cancelled synthetic order snapshots.

## Safety and remaining gate

- Staging was mutated only by exact-SHA deployment, bounded synthetic
  validation Stores and one cancelled MOCK order.
- Production had no deploy, restart, Flyway, credential or data mutation.
- No real printer endpoint, Pad binding or physical output was created.
- Phase B Part 2, activation and Phase C remain unauthorized.
- Remaining gate: Owner manual Phase B Part 1 acceptance.
