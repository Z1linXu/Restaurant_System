# Git, PR, Deploy, And Rollback Workflow

This file is the day-to-day checklist for updating Restaurant_System safely.

Use it when you are about to:

- split local changes into PRs
- open a GitHub PR
- deploy merged code to the Ubuntu server
- update the running app
- back up or roll back production

## 1. Mental Model

There are three separate places:

```text
Your Mac working tree
  Code you are editing now. Uncommitted changes live here.

GitHub repository
  Branches and PRs live here. Merged PRs become main.

Tencent Cloud server
  The running app lives here. The server only changes after git pull + deploy/update.
```

Important:

- A branch is a code line.
- A commit is a saved snapshot.
- A PR is a review/merge request from one branch into another.
- Docker deployment is how the server turns code into running containers.
- Database backups protect real restaurant data.

## 2. Recommended Branch Strategy

Keep `main` as the stable online branch.

Use one feature branch per PR:

```text
main
  stable online code

codex/auth-platform-foundation
  staff login, Pad binding, device management

codex/noodle-print-grouping
  noodle print display fix

codex/server-docker-deploy
  Ubuntu Docker server deployment
```

Rule:

```text
one topic = one branch = one PR
```

This makes rollback much easier. If one PR causes a production issue, revert
that PR instead of guessing through mixed changes.

## 3. Start New Work

Always start from the latest `main`:

```bash
git switch main
git pull
git switch -c codex/short-feature-name
```

Examples:

```bash
git switch -c codex/server-docker-deploy
git switch -c codex/noodle-print-grouping
```

## 4. Check Local Changes

Before staging anything:

```bash
git status
```

See exact file content changes:

```bash
git diff
```

Never use this when multiple PRs are mixed together:

```bash
git add -A
```

Prefer adding exact files:

```bash
git add path/to/file1 path/to/file2
```

For a file that contains changes from more than one PR, use patch staging:

```bash
git add -p SYSTEM_DOCUMENTATION.md
```

Then select only the hunks for the current PR.

## 5. Commit One PR

Stage only files for this PR:

```bash
git add path/to/current-pr-file
git add another/current-pr-file
```

Confirm staged files:

```bash
git diff --cached --name-only
```

Review staged content:

```bash
git diff --cached
```

Commit:

```bash
git commit -m "type(scope): short description"
```

Examples:

```bash
git commit -m "chore(deploy): add Ubuntu Docker server deployment"
git commit -m "fix(printing): group identical noodle configs on kitchen tickets"
git commit -m "feat(printing): add staff device management"
```

Push:

```bash
git push -u origin your-branch-name
```

Then open a GitHub PR from that branch.

## 6. Current Mixed Worktree Split Flow

Use this when your working tree already has changes for multiple PRs.

Example situation:

```text
codex/auth-platform-foundation has:
  PR A: auth / Pad / device changes
  PR B: noodle print changes
  PR C: server deploy changes
```

First, commit PR A on the current branch:

```bash
git status
git add only-pr-a-files
git diff --cached --name-only
git commit -m "feat(printing): add staff session and device management updates"
git push origin codex/auth-platform-foundation
```

Then create a new branch for PR C while the uncommitted PR C files remain in the
working tree:

```bash
git switch -c codex/server-docker-deploy
git add .gitignore README_SERVER_DEPLOY.md README_GIT_DEPLOY_WORKFLOW.md SYSTEM_DOCUMENTATION.md \
  backend/Dockerfile backend/.dockerignore \
  frontend/Dockerfile frontend/.dockerignore \
  deployment/cloud
git diff --cached --name-only
git commit -m "chore(deploy): add Ubuntu Docker server deployment"
git push -u origin codex/server-docker-deploy
```

If a wrong file is staged:

```bash
git restore --staged path/to/wrong-file
```

## 7. First Production Deploy

After the deploy PR is merged into `main`, SSH into the server:

```bash
ssh ubuntu@YOUR_SERVER_PUBLIC_IP
```

Clone the repo:

```bash
sudo mkdir -p /opt/restaurant-system
sudo chown "$USER":"$USER" /opt/restaurant-system
git clone YOUR_GIT_REPO_URL /opt/restaurant-system
cd /opt/restaurant-system/deployment/cloud
```

Create server env:

```bash
cp .env.example .env
nano .env
```

Generate secrets:

```bash
openssl rand -base64 32
openssl rand -base64 48
```

Run first deploy:

```bash
./deploy.sh
./health-check.sh
```

Open:

```text
https://YOUR_DOMAIN
```

## 8. Normal Production Update

After a PR is merged into `main`, update the server:

```bash
ssh ubuntu@YOUR_SERVER_PUBLIC_IP
cd /opt/restaurant-system
git switch main
git pull
cd deployment/cloud
./backup-db.sh
./update.sh
./health-check.sh
```

Backup before update is strongly recommended because restaurant data matters
more than code convenience.

## 9. Tag A Known Good Deploy

After a successful deploy:

```bash
cd /opt/restaurant-system
git rev-parse --short HEAD
git tag deploy-YYYY-MM-DD-short-note
git push origin deploy-YYYY-MM-DD-short-note
```

Example:

```bash
git tag deploy-2026-07-11-server-deploy
git push origin deploy-2026-07-11-server-deploy
```

## 10. Roll Back Code

If a new code deploy has a serious bug:

```bash
ssh ubuntu@YOUR_SERVER_PUBLIC_IP
cd /opt/restaurant-system
git log --oneline -10
git checkout PREVIOUS_GOOD_COMMIT_OR_TAG
cd deployment/cloud
./update.sh
./health-check.sh
```

To return to normal branch mode later:

```bash
cd /opt/restaurant-system
git switch main
```

If the bad PR was already merged into `main`, open a GitHub revert PR when there
is time. Emergency rollback on the server is for restoring service quickly.

## 11. Back Up Database

Manual backup:

```bash
cd /opt/restaurant-system/deployment/cloud
./backup-db.sh
```

Backup files are under:

```text
deployment/cloud/data/backups/
```

Copy important backups off the server.

## 12. Restore Database

Use this only when you are sure the database must be restored:

```bash
cd /opt/restaurant-system/deployment/cloud
./restore-db.sh ./data/backups/restaurant_pos-YYYYMMDD-HHMMSS.dump
```

The script asks you to type:

```text
RESTORE
```

It stops the backend, restores PostgreSQL, and starts the backend again.

## 13. Useful Commands

Show current branch:

```bash
git branch --show-current
```

Show changed files:

```bash
git status
```

Show unstaged changes:

```bash
git diff
```

Show staged files:

```bash
git diff --cached --name-only
```

Show staged content:

```bash
git diff --cached
```

Unstage one file:

```bash
git restore --staged path/to/file
```

Discard one local file change:

```bash
git restore path/to/file
```

View server containers:

```bash
cd /opt/restaurant-system/deployment/cloud
docker compose --env-file .env -f docker-compose.yml ps
```

View logs:

```bash
docker compose --env-file .env -f docker-compose.yml logs -f backend
docker compose --env-file .env -f docker-compose.yml logs -f nginx
docker compose --env-file .env -f docker-compose.yml logs -f db
```

## 14. Golden Rules

- Do not mix unrelated work in one PR.
- Do not use `git add -A` when the working tree contains multiple tasks.
- Always check `git diff --cached --name-only` before commit.
- Back up the database before production update.
- Run `./health-check.sh` after deploy or update.
- Keep `.env` and `deployment/cloud/data/` out of git.
- Prefer rollback by commit/tag for code issues.
- Prefer database restore only for real data issues.
