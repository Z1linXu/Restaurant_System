# TWIN-001 Staging Reconstruction Runbook

> Scope: isolated Staging only; manifest v2 fingerprint
> `1c82440ca4677f9d1585369dc719a2f9b55d47e34344f5824f256775ec875e68`.

This runbook projects the retained synthetic Store `1` into a Production-like
St-Denis operational Twin without contacting Production, changing Flyway,
deleting the synthetic baseline, copying credentials, or configuring physical
printer/device endpoints.

## Fixed boundaries

- database: `restaurant_pos_staging`, user `restaurant_pos_staging`;
- Organization: `1 / STG005_ORG_20260809_R01`;
- Store: `1 / STG005_SRC_20260809_R01`;
- Flyway: ten successful rows, V10;
- input: the reviewed manifest v2 only;
- Printing: Store feature remains `DISABLED`; logical printers have null
  endpoints;
- Devices: safe topology only, with no token/hash/pairing action;
- STAFF: Production-equivalent usernames and roles, independently generated
  Staging credentials;
- Production: no query, lifecycle action, or write.

The Store/Organization codes remain synthetic environment identities. Menu,
table, KDS and logical topology values are projected from manifest v2. This is
an intentional environment difference, not a behavior difference.

## Projector contract

`tools/twin_staging_reconstruction.py` has three explicit modes:

```text
python3 tools/twin_staging_reconstruction.py plan
python3 tools/twin_staging_reconstruction.py apply --execute --config-only
python3 tools/twin_staging_reconstruction.py validate
```

`plan` and `validate` use one bounded explicit-column read-only transaction.
`apply` accepts only the complete concrete retained `4/3/13/38` baseline or returns a
sanitized replay result for an already exact Twin. The write transaction uses
an advisory action lock plus fixed-order configuration-table locks, short lock
timeout, exact database/Organization/Store/V10 guards, and an exact in-lock
baseline recheck. Before commit it performs full bidirectional value and
relationship parity checks for every projected domain. It preserves all
existing baseline IDs, inserts only missing rows, sets no printer endpoint or
device token, and performs no delete, truncate, schema migration, Flyway
history edit, or cross-Store write.

The only synthetic-only menu semantic is the retained
`cucumber_salad/remove_garlic` option. Manifest v2 proves that the Production
row for that item is `remove_peanut`; the projector records this exact
one-to-one replacement explicitly and preserves the existing row ID. No seed
default or inferred Production value is used.

Expected projected counts are:

```text
categories=6 stations=5 items=39 options=380 parent_edges=11
tables=13 kds=6 printers=4 assignments=3 devices=7
```

## Independent Staging credentials

The permanent credential bundle must be a server-private regular file with
mode `0600`. It contains the retained Staging Owner password and three newly
generated 20-character Staging-only staff passwords. It must never enter Git,
argv, stdout, shell history, evidence, or a PR.

The approved retrieval path is:

```text
/srv/restaurant-pos/staging/state/twin001-staff-credentials-v1.json
```

Create it atomically under `umask 077`, sourcing the retained Owner password
from its existing private path and generating each staff password independently.
Do not print the bundle. Open it as an inherited descriptor and run:

```text
python3 tools/twin_staging_staff_reconcile.py \
  --execute --secrets-fd 3 \
  --expected-runtime-sha <exact-current-main-sha> \
  --runtime-evidence /absolute/private/path/runtime-evidence.txt \
  --runtime-evidence-sha256 <sha256> \
  --manifest-fingerprint 1c82440ca4677f9d1585369dc719a2f9b55d47e34344f5824f256775ec875e68 \
  3</srv/restaurant-pos/staging/state/twin001-staff-credentials-v1.json
```

The runtime evidence must be a mode-`0600` regular file generated within the
previous 15 minutes and must bind exact deployed SHA, Flyway V10 and PASS.
The tool logs in through the existing API, renames only the retained synthetic
Owner login to `owner`, and creates or validates `manager/MANAGER`,
`staffA/FRONTDESK`, and `staffB/FRONTDESK`. It verifies the unique synthetic
Organization/Store through both workspace and Store-context APIs, sends no
`full_name` or `phone`, and uses the existing BCrypt Staff service. Each
supplied staff credential must complete a real login; an existing credential
that cannot log in is rotated through the bounded Staff API and then verified.
It never reads a Production credential/hash.

After the first successful run, atomically change only
`owner_login_identifier` in the private bundle to `owner`; retain the same
password and mode. The reconciler deliberately tries the retained and target
Owner identifier so a bounded retry remains possible after a partial API
batch.

## Validation and rollback boundary

The final validator compares every concrete safe value and relationship,
including duplicate-SKU disambiguation and all 11 parent-option edges. It also
requires Printing `DISABLED`, no logical-printer endpoint, zero configured
device-token count, exact staff/access, and V10. No device-token value is ever
selected. A mismatch fails closed.

No automatic rollback deletes projected rows. A transaction failure rolls back
the configuration batch. A later failure retains the current state for bounded
diagnosis and retry. Reset, volume deletion, Flyway edits, downgrade, physical
printer binding, Pad pairing, or Production action is a separate Owner gate.

Automated application smoke runs only after parity validation. Real printing,
home-printer binding and physical Pad pairing remain
`SEPARATE_OWNER_RUNTIME_GATE_PENDING`.
