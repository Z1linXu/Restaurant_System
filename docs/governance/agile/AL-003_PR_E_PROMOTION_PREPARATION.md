# AL-003 PR-E Promotion Preparation

> Status: `PREPARED_WAITING_FOR_PR_D_DEPENDENCY`
>
> Audit base: `e6b41dd644c50b847d27947b5b0d27e1d4449c09`
>
> Required upstream: Draft PR #52 must first enter `origin/main`
>
> Historical single-layer source: `972802e701cb9cb2623b647132e4430a7b338e32`

## Boundary

This is a preparation record, not a promotion and not evidence of capability in
`main`. The future PR-E must be rebuilt from the post-PR-D `origin/main`; it
must not cherry-pick the historical stacked merge or copy historical shared
governance files over newer content.

## Audited single-layer scope

Production changes in the historical layer are limited to:

- the concrete `ChinatownMenuCloneProfile`;
- the concrete `ChinatownMenuProfileOverridesComposer`;
- bounded Small-size display compatibility in `OrderServiceImpl` and
  `FrontdeskReceiptRenderer`.

The layer also contains focused profile, transaction, order-display, and
receipt tests. It contains no migration, Controller, public endpoint,
authorization implementation, runtime read, or deployment.

## Valid content to migrate

- Four categories and three station semantics.
- Seventeen reviewed target items and the AL-003A category/item order.
- PDF prices, bilingual names, exact size sets, seven noodle types on all five
  target noodles, and the four reviewed Combo definitions.
- Combo 3 includes one tea egg; no Combo is created for Beef Tendon.
- `tea_egg` remains both a standalone item and an add-on.
- `sichuan_pepper_chicken` and `tea_egg` use the reviewed
  clone-if-active-or-create policy; `seven_up` and `ginger_ale` are create-only.
- Store 1, Chinatown SKUs, prices, ordering, and Combo rules remain only in the
  concrete profile/composer. Shared services remain profile-agnostic.
- Profile code matching remains strict, case-sensitive, and whitespace-exact.

## Required corrections during promotion

- Rebuild governance text from the then-current `main`; historical Planbook and
  SYSTEM status text is stale.
- Do not describe the layer as having no printing-related file change: it has a
  bounded receipt-label compatibility change, but no routing or printing-state
  change.
- Bind the package to the actual merged PR-D SHA, not historical PR-D.
- Synchronize Feature Backlog, technical plan, Planbook, SYSTEM documentation,
  and API boundary in the same code iteration.
- Keep the Small compatibility work as a reviewable commit inside PR-E.
- Add frontend regression coverage for default Small selection, size price
  calculation, and submitted snapshot preservation. Historical backend tests
  alone do not prove those frontend behaviors.

## Verification matrix

- Focused Chinatown profile, override composer, source-option composer, and
  transaction integration tests.
- Order shorthand and receipt compatibility tests for Small/Medium/Large.
- Frontend default-size, price, cart, and submitted-snapshot tests and build.
- Full backend tests and compile.
- Transaction late-failure rollback and source invariance.
- Shared-code Store-name/Store-ID hardcode scan.
- Migration, secret, scope, and `git diff --check` scans.
- Independent Agent 6 review.

## Explicit exclusions

No migration, Controller/API, authorization package, PR-F0 planning layer,
Store 1 runtime query, real clone, SSH, Staging/Production action, printing
routing/state change, Payment, Order lifecycle, or KDS change.

## Promotion gate

PR-E may start only after PR #52 is merged and a fresh `origin/main` SHA is
recorded. No new prerequisite conflict was found in the PR-E layer itself.
