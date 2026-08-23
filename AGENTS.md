# Repository Agent Constitution

## 1. Purpose

This repository is an active restaurant-system productization and pilot
repository. It is no longer governed as the original single-store MVP.

The operating priorities are:

1. runtime and data safety;
2. business-rule correctness;
3. Store and Organization isolation;
4. reliable ordering and printing;
5. controlled productization through Owner-authorized phases.

Keep solutions focused and maintainable for a solo-developer project. Do not
turn governance or implementation into a framework without demonstrated need.

## 2. Owner Communication

- Owner-facing communication defaults to Chinese.
- Questions, summaries, failure reports, acceptance results and Owner Gates
  must be written in Chinese unless the Owner requests another language.
- Preserve canonical technical identifiers, including filenames, APIs,
  class/function names, table/field names, Git SHAs, branches, environment
  variables, status/error codes, commands and log excerpts.
- Do not mechanically translate source-code, API or schema identifiers.

## 3. Default Required Reading

At the start of substantial work, read only:

1. `AGENTS.md`;
2. `docs/governance/AUTHORITY.md`;
3. `docs/governance/CURRENT_STATE.yml`.

Then use `AUTHORITY.md` to select only the task-specific authority required.
Historical evidence is never part of the default reading bundle.

## 4. Ground Truth

Before substantial work:

```bash
git fetch origin --prune
git status --short --branch
git worktree list
git rev-parse origin/main
```

Establish and report:

- exact fresh `origin/main` SHA;
- current branch/worktree and divergence;
- staged, unstaged and untracked state;
- current Phase, Package, Gate and Stop Marker;
- the task-specific contract or runbook;
- whether the requested mutation is authorized.

Fresh `origin/main` beats chat history, old prompts and stale local branches.
Git proves repository content; it does not prove what is deployed. Runtime
claims require evidence from the exact environment they describe.

If fresh authority materially differs from the request's assumptions, explain
the difference and adapt to the merged authority. Fail closed if that changes
the requested safety boundary.

## 5. Existing Work Protection

- Never reset, clean, unstage, overwrite, discard, commit or merge unknown
  user work.
- Never checkout over a dirty worktree.
- Treat staged, unstaged and untracked content as protected until provenance
  and ownership are established.
- Prefer a fresh worktree based exactly on `origin/main` for isolated work.
- Do not use a stale or dirty Owner workspace as current authority or a release
  input.
- Destructive Git operations require explicit Owner authorization and exact
  target verification.

## 6. Authority and Scope

- Follow `docs/governance/AUTHORITY.md` for precedence, routing and workflow.
- Follow `docs/governance/CURRENT_STATE.yml` for the single current Phase,
  Package, Gate, next action and authorization boundary.
- `CURRENT_STATE.yml` does not override executable code, migrations or verified
  target-environment evidence.
- Historical evidence proves a past event only. It cannot authorize current or
  future mutation.
- A plan, branch, draft PR or merged capability does not by itself authorize
  deployment or runtime mutation.
- Never expand a bounded package because adjacent work appears convenient.

## 7. Agile Loop

Inside an Owner-authorized Phase and Package, use this bounded loop:

```text
Ground Truth
-> bounded task
-> implementation
-> focused tests
-> required review
-> PR
-> merge when authorized
-> fresh fetch
-> authority refresh
-> authorized exact-SHA Staging
-> acceptance
-> concise evidence
-> governance sync
-> next bounded task inside the same authorized Package
```

Ordinary technical failures inside the approved scope enter a bounded repair
loop automatically:

```text
root cause -> minimal repair -> focused validation -> re-review when required
```

Stop when the repair would expand scope, weaken a safety boundary, require an
unapproved real-data action, or cross a TRUE OWNER GATE.

Technical acceptance may close the current technical gate. It never opens the
next Phase or expands the Package without Owner authorization.

## 8. TRUE OWNER GATES

Stop and obtain an explicit Owner decision before:

- opening the next Phase;
- expanding the current Package;
- making a material product/business-rule change outside current authority;
- weakening an authentication, authorization or environment boundary;
- mutating real credentials, Master data or operational data without existing
  explicit authority;
- activating a real Store;
- binding a real Printer or Pad unless the exact action is already authorized;
- destructive or irreversible database work;
- choosing between materially different safety/product alternatives;
- any Production release, deployment, restart, Flyway or data mutation.

When authority is ambiguous, use the safer interpretation and fail closed.

## 9. Business and System Invariants

- Backend Store/Organization authorization is authoritative. Frontend URL or
  Store context is never authorization.
- Retry must preserve one stable idempotency identity.
- Offline drafts and outbox state must not be casually cleared or duplicated.
- A local queued record is not a server-confirmed order.
- Submitted-order updates follow the current add-only/current API contract.
- Print failure must not roll back a committed order.
- `PAD_DIRECT` claim/lease and duplicate-print protections must remain intact.
- Ambiguous physical-print failure must not trigger blind automatic reprint.
- Historical orders and receipts must remain snapshot-safe.
- Environment credentials, configuration, data and device endpoints must stay
  isolated.
- Store Profile or Master Menu materialization must produce independent
  Store-owned state; no shared mutable menu graph is allowed.

Read `SYSTEM_DOCUMENTATION.md`, `doc/API.md` and the routed technical contract
before changing one of these boundaries.

## Product Simplicity / 产品简化原则

本项目是餐厅实际运营软件。默认产品原则：

**内部可以复杂，用户操作必须简单。**

Agent 不得因为后台架构、验证、readiness、provisioning、deployment 或测试
机制复杂，而把这些复杂度直接暴露给 Owner、Manager、Frontdesk 或 Kitchen
用户。

### 核心规则

1. 优先减少用户步骤。如果系统可以安全自动完成 A → B → C → D，不要要求
   用户逐步点击、检查和确认 B、C、D。
2. 内部工程状态默认不是产品功能。readiness、provisioning stages、
   fingerprints、structural smoke、synthetic fixtures、internal validation、
   replay/idempotency state、device proof、migration/deployment state 以及
   Phase/Part 名称，除非 Owner 明确要求，应保留在 backend、diagnostics、
   support 或 automated acceptance 中。
3. Create、Save、Submit 应尽量形成完整业务动作。后台 provisioning、
   validation、defaults、mapping 和 isolation checks 应由系统内部完成。
4. 配置缺失与业务实体生命周期必须分离。Printer 未配置或 Offline、Pad
   未绑定以及其他 optional hardware 缺失，不自动等于 Store Not Live，也不
   应无理由阻塞整个业务流程。
5. Management Access 与 Runtime Capability 必须分离。配置页面不能依赖
   “已经配置完成”才能进入，避免形成无法进入配置页面因而永远无法配置的
   循环依赖。
6. 不建立重复 Source of Truth。保持 Menu Management = WHAT CAN BE
   ORDERED、Printing Assignment = WHERE TO PRINT、Printing Display Rule =
   HOW TO DISPLAY，不要求 Owner 在多个模块重复维护同一业务事实。
7. 新增 Owner-facing 状态、按钮、步骤或页面前，必须确认用户是否真正需要
   该概念、系统能否自动完成、它是产品需求还是内部实现细节，以及是否把
   automated acceptance/debug tooling 暴露成产品 UI。
8. 优先复用后台能力，不把每个 backend service/check 一一映射成 button、
   card、status 或 Owner workflow step。
9. Frontdesk、POS、KDS、Ready 和 Printing setup 尤其应优先少点击、大按钮、
   明确状态、快速操作和最少认知负担。
10. 不得为了架构完整性主动扩大产品流程。新增 lifecycle state、Owner Gate、
    人工 provisioning step、配置页面、重复设置或用户 validation 操作前，
    必须证明这些复杂度是业务真正需要的；否则选择更简单的方案。

### 实施前 Product Simplicity Check

任何新增或修改 Owner-facing workflow 的任务，在实施前快速检查：

- 用户操作步骤是否增加；
- 是否可以安全自动化；
- 是否暴露内部工程概念；
- 是否产生新的重复配置；
- 是否存在循环依赖；
- 是否把 optional dependency 变成全局 blocker。

如果产品复杂度增加而当前 Owner requirement 没有明确要求，不要自行增加该
复杂度。先简化方案；如果确实必须增加，则作为产品决策向 Owner 说明。

在不破坏 business correctness、data integrity、authorization/isolation、
transaction/idempotency safety、auditability 和 Production safety 的前提下，
优先：

**更少的用户步骤 > 更多的显式工程控制。复杂度应留在系统内部。**

## 10. Database and Migrations

- Staging and Production schema evolution uses reviewed Flyway migrations.
- Never edit an applied migration.
- Use additive forward repair unless an explicitly approved recovery plan says
  otherwise.
- Do not run ad-hoc SQL against a shared environment unless explicitly
  authorized for the exact statement and target.
- Destructive database actions are TRUE OWNER GATES.
- Migration, backup and rollback evidence are environment-specific; do not
  generalize a Staging result to Production.

## 11. API and Implementation

- Never assume a missing API.
- Inspect the current controller, service, DTO, tests and `doc/API.md` before
  changing a contract.
- Preserve existing naming and compatibility unless authority explicitly
  permits a change.
- Keep module boundaries clear and changes minimal.
- Use transaction management for multi-write business flows, including order
  submission, kitchen completion, prep execution, inventory restock and Store
  provisioning/materialization.
- Do not implement Product, Phase or runtime work that is not authorized by
  `CURRENT_STATE.yml` and the Owner request.

## 12. Testing and Review

Use focused tests first, then the repository checks proportionate to risk.
Document what ran and distinguish PASS, FAIL, BLOCKED and NOT RUN.

Agent 6 review is mandatory for material changes involving:

- application code;
- database schema or Flyway;
- authentication/security;
- deployment or runtime tooling;
- printing;
- offline/idempotency behavior;
- architecture-sensitive boundaries.

Pure documentation and trivial mechanical changes may use a documented
risk-based exception unless the Owner explicitly requires Agent 6.

## 13. PR and Merge

- Material work uses a dedicated branch/worktree and a PR.
- Inspect staged and unstaged diffs; stage only confirmed paths.
- Required tests, checks and review must pass before merge.
- Auto-merge is allowed only when current Owner/governance authority explicitly
  allows it and no TRUE OWNER GATE is open.
- After merge, fetch fresh `origin/main`, verify ancestry and reread the three
  default authority files plus authority files actually changed by the merge.
- A GitHub Merged badge into a non-main base is not proof of entry into `main`.

## 14. Staging

Standing Staging authorization exists only inside the current explicitly
Owner-authorized Phase/Package and reviewed safety boundary.

For runtime-sensitive changes inside that authorized Phase/Package, the
default delivery loop continues after merge through fresh exact-SHA Staging
deployment and applicable automated acceptance without a second per-deploy
Owner approval. This includes user-testable application, UI, backend,
printing, API and runtime-contract changes. Ordinary Staging technical failures
remain inside the bounded repair loop and may be fixed, reviewed, merged,
redeployed and re-tested without repeated Owner approval while scope and safety
boundaries remain unchanged.

Documentation-only, governance-only, archive/cleanup, comment/formatting and
other non-runtime mechanical changes do not trigger a Staging deployment.

When authorized, Staging work must use:

- an exact full SHA;
- the Staging runbook and isolated environment configuration;
- preflight and migration safety gates;
- focused automated acceptance;
- concise evidence tied to the exact SHA and environment.

Standing authorization never opens a new Package or Phase and never authorizes
real Store activation, new real credentials or Master-data mutation, or real
Printer/Pad binding. Do not copy Production credentials/data/endpoints into
Staging. Staging authorization never implies Production authorization.

## Docker Build Cache / Disk Hygiene

Frequent exact-SHA builds, Staging deploys and repair/rebuild loops make
BuildKit cache a managed deployment resource. Cache should retain recent useful
layers, but it must not grow without age, size and disk-pressure limits.

### Safety boundary

- Never run `docker system prune`, `docker volume prune`, unrestricted
  `docker image prune`, unrestricted `docker builder prune -a`, manually delete
  `/var/lib/docker` or containerd snapshots, directly `rm -rf` release
  directories, or delete database volumes without explicit Owner authorization
  and destructive-safety review.
- Build cache, images, releases, logs, containers and volumes are separate
  retention domains. Permission to clean one does not authorize cleaning the
  others.
- Every cleanup must protect the current Staging and Production exact-SHA
  runtimes, active containers and images, rollback/recovery artifacts, database
  containers/volumes, private configuration and authority-required evidence.
- If cache activity, rollback provenance, evidence references, Production
  impact or volume/data safety cannot be proven, fail closed.

### Deploy inspection and thresholds

For each runtime-sensitive Staging or Production deploy, inspect before and
after at least `df -h` and `docker system df`. When needed, also inspect
`docker builder du`, running containers/images, current Staging and Production
SHAs, and protected rollback/recovery artifacts. Inspection is routine;
cleanup is conditional and must not run automatically on every deploy.

- Healthy disk target: below 70% used.
- Warning: 75% or more used.
- Critical: 90% or more used.
- BuildKit soft target: at most 8 GB when practical.
- From 8–12 GB, observe and explain growth.
- Above 12 GB, perform a cache-hygiene review.
- If disk is at least 75% used and old reclaimable cache exists, prioritize
  reviewed old-cache cleanup.

The target is not zero cache. Stop after the conservative layer once disk is
below 70% and cache is reasonable; if disk remains at least 75% or cache remains
above 12 GB, perform a second review rather than escalating automatically.

### Default bounded retention

- Prefer only build cache older than seven days that is clearly
  reclaimable/dangling. The default reviewed command shape is
  `docker builder prune --filter "until=168h"`; do not add `-a`.
- Before execution, prove Staging and Production containers are healthy,
  identify active images, confirm database volumes are outside scope, and use
  the repository's reviewed hygiene tooling when it imposes stricter
  eligibility or protected-set checks.
- Do not remove an active image merely because related BuildKit records appear
  reclaimable.
- Old Staging releases must use reviewed bounded rotation, retaining current
  Staging, previous verified Staging, shared current Production artifacts,
  explicit rollback/recovery artifacts and authority/evidence references.
  Never directly delete release directories.
- Inactive historical images may be removed only through a reviewed retention
  path after proving that no running container, current exact SHA or protected
  rollback/recovery artifact uses them.
- Maintain bounded Docker/container, Nginx and journald retention without
  deleting logs or evidence needed for an active incident or acceptance run.

### Exact-SHA reuse

Avoid rebuilding the same exact SHA when its image already exists, provenance
is verifiable and the deployment contract permits reuse. Validation retries,
acceptance retries, evidence sync and docs-only governance commits with no
executable/runtime change should reuse the verified application image and
must not trigger an unnecessary application Docker build.

### Post-cleanup proof and reporting

After cleanup, verify `df -h`, `docker system df`, Staging and Production
containers Up, PostgreSQL healthy, Staging reachable, and no unapproved
Production mutation or restart. Record before/after disk used percentage, free
GB, total/reclaimable Build Cache and reclaimed GB.

If a runtime-sensitive deploy observes disk at least 75%, Build Cache above
8 GB, or performs cleanup, the final report must include a `Docker / Disk
Hygiene` section with:

- Disk Before and After;
- Build Cache Before and After;
- Cleanup Performed and Reclaimed Space;
- Protected Runtime Artifacts;
- Staging Health;
- Production Mutation.

Routine read-only inspection, reviewed cleanup of clearly reclaimable cache
older than seven days, reviewed Staging release rotation and existing-policy
log rotation may proceed inside current authorization. Unrestricted builder
prune, system prune, volume/database cleanup, active image/release deletion,
Production mutation or any destructive cleanup whose safety cannot be proven
is a TRUE OWNER GATE.

Server-side Docker builds remain supported. If build frequency continues to
grow, prefer proposing CI-built immutable images in a registry followed by
server pull/deploy. That is a future deployment-architecture decision and must
not be introduced without Owner authorization.

## 15. Production

Production is always a TRUE OWNER GATE.

A Production request must prepare one immutable release batch containing:

- exact SHA and artifacts;
- migration scope;
- backup plan/evidence;
- rollback plan;
- Staging acceptance evidence;
- deployment/restart actions;
- post-deploy smoke and observation plan.

The Owner may approve that exact batch once. Any material SHA, artifact,
migration, environment-assumption or procedure change invalidates approval.

## 16. Documentation and Evidence

- Update only authority whose facts changed.
- Update `SYSTEM_DOCUMENTATION.md` when current technical behavior or its
  governance routing changes; do not append status history mechanically.
- Current execution state belongs only in `CURRENT_STATE.yml`.
- Phase definitions belong only in `ROADMAP.md`.
- Active work belongs only in `BACKLOG.md`.
- Evidence records proof, not future authorization.
- Archive historical context outside default authority paths.
- Prefer concise structured evidence with exact identifiers over raw logs.
- Preserve full logs only for meaningful acceptance, incident, security,
  migration or forensic value.

## 17. Token and Tool Efficiency

- Do not reread unchanged large authority without a concrete reason.
- Prefer repository search, headings, targeted sections, Git history and blob
  comparison.
- After merge, refresh changed authority plus the three default files only.
- Do not repeatedly summarize unchanged Ground Truth.
- Do not emit progress-only narration without a new finding, decision, failure,
  blocker or completed milestone.
- Historical evidence is not default reading.
- Do not restate whole Planbooks in prompts or reports.

For long-running asynchronous work:

- Empty `write_stdin` polls MUST use `yield_time_ms >= 180000`; prefer `300000`
  when intermediate output is unnecessary.
- `functions.wait` MUST use `yield_time_ms >= 180000`.
- `functions.exec` MUST set its outer `@exec yield_time_ms` at least 30000 ms
  longer than the longest nested tool wait.
- Do not apply the long wait to non-empty `write_stdin` calls that send input.
- These tools return early when the process or cell completes.
- Do not wake the model merely to report that work is still running.

## 18. Completion Report

Report in Chinese:

- what changed;
- how it was validated;
- exact branch/PR/merge/runtime SHAs where applicable;
- assumptions and unresolved risks;
- whether Staging or Production was mutated;
- the resulting Gate and Stop Marker.

Never continue into a new Phase or Production merely because the current task
completed successfully.

## 19. Execution Time Breakdown

After every substantive engineering task, the final Owner-facing report must
include an `Execution Time Breakdown` with these stages:

- Investigation / Planning
- Implementation
- Tests
- Agent 6 / PR / Merge
- Staging Deploy
- Staging Acceptance
- Evidence / Governance
- Total

Prefer actual wall-clock time. If a stage was not executed, report `N/A` or
`Not required`; if time can only be estimated, mark it `Estimated`. `Total`
must cover the actual interval from the start of the task through the final
Stop. Add a brief reason for materially long stages, such as full regression,
Maven/npm build, Agent 6 re-review, Staging image build, Flyway,
readiness wait, acceptance replay, repair or redeploy. Include retries in the
stage total and note their count and reason.

Do not add frequent polling or progress wake-ups solely for timing, and do not
weaken the existing long-running asynchronous-work or token-efficiency rules.
Documentation-only or trivial mechanical tasks may use a simplified report,
but must still include Implementation, Validation and Total. This reporting
rule changes only the final report; it does not change testing, Agent 6, PR,
Staging or Owner Gate requirements.

Recommended format:

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | ... | ... |
| Implementation | ... | ... |
| Tests | ... | ... |
| Agent 6 / PR / Merge | ... | ... |
| Staging Deploy | ... | ... |
| Staging Acceptance | ... | ... |
| Evidence / Governance | ... | ... |
| Total | ... | ... |
