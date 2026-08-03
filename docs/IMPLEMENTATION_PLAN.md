# Implementation Plan — Library Management System

This document tracks the staged review and implementation plan for the Library Management System. Each phase ends with a green build, all tests passing, docs updated if behavior changed, and a commit before continuing.

---

## Phased Implementation

### Phase 0 — Spotless Formatting + AdminSeeder Fix + Test Infrastructure + Docs Corrections + CI

**Status:** Done

### Phase 0.5 — Account Orphaning Fix

**Status:** Done

### Phase 1 — Edit/Delete UI + Controller Refactors

**Status:** Skipped — BookController was already clean.

### Phase 2 — N+1 Query Fixes

**Status:** Done — BorrowRecord, Student, Librarian repositories.

### Phase 3A — Swagger Restriction to Dev/Test

**Status:** Done (disabled in production profile)

### Phase 3B — Actuator + Structured Logging

**Status:** Done

### Phase 4 — Integration Tests

**Status:** Done — 7 new tests added, 13 total.

### Phase 5 — Frontend Cleanup

**Status:** Skipped — low priority, large scope.

### Phase 6 — Documentation + README Update

**Status:** Done

### Phase D — Engineering Documentation & AI Knowledge Base

**Status:** Done

### Phase 7 — v1.1 Features (email notifications, analytics enhancements, etc.)

**Status:** Pending

### Final Project B Retirement Audit

**Status:** Pending

---

## User Overrides

These decisions were made intentionally and should be respected in all future work:

- **Skip service interfaces** — not needed for portfolio.
- **Skip MapStruct** — not needed for portfolio.
- **Skip equals/hashCode on entities** — not needed for portfolio.
- **Move logging/JaCoCo/Swagger-restriction priority up** — already done in Phase 3B.
- **H2 stays at `runtime` scope** — required by `h2` dev profile.
- **Every phase must end with:** green build, all tests passing, docs updated if behavior changed, committed before continuing.

---

## Version Plan

| Version | Milestone |
|---------|-----------|
| v1.0.0 | Released — initial commit |
| v1.1.0 | After Phases 0–3 |
| v1.2.0 | After Phases 4–6 |
| v1.3.0 | After Phase 7 |
