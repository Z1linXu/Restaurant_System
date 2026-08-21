# Chain Master Menu Versioning Contract

## Lifecycle

```text
DRAFT -> PUBLISHED -> RETIRED
```

Only `PUBLISHED` versions may be used for Store materialization. Published
versions are immutable. Corrections produce a new version.

## Version identity

Each version has:

- Organization identity;
- `master_menu_key`;
- `version_key`;
- canonical content artifact;
- deterministic `fingerprint_sha256`;
- source profile/artifact provenance;
- publication timestamp and publisher metadata;
- lifecycle status.

## Immutability

After publication, the following must not change in place:

- graph content;
- master category/product/option keys;
- source provenance;
- fingerprint;
- version key;
- publication metadata.

Runtime Store state must never be used to rewrite a published Master version.

## New versions

`LANZHOU_CHAIN_MASTER_MENU/v2` may be introduced later for chain standard
changes. Publishing a new version does not modify existing Stores.

Future update handling is explicit:

```text
published v1 Store materialization remains as-is
published v2 exists as a separate standard
future diff/apply workflow may compare v1-derived Store state against v2
Owner explicitly decides whether to apply changes
```

## Diff contract for the future

Future diff tooling may classify:

- new master category/product/option;
- renamed master entity;
- default price/reference change;
- default active state change;
- option hierarchy change;
- item moved to another category;
- removed or retired master entity;
- conflict with Store-local override or Store-only addition.

The diff tool must not auto-apply. It must present the conflict and require an
Owner-approved apply action.

## Compatibility

A Store materialized from `v1` must remain operational after `v2` is published.
Reports and historical orders continue to use captured Store-local snapshots
plus any retained master identity mapping.
