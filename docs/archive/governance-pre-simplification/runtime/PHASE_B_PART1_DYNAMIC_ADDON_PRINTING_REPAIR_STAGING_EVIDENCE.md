# Phase B Part 1 Dynamic ADD_ON Printing Repair — Staging Evidence

Date: 2026-08-21 (America/Toronto)

This evidence records the bounded Dynamic `ADD_ON` kitchen-printing repair
inside the existing Phase B Part 1 Owner manual acceptance gate. It does not
authorize Phase B Part 2, Store activation, a real printer/Pad binding,
Production deployment or Production mutation.

## Reviewed release

- implementation PR: `#179`
- implementation commit: `c6b7b017bc2b9578e1728a4d794f63194f0e7ffc`
- merged and deployed SHA: `8e787fa1c0030d74cb9d785122392f540b206db5`
- Staging environment: `restaurant-pos-staging`
- Flyway: `count=21`, `max_version=21`
- migration/backfill: none
- Production mutation: none

## Repair contract

`KitchenModifierTokenResolver` is the shared semantic boundary used by
original/update GRAB creation, HOT_KITCHEN rendering and Printing Display Rule
preview. A Display Rule token wins when configured. Otherwise a legal ADD_ON
or REMOVE remains visible using its frozen `OrderItemOption` snapshot label;
an unknown Java code is no longer a print-admission failure. Labels remain
presentation fallback only and are not used for identity, Combo de-duplication
or Store isolation.

The repair does not change Combo component identity, Combo egg eligibility,
KitchenTask/station routing, FRONTDESK_RECEIPT rendering, existing
`PrintJob.rendered_text_snapshot` reprint, or MOCK/REAL/PAD_DIRECT transport.

The separate option-code stability defect observed as `jianiujin -> s` remains
the explicit `OPTION-CODE-STABILITY` backlog item. This repair adds no schema
constraint and rewrites no historical snapshot.

## Repository verification

- focused/relevant backend regression: `113` tests, PASS
- complete backend regression: `591` tests, `0` failures, `0` errors,
  `3` skipped, PASS
- backend package: PASS
- governance validation: PASS
- `git diff --check`: PASS
- Agent 6: `AGENT_6_ACCEPT`, no blocker or P0/P1/P2 finding
- frontend suite/build: not run because no frontend source or API request shape
  changed; backend preview and runtime paths are covered directly

## Exact-SHA Staging evidence

All listed files are private mode-`0600` evidence under
`/srv/restaurant-pos/staging/evidence`.

| Check | Evidence file | SHA-256 / result |
| --- | --- | --- |
| candidate import | `dynamic-addon-candidate-import-8e787fa1c0030d74cb9d785122392f540b206db5.txt` | `416742d7b05be446ffe4e8096cc8ab656dd725acf5eef32c4e6870c32accf4d8`; PASS |
| release environment | `dynamic-addon-release-env-8e787fa1c0030d74cb9d785122392f540b206db5.txt` | `22ce122332f065d99744b83cdb2f993012c857f20809724a17ecd25ba85d613f`; resulting env digest `181797eeea04196638f8cd7585ab7d86edc77fcca090805d7e45af13cd4eabf5` |
| preflight | `dynamic-addon-preflight-8e787fa1c0030d74cb9d785122392f540b206db5.txt` | `38fdcc42e198e17e15e444c42f0d8966190e680e608bc129a602d5d4f7821a0f`; PASS |
| deploy | `dynamic-addon-deploy-8e787fa1c0030d74cb9d785122392f540b206db5.txt` | `9efb958dc80b9ee24a3a72a33d87e7f546a885c4b5edfef78b0a74ec549b812c`; PASS |
| health | `dynamic-addon-health-8e787fa1c0030d74cb9d785122392f540b206db5.txt` | `e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14`; PASS |
| readiness | `dynamic-addon-readiness-8e787fa1c0030d74cb9d785122392f540b206db5.txt` | `6be3cce13d6d0274929661cc82068e3f80ec4a4274014105e5fb5efc8207769a`; PASS |
| runtime | `dynamic-addon-runtime-8e787fa1c0030d74cb9d785122392f540b206db5.txt` | `ac82e4a16a4c5fe58cb022af110df1d82349eb7b846f3b25cd4d271f9c8abd6b`; exact SHA, Flyway V21, restart counts 0, PASS |
| affected slice | `dynamic-addon-affected-slice-8e787fa1c0030d74cb9d785122392f540b206db5.txt` | `899ade00eaa85d5b882d3454696538a4790277e404a71a6c28187aeed255379e`; PASS |
| post-acceptance health | `dynamic-addon-post-acceptance-health-8e787fa1c0030d74cb9d785122392f540b206db5.txt` | `e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14`; PASS |
| post-acceptance readiness | `dynamic-addon-post-acceptance-readiness-8e787fa1c0030d74cb9d785122392f540b206db5.txt` | `c0fec6c04c3e8ddab1e0ca74a94630dc8920172a1c3db82e324446785a16c5d9`; PASS |

The first health probes returned `502` while Spring was still starting; Flyway
validated V21 and bounded retry then passed. The first affected-slice harness
attempt assumed the PrintJob API would return `execution_mode=MOCK`; that field
was null even though Store 1 was endpoint-free `MOCK` and the jobs were
`PRINTED`. The failed harness evidence was retained as
`dynamic-addon-affected-slice-failed-r1-<sha>.txt`; the corrected harness bound
the Store printing boundary separately and passed without application repair.

## Affected Staging acceptance

The actual Store 1 catalog returned `红烧牛筋面`, Store-local ADD_ON
`id=3437, code=s, label=加牛筋`, and REMOVE
`id=3438, code=removebaicai, label=走上海青`. No Display Rule was published for
the dynamic ADD_ON. ORIGINAL and UPDATE MOCK GRAB jobs both rendered:

```text
中红×1 | +牛筋 走上海青
```

Both synthetic orders used the normal API and frozen order snapshots. The
successful order was cancelled after evidence collection. HOT_KITCHEN was not
applicable for this exact option selection under the unchanged eligibility and
station rules. Focused and complete regression separately proved the same
fallback on an applicable HOT_KITCHEN path.

An actual Staging Display Rule preview injected `s -> +筋验收`; both GRAB and
HOT_KITCHEN previews used the override. No draft was saved or published.

Post-acceptance readiness retained Staging fingerprint
`d4808925c7c9e50ac5fd26c575ef55835fdcc16dc57a2e6b87f381bf03f8bb33`
and Production fingerprint
`df8fe7cc36fbfb0909fcfe6528f855bab69190271f586adc31d4794a1d8bbc91`.
Production runtime and data were not mutated.

## Gate

Repository repair and exact-SHA Staging automated acceptance are PASS. The
current gate remains `WAITING_FOR_OWNER_MANUAL_ACCEPTANCE`; the next action is
Owner manual retest on Staging. Phase B Part 2 remains unauthorized.
