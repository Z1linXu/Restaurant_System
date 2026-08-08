# AL-005B Device and Pad Provisioning Module Plan

> Status: `DRAFT_PR_WAITING_FOR_OWNER_REVIEW` in Draft PR #68
>
> Package: `AL-005B_DEVICE_PAD_PROVISIONING_MODULE`
>
> Git classification: `DRAFT_PR` based on latest `main`
>
> Base: `origin/main@65e3d3ced2b5b05eb36d56ce67e475768ad19dff` (PR #67 IN_MAIN)
>
> Draft PR: #68 (`base=main`)
>
> Runtime access: not performed

## 1. Purpose

AL-005B defines the reusable, Store-neutral boundary between a reviewed Store
Profile and the existing Android Pad pairing and PAD_DIRECT execution
capabilities. It does not create a new device registry, pairing flow, print
worker, or module assignment model.

This package is an authority audit and implementation plan only. It adds no
writer, public endpoint, migration, device, token, pairing, printer, assignment,
Worker behavior, runtime action, or activation state.

The architectural classification is:

| Concern | Classification | Boundary |
|---|---|---|
| validation, fingerprinting, readiness planning | generic shared capability | Store-neutral code only |
| required Pad count/platform/executor policy | versioned Store Profile rule | reviewed non-secret desired state |
| device identity, token, pairing, auto-print preference | runtime-only configuration | created and retained on site |
| current Worker/poll/error state | runtime-only observation | Android Control Panel and approved evidence |
| activation proof | one-time operational evidence | sanitized evidence reference only |

## 2. Existing executable authorities

| Capability | Current authority | Reuse decision |
|---|---|---|
| device registration and token hashing | `StoreDeviceServiceImpl.registerDevice()` | preserve; a future module must not issue credentials implicitly |
| device authentication and Store binding | `StoreDeviceServiceImpl.authenticateDevice()` and `PadPrintJobServiceImpl.ensureDeviceStore()` | preserve Store-scoped enforcement |
| heartbeat and `last_seen_at` | `StoreDeviceServiceImpl.heartbeat()` | reuse as connectivity evidence only, not Worker-health proof |
| device list/rename/disable/revoke | `StoreDeviceController` and `StoreDeviceServiceImpl` | operational authority; not a provisioning API |
| pending/claim/start/payload/complete/fail/release | `PadPrintingController`, `PadPrintJobServiceImpl`, and Store-scoped repositories | preserve current job-state and duplicate-print safeguards |
| assigned printer routing | `PadPrintJobServiceImpl.requireAssignedPayloadPrinter()` | preserve; physical endpoint remains runtime-only |
| Android pairing persistence | `PadDeviceBridge.saveDeviceCredentials()` | preserve current behavior pending the credential-storage security gate |
| Worker state and recovery visibility | `MainActivity.buildPadDirectWorkerStatusJson()` and `RestaurantPadWorker` logs | operational evidence authority; no backend inference from heartbeat alone |
| web health surface | `PrintWorkerHealthBanner` and Android bridge methods | operator visibility only; not durable provisioning evidence by itself |

There is no current per-device module assignment. Every active authenticated Pad
for a Store can poll and claim that Store's eligible PAD_DIRECT jobs, regardless
of module. AL-005B must not invent device-module affinity or infer it from a
printer name, Pad name, endpoint, or physical location.

## 3. Profile-safe desired state

A future versioned profile section may declare only non-secret readiness intent:

```text
DevicePadProvisioningConfiguration
- contractVersion
- policy: MANUAL_AFTER_CREATION
- executor: PAD_DIRECT_ANDROID
- minimumReadyPadCount
- allowedPlatforms[]
- requiredPrintingModuleCodes[]
- activationRequirements[]
```

Required validator rules:

- exact versioned input and deterministic fingerprint;
- policy is exactly `MANUAL_AFTER_CREATION` for the first version;
- count is positive and bounded by a reviewed generic limit;
- platforms and executor values come from reviewed allow-lists;
- requested printing modules are a subset of the AL-005 Printing Profile's
  enabled modules;
- no physical identity, database ID, token, secret, endpoint, mutable status,
  timestamp, error text, app session, or Print Job identity is accepted or
  fingerprinted;
- no profile can pair, start/stop a Worker, enable auto print, activate printing,
  or revoke a device;
- unknown values fail validation rather than falling back to a permissive mode.

The read-only planner may report sanitized diagnostics such as required Pad
count, active-device count, recently-seen count, worker-evidence count,
unsupported platform count, printing-policy mismatch, and missing acceptance
evidence. It must not return device tokens, token suffixes, endpoint values,
raw Worker errors, Print Job IDs, payloads, or customer/order content.

## 4. Runtime-only state

The following never belongs in Git, a Store Profile, profile fingerprint,
replay response, or durable provisioning-request evidence:

- `store_devices.id`, raw device token, token hash, pairing token, or Android
  preference contents;
- physical Pad identity, serial number, device label, or exact on-site location;
- pairing/registration timestamp and Store binding row;
- mutable `status`, `is_active`, `last_seen_at`, app version, and platform report;
- `userAutoPrintEnabled`, Worker state, foreground state, last poll, next poll,
  watchdog, recovery backoff, stop reason, or last error;
- printer endpoint, current job/attempt/token, rendered content, ESC/POS payload,
  or customer/order information.

An approved operational evidence record may retain only a sanitized device
label, Store identity, app-version summary, active/paired booleans, auto-print
boolean, Worker-state/result code, age buckets rather than raw timestamps where
appropriate, and an immutable evidence reference. Token material is never
evidence.

## 5. Readiness semantics

Backend `last_seen_at` is initialized when a device row is registered and is
later touched by authenticated device calls. A recent value alone therefore
proves neither successful local credential persistence nor a later authenticated
request. It also does not prove that the Android Worker is enabled, scheduled,
foregrounded, polling, free of a high-risk stop, or able to reach a printer. A
device is activation-ready only when separately approved evidence proves all
required conditions:

1. device is active and bound to the intended Store;
2. exact installed APK/build provenance satisfies the reviewed acceptance
   policy; the client-reported heartbeat value is supporting evidence only;
3. local auto-print preference is explicitly enabled;
4. Worker is running or waiting, not recovering indefinitely or error-stopped;
5. last poll is within the approved freshness threshold;
6. watchdog/scheduling state is healthy;
7. AL-005 printing configuration is complete and still disabled until physical
   acceptance authorizes activation;
8. owner-approved field checks prove each required logical printing route.

The minimum app version, trusted build-evidence source, last-seen freshness,
last-poll freshness, and evidence validity periods are not selected by this
package. They require an explicit Owner decision before a validator can treat
them as activation criteria.

## 6. Fixed Chinatown profile input

The AL-001 Owner decision remains authoritative:

- four Android Pads are independently paired to Chinatown;
- any active Chinatown Pad may claim any eligible Chinatown PAD_DIRECT module;
- no per-device module assignment is introduced;
- device readiness depends on AL-005's fixed Chinatown printing policy, which
  enables exactly `GRAB` and `FRONTDESK_RECEIPT` and excludes `HOT_KITCHEN`;
- every device identity, token, pairing action, app installation, auto-print
  setting, and Worker-health observation remains on-site runtime state;
- pairing does not activate the Store or prove physical-print acceptance.

The generic shared implementation may consume the profile's reviewed count and
policy but must not branch on Store ID, Store name, Chinatown, or ordinal Store
number.

## 7. Current gaps and Dependency Repair Gates

Executable provisioning remains blocked by the following verified gaps:

1. registration is create-only and has no parent-workflow idempotency contract;
2. Android pairing currently enables auto print and starts the Worker, so it is
   a runtime side effect rather than a safe profile write;
3. raw device credentials are stored in regular Android `SharedPreferences`;
4. repository schema does not visibly enforce all Store/Organization integrity
   relationships needed by a generic writer;
5. device management has no provisioning-request/audit contract;
6. free-text platform/device labels are not a stable profile identity;
7. Store Organization snapshot consistency needs an explicit validation rule;
8. backend `last_seen_at` can represent registration or authenticated activity
   and cannot substitute for proof of successful local pairing or Worker health;
9. current pairing may report `unknown` or client-controlled app-version text;
   the native installed-version reader is not yet the trusted registration/
   heartbeat authority;
10. test coverage does not yet prove parent replay, concurrent registration,
    credential redaction, or activation evidence expiry;
11. the AL-005P1 enabled-module contract is now in `main` via PR #67 and must
    remain stable while the AL-005B planner validates module dependencies.
    AL-005's inactive P3 writer does not wait for AL-005B; AL-005P4 runtime
    binding and AL-006 activation do.

These gaps are not repaired in this documentation package. Any prerequisite
repair follows the Dependency Repair Gate and receives its own reviewed PR.

## 8. Staged delivery

| Package | Allowed output | Explicit non-goal |
|---|---|---|
| `AL-005B1_DEVICE_CONTRACT_PLANNER` | immutable contract, validator, fingerprint, read-only planner, focused tests | no pairing, token, Worker or device write |
| `AL-005B2_DEVICE_PREREQUISITE_REPAIRS` | smallest separately reviewed integrity/security/idempotency repairs | no automatic Store activation |
| `AL-005B3_DEVICE_OPERATIONAL_ACCEPTANCE` | Owner-approved pairing/install/Worker/field evidence runbook and sanitized evidence | no credential retention, no Production action without runtime approval |

No public provisioning endpoint or migration is authorized by this plan. A
future writer must be subordinate to the parent Generic Store Provisioning
Engine and reuse current device services rather than exposing a second pairing
or PAD_DIRECT path.

## 9. Test contract for future implementation

### Contract and planner

- deterministic fingerprint across equivalent safe profile input;
- rejection of duplicate/unknown platforms, executors, and module codes;
- rejection of endpoint, device ID, token, mutable Worker state, or raw error
  fields in profile input and summaries;
- required modules must be a subset of the AL-005 enabled-module policy;
- no Store ID/name special case in shared code;
- safe plan counts and diagnostics contain no device or order secrets.

### Store and authorization isolation

- Bearer-authenticated administrators cannot list, rename, disable, or revoke a
  device outside their authorized Store;
- device credentials cannot authorize those human-admin management endpoints;
- a device cannot heartbeat, poll, claim, start, read payload, complete, fail,
  or release outside its bound Store/current claim;
- device/target Store Organization snapshot mismatch fails closed;
- dirty cross-Store printer references cannot redirect a Pad job or health write;
- a missing device or printer does not leak whether another Store owns it.

### Runtime acceptance

- exactly the approved synthetic/field devices are paired through formal UI;
- each required Pad has separately verified installed APK/build provenance and
  the correct Store; self-reported app version is not sufficient on its own;
- Worker poll freshness and error-stopped/recovering behavior are observed;
- one Pad loss does not create duplicate claims and another active Pad can
  consume the Store queue under existing atomic claim semantics;
- all tests avoid real customer/order content and never retain tokens or raw
  print payloads.

## 10. Acceptance criteria for this plan package

- current device, pairing, heartbeat, queue, and Worker authorities are cited;
- Store Profile and runtime-only boundaries are explicit;
- Chinatown's four-Pad Store-wide queue rule is frozen without new affinity;
- `last_seen_at` is not presented as Worker-health proof;
- current security, idempotency, integrity, and evidence gaps remain visible;
- no endpoint, migration, device, token, pairing, Worker, printer, or runtime
  behavior is added;
- governance, system, and API documentation agree on the package boundary.

## 11. Stop state

`DRAFT_PR_WAITING_FOR_OWNER_REVIEW`

The next executable step is not authorized. Owner review must first resolve the
identified security/integrity/evidence gates; no pairing, credential creation,
Worker mutation, or runtime operation is implied.
