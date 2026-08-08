# AL-004 Generic Store Profile Contract

> Package state: `STACKED_ONLY_WAITING_FOR_OWNER_REVIEW`
>
> Parent: stacked AL-003S preparation Head `880795f9fa6101116f9fd1f370caeb0bdf16b647`
>
> Review: Draft PR #64, base Draft PR #63 branch
>
> Runtime access: not performed

## 1. Bounded purpose

This package adds the first Store-level profile contract above the existing
menu-only profile registry. It is intentionally declarative. It does not call
Owner onboarding, clone a menu, create a Store, register a concrete Store
Profile, expose an HTTP endpoint, add a migration, or run a provisioning
module.

The implementation follows the fixed rule:

`Profile defines WHAT the Store needs. Provisioning Module defines HOW the capability is provisioned.`

## 2. Contract

`StoreProfileDescriptor` declares:

- an exact, case-sensitive `profileCode` and immutable `profileVersion`;
- one versioned reference and reviewed expected fingerprint per provisioning module;
- explicit module policy: `REQUIRED`, `MANUAL_AFTER_CREATION`, or
  `NOT_APPLICABLE`;
- activation requirements; and
- a deterministic SHA-256 fingerprint over canonical reviewed fields.

`StoreProfileRegistry` rejects whitespace aliases, duplicate exact
code/version identities, duplicate module references, invalid activation
requirements, and a fingerprint that differs from the canonical composition.
Its summary projection contains only profile identity, fingerprint, and module
codes.

The schema deliberately has no arbitrary settings map. Passwords, tokens,
printer endpoints, device secrets, raw requests, customer data, and runtime
credentials therefore have no profile field through which they can enter this
contract.

Each module reference carries both a reviewed configuration identity and its
expected fingerprint. This first registry preserves that expected digest in
the parent fingerprint but does not prove the referenced module/configuration
exists or is deployed. That transitive compatibility check belongs to the
future module registry/engine. A future complete Store Profile can therefore
bind the exact version of the existing Chinatown menu profile or another module contract
without inheriting `sourceStoreId` as a Store-wide concept and without creating
a second clone engine.

## 3. Explicit exclusions

- No concrete Chinatown or St-Denis Store Profile is registered by this slice.
- No Store Core base settings are guessed while timezone, Store-scoped tax,
  language, and receipt-default authorities remain incomplete.
- No Store provisioning engine or cross-module transaction coordinator.
- No Owner Create Store / template-selection API or UI.
- No Store creation, staff creation, menu clone, table write, printer setup,
  Pad pairing, or activation transition.
- No new database table or Flyway migration.
- No new menu clone path and no change to AL-003 replay/FAILED semantics.
- No Production or Staging access.

## 4. Verification contract

Focused tests cover exact identity lookup, ASCII-safe identity grammar,
version coexistence, zero-profile Spring startup, duplicate rejection,
deterministic ordering-independent fingerprints, canonical fingerprint
enforcement, immutable registry snapshots, applicable activation requirements,
and safe summary projection.

Full backend tests and compile must pass before the Draft PR review gate. This
package remains stacked-only until each dependency is independently reviewed
and promoted from latest `main`.

## 5. Next bounded input

The next AL-004 slice may add a concrete reviewed Store Profile only after its
module configuration references and fingerprints are fixed. Chinatown remains
the first intended sample. `ST_DENIS_PROFILE_V1` remains unregistered until its
complete desired state and menu reference are reviewed; STG-005B synthetic
menu evidence is not a complete St-Denis Store Profile or Production evidence.
