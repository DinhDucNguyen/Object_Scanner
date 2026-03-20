# GitHub Copilot Skills

Bộ skills tùy chỉnh cho dự án Object Language App, giúp tự động hóa các quy trình phát triển phổ biến.

## Available Skills

### 1. 📊 add-database-table

**Mô tả:** Tạo bảng database mới hoàn chỉnh với SQLAlchemy model, Alembic migration, và repository.

**Khi nào dùng:**
- Thêm table mới vào database
- Tạo entity mới trong hệ thống
- Mở rộng data model

**Cách dùng:**
```
/add-database-table user_badge với các trường: user_id, badge_id, earned_at
```

**Skill này sẽ tạo:**
- `app/models/{table_name}.py` - SQLAlchemy model
- Migration file với Alembic
- `app/repositories/{table_name}_repo.py` - Repository class
- Export trong `__init__.py`

---

### 2. 🚀 create-api-endpoint

**Mô tả:** Tạo REST API endpoint hoàn chỉnh với Router, Service, Schema, và Repository.

**Khi nào dùng:**
- Tạo API endpoint mới
- Implement CRUD operations
- Expose data qua HTTP API

**Cách dùng:**
```
/create-api-endpoint cho resource badges với GET list, GET detail, POST create
```

**Skill này sẽ tạo:**
- `app/schemas/{resource}.py` - Pydantic schemas (Create, Update, Response)
- `app/services/{resource}_service.py` - Business logic layer
- `app/routers/{resource}_router.py` - FastAPI routes
- Repository methods bổ sung (nếu cần)

---

### 3. ⚡ add-learning-feature

**Mô tả:** Implement feature mới hoàn chỉnh theo GSD methodology (SPEC → PLAN → EXECUTE → VERIFY → COMMIT).

**Khi nào dùng:**
- Implement feature/chức năng mới lớn
- Cần planning và tracking chi tiết
- Feature có nhiều components
- Đảm bảo quality cao

**Cách dùng:**
```
/add-learning-feature hệ thống badge và achievements cho người dùng
```

**Quy trình GSD:**
1. **SPEC** - Định nghĩa requirements rõ ràng
2. **PLAN** - Chia thành waves và tasks
3. **EXECUTE** - Implement từng wave
4. **VERIFY** - Chứng minh hoàn thành
5. **COMMIT** - Commit sạch sẽ với proof

---

### 4. ✅ validate-project

**Mô tả:** Kiểm tra consistency, conventions, và quality của codebase.

**Khi nào dùng:**
- Trước khi commit code
- Code review
- Quality check định kỳ
- Onboard team member mới

**Cách dùng:**
```
/validate-project all           # Validate toàn bộ
/validate-project models        # Chỉ validate models
/validate-project api          # Chỉ validate API
```

**Kiểm tra gì:**
- Naming conventions (models, tables, files)
- Required fields (id, created_at, updated_at)
- Architecture patterns (separation of concerns)
- Error handling
- Security (auth, validation)
- Code quality

**Script tự động:**
```powershell
# Chạy validation script
.\scripts\validate-code.ps1
```

---

## How to Use Skills

### Trong VS Code Chat

1. Mở GitHub Copilot Chat (`Ctrl+Alt+I` hoặc `Cmd+Shift+I`)
2. Gõ `/` để xem danh sách skills
3. Chọn skill và cung cấp thông tin cần thiết

**Ví dụ:**
```
/add-database-table achievement với các trường: name, description, icon_url, points
```

### Invoke Trực Tiếp

Bạn cũng có thể mention skill trong conversation:

```
Tôi cần tạo một bảng mới cho user badges. Use skill add-database-table.
```

---

## Skill Architecture

Mỗi skill được tổ chức theo cấu trúc:

```
.github/skills/{skill-name}/
├── SKILL.md           # Hướng dẫn chi tiết (required)
├── scripts/           # Executable scripts (optional)
├── references/        # Documentation (optional)
└── assets/           # Templates, boilerplate (optional)
```

### SKILL.md Structure

```markdown
---
name: skill-name
description: 'Mô tả ngắn gọn. Use when: trigger keywords'
argument-hint: 'Gợi ý parameter cho user'
---

# Skill Title

## When to Use
- Use case 1
- Use case 2

## Step-by-Step Procedure
1. Step 1
2. Step 2

## Best Practices
- Practice 1
- Practice 2

## Checklist
- [ ] Task 1
- [ ] Task 2
```

---

## Project Conventions

Skills được thiết kế dựa trên conventions của dự án:

### Architecture Pattern

```
Request → Router → Service → Repository → Database
```

- **Router**: Routing, validation, auth
- **Service**: Business logic, error handling
- **Repository**: Database operations only

### Naming Conventions

| Item | Convention | Example |
|------|-----------|---------|
| Model class | PascalCase, singular | `UserBadge` |
| Table name | snake_case, plural | `user_badges` |
| File name | snake_case | `user_badge.py` |
| Repository | `{Model}Repository` | `UserBadgeRepository` |
| Service | `{Resource}Service` | `BadgeService` |
| Router file | `{resource}_router.py` | `badge_router.py` |

### Required Model Fields

Mọi model phải có:
- `id` (primary key, autoincrement)
- `created_at` (TIMESTAMP, default=datetime.utcnow)
- `updated_at` (TIMESTAMP, onupdate=datetime.utcnow)

### Schema Pattern

- **Create**: Required fields, không có id/timestamps
- **Update**: All fields optional
- **Response**: All fields, có id/timestamps, `from_attributes = True`
- **ListResponse**: `{items, total, skip, limit}`

---

## GSD Methodology

Skills tuân thủ **GSD (Get Shit Done)** methodology:

### Core Protocol

```
SPEC → PLAN → EXECUTE → VERIFY → COMMIT
```

1. **SPEC**: Requirements rõ ràng, status FINALIZED
2. **PLAN**: Chia waves theo dependencies
3. **EXECUTE**: Implement atomic tasks
4. **VERIFY**: Empirical proof (không accept "looks good")
5. **COMMIT**: One task = one commit

### Proof Requirements

| Change Type | Required Proof |
|-------------|----------------|
| API endpoint | curl response with status |
| Database | Schema dump hoặc query |
| Business logic | Test output |
| UI | Screenshot |
| Config | Verification command |

### Wave Execution

Plans được chia thành waves:

- **Wave 1**: Foundation, no dependencies → parallel
- **Wave 2**: Depends on Wave 1 → wait then parallel
- **Wave 3**: Depends on Wave 2 → wait then parallel

---

## Quick Commands

### Validate Code
```powershell
# Validate toàn bộ project
.\scripts\validate-code.ps1

# Hoặc run từng phần
python -c "from app.models import *"
alembic check
pytest
```

### Generate Migration
```bash
alembic revision --autogenerate -m "add user_badges table"
alembic upgrade head
```

### Test API
```bash
# Start server
uvicorn main:app --reload

# Test endpoint
curl -X GET http://localhost:8000/api/badges
curl -X GET http://localhost:8000/docs
```

---

## Tips & Best Practices

### 1. Be Specific
Càng cung cấp thông tin chi tiết, skill càng hiệu quả:

❌ Không tốt:
```
/add-database-table badge
```

✅ Tốt:
```
/add-database-table user_badge với các trường:
- user_id (FK to users)
- badge_id (FK to badges)
- earned_at (timestamp)
- progress (float, nullable)
```

### 2. Use Skills Together

Skills được thiết kế để kết hợp:

```
1. /add-database-table user_badge ...
2. /create-api-endpoint badges ...
3. /validate-project all
```

### 3. Follow GSD for Complex Features

Nếu feature phức tạp (>3 components), dùng `add-learning-feature`:

```
/add-learning-feature hệ thống gamification với badges, achievements, và leaderboard
```

### 4. Validate Often

Chạy validation thường xuyên:
- Sau mỗi thay đổi lớn
- Trước khi commit
- Trước khi push

---

## Customization

### Modify Existing Skills

Edit file `SKILL.md` trong thư mục skill:

```bash
code .github/skills/add-database-table/SKILL.md
```

### Add New Skill

1. Tạo thư mục mới: `.github/skills/{skill-name}/`
2. Tạo `SKILL.md` với frontmatter:

```yaml
---
name: my-skill
description: 'Short description. Use when: keywords'
---
```

3. Thêm nội dung với sections:
   - When to Use
   - Step-by-Step Procedure
   - Best Practices
   - Checklist

### Disable Skill

Thêm vào frontmatter:

```yaml
user-invocable: false        # Ẩn khỏi slash commands
disable-model-invocation: true  # AI không tự load
```

---

## Troubleshooting

### Skill Không Hiện Trong Slash Commands

1. Kiểm tra tên thư mục = `name` trong frontmatter
2. Đảm bảo file tên là `SKILL.md` (chính xác)
3. Reload VS Code window

### Skill Không Được AI Load Automatically

Thêm keywords vào `description`:

```yaml
description: 'Create database table. Use when: add table, create model, new entity'
```

### Validation Script Fails

```powershell
# Check Python environment
python --version

# Check dependencies
pip install -r requirements.txt

# Check Alembic config
alembic current
```

---

## Resources

- **PROJECT_RULES.md** - GSD methodology chi tiết
- **GSD-STYLE.md** - Code style và conventions
- **docs/runbook.md** - Operational procedures
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [SQLAlchemy Documentation](https://docs.sqlalchemy.org/)
- [Alembic Tutorial](https://alembic.sqlalchemy.org/en/latest/tutorial.html)

---

## Contributing

Khi thêm skill mới:

1. Follow template trong existing skills
2. Include comprehensive examples
3. Add to this README
4. Test thoroughly
5. Document edge cases

---

**Made with ❤️ for efficient development**

Last updated: March 11, 2026
