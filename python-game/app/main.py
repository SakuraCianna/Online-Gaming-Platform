from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import get_settings
from app.routers import health

settings = get_settings()

app = FastAPI(
    title="Game AI Service",
    description="游戏AI服务",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, tags=["健康检查"])


@app.get("/")
async def root():
    return {"message": "Game AI Service is running"}
