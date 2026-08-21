# STG-008 Non-Web Request Context Dependency Repair Evidence

> Evidence classification: `STG008_NON_WEB_REQUEST_CONTEXT_DEPENDENCY_REPAIR`
>
> Runtime result: `NO_GO_BEFORE_STG005A_PLAN_PASS`
>
> Repository publication state: `IN_MAIN_PR_89_434c9cc_REQUIRES_NEW_EXACT_SHA_RUNTIME_REBIND`

## Observed bounded runtime failure

The Owner-authorized exact-main continuation deployed
`6753855497b8c47be99a8d88ae9d9961653addb0` to isolated Staging through the
reviewed release rebind, formal preflight, serial V10-to-V10 build/start, fresh
readiness, and bounded blocked-state recovery sequence.  Staging remained
loopback-only with printing disabled; Flyway remained V10 with no V11+ or
failed row; the scoped topology, menu, table, order, and request counts were
zero before and after recovery.  The permitted Production continuity metadata
and canonical health stayed unchanged.

The following fresh, password-free `bootstrap-plan` created and cleaned its
scoped one-shot container, but its non-web application context did not start:

```text
RequestUserContextService required jakarta.servlet.http.HttpServletRequest
```

The failure occurred before the STG-005A command, credential reader,
transactional bootstrap service, or any synthetic business write.  The
launcher therefore correctly retained its marker and lock record.  No password
was requested, read, stored, or emitted.

## Deterministic root cause

`RequestUserContextService` was a singleton service with a constructor
dependency on `HttpServletRequest`.  That servlet-scoped dependency exists for
HTTP execution but is absent when the reviewed one-shot uses
`spring.main.web-application-type=none`.  `AuthorizationService` is still
constructed in that application context, so Spring failed before the guarded
plan command could validate its no-write contract.

This is neither a Flyway, deployment, health-probe, database, proxy, nor
credential defect.  It is a repository dependency-injection defect exposed by
the approved non-web runtime shape.

## Bounded repair

The repair changes request-context injection from a mandatory servlet request
to `ObjectProvider<HttpServletRequest>`.

- HTTP requests retain the existing current-request lookup, header fallback,
  role lookup, and authorization behavior.
- A non-web process can construct the application context without inventing a
  request or identity.
- If non-web code attempts request authorization, it fails closed with
  `UnauthorizedException` before any user or role lookup.
- No migration, endpoint, authorization grant, feature flag, deployment tool,
  runtime environment value, credential path, Store-specific behavior, or
  Production behavior changes.

## Verification and next runtime boundary

Focused authorization, STG-005 bootstrap safety, full backend regression, and
the adjacent AL-003S shell regressions passed; backend compilation also passed.
Normal repository publication gates and independent review apply; regardless
of publication state, this runtime-sensitive repair requires a new exact-SHA
runtime rebind before any retry.

Publication does not deploy the correction.  Because this is a backend
runtime-sensitive change, its merge creates a new exact candidate.  Do not
reuse the `6753855...` release, preflight, readiness, recovery, or failed-plan
artifacts.  The next permitted runtime action after publication is a new Owner
approval for fresh Git Ground Truth, exact release/private-env binding, formal
preflight, Staging-only V10-to-V10 redeploy, fresh readiness, and a newly
bounded recovery of the records created by this failed plan.  No password,
STG-005A execute/replay, STG-005B, login, clone, or Production mutation is
authorized by this repair.
