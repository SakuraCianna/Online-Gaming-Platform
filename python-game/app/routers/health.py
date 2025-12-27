from fastapi import APIRouter
from datetime import datetime

router = APIRouter()


@router.get("/health")
async def health_check():
    """健康检查接口"""
    return {
        "status": "UP",
        "service": "python-game-ai",
        "timestamp": datetime.now().isoformat()
    }
