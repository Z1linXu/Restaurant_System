# AL-003 PR-E Chinatown Profile Overrides

> Status: `AL-003_PR_E_PROMOTION_WAITING_FOR_OWNER_REVIEW`
>
> Promotion base: `4265d66ed9246738ab3baea8b4853a2c8cad4c20`
>
> Merged PR-D dependency: PR #52, merge `13f26f1`
>
> Historical single-layer source: `972802e701cb9cb2623b647132e4430a7b338e32`

## Scope

PR-E supplies the first complete reviewed, versioned Store Profile. Shared
clone coordination, repositories, transaction boundaries, and source-option
copying remain profile-agnostic. Only the concrete Chinatown Profile and its
override composer contain Store 1, category/item rules, PDF prices, ordering,
size sets, Combo policy, and new target SKUs.

The package adds no Migration, Controller, public endpoint, authorization,
runtime database query, real clone, French localization, or weekend schedule.
It makes only bounded Small label compatibility changes in existing order
instruction and frontdesk receipt formatting; it does not change print routing,
printing state, payment, order lifecycle, or KDS behavior.

## Target graph

The Profile defines these four categories in order:

1. `SOUP_NOODLE`
2. `DRY_NOODLE`
3. `SIDE_DISHES`
4. `DRINK`

It selects the `NOODLE` and `COLD` source station semantics and resolves one
active source drink station for reused drinks. It declares exactly 17 reviewed
target items with bilingual names, PDF prices, source policies, and
category-local order from `AL-003A_FINAL_MENU_COMPARISON.md`.
`sichuan_pepper_chicken` and `tea_egg` are clone-if-active-or-create;
`seven_up` and `ginger_ale` are create-only. All other target SKUs require
unique active source evidence.

## Option policy

- Active `ADD_ON`, `REMOVE`, `SPICY_LEVEL`, and `SOUP_BASE` options are copied
  under PR-D rules.
- `NOODLE_TYPE`, `SIZE`, `COMBO`, `COMBO_EGG`, `COMBO_SIDE`, and legacy
  `COMBO_SIDE_REMOVE` are reserved for reviewed Profile overrides.
- All seven source-proven noodle types are created on all five target noodles.
- Traditional Beef and Vegetable receive S/M/L; Dan Dan receives S/M; Beef
  Tendon and Zha Jiang receive no size option.
- Tea egg `1.99` and extra meat `6.99` are normalized or created on each target
  noodle without duplicate stable codes.
- Combo `+5.00`, tea egg, and three approved side choices are created only for
  Traditional Beef, Zha Jiang, Vegetable, and Dan Dan.
- Beef Tendon receives no Combo; no fried-egg Combo is created.
- Side removal choices continue to derive from the referenced standalone side
  item. Legacy `COMBO_SIDE_REMOVE` children are not duplicated.

Generated option IDs are fresh. Override writes remain inside the outer PR-C
transaction, so a late failure rolls back base rows, copied options, Profile
overrides, and revision advancement together. The integration test also verifies
that the global option count returns to its pre-attempt value. Durable request
completion remains the existing coordinator contract and is not redefined by
PR-E.

## Fingerprint and identity

The exact, case-sensitive, whitespace-exact profile code is
`CHINATOWN_MENU_2026_02_02`. Its SHA-256 fingerprint covers the reviewed source
constraint, categories, stations, 17 item rules, source-option classifications,
seven noodle types, size sets, Combo definitions, add-on prices, and explicit
no-French/no-schedule decisions. It includes the reviewed Source Store constraint
(`source_store_id=1`), but contains no runtime-generated IDs, target Store ID,
menu payload, credential, token, printer endpoint, or production data.

## Verification contract

The promotion must pass:

- exact profile/category/station/item/name/price/order/source-policy tests;
- generated noodle type, size, add-on, and Combo graph tests;
- strict profile identity and stable fingerprint tests;
- real PR-C -> PR-D -> PR-E transaction composition, rollback, and source
  invariance tests;
- Small/Medium/Large order-instruction and receipt-label compatibility tests;
- frontend Small default, amount, and submitted snapshot tests;
- full backend and frontend suites, compile/build, scope, migration, secret,
  hardcode, and diff checks;
- independent Agent 6 review.

## Dependency boundary

PR-F0 and PR-F are not part of this package. No public validate/execute route is
created. Store 1 runtime access, any real target clone, Flyway execution,
Staging/Production action, merge, and deployment remain Owner-gated.

## Promotion verification evidence

The latest-main promotion candidate passed:

- focused backend Profile, override-composer, transaction, PR-D compatibility,
  order-instruction, and GRAB receipt tests;
- full backend `mvn -q test` and `mvn -q -DskipTests compile`;
- full frontend `npm test -- --run` (`16` files, `79` tests) and
  `npm run build`;
- an end-to-end frontend Small path from catalog mapping through default draft,
  frozen line item, and submit payload;
- migration, scope, shared-hardcode, secret, stale-governance, and
  `git diff --check` scans.

Agent 6 completed an independent read-only review. Its pre-commit findings were
resolved by tracking this evidence file, adding an Option rollback count
assertion, narrowing the durable-request rollback claim, clarifying the
fingerprint Source Store constraint, and aligning `Tea Boil Egg` with the final
comparison. The post-fix review has no blocking code, contract, scope, or
governance finding.
