# Staging MOCK Printing Runtime Policy Repair Evidence

> Status: `REPOSITORY_REPAIR_VERIFIED_WAITING_FOR_AGENT6_RECHECK_PR`
>
> Package: `STAGING_MOCK_PRINTING_FIELD_TEST_ENABLEMENT`
>
> Date: 2026-08-11, America/Toronto

## Root cause

The current Staging deployment guard accepts server runtime only when
`STAGING_PRINT_MODE=DISABLED` and
`STAGING_PRINTING_FEATURE_ENABLED=false`. The existing application Print
Center accepts all four Store modes once the feature API is enabled, so simply
changing the feature flag would also expose `REAL` and `PAD_DIRECT`. The
current STG-001 authority therefore correctly deferred server-side MOCK until
an application-level allowlist existed.

Fresh read-only runtime inspection retained exact Staging
`53209823fa320cc56c31d04ee5c7719a83a78acc` / V10 with Store 1
`DISABLED/false`, four enabled endpoint-free logical printers, three enabled
assignments (`FRONTDESK_RECEIPT`, `GRAB`, `HOT_KITCHEN`) and zero historical
Print Jobs. Production container identity and health were unchanged.

## Bounded correction

- Add a generic `app.printing.allowed-modes` runtime policy. Its default keeps
  the existing shared application behavior; Staging Compose sets the policy to
  exactly `DISABLED,MOCK`.
- Add a generic `app.printing.endpoint-configuration-enabled` policy. It
  defaults to the existing shared behavior; Staging sets it to `false`.
- Enforce both policies in the existing Store-scoped printing configuration
  service. A disallowed persisted mode also fails before automatic dispatch
  can reach renderer or transport execution.
- Permit only the fail-closed Staging environment pairs `DISABLED/false` and
  `MOCK/true`. Continue rejecting `REAL`, `PAD_DIRECT`, endpoint enablement and
  `STAGING_PRINTER_ENDPOINT`.
- Preserve the existing MOCK path: persisted outbox event, routing,
  `PrintJob`, renderer, assignment, rendered snapshot, MOCK log and terminal
  `PRINTED` state. No Staging-specific business branch or migration is added.
- Capture the effective Store printing mode once per dispatch, reprint or
  diagnostic operation and use that immutable snapshot through the transport
  decision. This closes the Agent 6 mode-change time-of-check/time-of-use
  finding: a concurrent `MOCK` to `DISABLED` change cannot cause the same
  operation to enter physical transport.

## Verification state

- Focused printing/outbox tests: 27 passed, including a regression that returns
  `MOCK` then `DISABLED`, proves one mode read and proves zero printer transport.
- Full backend regression: 394 tests, zero failures, zero errors, three
  pre-existing skips.
- Staging guard regression: passed, including MOCK pair acceptance and
  rejection of expanded mode allowlists or endpoint configuration.
- All 14 deployment shell test files passed after the fake Compose fixtures
  were updated to represent the new two-property application policy.
- The first Agent 6 review rejected the repeated mode reads as a transport
  safety race. The correction above and its regression are complete; fresh
  Agent 6 re-review, PR/merge and exact-SHA Staging runtime validation remain
  pending in this repository repair record.

No Staging or Production mutation, migration, restart, printer contact,
endpoint write, Pad pairing, order creation or credential read occurred during
this repair phase.

## Stop state

`STAGING_MOCK_PRINTING_FIELD_TEST_ENABLEMENT_REPAIR_VERIFIED_WAITING_FOR_AGENT6_RECHECK_PR`
