# Store Menu Materialization Contract

## Purpose

Store materialization creates Store-owned menu rows from a published Master
Menu and a reviewed Store Profile version. It is a one-time provisioning
transaction for Phase B v1.

## Inputs

- target Organization;
- target Store request and Store code/name;
- published Master Menu key/version/fingerprint;
- reviewed Profile key/version/fingerprint;
- Owner-approved provisioning request id;
- runtime environment capability gate for Phase B provisioning.

## Outputs

The transaction creates or initializes Store-owned rows:

- `stores`;
- `menu_categories`;
- `stations` from profile station template;
- `menu_items`;
- `menu_item_options`;
- `store_pricing_policies`;
- `store_combo_groups` and `store_combo_components`;
- Store-owned `printing_display_rule_sets` and initial published revision;
- Store module rows, role/access defaults and logical topology according to the
  Profile contract;
- future master identity mappings for categories/items/options.

## Rules

- Do not copy source Store database IDs.
- Do not live-link to St-Denis.
- Do not read Production St-Denis as the materialization source.
- Do not materialize physical printer endpoints, Pad credentials, device
  secrets, passwords, tokens, cookies or payment/customer data.
- Materialization must be idempotent under the provisioning request id.
- Materialization must run in one transaction for Store, menu, pricing, combo,
  module and initial A11 rule state.
- Store `menu_revision` must be initialized/incremented consistently.
- Generated Store-local rows are the Store runtime source after creation.

## Store independence

After materialization, local Store operations mutate Store-owned rows only.
They do not rewrite the Master Menu, Profile version or source St-Denis
artifact.

## Deactivation semantics

- Category deactivation hides the category and its child items from the Store
  catalog/order flow but does not have to rewrite every child item active flag.
- Item deactivation hides the item from the Store catalog/order flow.
- Option deactivation hides the option from selection where applicable.
- Historical order and print snapshots remain unchanged.

## Failure behavior

If validation fails, the transaction must fail closed before creating an active
partial Store. Partial active Stores are not allowed.

Failure examples:

- Master fingerprint mismatch;
- Profile fingerprint mismatch;
- missing required Master/Profile artifact;
- unresolved station/category/item/option reference;
- duplicate target Store code;
- unsupported artifact schema;
- prohibited secret/runtime key in Profile or Master content.
