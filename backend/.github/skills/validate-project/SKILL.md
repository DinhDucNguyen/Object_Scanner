---
name: validate-project
description: 'Kiểm tra consistency, conventions, và quality của codebase. Use when: validate code, check conventions, verify architecture, code review, quality check.'
argument-hint: 'Phạm vi validation (all, models, api, services, etc.)'
---

# Validate Project

Skill này giúp validate codebase đảm bảo tuân thủ conventions, architecture patterns, và best practices của dự án.

## When to Use

Sử dụng skill này khi bạn cần:
- Kiểm tra code mới có follow conventions không
- Review code trước khi commit
- Verify architecture consistency
- Quality check định kỳ
- Onboard team member mới

## Validation Scope

```
validate-project [scope]

Scopes:
- all: Validate toàn bộ project
- models: Validate database models
- api: Validate API endpoints
- services: Validate service layer
- schemas: Validate Pydantic schemas
- repos: Validate repositories
```

## Validation Checklist

### 1. Database Models (`app/models/`)

#### Naming Conventions
- [ ] Class name: PascalCase, số ít (e.g., `User`, `UserBadge`)
- [ ] Table name: snake_case, số nhiều (e.g., `users`, `user_badges`)
- [ ] Foreign key: `{table_singular}_id` (e.g., `user_id`)

#### Required Fields
- [ ] Mỗi model có `id` (primary key, autoincrement)
- [ ] Mỗi model có `created_at` (TIMESTAMP, default=datetime.utcnow)
- [ ] Mỗi model có `updated_at` (TIMESTAMP, onupdate=datetime.utcnow)

#### Imports
```python
# Required imports
from sqlalchemy import Column, Integer, String, DateTime, TIMESTAMP
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime
import enum  # if using enums
```

#### Enums
- [ ] Enum inherit từ `str, enum.Enum`
- [ ] Enum name: PascalCase ending with "Enum" hoặc type (e.g., `UserStatus`, `UserRole`)

#### Relationships
- [ ] One-to-many: `cascade="all, delete-orphan"` on parent
- [ ] One-to-one: `uselist=False` + `cascade="all, delete-orphan"`
- [ ] Many-to-many: No cascade, use association table
- [ ] Relationship names: plural for one-to-many, singular for many-to-one

#### Timestamps
- [ ] Timestamp default: `datetime.utcnow` (không có ngoặc)
- [ ] Không dùng `datetime.now()` hoặc `datetime.utcnow()`

**Validation Commands:**
```bash
# Check model imports
python -c "from app.models import *; print('All models import OK')"

# Check model consistency
python -c "
from app.db.session import Base
from app.models import *
for model in Base.__subclasses__():
    table_name = model.__tablename__
    columns = [c.name for c in model.__table__.columns]
    assert 'id' in columns, f'{model.__name__} missing id'
    assert 'created_at' in columns, f'{model.__name__} missing created_at'
    assert 'updated_at' in columns, f'{model.__name__} missing updated_at'
print('All models validated')
"
```

### 2. Repositories (`app/repositories/`)

#### Naming Conventions
- [ ] File name: `{table_name}_repo.py` (e.g., `user_repo.py`)
- [ ] Class name: `{ModelName}Repository` (e.g., `UserRepository`)

#### Required Methods
- [ ] `get_by_id(self, db: Session, id: int)`
- [ ] `get_all(self, db: Session, skip: int = 0, limit: int = 100)`
- [ ] `create(self, db: Session, obj: Model)`
- [ ] `update(self, db: Session, obj: Model)`
- [ ] `delete(self, db: Session, id: int)` (optional)

#### Method Signatures
- [ ] First parameter: `self`
- [ ] Second parameter: `db: Session`
- [ ] Return type: Model instance hoặc List[Model]

#### Database Operations
- [ ] `create`: db.add() → db.commit() → db.refresh()
- [ ] `update`: db.commit() → db.refresh()
- [ ] `delete`: db.delete() → db.commit()
- [ ] No business logic (chỉ database operations)

**Validation Commands:**
```bash
# Check repository structure
python -c "
from app.repositories.user_repo import UserRepository
repo = UserRepository()
required_methods = ['get_by_id', 'get_all', 'create', 'update']
for method in required_methods:
    assert hasattr(repo, method), f'Missing method: {method}'
print('Repository structure validated')
"
```

### 3. Services (`app/services/`)

#### Naming Conventions
- [ ] File name: `{resource}_service.py` (e.g., `user_service.py`)
- [ ] Class name: `{ResourceName}Service` (e.g., `UserService`)

#### Structure
- [ ] Constructor khởi tạo repository: `self.repo = ResourceRepository()`
- [ ] Methods có `db: Session` parameter
- [ ] Business logic validation trong service (không trong repo)

#### Error Handling
- [ ] Raise `HTTPException` với proper status codes:
  - 400: Validation error, bad input
  - 401: Unauthorized (invalid credentials)
  - 403: Forbidden (no permission)
  - 404: Not found
  - 409: Conflict (duplicate)
  - 500: Internal server error

- [ ] Error messages clear và informative
- [ ] Ownership verification (nếu cần)

**Example Error Handling:**
```python
# Good ✅
if not user:
    raise HTTPException(404, "User not found")

# Bad ❌
if not user:
    raise Exception("Error")  # Vague message, wrong exception
```

**Validation Commands:**
```bash
# Check service imports
python -c "from app.services.user_service import UserService; print('OK')"

# Check service has repository
python -c "
from app.services.user_service import UserService
service = UserService()
assert hasattr(service, 'repo'), 'Service must have repo attribute'
print('Service structure validated')
"
```

### 4. Pydantic Schemas (`app/schemas/`)

#### Naming Conventions
- [ ] File name: `{resource}.py` (e.g., `user.py`)
- [ ] Schema names:
  - `{Resource}Create` for POST input
  - `{Resource}Update` for PUT/PATCH input
  - `{Resource}Response` for output
  - `{Resource}ListResponse` for paginated lists

#### Schema Structure

**Create Schema:**
- [ ] Các trường required (mandatory fields)
- [ ] Không có `id`, timestamps
- [ ] Dùng `Field()` cho validation

**Update Schema:**
- [ ] Tất cả trường optional
- [ ] Không có `id`, timestamps

**Response Schema:**
- [ ] Tất cả trường của model
- [ ] Có `id`, timestamps
- [ ] `class Config: from_attributes = True`

**List Response:**
- [ ] Structure:
  ```python
  class ListResponse(BaseModel):
      items: List[ResourceResponse]
      total: int
      skip: int
      limit: int
  ```

#### Validation
- [ ] Dùng Pydantic `Field()` validators:
  - `min_length`, `max_length` cho strings
  - `ge`, `le` cho numbers
  - `regex` cho patterns
- [ ] Default values appropriate

**Validation Commands:**
```bash
# Check schemas can be imported
python -c "from app.schemas.user import UserCreate, UserResponse; print('OK')"

# Validate schema has Config
python -c "
from app.schemas.user import UserResponse
assert hasattr(UserResponse, 'Config'), 'Response schema needs Config'
assert hasattr(UserResponse.Config, 'from_attributes'), 'Need from_attributes'
assert UserResponse.Config.from_attributes == True
print('Schema Config validated')
"
```

### 5. API Routers (`app/routers/`)

#### Naming Conventions
- [ ] File name: `{resource}_router.py` (e.g., `user_router.py`)
- [ ] Router variable: `router = APIRouter(prefix="/api/{resources}", tags=["{Resource}"])`

#### Router Configuration
- [ ] Prefix: `/api/{resource_plural}` (e.g., `/api/users`, `/api/badges`)
- [ ] Tags: `["{ResourceSingular}"]` for OpenAPI grouping

#### Endpoint Structure

**Path Parameters:**
- [ ] Resource ID: `{resource_id}` (e.g., `/api/users/{user_id}`)

**Query Parameters:**
- [ ] Pagination: `skip: int = Query(0, ge=0)`, `limit: int = Query(100, ge=1, le=100)`
- [ ] Filters: Optional parameters with descriptive names

**Dependencies:**
- [ ] `db: Session = Depends(get_db)` for database
- [ ] `user_id: int = Depends(get_current_user_id)` for auth

**Response Models:**
- [ ] Mỗi endpoint có `response_model`
- [ ] POST creates: `status_code=201`
- [ ] DELETE: return message hoặc 204

**Docstrings:**
- [ ] Mỗi endpoint có docstring describing action

#### HTTP Methods
- [ ] GET (list): Return paginated list
- [ ] GET (detail): Return single resource
- [ ] POST: Create new resource, status 201
- [ ] PUT/PATCH: Update resource
- [ ] DELETE: Remove resource

**Validation Commands:**
```bash
# Start server and check endpoints
uvicorn main:app --reload &
sleep 2
curl -s http://localhost:8000/docs | grep -q "openapi" && echo "API docs OK"

# Check endpoints registered
python -c "
from main import app
routes = [r.path for r in app.routes]
assert '/api/users' in str(routes), 'User routes not registered'
print('Routes registered OK')
"
```

### 6. Migrations (`alembic/versions/`)

#### Migration Files
- [ ] Generated with `alembic revision --autogenerate -m "description"`
- [ ] Descriptive message (e.g., "add user_badges table")
- [ ] Review upgrade() và downgrade()

#### Migration Content
- [ ] `upgrade()`: Creates tables, adds columns, etc.
- [ ] `downgrade()`: Reverses changes
- [ ] Foreign keys defined correctly
- [ ] Indexes created for FKs

**Validation Commands:**
```bash
# Check migrations can run
alembic check

# Get current revision
alembic current

# Show pending migrations
alembic history --verbose
```

### 7. Project Structure

#### Required Files
- [ ] `main.py` - FastAPI app entry point
- [ ] `requirements.txt` - Python dependencies
- [ ] `alembic.ini` - Alembic config
- [ ] `.env` - Environment variables (gitignored)
- [ ] `PROJECT_RULES.md` - GSD methodology
- [ ] `GSD-STYLE.md` - Style guide

#### Directory Structure
```
app/
├── __init__.py
├── core/
│   ├── config.py
│   └── __init__.py
├── db/
│   ├── session.py
│   └── seed.py
├── models/
│   ├── __init__.py
│   └── *.py
├── repositories/
│   ├── __init__.py
│   └── *_repo.py
├── services/
│   ├── __init__.py
│   └── *_service.py
├── routers/
│   ├── __init__.py
│   └── *_router.py
├── schemas/
│   ├── __init__.py
│   └── *.py
├── dependencies/
│   └── get_current_user.py
└── utils/
    ├── security.py
    └── sm2.py
```

### 8. Code Quality

#### Python Style
- [ ] PEP 8 compliant
- [ ] Import order: standard → third-party → local
- [ ] No unused imports
- [ ] No debug prints left in code

#### Type Hints
- [ ] Function parameters have type hints
- [ ] Return types specified
- [ ] Optional types used correctly

#### Comments and Docstrings
- [ ] Functions have docstrings for complex logic
- [ ] Comments explain "why", not "what"
- [ ] No commented-out code

**Validation Commands:**
```bash
# Check Python syntax
python -m py_compile app/**/*.py

# Check imports (requires pylint)
pylint --disable=all --enable=unused-import app/

# Format check (if using black)
black --check app/
```

### 9. Security

#### Authentication
- [ ] Protected endpoints use `Depends(get_current_user_id)`
- [ ] Public endpoints documented as such
- [ ] Token validation trong dependency

#### Authorization
- [ ] Ownership checks trong service layer
- [ ] Role-based access control (nếu cần)
- [ ] Return 403 for unauthorized access

#### Data Validation
- [ ] SQL injection prevented (use ORM)
- [ ] Input validation với Pydantic
- [ ] Output sanitization (không expose sensitive data)

#### Secrets
- [ ] No hardcoded secrets
- [ ] Environment variables cho config
- [ ] `.env` in `.gitignore`

### 10. Testing

#### Test Structure
- [ ] Tests trong `tests/` directory
- [ ] Test files: `test_{feature}.py`
- [ ] Test functions: `test_{scenario}`

#### Test Coverage
- [ ] Repository methods tested
- [ ] Service logic tested
- [ ] API endpoints tested (integration)
- [ ] Error cases tested

**Validation Commands:**
```bash
# Run tests
pytest

# Check coverage
pytest --cov=app --cov-report=term-missing

# Run specific test file
pytest tests/test_user.py
```

## Automated Validation Script

Create `scripts/validate-all.sh`:

```bash
#!/bin/bash

echo "🔍 Validating Project..."

# 1. Check Python syntax
echo "✓ Checking Python syntax..."
python -m py_compile app/**/*.py || exit 1

# 2. Check imports
echo "✓ Checking imports..."
python -c "from app.models import *" || exit 1
python -c "from app.services import *" || exit 1
python -c "from app.routers import *" || exit 1

# 3. Check database
echo "✓ Checking database migrations..."
alembic check || exit 1

# 4. Run tests
echo "✓ Running tests..."
pytest tests/ || exit 1

# 5. Check API
echo "✓ Checking API structure..."
python -c "
from main import app
routes = [r.path for r in app.routes]
print(f'Total routes: {len(routes)}')
" || exit 1

echo "✅ All validations passed!"
```

## Quick Validation Commands

```bash
# Validate everything
./scripts/validate-all.sh

# Validate models only
python -c "from app.models import *; from app.db.session import Base; print([m.__tablename__ for m in Base.__subclasses__()])"

# Validate API structure
python -c "from main import app; print([{'path': r.path, 'methods': r.methods} for r in app.routes if hasattr(r, 'methods')])"

# Check migrations
alembic current && alembic check

# Run tests
pytest -v
```

## Common Issues and Fixes

### Issue 1: Model missing timestamps
**Problem:** Model không có `created_at` hoặc `updated_at`
**Fix:** Add to model:
```python
created_at = Column(TIMESTAMP, default=datetime.utcnow)
updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)
```

### Issue 2: Datetime with parentheses
**Problem:** `default=datetime.utcnow()`
**Fix:** Remove parentheses: `default=datetime.utcnow`

### Issue 3: Missing response model Config
**Problem:** Response schema không có `from_attributes = True`
**Fix:** Add to schema:
```python
class Config:
    from_attributes = True
```

### Issue 4: Business logic in repository
**Problem:** Repository có validation hoặc HTTP exceptions
**Fix:** Move to service layer

### Issue 5: Không có error handling
**Problem:** Service không raise HTTPException
**Fix:** Add proper error handling:
```python
if not resource:
    raise HTTPException(404, "Resource not found")
```

## Best Practices Summary

1. **Models**: Timestamps, proper naming, relationships
2. **Repositories**: CRUD only, no business logic
3. **Services**: Business logic, error handling, ownership
4. **Schemas**: Validation, separate Create/Update/Response
5. **Routers**: Response models, auth, docstrings
6. **Migrations**: Review before running
7. **Security**: Auth, authorization, input validation
8. **Testing**: Cover all layers

## Checklist for Code Review

- [ ] Naming conventions followed
- [ ] Architecture layers respected
- [ ] Error handling comprehensive
- [ ] Security considerations addressed
- [ ] Tests written and passing
- [ ] Documentation updated
- [ ] No debug code left
- [ ] Migrations reviewed
- [ ] API docs updated (OpenAPI)
- [ ] Environment variables used correctly

## Summary

Use this skill to ensure code quality và consistency. Run validation:
- **Before commit**: Quick checks
- **Before push**: Full validation
- **Weekly**: Comprehensive audit
- **Code review**: Use checklist

Maintain high quality standards = Fewer bugs + Easier maintenance + Better team productivity!
