# Three Reliability Production Promotion Tooling Repair Evidence

> Status: `DEPENDENCY_REPAIR_USED_FOR_PRODUCTION_PROMOTION_PASS`
>
> Date: 2026-08-12, America/Toronto
>
> Scope: `THREE_RELIABILITY_REPAIRS_PRODUCTION_RC_PROMOTION`

## Authority and boundary

The Owner approved promotion of the exact Staging-deployed and tested
three-reliability repair candidate after all mandatory Production release gates
pass. This dependency repair is repository/tooling-only. It does not deploy,
restart, migrate, back up, restore or reconfigure Production by itself.

## Fresh blocker

Fresh runtime evidence showed the next Production candidate must be the exact
Staging runtime-sensitive SHA
`3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`, while later `main`
`1a69fc1f96774f21f76f54320dba236663fdf830` is docs-only and must not become
the Production application candidate.

Fresh Production also showed the running previous application artifact is the
exact backend/frontend pair for
`2661eb76c36dd9aa58db94ceacd278242ef4c9ab`, while the retained Production
control checkout remains
`4667f3c35f85c9f8538f82789d9df1531d4fbc9e`. The existing promotion helper used
one `production_previous_sha` field for both concepts. That made a semantically
correct RC manifest impossible for this promotion: `production_previous_sha`
must identify the previous application rollback target, not the control
checkout.

Fresh Production is already Flyway V10. The existing backup rehearsal helper
defaulted isolated-restore ledger verification to the old V1-through-V7 backup
shape. A new V10 pre-deploy backup therefore needed an explicit manifest-bound
backup Flyway target before it could be used as a current V10 gate.

## Repair

- `production-exact-artifact-promote.sh` now reads optional
  `production_control_checkout_sha`. If absent, old manifests remain compatible
  by falling back to `production_previous_sha`.
- `production-backup-rehearsal.sh` now reads optional `backup_flyway_target`.
  If absent, old manifests remain compatible by defaulting to `V7`; the new
  three-reliability RC can explicitly require a V10 restore ledger.

The repair does not weaken exact artifact checks, no-build/no-pull behavior,
fixed state-root checks, DB container preservation, digest checks, private
backup root checks, isolated network-none restore, or destructive-operation
guards.

## Verification

- `bash deployment/cloud/tests/test_production_exact_artifact_promote.sh`:
  PASS.
- `bash deployment/cloud/tests/test_production_backup_rehearsal.sh`: PASS.
- `git diff --check`: PASS.

No migration changed. No application business code changed. No Production or
Staging runtime action was performed by this repository repair.

## Runtime use boundary

This repair entered `main` through PR #124 at
`47584d40e9a4f65cd719d8ea898d723bd8dba64f` and was used to freeze and promote
`RC-THREE-RELIABILITY-20260812-3EC4D88`. The runtime promotion evidence is
[Production three-reliability RC promotion evidence](PRODUCTION_THREE_RELIABILITY_RC_PROMOTION_EVIDENCE.md).
