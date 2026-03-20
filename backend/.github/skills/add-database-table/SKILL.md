---
name: add-database-table
description: 'Thêm bảng database mới hoàn chỉnh với SQLAlchemy model, Alembic migration, và repository. Use when: thêm table mới, tạo model database, migration alembic, repository pattern.'
argument-hint: 'Tên bảng và các trường cần tạo'
---

# Add Database Table

Skill này giúp tạo một bảng database mới hoàn chỉnh theo architecture của dự án, bao gồm:
- SQLAlchemy Model
- Alembic Migration
- Repository Class
- Export trong `__init__.py`

## When to Use

Sử dụng skill này khi bạn cần:
- Tạo bảng database mới
- Thêm entity mới vào hệ thống
- Mở rộng data model

## Architecture Overview

```
app/models/{table_name}.py          # SQLAlchemy Model + Enums
alembic/versions/{hash}_xxx.py      # Migration file
app/repositories/{table_name}_repo.py  # Repository class
```

## Step-by-Step Procedure

### 1. Thu Thập Thông Tin

Xác định các thông tin sau:
- **Tên bảng** (số ít, snake_case, ví dụ: `user_badge`)
- **Các trường** (tên, kiểu dữ liệu, constraints)
- **Relationships** (nếu có liên kết với bảng khác)
- **Enums** (nếu có trường enum)

### 2. Tạo SQLAlchemy Model

Tạo file `app/models/{table_name}.py`:

**Required imports:**
```python
from sqlalchemy import Column, Integer, String, DateTime, Enum, TIMESTAMP, ForeignKey, Boolean, Text
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime
import enum
```

**Model structure:**
```python
# Enums (nếu có)
class StatusEnum(str, enum.Enum):
    value1 = "value1"
    value2 = "value2"

# Model class
class TableName(Base):
    __tablename__ = "table_names"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    # Các trường khác...
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # Relationships (nếu có)
    related = relationship("RelatedModel", back_populates="...", cascade="...")
```

**Conventions:**
- Class name: PascalCase (ví dụ: `UserBadge`)
- Table name: số nhiều, snake_case (ví dụ: `user_badges`)
- Luôn có `id`, `created_at`, `updated_at`
- Timestamp dùng `datetime.utcnow` (không có ngoặc)
- Enum inherit từ `str, enum.Enum`

### 3. Export Model

Thêm export vào `app/models/__init__.py`:
```python
from .{table_name} import TableName
```

### 4. Tạo Alembic Migration

**Tạo migration file:**
```bash
alembic revision --autogenerate -m "add {table_name} table"
```

**Review migration file:**
- Kiểm tra `upgrade()` function có tạo table đúng không
- Kiểm tra `downgrade()` function có drop table
- Kiểm tra foreign keys và indexes

### 5. Tạo Repository

Tạo file `app/repositories/{table_name}_repo.py`:

```python
from sqlalchemy.orm import Session
from app.models.{table_name} import TableName

class TableNameRepository:
    def get_by_id(self, db: Session, id: int):
        return db.query(TableName).filter(TableName.id == id).first()
    
    def get_all(self, db: Session, skip: int = 0, limit: int = 100):
        return db.query(TableName).offset(skip).limit(limit).all()
    
    def create(self, db: Session, obj: TableName):
        db.add(obj)
        db.commit()
        db.refresh(obj)
        return obj
    
    def update(self, db: Session, obj: TableName):
        db.commit()
        db.refresh(obj)
        return obj
    
    def delete(self, db: Session, id: int):
        obj = self.get_by_id(db, id)
        if obj:
            db.delete(obj)
            db.commit()
        return obj
```

**Repository Conventions:**
- Class name: `{TableName}Repository`
- Các method cơ bản: `get_by_id`, `get_all`, `create`, `update`, `delete`
- Luôn có `db: Session` là parameter đầu tiên
- `create`: add, commit, refresh
- `update`: commit, refresh (object đã được modify trước đó)
- `delete`: get, delete, commit

### 6. Chạy Migration

```bash
alembic upgrade head
```

### 7. Verify

**Kiểm tra database:**
```bash
# PostgreSQL
psql -d database_name -c "\d table_names"

# SQLite
sqlite3 database.db ".schema table_names"
```

**Kiểm tra imports:**
```python
from app.models import TableName
from app.repositories.{table_name}_repo import TableNameRepository
```

## Common Patterns

### One-to-Many Relationship

**Parent (One):**
```python
children = relationship("Child", back_populates="parent", cascade="all, delete-orphan")
```

**Child (Many):**
```python
parent_id = Column(Integer, ForeignKey("parents.id"), nullable=False)
parent = relationship("Parent", back_populates="children")
```

### One-to-One Relationship

**User (One):**
```python
profile = relationship("Profile", back_populates="user", uselist=False, cascade="all, delete-orphan")
```

**Profile (One):**
```python
user_id = Column(Integer, ForeignKey("users.id"), nullable=False, unique=True)
user = relationship("User", back_populates="profile")
```

### Many-to-Many Relationship

**Association Table:**
```python
association_table = Table('association',
    Base.metadata,
    Column('left_id', ForeignKey('lefts.id')),
    Column('right_id', ForeignKey('rights.id'))
)
```

**Models:**
```python
class Left(Base):
    rights = relationship("Right", secondary=association_table, back_populates="lefts")

class Right(Base):
    lefts = relationship("Left", secondary=association_table, back_populates="rights")
```

## Best Practices

1. **Naming Conventions:**
   - Table: snake_case, số nhiều
   - Model: PascalCase, số ít
   - Foreign key: `{table_name_singular}_id`

2. **Timestamps:**
   - Luôn có `created_at` và `updated_at`
   - Dùng `datetime.utcnow` (không có ngoặc)

3. **Cascade:**
   - `cascade="all, delete-orphan"` cho one-to-many và one-to-one
   - Không dùng cascade cho many-to-many

4. **Nullable:**
   - Mặc định là `nullable=True`
   - Chỉ set `nullable=False` khi thực sự cần thiết

5. **Indexes:**
   - Thêm index cho foreign keys
   - Thêm index cho các trường thường được query

## Checklist

- [ ] Tạo model file với đầy đủ imports
- [ ] Định nghĩa enums (nếu có)
- [ ] Định nghĩa model class với đúng conventions
- [ ] Thêm relationships (nếu có)
- [ ] Export model trong `models/__init__.py`
- [ ] Tạo migration với `alembic revision --autogenerate`
- [ ] Review migration file
- [ ] Chạy migration với `alembic upgrade head`
- [ ] Tạo repository class với CRUD methods
- [ ] Verify database schema
- [ ] Test imports

## Anti-patterns

❌ **Không làm:**
- Quên thêm `created_at`, `updated_at`
- Dùng `datetime.utcnow()` với ngoặc trong default
- Model name số nhiều (phải số ít)
- Table name số ít (phải số nhiều)
- Quên commit trong repository
- Quên refresh sau create/update

✅ **Nên làm:**
- Follow conventions chặt chẽ
- Review migration trước khi chạy
- Test repository methods sau khi tạo
- Thêm docstrings cho các method phức tạp
