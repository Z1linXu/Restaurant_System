# Phase A7 Backend DI Runtime Repair Evidence

Date: 2026-08-14

## Authority

- Owner-approved continuous package:
  `PHASE_A7_FRONTEND_MODULE_GATING`
- Repair classification:
  bounded repository/runtime dependency repair inside the A7 Staging validation
  loop
- Initial A7 PR:
  [PR #154](https://github.com/Z1linXu/Restaurant_System/pull/154)
- Initial A7 merge SHA:
  `c725e09db1f9211188755d265065d94883e3186d`

This repair does not add a Flyway migration, does not change business logic,
does not mutate Production, and does not start A8, Phase B, Phase C,
Chinatown, Sainte-Catherine or Production deployment.

## Runtime finding

Exact-SHA Staging deploy of initial A7 merge
`c725e09db1f9211188755d265065d94883e3186d` reached Docker build/start, but
loopback health failed because the backend repeatedly returned `502` through
Nginx while the Spring application failed during context startup.

Sanitized Staging log root cause:

```text
Error creating bean with name 'storeModuleAccessEvaluator'
Failed to instantiate StoreModuleAccessEvaluator: No default constructor found
java.lang.NoSuchMethodException: StoreModuleAccessEvaluator.<init>()
```

Flyway validated successfully at V16 before the bean failure:

```text
Successfully validated 16 migrations
Current version of schema "public": 16
Schema "public" is up to date. No migration necessary.
```

## Root cause

`StoreModuleAccessEvaluator` had two constructors:

- the runtime constructor with `StoreModuleRepository` and
  `StoreModuleCapabilityProvider`; and
- a package-private test constructor accepting `ModuleContractLoader`.

Because no constructor was explicitly marked for Spring injection, the cloud
runtime attempted default instantiation and failed. Unit tests constructed the
evaluator directly and therefore did not cover Spring's runtime constructor
selection.

## Repair

- Marked the runtime constructor with `@Autowired`.
- Added a focused Spring `AnnotationConfigApplicationContext` regression test
  proving the evaluator can be created by the container when both runtime
  collaborators are available.

No Store module semantics, authorization order, API response contract,
database schema, frontend behavior or runtime configuration changed.

## Validation

Focused backend tests:

```text
mvn -q -Dtest=StoreModuleAccessEvaluatorTest,StoreModuleAccessEvaluatorSpringInstantiationTest test
PASS
```

Full backend regression:

```text
mvn -q test
PASS
```

Expected post-merge action:

```text
fresh fetch
→ exact-SHA Staging release/env rebind
→ retry Staging deploy/health/Flyway/module acceptance
→ stop before A8
```

## Production / runtime boundary

- Production: no mutation.
- Staging: initial A7 exact-SHA deploy was attempted under A7 authority and
  exposed the backend DI startup defect. Retry is limited to the repaired
  exact SHA after merge.
- Flyway: remains V16; no migration expected.
- Hardware: A8 remains pending.
