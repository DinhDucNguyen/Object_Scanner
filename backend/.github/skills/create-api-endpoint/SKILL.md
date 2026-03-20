---
name: create-api-endpoint
description: 'Tạo REST API endpoint hoàn chỉnh với Router, Service, Schema, và Repository. Use when: tạo API mới, endpoint REST, CRUD operations, FastAPI router.'
argument-hint: 'Resource name và các operations cần tạo (GET, POST, PUT, DELETE)'
---

# Create API Endpoint

Skill này giúp tạo một API endpoint hoàn chỉnh theo architecture của dự án, bao gồm:
- Pydantic Schemas (request/response models)
- Service Layer (business logic)
- Router (FastAPI endpoints)
- Repository methods (nếu cần thêm)

## When to Use

Sử dụng skill này khi bạn cần:
- Tạo REST API endpoint mới
- Implement CRUD operations
- Thêm business logic cho một resource
- Expose data qua HTTP API

## Architecture Overview

```
app/schemas/{resource}.py       # Pydantic models
app/services/{resource}_service.py  # Business logic
app/routers/{resource}_router.py    # FastAPI routes
app/repositories/{resource}_repo.py # Database queries (có thể có sẵn)
```

**Request Flow:**
```
Client Request
    ↓
Router (validate, auth)
    ↓
Service (business logic)
    ↓
Repository (database)
    ↓
Response
```

## Step-by-Step Procedure

### 1. Thu Thập Thông Tin

Xác định các thông tin sau:
- **Resource name** (ví dụ: `badge`, `achievement`, `lesson`)
- **Operations cần implement:**
  - GET (list/detail)
  - POST (create)
  - PUT/PATCH (update)
  - DELETE (delete)
- **Authentication required?** (hầu hết các API cần auth)
- **Business logic đặc biệt?** (validation, calculation, external API calls)

### 2. Tạo Pydantic Schemas

Tạo file `app/schemas/{resource}.py`:

```python
from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime

# CREATE Schema (input cho POST)
class ResourceCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=100)
    description: Optional[str] = None
    # Các trường khác...

# UPDATE Schema (input cho PUT/PATCH)
class ResourceUpdate(BaseModel):
    name: Optional[str] = Field(None, min_length=1, max_length=100)
    description: Optional[str] = None
    # Các trường optional...

# RESPONSE Schema (output)
class ResourceResponse(BaseModel):
    id: int
    name: str
    description: Optional[str]
    created_at: datetime
    updated_at: datetime
    
    class Config:
        from_attributes = True  # Cho phép convert từ SQLAlchemy model

# LIST Response (cho pagination)
class ResourceListResponse(BaseModel):
    items: List[ResourceResponse]
    total: int
    skip: int
    limit: int
```

**Schema Conventions:**
- Create: Các trường bắt buộc, không có `id`, timestamps
- Update: Tất cả trường optional
- Response: Tất cả trường của model, có `id`, timestamps
- List: Wrap trong object có `items`, `total`, `skip`, `limit`
- Dùng `Field()` cho validation (min_length, max_length, ge, le, regex)
- `class Config: from_attributes = True` để convert từ ORM

### 3. Export Schemas

Thêm export vào `app/schemas/__init__.py`:
```python
from .{resource} import ResourceCreate, ResourceUpdate, ResourceResponse, ResourceListResponse
```

### 4. Thêm Repository Methods (Nếu Cần)

Nếu repository chưa có methods cần thiết, thêm vào `app/repositories/{resource}_repo.py`:

```python
def get_by_user_id(self, db: Session, user_id: int, skip: int = 0, limit: int = 100):
    return db.query(Resource).filter(
        Resource.user_id == user_id
    ).offset(skip).limit(limit).all()

def count_by_user_id(self, db: Session, user_id: int):
    return db.query(Resource).filter(Resource.user_id == user_id).count()

def get_by_name(self, db: Session, name: str):
    return db.query(Resource).filter(Resource.name == name).first()
```

### 5. Tạo Service Layer

Tạo file `app/services/{resource}_service.py`:

```python
from fastapi import HTTPException
from sqlalchemy.orm import Session
from datetime import datetime

from app.models.{resource} import Resource
from app.repositories.{resource}_repo import ResourceRepository
from app.schemas.{resource} import ResourceCreate, ResourceUpdate

class ResourceService:
    def __init__(self):
        self.repo = ResourceRepository()
    
    def get_by_id(self, db: Session, resource_id: int, user_id: int = None):
        """Lấy resource theo ID, verify ownership nếu có user_id."""
        resource = self.repo.get_by_id(db, resource_id)
        if not resource:
            raise HTTPException(404, "Resource not found")
        
        # Verify ownership (nếu cần)
        if user_id and resource.user_id != user_id:
            raise HTTPException(403, "Access denied")
        
        return resource
    
    def get_list(self, db: Session, user_id: int = None, skip: int = 0, limit: int = 100):
        """Lấy danh sách resources, filter theo user nếu có."""
        if user_id:
            items = self.repo.get_by_user_id(db, user_id, skip, limit)
            total = self.repo.count_by_user_id(db, user_id)
        else:
            items = self.repo.get_all(db, skip, limit)
            total = db.query(Resource).count()
        
        return {
            "items": items,
            "total": total,
            "skip": skip,
            "limit": limit
        }
    
    def create(self, db: Session, data: ResourceCreate, user_id: int = None):
        """Tạo resource mới."""
        # Validation logic
        if self.repo.get_by_name(db, data.name):
            raise HTTPException(400, "Resource with this name already exists")
        
        # Create object
        resource = Resource(
            name=data.name,
            description=data.description,
            user_id=user_id,  # Nếu có
            # ... các trường khác
        )
        
        return self.repo.create(db, resource)
    
    def update(self, db: Session, resource_id: int, data: ResourceUpdate, user_id: int = None):
        """Update resource."""
        resource = self.get_by_id(db, resource_id, user_id)
        
        # Update fields
        if data.name is not None:
            resource.name = data.name
        if data.description is not None:
            resource.description = data.description
        # ... các trường khác
        
        return self.repo.update(db, resource)
    
    def delete(self, db: Session, resource_id: int, user_id: int = None):
        """Delete resource."""
        resource = self.get_by_id(db, resource_id, user_id)
        self.repo.delete(db, resource_id)
        return {"message": "Resource deleted successfully"}
```

**Service Conventions:**
- Luôn raise `HTTPException` với status code và message rõ ràng
- Verify ownership trong service layer
- Business logic validation trong service (không trong repository)
- Repository chỉ lo database operations
- Return dict hoặc model object

### 6. Tạo Router

Tạo file `app/routers/{resource}_router.py`:

```python
from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from typing import Optional

from app.db.session import get_db
from app.services.{resource}_service import ResourceService
from app.schemas.{resource} import (
    ResourceCreate, ResourceUpdate, 
    ResourceResponse, ResourceListResponse
)
from app.dependencies.get_current_user import get_current_user_id

router = APIRouter(prefix="/api/{resources}", tags=["{Resource}"])
service = ResourceService()


@router.get("/", response_model=ResourceListResponse)
def get_resources(
    skip: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=100),
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)  # Nếu cần auth
):
    """Lấy danh sách resources."""
    return service.get_list(db, user_id=user_id, skip=skip, limit=limit)


@router.get("/{resource_id}", response_model=ResourceResponse)
def get_resource(
    resource_id: int,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    """Lấy chi tiết một resource."""
    return service.get_by_id(db, resource_id, user_id=user_id)


@router.post("/", response_model=ResourceResponse, status_code=201)
def create_resource(
    data: ResourceCreate,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    """Tạo resource mới."""
    return service.create(db, data, user_id=user_id)


@router.put("/{resource_id}", response_model=ResourceResponse)
def update_resource(
    resource_id: int,
    data: ResourceUpdate,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    """Cập nhật resource."""
    return service.update(db, resource_id, data, user_id=user_id)


@router.delete("/{resource_id}")
def delete_resource(
    resource_id: int,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    """Xóa resource."""
    return service.delete(db, resource_id, user_id=user_id)
```

**Router Conventions:**
- Prefix: `/api/{resource_plural}`
- Tags: `[{ResourceSingular}]`
- Docstring cho mỗi endpoint
- Dùng `Query()` cho query parameters với validation
- Status code 201 cho POST
- Response model cho tất cả endpoints
- Dependencies: `get_db`, `get_current_user_id`

### 7. Register Router

Thêm router vào `main.py`:

```python
from app.routers import {resource}_router
app.include_router({resource}_router.router)
```

Hoặc trong `app/routers/__init__.py` nếu có:
```python
from .{resource}_router import router as {resource}_router
```

### 8. Verify

**Test API với curl:**

```bash
# GET list
curl -X GET "http://localhost:8000/api/resources?skip=0&limit=10" \
  -H "Authorization: Bearer YOUR_TOKEN"

# GET detail
curl -X GET "http://localhost:8000/api/resources/1" \
  -H "Authorization: Bearer YOUR_TOKEN"

# POST create
curl -X POST "http://localhost:8000/api/resources" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test", "description": "Test resource"}'

# PUT update
curl -X PUT "http://localhost:8000/api/resources/1" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Updated"}'

# DELETE
curl -X DELETE "http://localhost:8000/api/resources/1" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Check OpenAPI docs:**
```
http://localhost:8000/docs
```

## Common Patterns

### Pagination

```python
@router.get("/")
def get_list(
    skip: int = Query(0, ge=0, description="Number of items to skip"),
    limit: int = Query(100, ge=1, le=100, description="Max items to return"),
    db: Session = Depends(get_db)
):
    return service.get_list(db, skip=skip, limit=limit)
```

### Filtering

```python
@router.get("/")
def get_list(
    status: Optional[str] = Query(None),
    category: Optional[str] = Query(None),
    db: Session = Depends(get_db)
):
    return service.get_list(db, status=status, category=category)
```

### Sorting

```python
@router.get("/")
def get_list(
    sort_by: str = Query("created_at", regex="^(name|created_at|updated_at)$"),
    order: str = Query("desc", regex="^(asc|desc)$"),
    db: Session = Depends(get_db)
):
    return service.get_list(db, sort_by=sort_by, order=order)
```

### Search

```python
@router.get("/search")
def search(
    q: str = Query(..., min_length=1),
    db: Session = Depends(get_db)
):
    return service.search(db, query=q)
```

### Batch Operations

```python
@router.post("/batch")
def create_batch(
    items: List[ResourceCreate],
    db: Session = Depends(get_db)
):
    return service.create_batch(db, items)
```

## Best Practices

1. **Error Handling:**
   - 404: Resource not found
   - 403: Access denied (ownership)
   - 400: Validation error, duplicate
   - 401: Unauthorized (no token)
   - 500: Server error (log details)

2. **Authentication:**
   - Dùng `Depends(get_current_user_id)` cho protected endpoints
   - Verify ownership trong service layer
   - Public endpoints không cần auth dependency

3. **Validation:**
   - Schema-level: Pydantic Field validators
   - Business-level: Service layer
   - Database-level: Model constraints

4. **Response Models:**
   - Luôn define explicit response_model
   - Không expose sensitive data (password_hash, tokens)
   - Use separate schemas cho input/output

5. **HTTP Methods:**
   - GET: Read, idempotent
   - POST: Create, 201 status
   - PUT: Full update
   - PATCH: Partial update
   - DELETE: Remove, return message hoặc 204

## Checklist

- [ ] Tạo Pydantic schemas (Create, Update, Response, List)
- [ ] Export schemas trong `schemas/__init__.py`
- [ ] Thêm repository methods (nếu cần)
- [ ] Tạo service class với business logic
- [ ] Verify error handling trong service
- [ ] Tạo router với tất cả endpoints
- [ ] Thêm docstrings cho endpoints
- [ ] Register router trong `main.py`
- [ ] Test với curl hoặc Postman
- [ ] Kiểm tra OpenAPI docs
- [ ] Test authentication và authorization
- [ ] Test validation và error cases

## Anti-patterns

❌ **Không làm:**
- Business logic trong router (phải ở service)
- Database queries trực tiếp trong router (phải qua service)
- Không có error handling
- Expose sensitive data trong response
- Không validate input
- Hardcode values trong router

✅ **Nên làm:**
- Router chỉ lo routing và validation
- Service lo business logic
- Repository lo database
- Clear separation of concerns
- Comprehensive error handling
- Proper status codes
- Clear API documentation
