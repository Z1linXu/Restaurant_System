# CODEX_WORKFLOW.md

# Restaurant System Development Workflow

Version: 1.0

This workflow MUST be followed for every development task.

The purpose is to keep the project stable while evolving from a local restaurant POS into a cloud-ready multi-store platform.

---

# Core Principles

This project is now entering the stabilization stage.

Priority is no longer adding features as quickly as possible.

Priority is:

1. Stability
2. Maintainability
3. Backward Compatibility
4. Cloud Readiness
5. New Features

Never sacrifice stability for convenience.

---

# Documents That Must Be Read First

Before starting ANY task, read:

1.

AGENTS.md

2.

SYSTEM_DOCUMENTATION.md

3.

doc/RESTAURANT_POS_CLOUD_READY_MASTER_PLAN_AND_CODEX_SKILL.md

4.

doc/CODEX_SKILL_RESTAURANT_POS_GUARDRAILS.md

If any of these documents conflict,

STOP

and explain the conflict before modifying code.

Never guess.

---

# PR Workflow

Every PR MUST follow this order.

## Phase 1

Architecture Review

Read the project.

Understand existing implementation.

Do NOT write code.

Output:

- current architecture
- affected modules
- risks
- implementation plan

Wait for approval.

---

## Phase 2

Implementation

After approval,

implement only the requested PR.

Do NOT optimize unrelated code.

Do NOT refactor unrelated modules.

Do NOT "while I'm here..." modify anything.

Keep changes focused.

---

## Phase 3

Compilation

Always compile after implementation.

Backend:

mvn clean compile

Frontend:

npm run build

Never assume compilation succeeds.

---

## Phase 4

Self Review

Review your own code.

Check:

- architecture consistency
- backward compatibility
- cloud compatibility
- performance
- security

Do NOT write new features during review.

---

## Phase 5

Documentation

Update documentation.

Include:

SYSTEM_DOCUMENTATION.md

only if necessary.

Explain:

- what changed
- why
- migration notes
- risks

---

## Phase 6

Stop

After finishing ONE PR,

STOP.

Never continue to the next PR automatically.

Wait for approval.

---

# Allowed Scope

Only modify files related to the current PR.

If another issue is discovered,

report it,

but do NOT fix it unless requested.

---

# Forbidden Behaviors

Never:

- rewrite large modules without approval
- rename APIs
- rename database tables
- rename database columns
- change order lifecycle
- change printing behavior
- change completeOrder semantics
- implement payment
- implement refund
- implement Android App
- introduce breaking API changes

unless the current PR explicitly requires it.

---

# Cloud First Principle

Every new code must be cloud compatible.

Never assume:

localhost

192.168.x.x

Mac

Windows

single store

single user

development profile

---

# Backward Compatibility

Cloud migration must never break:

Windows Pilot

Mock Printing

Pad Direct

Owner Dashboard

Order History

Existing restaurant data

---

# Database Rules

Never use Hibernate auto-update as the migration strategy for production.

Production schema changes must be versioned.

Prefer:

Flyway

Never:

drop tables

rename columns

rewrite historical data

without an explicit migration plan.

---

# Printing Rules

Printing must never block order submission.

Printing failure must be visible.

Printing failure must never silently disappear.

Cloud server must never directly connect to private LAN printers.

---

# Authentication Rules

Production must never rely on:

X-User-Id

Developer Role Switcher

Default JWT Secret

Default Passwords

Development-only configuration

---

# Coding Rules

Prefer:

small commits

small PRs

small risks

Never combine multiple architecture changes in one PR.

---

# Review Checklist

Before finishing, verify:

✓ Code compiles

✓ Existing behavior unchanged

✓ Documentation updated

✓ No unrelated refactoring

✓ No breaking API changes

✓ Cloud compatible

✓ Local development still works

✓ No new security risks

---

# Output Format

At the end of every PR output:

## Files Changed

## Why They Changed

## Risks

## Verification

## Rollback Plan

## Next Recommended PR

Then STOP.

Wait for approval.

---

# Long-Term Goal

Current Roadmap:

Cloud Ready

↓

Production Deployment

↓

First Restaurant Pilot

↓

Android Pad Shell

↓

Pad Direct Printing

↓

Multi-Store Expansion

↓

Platform Product

Always make decisions that move the project toward this roadmap.

Never optimize for short-term convenience at the expense of long-term architecture.