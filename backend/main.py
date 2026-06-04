import os
import logging
from fastapi import FastAPI, Request

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("google_genai").setLevel(logging.WARNING)
logging.getLogger("google_genai.models").setLevel(logging.WARNING)
from fastapi.exceptions import RequestValidationError
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from app.core.config import settings
from app.core.limiter import limiter
from app.routers import auth_router, scan_router, review_router, collection_router, history_router, data_router, dictionary_router
from app.routers import admin_router, prediction_router, training_router
from app.routers import streak_router

logger = logging.getLogger("uvicorn.error")

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="API Backend cho ứng dụng nhận diện vật thể đa ngôn ngữ",
    docs_url="/docs" if settings.DEBUG else None,
    redoc_url="/redoc" if settings.DEBUG else None,
    openapi_url="/openapi.json" if settings.DEBUG else None,
)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    errors = []
    for error in exc.errors():
        sanitized = dict(error)
        sanitized.pop("input", None)
        if "ctx" in sanitized and "error" in sanitized["ctx"]:
            sanitized["ctx"] = dict(sanitized["ctx"])
            sanitized["ctx"]["error"] = str(sanitized["ctx"]["error"])
        errors.append(sanitized)
    logger.error("[422] %s %s | errors=%s", request.method, request.url.path, errors)
    return JSONResponse(status_code=422, content=jsonable_encoder({"detail": errors}))

cors_allow_origins = settings.CORS_ALLOW_ORIGINS
cors_allow_credentials = "*" not in cors_allow_origins

# Cấu hình CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_allow_origins,
    allow_credentials=cors_allow_credentials,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Đăng ký các routers
app.include_router(auth_router.router)
app.include_router(scan_router.router)
app.include_router(review_router.router)
app.include_router(collection_router.router)
app.include_router(history_router.router)
app.include_router(data_router.router)
app.include_router(dictionary_router.router)
app.include_router(admin_router.router, prefix="/api")
app.include_router(prediction_router.router, prefix="/api")
app.include_router(training_router.router, prefix="/api")
app.include_router(streak_router.router)

os.makedirs("uploads/scans", exist_ok=True)
os.makedirs("uploads/objects", exist_ok=True)
os.makedirs("uploads/avatars", exist_ok=True)
os.makedirs("uploads/tts", exist_ok=True)
os.makedirs("static/admin", exist_ok=True)
app.mount("/uploads", StaticFiles(directory="uploads"), name="uploads")
app.mount("/admin-panel", StaticFiles(directory="static/admin", html=True), name="admin-panel")

@app.get("/")
def read_root():
    return {"message": f"Welcome to {settings.APP_NAME}"}

@app.get("/health")
def health_check():
    return {"status": "ok"}

@app.get("/admin")
def admin_redirect():
    return RedirectResponse(url="/admin-panel/index.html")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
