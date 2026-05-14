import os
import logging
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from app.core.config import settings
from app.routers import auth_router, scan_router, review_router, collection_router, history_router, data_router, dictionary_router
from app.routers import admin_router
from app.routers import streak_router

logger = logging.getLogger("uvicorn.error")

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="API Backend cho ứng dụng nhận diện vật thể đa ngôn ngữ"
)

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    body = await request.body()
    logger.error(f"[422] {request.method} {request.url} | body={body!r} | errors={exc.errors()}")
    return JSONResponse(status_code=422, content={"detail": exc.errors()})

# Cấu hình CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ALLOW_ORIGINS,
    allow_credentials=True,
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
app.include_router(streak_router.router)

os.makedirs("uploads/scans", exist_ok=True)
os.makedirs("uploads/objects", exist_ok=True)
os.makedirs("uploads/avatars", exist_ok=True)
os.makedirs("uploads/tts", exist_ok=True)
app.mount("/uploads", StaticFiles(directory="uploads"), name="uploads")

@app.get("/")
def read_root():
    return {"message": f"Welcome to {settings.APP_NAME}"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
