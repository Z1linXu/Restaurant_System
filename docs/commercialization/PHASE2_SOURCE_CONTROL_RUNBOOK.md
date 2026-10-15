# Commercial source control and local verification

Execution authority is [CURRENT_STATE](../governance/CURRENT_STATE.yml). This runbook
does not authorize runtime mutation or merge into main.

The integration branch `codex/commercialization-phase1-3` starts at fresh main
`06bac51ddc0a829a9aa1106f07ef886572efbb65`. The protected Owner workspace is separate.
Local immutable source references are `refs/commercialization/baseline/backend`,
`frontend`, and `main`; their exact objects are in [the manifest](phase2-baseline.json).
Remote reviewers can also resolve the full commits without those local references.

1. Run `python3 scripts/verify-commercial-baseline.py` from this worktree. It reads
   Git and migration files only; it never reads environment secrets or a database.
2. For a clean historical build, use `git archive <backend source SHA> backend deployment` and
   `git archive <frontend source SHA> frontend` into separate empty temporary directories.
   Use Java 17/Maven for `mvn clean verify`; use `npm ci`, `npm test`, `npm run build`
   with the committed lockfile. Optional PostgreSQL suites require a dedicated
   synthetic loopback PostgreSQL 16 database, never an existing shared environment.
3. Create task branches from the reviewed Commercial dependency commit. Separate
   worktrees isolate concurrent modules. Root alone owns Flyway, Order and Profile
   integration. Review the complete integration after cherry-picking dependencies.
4. Commit only known paths. Use stacked Draft PRs for the local Commercial line;
   attach each PR to the chat. No main merge, Staging/Production deployment,
   physical printing, activation or real data write is authorized here.

Production hotfixes originate from their exact release sources, receive a small
reviewed PR, and are ported into shared Core with both compatibility fixtures and
the cross-tenant matrix. Commercial changes return to the existing restaurant only
through a separately authorized exact-artifact release, backup/rollback and acceptance.
Copying files from two versions is never evidence of a Combined Production baseline.

Rollback of this local development means reverting the new commits or stopping the
Commercial line. Preserve Owner changes and source refs. Additive schemas remain;
security fixes must not be reverted in a way that reopens private data access.
