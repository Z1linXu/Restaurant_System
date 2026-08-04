# AL-003 PR-E Chinatown Profile Overrides

> Status: `AL-003_PR_E_WAITING_FOR_OWNER_REVIEW`
>
> PR-D contract: `5a0dc09944b4b0945fe95027d7f12647212ea559`

## Scope

PR-E supplies the first reviewed, versioned Store Profile. The shared clone
coordinator, repositories, transaction boundary, and generic source-option
composer remain profile-agnostic. Only the concrete Chinatown Profile contains
Store 1, Chinatown category/item rules, PDF prices, ordering, size sets, Combo
policy, and the new target SKUs.

This package does not add a Migration, Controller, public endpoint, runtime
database query, real menu clone, printing change, French localization, or
weekend schedule.

## Target graph

The Profile creates exactly four target categories in this order:

1. `SOUP_NOODLE`
2. `DRY_NOODLE`
3. `SIDE_DISHES`
4. `DRINK`

It selects the `NOODLE` and `COLD` source station semantics and requires the
four reused drinks and two create-only drinks to resolve to one active source drink station. It declares
exactly 17 reviewed target items with the bilingual names, prices, source
policies, and category-local ordering frozen by
`AL-003A_FINAL_MENU_COMPARISON.md`. `sichuan_pepper_chicken` and `tea_egg` use
clone-if-active-or-create; `seven_up` and `ginger_ale` are create-only. All
other target SKUs require unique active source evidence.

## Option policy

The concrete Profile classifies active source options before any write:

- `ADD_ON`, `REMOVE`, `SPICY_LEVEL`, and `SOUP_BASE` are copied where present;
- `NOODLE_TYPE`, `SIZE`, `COMBO`, `COMBO_EGG`, `COMBO_SIDE`, and legacy
  `COMBO_SIDE_REMOVE` are reserved for the reviewed target policy;
- unknown active groups fail closed through PR-D.

The `PROFILE_OVERRIDES` composer then:

- resolves all seven noodle-type definitions from stable source option codes
  and requires consistent names/type/group/price evidence;
- creates all seven types on each of the five target noodles;
- creates S/M/L for Traditional Beef and Vegetable, S/M for Dan Dan, and no
  size row for Beef Tendon or Zha Jiang;
- ensures the stable tea egg and extra-meat add-ons exist on every noodle at
  the PDF prices `1.99` and `6.99`; copied rows are normalized instead of
  duplicated, and missing rows are created by the reviewed Profile;
- creates Combo `+5.00`, tea egg, and the three approved side choices only on
  Traditional Beef, Zha Jiang, Vegetable, and Dan Dan;
- creates no fried-egg Combo and no Combo for Beef Tendon.

Current menu catalog and order submission code attach each `COMBO_SIDE` to the
active `REMOVE` options of its referenced standalone side item. PR-E therefore
uses that reviewed cross-item representation and does not recreate legacy
`COMBO_SIDE_REMOVE` child rows. This avoids duplicate side-removal choices while
retaining the current frontend parent snapshot behavior.

All generated option IDs are fresh. Profile override writes participate in the
outer PR-C transaction; any validation or persistence failure rolls back the
base graph, copied source options, target overrides, revision update, and clone
request completion together.

## Fingerprint

The profile fingerprint includes the exact, case-sensitive profile code,
contract/fingerprint versions, Store 1 source constraint, category/station
selection, all 17 item rules, source-option classifications, seven noodle-type
codes, size rules, four Combo definitions, tea egg/extra-meat price policy, and explicit
no-French/no-schedule decisions. It contains no runtime IDs, source payload,
credential, token, endpoint, or production data.

## Verification coverage

- exact Profile code and Store 1 constraint;
- four categories, station policies, and all 17 item mappings;
- names, PDF prices, active/sold-out state, and item order;
- required, clone-if-active-or-create, and create-only source policies;
- complete source-option classification;
- seven noodle types on all five noodles;
- exact size sets and price deltas;
- four approved Combo sets with tea egg and three sides;
- no Beef Tendon Combo, no fried egg, and no legacy child duplication;
- copied tea egg normalization without duplicate stable codes;
- copied or missing extra-meat normalization to the PDF `6.99` price;
- real PR-C -> PR-D -> PR-E transaction composition and late-failure rollback;
- missing or inconsistent source noodle definitions fail before writes;
- exact Profile code support and stable fingerprint.

## PR-F dependency note

The PR-F preparation audit found that execute is contract-ready but the current
shared code has no genuinely read-only validation/planning boundary. A separate
Dependency Repair package must establish that boundary before PR-F can expose
`/validate`; rollback-only execution is not an acceptable substitute because it
would perform writes and acquire write locks. PR-E does not expand into that
shared API concern.
