---
name: add-learning-feature
description: 'Implement feature mới hoàn chỉnh theo GSD methodology (SPEC → PLAN → EXECUTE → VERIFY → COMMIT). Use when: thêm feature mới, implement tính năng, phát triển chức năng, GSD workflow.'
argument-hint: 'Mô tả feature cần implement'
---

# Add Learning Feature

Skill này hướng dẫn implement một feature mới hoàn chỉnh theo **GSD (Get Shit Done) methodology**, đảm bảo feature được spec rõ ràng, plan chi tiết, execute đúng cách, verify kỹ lưỡng, và commit sạch sẽ.

## When to Use

Sử dụng skill này khi bạn cần:
- Implement một feature/chức năng mới
- Thêm business logic phức tạp
- Phát triển feature có nhiều components
- Đảm bảo quality và traceability

## GSD Methodology

```
SPEC → PLAN → EXECUTE → VERIFY → COMMIT
```

### Core Protocol

1. **SPEC**: Define requirements until status = FINALIZED
2. **PLAN**: Decompose into phases and detailed tasks
3. **EXECUTE**: Implement with atomic commits per task
4. **VERIFY**: Prove completion with empirical evidence
5. **COMMIT**: One task = one commit

**Planning Lock**: Không code cho đến khi SPEC.md có "Status: FINALIZED".

## Step-by-Step Procedure

### Phase 1: SPEC (Specification)

#### 1.1 Tạo SPEC Document

Tạo file `.gsd/SPEC.md` (hoặc feature-specific spec):

```markdown
# Feature Specification: {Feature Name}

**Status:** DRAFT | IN_REVIEW | FINALIZED
**Owner:** {Your Name}
**Created:** {Date}
**Updated:** {Date}

## Overview

Brief description of the feature.

## Problem Statement

What problem does this solve?
- Pain point 1
- Pain point 2

## Goals

What should this feature achieve?
- Goal 1: Measurable outcome
- Goal 2: Measurable outcome

## Non-Goals

What is explicitly out of scope?
- Non-goal 1
- Non-goal 2

## Requirements

### Functional Requirements

**FR1: {Requirement Name}**
- Description: Clear statement
- Priority: HIGH | MEDIUM | LOW
- Acceptance Criteria:
  - [ ] Criterion 1
  - [ ] Criterion 2

**FR2: {Requirement Name}**
- ...

### Non-Functional Requirements

**NFR1: Performance**
- Response time < 200ms
- Support 1000 concurrent users

**NFR2: Security**
- Role-based access control
- Data encryption at rest

## User Stories

**As a** {user type}
**I want** {goal}
**So that** {benefit}

**Acceptance Criteria:**
- [ ] Scenario 1
- [ ] Scenario 2

## Technical Approach

### Architecture

High-level overview of the solution:
- Components involved
- Data flow
- Integration points

### Database Changes

- New tables: {list}
- Modified tables: {list}
- Migrations: {description}

### API Changes

- New endpoints: {list}
- Modified endpoints: {list}
- Breaking changes: {list}

## Dependencies

- External libraries: {list}
- Other features: {list}
- Third-party APIs: {list}

## Risks & Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Risk 1 | HIGH | LOW | Strategy |

## Open Questions

- [ ] Question 1?
- [ ] Question 2?

## Sign-Off

- [ ] All requirements clear
- [ ] Technical approach validated
- [ ] Open questions resolved
- [ ] Status: FINALIZED
```

#### 1.2 Review và Finalize

- Đọc lại SPEC kỹ lưỡng
- Resolve tất cả Open Questions
- Đảm bảo requirements đủ chi tiết để plan
- Set status = **FINALIZED**

### Phase 2: PLAN (Planning)

#### 2.1 Decompose into Waves

Tạo file `.gsd/ROADMAP.md`:

```markdown
# Roadmap: {Feature Name}

## Wave 1: Foundation (No Dependencies)

**Objective:** Set up database and core models

**Tasks:**
- Task 1.1: Create database models
- Task 1.2: Create migrations
- Task 1.3: Create repositories

**Dependencies:** None
**Estimated Duration:** 2 hours

## Wave 2: Business Logic (Depends on Wave 1)

**Objective:** Implement service layer

**Tasks:**
- Task 2.1: Create service class
- Task 2.2: Implement core logic
- Task 2.3: Add validation

**Dependencies:** Wave 1 complete
**Estimated Duration:** 3 hours

## Wave 3: API Layer (Depends on Wave 2)

**Objective:** Expose via REST API

**Tasks:**
- Task 3.1: Create schemas
- Task 3.2: Create router
- Task 3.3: Register endpoints

**Dependencies:** Wave 2 complete
**Estimated Duration:** 2 hours

## Wave 4: Integration & Testing (Depends on Wave 3)

**Objective:** Verify end-to-end

**Tasks:**
- Task 4.1: Integration tests
- Task 4.2: Manual testing
- Task 4.3: Documentation

**Dependencies:** Wave 3 complete
**Estimated Duration:** 2 hours
```

#### 2.2 Create Detailed Task Plans

Cho mỗi task phức tạp, tạo detailed plan:

```markdown
## Task: {Task Name}

**Type:** auto | checkpoint:human
**Effort:** low | medium | high | max
**Files:**
- path/to/file1.py
- path/to/file2.py

**Action:**
Specific implementation steps:
1. Step 1
2. Step 2

**AVOID:**
- Common mistake (reason)
- Anti-pattern (reason)

**USE:**
- Best practice (reason)
- Recommended approach (reason)

**Verify:**
```bash
# Command to verify completion
pytest tests/test_feature.py
```

**Done:**
- [ ] Measurable criterion 1
- [ ] Measurable criterion 2
```

### Phase 3: EXECUTE (Implementation)

#### 3.1 Execute Wave by Wave

**For each wave:**

1. **Start wave**
   - Review all tasks in wave
   - Ensure dependencies met
   - Prepare development environment

2. **Execute tasks in parallel** (if possible)
   - Follow detailed plans
   - Use other skills (add-database-table, create-api-endpoint)
   - Keep changes focused

3. **Verify each task**
   - Run verification commands
   - Capture output as proof
   - Ensure acceptance criteria met

4. **Complete wave**
   - All tasks verified
   - Create state snapshot
   - Commit wave work

#### 3.2 State Snapshot Template

After each wave, document:

```markdown
## Wave {N} Summary

**Objective:** {what this wave aimed to accomplish}

**Changes:**
- Created `app/models/feature.py` with FeatureModel
- Added migration `add_feature_table`
- Implemented FeatureRepository with CRUD operations

**Files Touched:**
- app/models/feature.py (new)
- alembic/versions/xxx_add_feature.py (new)
- app/repositories/feature_repo.py (new)
- app/models/__init__.py (modified)

**Verification:**
```bash
$ alembic upgrade head
INFO  [alembic.runtime.migration] Running upgrade -> xxx, add_feature
$ python -c "from app.models import Feature; print('OK')"
OK
```

**Risks/Debt:**
- None identified

**Next Wave TODO:**
- Implement FeatureService
- Add business validation logic
```

### Phase 4: VERIFY (Verification)

#### 4.1 Proof Requirements

Every change requires empirical evidence:

| Change Type | Required Proof |
|-------------|----------------|
| API endpoint | `curl` output with status code |
| Database | Schema dump or query result |
| Business logic | Test output showing pass |
| UI | Screenshot |
| Config | Verification command output |

**Example:**

```bash
# API endpoint proof
$ curl -X POST http://localhost:8000/api/features \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{"name": "Test Feature"}'
  
{
  "id": 1,
  "name": "Test Feature",
  "created_at": "2026-03-11T09:30:00Z"
}
```

#### 4.2 Verification Checklist

- [ ] All functionality works as specified
- [ ] All acceptance criteria met
- [ ] No errors in logs
- [ ] API returns correct status codes
- [ ] Database constraints working
- [ ] Authentication/authorization working
- [ ] Edge cases handled
- [ ] Error messages clear

### Phase 5: COMMIT (Version Control)

#### 5.1 Commit Convention

**Format:**
```
type(scope): description

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code restructure
- `docs`: Documentation
- `test`: Tests
- `chore`: Build, dependencies

**Examples:**
```bash
# Wave 1 commit
git add app/models/feature.py alembic/versions/xxx.py app/repositories/
git commit -m "feat(feature): add Feature model, migration, and repository

- Create Feature SQLAlchemy model with name, description, status
- Add alembic migration for features table
- Implement FeatureRepository with CRUD operations

Verified: Migration runs, imports work"

# Wave 2 commit
git commit -m "feat(feature): implement FeatureService business logic

- Add validation for feature name uniqueness
- Implement create, update, delete operations
- Add ownership verification for user features

Verified: Service methods work, validation catches errors"

# Wave 3 commit
git commit -m "feat(feature): add REST API endpoints for features

- Create FeatureCreate, FeatureResponse schemas
- Implement CRUD endpoints in feature_router
- Add authentication and authorization

Verified: curl tests pass, OpenAPI docs updated"
```

#### 5.2 Commit Best Practices

- **One task = one commit**
- Commit messages should be clear and descriptive
- Include verification info in commit body
- Atomic commits: each commit should work independently
- Group related changes together

## Feature Complexity Matrix

| Feature Type | Waves | Duration | Complexity |
|--------------|-------|----------|------------|
| Simple CRUD | 3 | 4-6h | Low |
| Business Logic | 4 | 8-12h | Medium |
| Integration | 5 | 12-20h | High |
| Platform Feature | 6+ | 20-40h | Very High |

## Integration with Other Skills

Use these skills during EXECUTE phase:

- **Wave 1 (Database)**: Use `add-database-table` skill
- **Wave 3 (API)**: Use `create-api-endpoint` skill
- **Wave 4 (Validation)**: Use `validate-project` skill

## Best Practices

1. **SPEC First:**
   - No code until SPEC finalized
   - Clear requirements save time later
   - Get feedback early on spec

2. **Wave-Based Execution:**
   - Respect dependencies
   - Verify each wave completely
   - Create state snapshots

3. **Empirical Verification:**
   - Never accept "looks correct"
   - Always capture proof
   - Test edge cases

4. **Clean Commits:**
   - Atomic and focused
   - Clear messages
   - Include verification

5. **Search-First:**
   - Search before reading files
   - Targeted reads only
   - Avoid context pollution

## Anti-patterns

❌ **Không làm:**
- Start coding without finalized SPEC
- Mix tasks from different waves
- Skip verification steps
- Commit without proof
- Large monolithic commits
- Vague commit messages

✅ **Nên làm:**
- Follow GSD protocol strictly
- Execute waves sequentially
- Verify everything
- Atomic commits with proof
- Clear documentation
- State snapshots

## Checklist

### SPEC Phase
- [ ] Create SPEC.md with all sections
- [ ] Define clear requirements
- [ ] Identify dependencies and risks
- [ ] Resolve all open questions
- [ ] Status = FINALIZED

### PLAN Phase
- [ ] Create ROADMAP.md with waves
- [ ] Define tasks with clear actions
- [ ] Identify dependencies between waves
- [ ] Estimate effort for each task

### EXECUTE Phase
- [ ] Execute waves in order
- [ ] Verify each task completion
- [ ] Create state snapshots
- [ ] Document changes

### VERIFY Phase
- [ ] Capture empirical proof
- [ ] Test all acceptance criteria
- [ ] Verify edge cases
- [ ] Check error handling

### COMMIT Phase
- [ ] Atomic commits per task
- [ ] Clear commit messages
- [ ] Include verification info
- [ ] Update documentation

## Example: Complete Feature Flow

**Feature:** User Badge System

### SPEC
```markdown
# Feature: User Badge System

Status: FINALIZED

## Overview
Allow users to earn badges for completing learning milestones.

## Requirements
FR1: Users can earn badges
FR2: Display badges on profile
FR3: Track badge progress
```

### PLAN
```markdown
Wave 1: Database
- Create Badge model
- Create UserBadge model (junction table)
- Migrations

Wave 2: Business Logic
- BadgeService.check_and_award()
- BadgeService.get_user_badges()

Wave 3: API
- GET /api/badges (list available)
- GET /api/users/me/badges (my badges)
- POST /api/badges/check (trigger check)
```

### EXECUTE
```bash
# Wave 1
[Create models, migrations, repositories]
git commit -m "feat(badges): add badge models and migrations"

# Wave 2
[Implement service logic]
git commit -m "feat(badges): implement badge awarding logic"

# Wave 3
[Create API endpoints]
git commit -m "feat(badges): add badge API endpoints"
```

### VERIFY
```bash
# Test full flow
curl -X GET http://localhost:8000/api/badges
curl -X GET http://localhost:8000/api/users/me/badges \
  -H "Authorization: Bearer ${TOKEN}"
```

## Summary

GSD methodology đảm bảo:
- ✅ Clear requirements trước khi code
- ✅ Structured implementation process
- ✅ Empirical verification
- ✅ Clean version control
- ✅ Traceable progress

Follow strictly để tránh:
- ❌ Scope creep
- ❌ Unclear requirements
- ❌ Incomplete features
- ❌ Messy commits
- ❌ Unverified code
