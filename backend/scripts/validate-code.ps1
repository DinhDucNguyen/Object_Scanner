# Validate Code Quality - PowerShell Script
# Kiểm tra consistency và quality của codebase

Write-Host "🔍 Validating Code Quality..." -ForegroundColor Cyan

$hasErrors = $false

# 1. Check Python syntax
Write-Host "`n✓ Checking Python syntax..." -ForegroundColor Yellow
try {
    $pyFiles = Get-ChildItem -Path "app" -Filter "*.py" -Recurse -File
    foreach ($file in $pyFiles) {
        python -m py_compile $file.FullName 2>$null
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  ❌ Syntax error in: $($file.FullName)" -ForegroundColor Red
            $hasErrors = $true
        }
    }
    if (-not $hasErrors) {
        Write-Host "  ✅ All Python files syntax OK" -ForegroundColor Green
    }
} catch {
    Write-Host "  ❌ Error checking syntax: $_" -ForegroundColor Red
    $hasErrors = $true
}

# 2. Check imports
Write-Host "`n✓ Checking imports..." -ForegroundColor Yellow
try {
    python -c "from app.models import *" 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✅ Models import OK" -ForegroundColor Green
    } else {
        Write-Host "  ❌ Models import failed" -ForegroundColor Red
        $hasErrors = $true
    }
} catch {
    Write-Host "  ❌ Error checking imports: $_" -ForegroundColor Red
    $hasErrors = $true
}

# 3. Check database migrations
Write-Host "`n✓ Checking database migrations..." -ForegroundColor Yellow
try {
    alembic check 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✅ Migrations OK" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  Migration check failed (may need to run migrations)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ⚠️  Alembic not available or configured" -ForegroundColor Yellow
}

# 4. Check model consistency
Write-Host "`n✓ Checking model conventions..." -ForegroundColor Yellow
try {
    $modelCheck = @"
from app.db.session import Base
from app.models import *

errors = []
for model in Base.__subclasses__():
    table_name = model.__tablename__
    columns = [c.name for c in model.__table__.columns]
    
    if 'id' not in columns:
        errors.append(f'{model.__name__} missing id column')
    if 'created_at' not in columns:
        errors.append(f'{model.__name__} missing created_at column')
    if 'updated_at' not in columns:
        errors.append(f'{model.__name__} missing updated_at column')

if errors:
    for error in errors:
        print(f'ERROR: {error}')
    exit(1)
else:
    print('All models have required fields')
"@
    
    $result = python -c $modelCheck 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✅ $result" -ForegroundColor Green
    } else {
        Write-Host "  ❌ Model validation failed:" -ForegroundColor Red
        Write-Host "  $result" -ForegroundColor Red
        $hasErrors = $true
    }
} catch {
    Write-Host "  ❌ Error checking models: $_" -ForegroundColor Red
    $hasErrors = $true
}

# 5. Check API structure
Write-Host "`n✓ Checking API structure..." -ForegroundColor Yellow
try {
    $apiCheck = @"
from main import app
routes = [r.path for r in app.routes if hasattr(r, 'path')]
print(f'Total routes: {len(routes)}')
print(f'API routes: {len([r for r in routes if r.startswith("/api")])}')
"@
    
    $result = python -c $apiCheck 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✅ $result" -ForegroundColor Green
    } else {
        Write-Host "  ❌ API structure check failed" -ForegroundColor Red
        $hasErrors = $true
    }
} catch {
    Write-Host "  ❌ Error checking API: $_" -ForegroundColor Red
    $hasErrors = $true
}

# Summary
Write-Host "`n" + ("="*50) -ForegroundColor Cyan
if ($hasErrors) {
    Write-Host "❌ Validation completed with ERRORS" -ForegroundColor Red
    Write-Host "Please fix the issues above before committing." -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "✅ All validations PASSED!" -ForegroundColor Green
    Write-Host "Code quality looks good! 🎉" -ForegroundColor Green
    exit 0
}
